"""Token constraints for the offline experiment; not an Android runtime binding.

The model chooses the focus and material IDs. Code supplies tool/JSON structure,
type/choice-count constraints, and avoids repeated/recent targets. It does NOT
choose the weak type or force the recorded confusion into the model's answer.
Report these constrained results separately from raw generation.
"""
import json

from contract import CATALOG, FOCUSES, TYPES, compact, validate_context


def segments(context):
    yield ['<tool_call>\n{"name":"submit_question_plan","arguments":{"focus":']
    yield [compact(focus) for focus in FOCUSES]
    yield [',"questions":[']
    used = {kind: set() for kind in TYPES}
    for slot, kind in enumerate(context["slots"]):
        yield ["[" if slot == 0 else ",["]
        chosen = []
        for index in range(context["option_count"]):
            if index:
                yield [","]
            allowed = [item for item in CATALOG[kind] if item not in chosen]
            if index == 0:
                allowed = [item for item in allowed if item not in used[kind] and item not in context["records"][kind]["recent"]]
            value = yield [compact(item) for item in allowed]
            chosen.append(json.loads(value))
        used[kind].add(chosen[0])
        yield ["]"]
    yield [']}}\n</tool_call>']


class QuestionGrammar:
    """Single-request, one-beam prefix_allowed_tokens_fn for Transformers."""
    def __init__(self, tokenizer, context, prompt_length):
        validate_context(context)
        self.tokenizer = tokenizer
        self.prompt_length = prompt_length
        self.iterator = segments(context)
        self.consumed = []
        self.current = []
        self.done = False
        self._set_choices(next(self.iterator))

    def _set_choices(self, choices):
        self.choices = [(text, self.tokenizer.encode(text, add_special_tokens=False)) for text in choices]
        if not self.choices or any(not tokens for _, tokens in self.choices):
            raise ValueError("Empty grammar branch")
        if len({tuple(tokens) for _, tokens in self.choices}) != len(self.choices):
            raise ValueError("Ambiguous tokenization")
        self.current = []

    def allowed(self, generated):
        if generated[:len(self.consumed)] != self.consumed:
            raise ValueError("Grammar requires one monotonic generation (no beam search)")
        for token in generated[len(self.consumed):]:
            if self.done:
                raise ValueError("Tokens after complete tool call")
            self.current.append(token)
            self.consumed.append(token)
            matches = [(text, tokens) for text, tokens in self.choices if tokens[:len(self.current)] == self.current]
            if not matches:
                raise ValueError("Generated token outside the grammar")
            complete = [(text, tokens) for text, tokens in matches if len(tokens) == len(self.current)]
            if complete:
                if len(matches) != 1:
                    raise ValueError("Prefix-ambiguous grammar choice")
                try:
                    self._set_choices(self.iterator.send(complete[0][0]))
                except StopIteration:
                    self.done = True
            else:
                self.choices = matches
        if self.done:
            return [self.tokenizer.eos_token_id]
        return sorted({tokens[len(self.current)] for _, tokens in self.choices})

    def __call__(self, batch_id, input_ids):
        if batch_id != 0:
            raise ValueError("Use one independent grammar per request")
        return self.allowed(input_ids[self.prompt_length:].tolist())
