"""Atomic local checkpoints at optimizer-step boundaries; no base weights or DB."""
import os
from pathlib import Path
import random

import torch


def cpu_weights(weights):
    return {name: value.detach().cpu().clone() for name, value in weights.items()}


def save_state(path, weights, best_weights, optimizer, scheduler, identity, progress, report):
    path = Path(path)
    payload = {
        "version": 1, "identity": identity, "progress": progress, "report": report,
        "weights": cpu_weights(weights), "best_weights": cpu_weights(best_weights),
        "optimizer": optimizer.state_dict(), "scheduler": scheduler.state_dict(),
        "python_rng": random.getstate(), "torch_rng": torch.get_rng_state(),
        "cuda_rng": torch.cuda.get_rng_state_all() if torch.cuda.is_available() else [],
    }
    temporary = path.with_suffix(".tmp")
    path.parent.mkdir(parents=True, exist_ok=True)
    with temporary.open("wb") as stream:
        torch.save(payload, stream)
        stream.flush()
        os.fsync(stream.fileno())
    os.replace(temporary, path)


def load_state(path, identity):
    state = torch.load(path, map_location="cpu", weights_only=True)
    if state.get("version") != 1 or state.get("identity") != identity:
        raise ValueError("Resume checkpoint does not match model, data, recipe or training source")
    if state["report"].get("training_complete"):
        raise ValueError("This run already completed; use its adapter for a new warm start")
    return state


def restore_optimization(state, optimizer, scheduler):
    optimizer.load_state_dict(state["optimizer"])
    scheduler.load_state_dict(state["scheduler"])
    random.setstate(state["python_rng"])
    torch.set_rng_state(state["torch_rng"])
    if state["cuda_rng"]:
        if not torch.cuda.is_available() or len(state["cuda_rng"]) != torch.cuda.device_count():
            raise ValueError("CUDA device count changed since checkpoint")
        torch.cuda.set_rng_state_all(state["cuda_rng"])
