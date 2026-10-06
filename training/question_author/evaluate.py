"""Measure raw or explicitly constrained outputs, retaining all failures."""
import argparse
from collections import Counter
from hashlib import sha256
import json
from pathlib import Path
import statistics
import time

import torch
from peft import PeftModel
from transformers import AutoModelForCausalLM, AutoTokenizer

from contract import expected_focus, parse_tool, quality, random_plan, validate_plan
from constrained import QuestionGrammar
from constrained_decode import generate_constrained
from train import digest, load_rows, prompt_for


def summarize(results):
    total = len(results)
    valid = [r for r in results if r["valid"]]
    needing_review = [r for r in results if r["review_expected"]]
    return {
        "requests": total, "structurally_valid": len(valid), "structural_valid_rate": len(valid) / total,
        "focus_accuracy": sum(r.get("focus_correct", False) for r in results) / total,
        "no_recent_or_repeated_target_rate": sum(r["valid"] and r["recent_targets"] == 0 and r["repeated_targets"] == 0 for r in results) / total,
        "confusion_review_cases": len(needing_review),
        "confusion_review_rate": sum(r.get("reviewed", False) for r in needing_review) / len(needing_review) if needing_review else None,
        "median_seconds": statistics.median(r["seconds"] for r in results),
        "max_seconds": max(r["seconds"] for r in results),
        "errors": dict(Counter(r["error"] for r in results if not r["valid"])),
        "focus_outcomes": dict(Counter(f"{r['focus_expected']} -> {r.get('focus_predicted', 'INVALID_PLAN')}" for r in results)),
    }


def assess(row, raw, seconds):
    context = row["input"]
    focus = expected_focus(context)
    pair = context["records"][focus]["confusion"] if focus in context["records"] else None
    result = {"learner_id": row["learner_id"], "scenario": row["scenario"],
              "option_count": context["option_count"], "seconds": seconds,
              "review_expected": bool(pair), "valid": False, "raw": raw, "focus_expected": focus}
    try:
        plan = validate_plan(context, parse_tool(raw))
        result["focus_predicted"] = plan["focus"]
        result.update(quality(context, plan), valid=True)
    except (ValueError, TypeError, KeyError, RecursionError) as error:
        result["error"] = str(error)[:160] or type(error).__name__
    return result


def run(args):
    if args.fast_constrained and not args.constrained:
        raise ValueError("--fast-constrained requires --constrained")
    if args.output.exists():
        raise ValueError("Evaluation report already exists; use a new filename")
    config = json.loads(Path(__file__).with_name("model_config.json").read_text())
    torch.set_num_threads(8)
    torch.manual_seed(config["training"]["seed"])
    if not torch.cuda.is_available():
        raise RuntimeError("This timing recipe is for the CUDA training PC")
    device = torch.device("cuda")
    dtype = torch.bfloat16 if torch.cuda.is_bf16_supported() else torch.float32
    tokenizer = AutoTokenizer.from_pretrained(args.base, local_files_only=True, trust_remote_code=False)
    model = AutoModelForCausalLM.from_pretrained(args.base, local_files_only=True, trust_remote_code=False,
                                                dtype=dtype, attn_implementation="sdpa").to(device)
    if args.adapter:
        model = PeftModel.from_pretrained(model, args.adapter, local_files_only=True).merge_and_unload()
    model.eval()
    rows = load_rows(args.data)
    if args.limit:
        rows = rows[:args.limit]
    if not rows:
        raise ValueError("Evaluation needs at least one request")
    adapter_hash = digest(args.adapter / "adapter_model.safetensors") if args.adapter else None
    results = []
    random_results = []
    from contract import tool_text
    for row in rows:
        prompt = prompt_for(tokenizer, row["input"], record_rates=args.record_rates)
        tokens = tokenizer(prompt, return_tensors="pt", add_special_tokens=False).to(device)
        start = time.perf_counter()
        torch.cuda.synchronize()
        constraints = {"prefix_allowed_tokens_fn": QuestionGrammar(tokenizer, row["input"], tokens.input_ids.shape[1])} if args.constrained else {}
        with torch.inference_mode():
            if args.fast_constrained:
                output = generate_constrained(model, tokens.input_ids, constraints["prefix_allowed_tokens_fn"],
                                              args.max_new_tokens, tokenizer.eos_token_id)
            else:
                output = model.generate(**tokens, max_new_tokens=args.max_new_tokens, do_sample=False,
                                        pad_token_id=tokenizer.eos_token_id, use_cache=True, **constraints)
        torch.cuda.synchronize()
        seconds = time.perf_counter() - start
        raw = tokenizer.decode(output[0, tokens.input_ids.shape[1]:], skip_special_tokens=True).strip()
        result = assess(row, raw, seconds)
        result["generated_tokens"] = output.shape[1] - tokens.input_ids.shape[1]
        result["token_limit_reached"] = result["generated_tokens"] >= args.max_new_tokens
        results.append(result)
        random_results.append(assess(row, tool_text(random_plan(row["input"], row["seed"] + 2)), 0.0))
        print(json.dumps({k: v for k, v in result.items() if k != "raw"}), flush=True)
        report = {
            "base_model": config["base_model"], "base_revision": config["base_revision"],
            "adapter": str(args.adapter) if args.adapter else None,
            "adapter_sha256": adapter_hash,
            "requested_rows": len(rows), "complete": len(results) == len(rows),
            "source": "synthetic_only", "data_sha256": sha256(args.data.read_bytes()).hexdigest(),
            "device": torch.cuda.get_device_name(0), "decoding": "deterministic_greedy_non_thinking",
            "constraints": "tool_json_count_catalog_and_no_target_repetition" if args.constrained else "none",
            "forced_token_prefill": args.fast_constrained,
            "record_rate_summary": args.record_rates,
            "max_new_tokens": args.max_new_tokens, "summary": summarize(results),
            "random_reference": summarize(random_results), "results": results,
            "per_scenario": {s: summarize([r for r in results if r["scenario"] == s]) for s in sorted({r["scenario"] for r in results})},
            "per_option_count": {str(n): summarize([r for r in results if r["option_count"] == n]) for n in sorted({r["option_count"] for r in results})},
            "tablet_offline_verified": False, "production_ready": False,
            "limitations": "Synthetic rule-authored instruction compliance only; no real-user benefit claim. Failed generations remain in denominators. GPU timing is not tablet timing.",
        }
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report["summary"], indent=2), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", type=Path, required=True)
    parser.add_argument("--adapter", type=Path)
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--limit", type=int)
    parser.add_argument("--max-new-tokens", type=int, default=1536)
    parser.add_argument("--constrained", action="store_true")
    parser.add_argument("--fast-constrained", action="store_true")
    parser.add_argument("--record-rates", action="store_true")
    run(parser.parse_args())
