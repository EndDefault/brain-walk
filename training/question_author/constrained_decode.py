"""Greedy grammar decoding that prefills forced tokens in one model call.

Only deterministic structure is batched. Every material/focus branch still uses
the model's logits. This is a single-request Qwen experiment, not an Android API.
"""
import torch


@torch.inference_mode()
def generate_constrained(model, input_ids, grammar, max_new_tokens, eos_token_id):
    if input_ids.ndim != 2 or input_ids.shape[0] != 1 or max_new_tokens < 1:
        raise ValueError("Use one unpadded request and a positive token budget")
    device = input_ids.device
    prompt = input_ids[0].tolist()
    generated, pending, cache = [], list(prompt), None
    while len(generated) < max_new_tokens:
        allowed = grammar.allowed(generated)
        while len(allowed) == 1:
            token = allowed[0]
            generated.append(token)
            pending.append(token)
            if token == eos_token_id or len(generated) == max_new_tokens:
                return torch.tensor([prompt + generated], device=device)
            allowed = grammar.allowed(generated)
        output = model(input_ids=torch.tensor([pending], device=device), past_key_values=cache,
                       attention_mask=torch.ones((1, len(prompt) + len(generated)), device=device, dtype=torch.long),
                       use_cache=True, logits_to_keep=1)
        cache = output.past_key_values
        candidates = torch.tensor(allowed, dtype=torch.long, device=device)
        token = candidates[output.logits[0, -1, candidates].argmax()].item()
        generated.append(token)
        pending = [token]
        del output
        if token == eos_token_id:
            break
    return torch.tensor([prompt + generated], device=device)
