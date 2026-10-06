"""A tiny local LoRA model must continue identically after an interrupted step."""
from pathlib import Path
import random
import sys
import tempfile
import unittest
from unittest.mock import patch

import torch
from peft import LoraConfig, get_peft_model, get_peft_model_state_dict, set_peft_model_state_dict
from transformers import Qwen3Config, Qwen3ForCausalLM

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from training_state import cpu_weights, load_state, restore_optimization, save_state


class TrainingStateTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / "resume.pt"
        self.cuda = patch("torch.cuda.is_available", return_value=False)
        self.cuda.start()
        self.addCleanup(self.cuda.stop)
        torch.set_num_threads(1)
        self.identity = {"data_sha256": "fixture", "recipe": {"seed": 51, "epochs": 2}}
        self.progress = {"global_step": 2, "epoch": 0, "next_row": 2}
        self.report = {"training_complete": False}

    def model(self):
        torch.manual_seed(51)
        random.seed(51)
        base = Qwen3ForCausalLM(Qwen3Config(vocab_size=32, hidden_size=16, intermediate_size=32,
                                          num_hidden_layers=1, num_attention_heads=2,
                                          num_key_value_heads=1, head_dim=8))
        model = get_peft_model(base, LoraConfig(r=2, lora_alpha=4, lora_dropout=.2,
                              target_modules=["q_proj", "v_proj"], task_type="CAUSAL_LM"))
        optimizer = torch.optim.AdamW([p for p in model.parameters() if p.requires_grad], lr=.01)
        scheduler = torch.optim.lr_scheduler.StepLR(optimizer, step_size=2, gamma=.8)
        model.train()
        return model, optimizer, scheduler

    @staticmethod
    def step(model, optimizer, scheduler):
        # Both Python and Torch RNG affect this sequence; dropout uses Torch RNG as well.
        length = random.choice([4, 5, 6])
        ids = torch.randint(0, 32, (1, length))
        optimizer.zero_grad(set_to_none=True)
        loss = model(input_ids=ids, labels=ids, use_cache=False).loss
        loss.backward()
        optimizer.step()
        scheduler.step()
        return loss.item()

    def test_resume_matches_uninterrupted_optimizer_dropout_and_rng(self):
        model, optimizer, scheduler = self.model()
        for _ in range(2):
            self.step(model, optimizer, scheduler)
        weights = cpu_weights(get_peft_model_state_dict(model))
        save_state(self.path, weights, weights, optimizer, scheduler,
                   self.identity, self.progress, self.report)
        expected_losses = [self.step(model, optimizer, scheduler) for _ in range(3)]
        expected = cpu_weights(get_peft_model_state_dict(model))
        resumed, new_optimizer, new_scheduler = self.model()
        state = load_state(self.path, self.identity)
        set_peft_model_state_dict(resumed, state["weights"])
        restore_optimization(state, new_optimizer, new_scheduler)
        actual_losses = [self.step(resumed, new_optimizer, new_scheduler) for _ in range(3)]
        self.assertEqual(expected_losses, actual_losses)
        for name, actual in get_peft_model_state_dict(resumed).items():
            self.assertTrue(torch.equal(expected[name], actual), name)
        self.assertEqual(scheduler.state_dict(), new_scheduler.state_dict())
        self.assertEqual(self.progress, state["progress"])

    def test_failed_save_preserves_last_complete_checkpoint(self):
        model, optimizer, scheduler = self.model()
        weights = get_peft_model_state_dict(model)
        save_state(self.path, weights, weights, optimizer, scheduler,
                   self.identity, self.progress, self.report)
        original = self.path.read_bytes()
        with patch("torch.save", side_effect=OSError("simulated disk interruption")):
            with self.assertRaises(OSError):
                save_state(self.path, weights, weights, optimizer, scheduler,
                           self.identity, self.progress, self.report)
        self.assertEqual(original, self.path.read_bytes())
        with self.assertRaisesRegex(ValueError, "does not match"):
            load_state(self.path, dict(self.identity, data_sha256="different"))

    def test_completed_runs_are_not_silently_retrained(self):
        model, optimizer, scheduler = self.model()
        weights = get_peft_model_state_dict(model)
        save_state(self.path, weights, weights, optimizer, scheduler,
                   self.identity, self.progress, {"training_complete": True})
        with self.assertRaisesRegex(ValueError, "already completed"):
            load_state(self.path, self.identity)


if __name__ == "__main__":
    unittest.main()
