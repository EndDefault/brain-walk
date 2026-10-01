"""LoRA supervised fine-tuning on explicitly synthetic, grouped learner fixtures.

Only assistant tool-call tokens contribute to loss. The original base weights
remain frozen. No device DB, remote training API, telemetry, or hub upload.
"""
import argparse
from hashlib import sha256
import json
import math
from pathlib import Path
import random
import subprocess
import time

import torch
from peft import LoraConfig, get_peft_model
from transformers import AutoModelForCausalLM, AutoTokenizer, get_cosine_schedule_with_warmup

from contract import TOOLS, messages, tool_text, validate_plan


def digest(path):
    h = sha256()
    with Path(path).open("rb") as file:
        for part in iter(lambda: file.read(8 * 1024 * 1024), b""):
            h.update(part)
    return h.hexdigest()


def prompt_for(tokenizer, context, record_rates=False):
    return tokenizer.apply_chat_template(messages(context, record_rates=record_rates), tools=TOOLS, tokenize=False,
                                         add_generation_prompt=True, enable_thinking=False)


def load_rows(path):
    rows = [json.loads(line) for line in Path(path).read_text(encoding="utf-8").splitlines() if line]
    for row in rows:
        if row.get("source") != "authored_simulation_not_real_people":
            raise ValueError("This recipe accepts only the authored synthetic fixtures")
        validate_plan(row["input"], row["expected"])
    return rows


def encode_rows(tokenizer, rows, max_length):
    encoded = []
    for row in rows:
        prompt = prompt_for(tokenizer, row["input"])
        prompt_ids = tokenizer.encode(prompt, add_special_tokens=False)
        answer_ids = tokenizer.encode(tool_text(row["expected"]) + tokenizer.eos_token, add_special_tokens=False)
        ids = prompt_ids + answer_ids
        if len(ids) > max_length:
            raise ValueError(f"Refusing to truncate {row['learner_id']}: {len(ids)} > {max_length}")
        encoded.append((ids, [-100] * len(prompt_ids) + answer_ids))
    return encoded


def tensor_batch(row, device):
    ids, labels = row
    ids = torch.tensor([ids], dtype=torch.long, device=device)
    return {"input_ids": ids, "attention_mask": torch.ones_like(ids),
            "labels": torch.tensor([labels], dtype=torch.long, device=device)}


@torch.inference_mode()
def validation_loss(model, encoded, device):
    model.eval()
    total, tokens = 0.0, 0
    for row in encoded:
        n = sum(x != -100 for x in row[1])
        loss = model(**tensor_batch(row, device)).loss.item()
        if not math.isfinite(loss):
            raise ValueError("Non-finite validation loss")
        total += loss * n
        tokens += n
    return total / tokens


def run(args):
    if args.output.exists() and any(args.output.iterdir()):
        raise ValueError("Use a new output directory; previous experiments are not overwritten")
    config_path = Path(__file__).with_name("model_config.json")
    config = json.loads(config_path.read_text(encoding="utf-8"))
    train_config = config["training"]
    torch.manual_seed(train_config["seed"])
    random.seed(train_config["seed"])
    torch.set_num_threads(8)
    if not torch.cuda.is_available():
        raise RuntimeError("This measured recipe requires CUDA; no silent long CPU training")
    device = torch.device("cuda")
    dtype = torch.bfloat16 if torch.cuda.is_bf16_supported() else torch.float32
    base_manifest = json.loads(args.base_manifest.read_text(encoding="utf-8-sig"))
    expected_weight = next(f["lfs"]["sha256"] for f in base_manifest["siblings"] if f["rfilename"] == "model.safetensors")
    if base_manifest["sha"] != config["base_revision"] or digest(args.base / "model.safetensors") != expected_weight:
        raise ValueError("Base model revision/checksum mismatch")
    tokenizer = AutoTokenizer.from_pretrained(args.base, local_files_only=True, trust_remote_code=False)
    train_rows = load_rows(args.data / "train.jsonl")
    valid_rows = load_rows(args.data / "validation.jsonl")
    if {r["learner_id"] for r in train_rows} & {r["learner_id"] for r in valid_rows}:
        raise ValueError("Learner leakage across splits")
    train = encode_rows(tokenizer, train_rows, train_config["max_length"])
    valid = encode_rows(tokenizer, valid_rows, train_config["max_length"])
    print(json.dumps({"train_rows": len(train), "validation_rows": len(valid),
                      "max_tokens": max(len(row[0]) for row in train + valid), "dtype": str(dtype)}), flush=True)
    model = AutoModelForCausalLM.from_pretrained(args.base, local_files_only=True, trust_remote_code=False,
                                                dtype=dtype, attn_implementation="sdpa").to(device)
    model.config.use_cache = False
    lc = config["lora"]
    model = get_peft_model(model, LoraConfig(r=lc["rank"], lora_alpha=lc["alpha"],
                            lora_dropout=lc["dropout"], target_modules=lc["target_modules"],
                            bias="none", task_type="CAUSAL_LM"))
    model.peft_config["default"].base_model_name_or_path = config["base_model"]
    model.peft_config["default"].revision = config["base_revision"]
    model.gradient_checkpointing_enable(gradient_checkpointing_kwargs={"use_reentrant": False})
    trainable = [p for p in model.parameters() if p.requires_grad]
    optimizer = torch.optim.AdamW(trainable, lr=train_config["learning_rate"], weight_decay=.01)
    accumulation = train_config["gradient_accumulation"]
    total_steps = math.ceil(len(train) / accumulation) * train_config["epochs"]
    scheduler = get_cosine_schedule_with_warmup(optimizer, num_warmup_steps=max(1, total_steps // 10),
                                               num_training_steps=total_steps)
    args.output.mkdir(parents=True, exist_ok=True)
    report = {"base": config["base_model"], "base_revision": config["base_revision"],
              "base_weights_sha256": expected_weight, "training": train_config, "lora": lc,
              "gpu": torch.cuda.get_device_name(0), "torch": torch.__version__, "dtype": str(dtype),
              "trainable_parameters": sum(p.numel() for p in trainable), "seed": train_config["seed"],
              "source": "synthetic_only", "real_user_records_used": False,
              "production_ready": False, "epochs": [],
              "data_sha256": {s: digest(args.data / f"{s}.jsonl") for s in ("train", "validation", "test")},
              "source_sha256": {p.name: digest(p) for p in Path(__file__).parent.glob("*.py")}}
    report["initial_validation_loss"] = validation_loss(model, valid, device)
    print(json.dumps({"initial_validation_loss": report["initial_validation_loss"]}), flush=True)
    best = report["initial_validation_loss"]
    start = time.perf_counter()
    for epoch in range(train_config["epochs"]):
        order = list(range(len(train)))
        random.Random(train_config["seed"] + epoch).shuffle(order)
        model.train()
        loss_sum, loss_tokens = 0.0, 0
        for batch_start in range(0, len(order), accumulation):
            group = order[batch_start:batch_start + accumulation]
            optimizer.zero_grad(set_to_none=True)
            for i in group:
                row = train[i]
                loss = model(**tensor_batch(row, device)).loss
                if not torch.isfinite(loss):
                    raise ValueError("Non-finite training loss")
                n = sum(x != -100 for x in row[1])
                loss_sum += loss.item() * n
                loss_tokens += n
                (loss / len(group)).backward()
            torch.nn.utils.clip_grad_norm_(trainable, 1.0)
            optimizer.step()
            scheduler.step()
            if batch_start % (accumulation * 5) == 0:
                print(json.dumps({"epoch": epoch + 1, "rows": min(batch_start + accumulation, len(order)),
                                  "loss": round(loss_sum / loss_tokens, 5),
                                  "elapsed_s": round(time.perf_counter() - start, 1)}), flush=True)
        val = validation_loss(model, valid, device)
        entry = {"epoch": epoch + 1, "train_loss": loss_sum / loss_tokens, "validation_loss": val}
        report["epochs"].append(entry)
        if val < best:
            best = val
            model.save_pretrained(args.output / "adapter", safe_serialization=True)
            report["selected_epoch"] = epoch + 1
        report["elapsed_seconds"] = time.perf_counter() - start
        report["peak_cuda_memory_bytes"] = torch.cuda.max_memory_allocated()
        (args.output / "training_report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(entry), flush=True)
    if "selected_epoch" not in report:
        raise RuntimeError("Training did not improve validation loss; no adapter selected")
    # Only LoRA weights and config are exported here. Full base weights stay in local cache.
    report["adapter_sha256"] = digest(args.output / "adapter" / "adapter_model.safetensors")
    (args.output / "training_report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    freeze = subprocess.check_output([__import__("sys").executable, "-m", "pip", "freeze"], text=True)
    (args.output / "environment-lock.txt").write_text(freeze, encoding="utf-8")
    print(json.dumps({"selected_epoch": report["selected_epoch"], "elapsed_seconds": report["elapsed_seconds"],
                      "adapter_sha256": report["adapter_sha256"]}), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", type=Path, required=True)
    parser.add_argument("--base-manifest", type=Path, required=True)
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    run(parser.parse_args())
