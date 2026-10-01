"""Warm-start the saved adapter, then support full step-boundary resumption.

The old epoch-1 snapshot has no optimizer state. Its first continuation therefore
uses a new optimizer/schedule; subsequent --resume calls restore the full state.
"""
import argparse
import json
import math
from pathlib import Path
import random
import subprocess
import sys
import time

import torch
from peft import PeftModel, get_peft_model_state_dict, set_peft_model_state_dict
from transformers import AutoModelForCausalLM, AutoTokenizer, get_cosine_schedule_with_warmup

from prepare_base import verify
from train import digest, load_rows
from decision_loss import encode_rows, loss_for, validation_loss
from training_state import cpu_weights, load_state, restore_optimization, save_state


def write_report(folder, report):
    temporary = folder / "training_report.tmp"
    temporary.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    temporary.replace(folder / "training_report.json")


def run(args):
    if not 1 <= args.epochs <= 20 or args.checkpoint_every < 1:
        raise ValueError("Invalid epoch count/checkpoint interval")
    if args.output.exists() and any(args.output.iterdir()) and not args.resume:
        raise ValueError("Use a new output directory, or --resume its matching checkpoint")
    if args.resume and not (args.output / "resume.pt").is_file():
        raise ValueError("No full resume checkpoint in output directory")
    if (args.output / "STOP_REQUESTED").exists():
        raise ValueError("Remove STOP_REQUESTED only when ready to resume")
    config = json.loads(Path(__file__).with_name("model_config.json").read_text())
    recipe = dict(config["training"], epochs=args.epochs,
                  supervision="class_balanced_focus_v1" if args.focus_only else "grammar_aligned_decisions_v1",
                  record_rates=args.record_rates, focus_only=args.focus_only,
                  focus_weight=1. if args.focus_only else 30., review_weight=0. if args.focus_only else 12.,
                  target_weight=0. if args.focus_only else 3.)
    base_manifest = json.loads(args.base_manifest.read_text(encoding="utf-8-sig"))
    verify(args.base, base_manifest, config)
    parent = json.loads((args.initial_adapter / "training_report.json").read_text())
    adapter_config = json.loads((args.initial_adapter / "adapter_config.json").read_text())
    parent_hash = digest(args.initial_adapter / "adapter_model.safetensors")
    if (parent.get("source") != "synthetic_only" or parent.get("real_user_records_used") is not False
            or parent.get("adapter_sha256") != parent_hash
            or parent.get("base_revision") != config["base_revision"]
            or adapter_config.get("base_model_name_or_path") != config["base_model"]
            or adapter_config.get("revision") != config["base_revision"]
            or parent.get("lora") != config["lora"]):
        raise ValueError("Parent adapter provenance/configuration mismatch")
    hashes = {s: digest(args.data / f"{s}.jsonl") for s in ("train", "validation", "test")}
    if hashes != parent["data_sha256"]:
        raise ValueError("This continuation requires the original disjoint data splits")
    source_hashes = {name: digest(Path(__file__).with_name(name)) for name in
                     ("continue_training.py", "training_state.py", "decision_loss.py", "constrained.py", "train.py", "contract.py")}
    identity = {"parent_adapter_sha256": parent_hash, "base_revision": config["base_revision"],
                "data_sha256": hashes, "recipe": recipe, "lora": config["lora"],
                "source_sha256": source_hashes, "torch": str(torch.__version__)}
    state = load_state(args.output / "resume.pt", identity) if args.resume else None
    torch.manual_seed(recipe["seed"])
    random.seed(recipe["seed"])
    torch.set_num_threads(8)
    if not torch.cuda.is_available():
        raise RuntimeError("The measured continuation recipe requires CUDA")
    device = torch.device("cuda")
    dtype = torch.bfloat16 if torch.cuda.is_bf16_supported() else torch.float32
    tokenizer = AutoTokenizer.from_pretrained(args.base, local_files_only=True, trust_remote_code=False)
    train_rows, valid_rows = (load_rows(args.data / f"{s}.jsonl") for s in ("train", "validation"))
    if {r["learner_id"] for r in train_rows} & {r["learner_id"] for r in valid_rows}:
        raise ValueError("Learner leakage across training and validation")
    train, valid = (encode_rows(tokenizer, rows, recipe["max_length"], record_rates=args.record_rates,
                               focus_only=args.focus_only) for rows in (train_rows, valid_rows))
    model = AutoModelForCausalLM.from_pretrained(args.base, local_files_only=True, trust_remote_code=False,
                                                dtype=dtype, attn_implementation="sdpa").to(device)
    model = PeftModel.from_pretrained(model, args.initial_adapter, is_trainable=True, local_files_only=True)
    model.peft_config["default"].base_model_name_or_path = config["base_model"]
    model.peft_config["default"].revision = config["base_revision"]
    model.config.use_cache = False
    model.gradient_checkpointing_enable(gradient_checkpointing_kwargs={"use_reentrant": False})
    trainable = [p for p in model.parameters() if p.requires_grad]
    optimizer = torch.optim.AdamW(trainable, lr=recipe["learning_rate"], weight_decay=.01)
    accumulation = recipe["gradient_accumulation"]
    total_steps = math.ceil(len(train) / accumulation) * recipe["epochs"]
    scheduler = get_cosine_schedule_with_warmup(optimizer, num_warmup_steps=max(1, total_steps // 10),
                                               num_training_steps=total_steps)
    args.output.mkdir(parents=True, exist_ok=True)
    if state:
        report, progress, best_weights = state["report"], state["progress"], state["best_weights"]
        set_peft_model_state_dict(model, best_weights)
        model.save_pretrained(args.output / "adapter", safe_serialization=True)
        set_peft_model_state_dict(model, state["weights"])
        restore_optimization(state, optimizer, scheduler)
        del state
    else:
        initial_loss = validation_loss(model, valid, device)
        best_weights = cpu_weights(get_peft_model_state_dict(model))
        model.save_pretrained(args.output / "adapter", safe_serialization=True)
        report = {
            "base": config["base_model"], "base_revision": config["base_revision"],
            "base_weights_sha256": parent["base_weights_sha256"], "training": recipe, "lora": config["lora"],
            "parent_adapter_sha256": parent_hash,
            "parent_completed_epochs": parent.get("parent_completed_epochs", 0) + parent["selected_epoch"],
            "continuation_mode": "warm_start_new_optimizer_then_full_state_checkpoints",
            "gpu": torch.cuda.get_device_name(0), "torch": str(torch.__version__), "dtype": str(dtype),
            "trainable_parameters": sum(p.numel() for p in trainable), "seed": recipe["seed"],
            "source": "synthetic_only", "real_user_records_used": False, "production_ready": False,
            "training_complete": False, "epochs": [], "selected_epoch": 0,
            "initial_validation_loss": initial_loss, "data_sha256": hashes, "source_sha256": source_hashes,
        }
        progress = {"epoch": 0, "next_row": 0, "global_step": 0, "loss_sum": 0., "loss_tokens": 0,
                    "best_validation_loss": initial_loss, "elapsed_seconds": 0.}
    freeze = subprocess.check_output([sys.executable, "-m", "pip", "freeze"], text=True)
    (args.output / "environment-lock.txt").write_text(freeze, encoding="utf-8")
    report.update(training_status="RUNNING", optimizer_state_saved=True, exact_training_resume_available=True)
    start, elapsed_before = time.perf_counter(), progress["elapsed_seconds"]

    def checkpoint(status):
        progress["elapsed_seconds"] = elapsed_before + time.perf_counter() - start
        report.update(training_status=status, elapsed_seconds=progress["elapsed_seconds"],
                      completed_epochs=len(report["epochs"]), global_step=progress["global_step"],
                      peak_cuda_memory_bytes=torch.cuda.max_memory_allocated(),
                      adapter_sha256=digest(args.output / "adapter" / "adapter_model.safetensors"))
        save_state(args.output / "resume.pt", get_peft_model_state_dict(model), best_weights,
                   optimizer, scheduler, identity, progress, report)
        write_report(args.output, report)

    checkpoint("RUNNING")
    print(json.dumps({"initial_validation_loss": report["initial_validation_loss"], "resumed": args.resume,
                      "epoch": progress["epoch"], "next_row": progress["next_row"], "total_steps": total_steps}), flush=True)
    for epoch in range(progress["epoch"], recipe["epochs"]):
        order = list(range(len(train)))
        # This is a fresh optimizer run, with deterministic new epoch orders.
        random.Random(recipe["seed"] + parent["selected_epoch"] + epoch).shuffle(order)
        model.train()
        for batch_start in range(progress["next_row"], len(order), accumulation):
            group = order[batch_start:batch_start + accumulation]
            optimizer.zero_grad(set_to_none=True)
            for index in group:
                row = train[index]
                loss = loss_for(model, row, device) * row[3]
                if not torch.isfinite(loss):
                    raise ValueError("Non-finite training loss")
                tokens = sum(row[2])
                progress["loss_sum"] += loss.item() * tokens
                progress["loss_tokens"] += tokens
                (loss / len(group)).backward()
                del loss
            torch.nn.utils.clip_grad_norm_(trainable, 1.0)
            optimizer.step()
            scheduler.step()
            optimizer.zero_grad(set_to_none=True)
            progress["global_step"] += 1
            progress["next_row"] = min(batch_start + accumulation, len(order))
            stop = (args.output / "STOP_REQUESTED").exists() or (
                args.stop_after_steps is not None and progress["global_step"] >= args.stop_after_steps)
            if stop or progress["global_step"] % args.checkpoint_every == 0:
                checkpoint("PAUSED" if stop else "RUNNING")
                # Avoid keeping large, differently sized cached logit buffers on an 8GB GPU.
                torch.cuda.empty_cache()
                print(json.dumps({"epoch": epoch + 1, "rows": progress["next_row"],
                                  "global_step": progress["global_step"], "status": report["training_status"],
                                  "loss": progress["loss_sum"] / progress["loss_tokens"],
                                  "elapsed_s": progress["elapsed_seconds"]}), flush=True)
            if stop:
                return
        val = validation_loss(model, valid, device)
        entry = {"epoch": epoch + 1, "train_loss": progress["loss_sum"] / progress["loss_tokens"],
                 "validation_loss": val}
        report["epochs"].append(entry)
        if val < progress["best_validation_loss"]:
            progress["best_validation_loss"] = val
            best_weights = cpu_weights(get_peft_model_state_dict(model))
            model.save_pretrained(args.output / "adapter", safe_serialization=True)
            report["selected_epoch"] = epoch + 1
        progress.update(epoch=epoch + 1, next_row=0, loss_sum=0., loss_tokens=0)
        checkpoint("RUNNING")
        torch.cuda.empty_cache()
        print(json.dumps(entry), flush=True)
    report["training_complete"] = True
    checkpoint("COMPLETED")
    print(json.dumps({"training_complete": True, "selected_additional_epoch": report["selected_epoch"],
                      "adapter_sha256": report["adapter_sha256"]}), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", type=Path, required=True)
    parser.add_argument("--base-manifest", type=Path, required=True)
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--initial-adapter", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--epochs", type=int, default=2)
    parser.add_argument("--checkpoint-every", type=int, default=5)
    parser.add_argument("--resume", action="store_true")
    parser.add_argument("--record-rates", action="store_true", help="Supply percentages derived from counts, never teacher labels")
    parser.add_argument("--focus-only", action="store_true", help="Class-balanced focus supervision; no end-of-answer token")
    parser.add_argument("--stop-after-steps", type=int, help="Orderly stop for a resume smoke test")
    run(parser.parse_args())
