"""Emphasize context decisions over arbitrary distractor ordering in supervision.

Training uses the same token boundaries as constrained generation. No expected
focus or confusion answer is supplied to the inference-time grammar.
"""
import re
from collections import Counter

import torch
import torch.nn.functional as F

from constrained import segments
from contract import TYPES, compact, tool_text
from train import prompt_for


def decision_spans(context, plan, focus_weight=30., review_weight=12., target_weight=3.):
    answer = tool_text(plan)
    focus_start = answer.index('"focus":') + len('"focus":')
    spans = [(focus_start, focus_start + len(compact(plan["focus"])), focus_weight)]
    materials = list(re.finditer(r'"(?:[CP][0-9]{2}|N[0-9]{2})"', answer))
    focus = plan["focus"]
    pair = context["records"][focus]["confusion"] if focus in TYPES else None
    count = context["option_count"]
    for row_index, options in enumerate(plan["questions"]):
        first = materials[row_index * count]
        spans.append((*first.span(), target_weight))
        if pair and context["slots"][row_index] == focus and options[0] == pair[0]:
            for index in (0, options.index(pair[1])):
                spans.append((*materials[row_index * count + index].span(), review_weight))
    return spans


def encode_answer(tokenizer, context, plan):
    answer = tool_text(plan)
    spans = decision_spans(context, plan)
    grammar = segments(context)
    choices = next(grammar)
    position, ids, weights = 0, [], []
    while True:
        matches = [choice for choice in choices if answer.startswith(choice, position)]
        if len(matches) != 1:
            raise ValueError("Teacher answer does not uniquely follow the generation grammar")
        text = matches[0]
        encoded = tokenizer(text, add_special_tokens=False, return_offsets_mapping=True)
        ids.extend(encoded["input_ids"])
        for start, end in encoded["offset_mapping"]:
            weights.append(max([1.] + [weight for low, high, weight in spans
                                      if position + start < high and position + end > low]))
        position += len(text)
        try:
            choices = grammar.send(text)
        except StopIteration:
            break
    if position != len(answer):
        raise ValueError("Unconsumed teacher output")
    return ids + [tokenizer.eos_token_id], weights + [1.]


def encode_rows(tokenizer, rows, max_length, record_rates=False, focus_only=False):
    result = []
    counts = Counter(row["expected"]["focus"] for row in rows)
    for row in rows:
        prompt = tokenizer.encode(prompt_for(tokenizer, row["input"], record_rates=record_rates), add_special_tokens=False)
        if focus_only:
            prefix = next(segments(row["input"]))[0]
            prefix_ids = tokenizer.encode(prefix, add_special_tokens=False)
            focus_ids = tokenizer.encode(compact(row["expected"]["focus"]), add_special_tokens=False)
            # No EOS is taught here: normal question generation must follow the focus.
            answer, weights = prefix_ids + focus_ids, [0.] * len(prefix_ids) + [1.] * len(focus_ids)
        else:
            answer, weights = encode_answer(tokenizer, row["input"], row["expected"])
        if len(prompt) + len(answer) > max_length:
            raise ValueError("Refusing to truncate decision-weighted training input")
        example_weight = len(rows) / (len(counts) * counts[row["expected"]["focus"]]) if focus_only else 1.
        result.append((prompt + answer, len(prompt), weights, example_weight))
    return result


def loss_for(model, row, device):
    ids, prompt_length, weights = row[:3]
    # Predict only answer positions. Prompt tokens still condition the transformer,
    # but their unused vocabulary logits no longer consume training memory.
    tokens = torch.tensor([ids], dtype=torch.long, device=device)
    supervised = [index for index, weight in enumerate(weights) if weight > 0]
    if not supervised:
        raise ValueError("No supervised answer positions")
    offsets = torch.tensor(supervised, device=device)
    positions = offsets + prompt_length - 1
    output = model(input_ids=tokens, attention_mask=torch.ones_like(tokens),
                   logits_to_keep=positions, use_cache=False)
    targets = tokens[0, offsets + prompt_length]
    per_token = F.cross_entropy(output.logits[0].float(), targets, reduction="none")
    factors = torch.tensor([weights[index] for index in supervised], dtype=per_token.dtype, device=device)
    return (per_token * factors).sum() / factors.sum()


@torch.inference_mode()
def validation_loss(model, encoded, device):
    model.eval()
    total, denominator = 0., 0.
    for row in encoded:
        weight = sum(row[2]) * (row[3] if len(row) > 3 else 1.)
        loss = loss_for(model, row, device)
        if not torch.isfinite(loss):
            raise ValueError("Non-finite validation loss")
        total += loss.item() * weight
        denominator += weight
    return total / denominator
