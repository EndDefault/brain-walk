"""Diagnostic: ask the same model to interpret a compact record summary first.

No teacher label enters the request. This probe alone is not a question-plan
evaluation, and its accuracy must not be reported as full question quality.
"""
import argparse
import json
from pathlib import Path
import time

import torch
from peft import PeftModel
from transformers import AutoModelForCausalLM, AutoTokenizer

from constrained_decode import generate_constrained
from constrained import segments
from contract import FOCUSES, TYPES, compact, expected_focus
from train import digest, load_rows, prompt_for

INSTRUCTION = ("Choose a focus for a memory game. Return exactly one label: COLOR, PICTURE, NUMBER, "
               "BALANCED, INSUFFICIENT_DATA. If ANY type has n < 12, choose INSUFFICIENT_DATA. "
               "Otherwise compare the three percentages: if the lowest is at least 20 percentage points "
               "below the second-lowest, choose the lowest type. Otherwise choose BALANCED. "
               "This is a game rule, not a health assessment. No explanation.")


def summary(context):
    return {kind: {"n": record["n"], "first_correct_percent":
                  round(100 * record["first_correct"] / record["n"], 4) if record["n"] else None}
            for kind, record in context["records"].items()}


def prompt(tokenizer, context, examples=False):
    messages = [{"role": "system", "content": INSTRUCTION}]
    if examples:
        fixtures = [([20, 20, 20], [15, 90, 85], "COLOR"),
                    ([20, 20, 20], [85, 20, 90], "PICTURE"),
                    ([20, 20, 20], [90, 80, 10], "NUMBER"),
                    ([20, 20, 20], [80, 85, 90], "BALANCED"),
                    ([20, 20, 20], [10, 15, 20], "BALANCED"),
                    ([20, 5, 20], [80, 20, 90], "INSUFFICIENT_DATA")]
        for ns, percentages, answer in fixtures:
            data = {kind: {"n": n, "first_correct_percent": rate} for kind, n, rate in zip(TYPES, ns, percentages)}
            messages.extend([{"role": "user", "content": compact(data)}, {"role": "assistant", "content": answer}])
    messages.append({"role": "user", "content": compact(summary(context))})
    return tokenizer.apply_chat_template(messages, tokenize=False, add_generation_prompt=True, enable_thinking=False)


class LabelGrammar:
    def __init__(self, tokenizer, prefix=None):
        prefix_ids = tokenizer.encode(prefix, add_special_tokens=False) if prefix is not None else []
        self.choices = [prefix_ids + tokenizer.encode(compact(label) if prefix is not None else label,
                                                     add_special_tokens=False) for label in FOCUSES]
        self.eos = tokenizer.eos_token_id
    def allowed(self, generated):
        matches = [tokens for tokens in self.choices if tokens[:len(generated)] == generated]
        if any(tokens == generated for tokens in matches):
            return [self.eos]
        if not matches:
            raise ValueError("Invalid focus-label token")
        return sorted({tokens[len(generated)] for tokens in matches})


def predict(model, tokenizer, context, examples=False, tool_context=False, record_rates=False):
    text = prompt_for(tokenizer, context, record_rates=record_rates) if tool_context else prompt(tokenizer, context, examples)
    inputs = tokenizer(text, return_tensors="pt", add_special_tokens=False).to(model.device)
    prefix = next(segments(context))[0] if tool_context else None
    output = generate_constrained(model, inputs.input_ids, LabelGrammar(tokenizer, prefix), 128, tokenizer.eos_token_id)
    label = tokenizer.decode(output[0, inputs.input_ids.shape[1]:], skip_special_tokens=True).strip()
    if prefix is not None:
        if not label.startswith(prefix):
            raise ValueError("Missing focus tool prefix")
        label = json.loads(label[len(prefix):])
    if label not in FOCUSES:
        raise ValueError("Invalid focus prediction")
    return label


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", type=Path, required=True)
    parser.add_argument("--adapter", type=Path, required=True)
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--examples", action="store_true")
    parser.add_argument("--tool-context", action="store_true")
    parser.add_argument("--record-rates", action="store_true")
    parser.add_argument("--keep-adapter", action="store_true")
    args = parser.parse_args()
    if args.output.exists():
        raise ValueError("Use a new report filename")
    torch.set_num_threads(8)
    tokenizer = AutoTokenizer.from_pretrained(args.base, local_files_only=True, trust_remote_code=False)
    model = AutoModelForCausalLM.from_pretrained(args.base, local_files_only=True, trust_remote_code=False,
                                                dtype=torch.bfloat16, attn_implementation="sdpa").to("cuda")
    model = PeftModel.from_pretrained(model, args.adapter, local_files_only=True)
    if not args.keep_adapter:
        model = model.merge_and_unload()
    model.eval()
    results = []
    start = time.perf_counter()
    for row in load_rows(args.data):
        label = predict(model, tokenizer, row["input"], args.examples, args.tool_context, args.record_rates)
        results.append({"scenario": row["scenario"], "expected": expected_focus(row["input"]), "predicted": label})
    report = {"kind": "focus_probe_not_full_plan", "examples": args.examples,
              "tool_context": args.tool_context, "record_rates": args.record_rates, "keep_adapter": args.keep_adapter,
              "adapter_sha256": digest(args.adapter / "adapter_model.safetensors"),
              "data_sha256": digest(args.data), "requests": len(results),
              "accuracy": sum(r["expected"] == r["predicted"] for r in results) / len(results),
              "seconds": time.perf_counter() - start, "results": results}
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in report.items() if k != "results"}), flush=True)
