from pathlib import Path
import sys
import unittest

import torch
from transformers import Qwen3Config, Qwen3ForCausalLM

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from constrained_decode import generate_constrained


class Grammar:
    """Alternating long forced spans and actual branches, with an EOS at the end."""
    eos = 31
    def allowed(self, generated):
        plan = [[2], [3], [4], [5, 6, 7], [8], [9], [10], [11, 12], [13], [14], [31]]
        return plan[len(generated)]
    def __call__(self, batch_id, tokens):
        return self.allowed(tokens[3:].tolist())


class DecoderTests(unittest.TestCase):
    def setUp(self):
        torch.manual_seed(8)
        torch.set_num_threads(1)
        self.model = Qwen3ForCausalLM(Qwen3Config(vocab_size=32, hidden_size=32, intermediate_size=64,
                                    num_hidden_layers=2, num_attention_heads=4, num_key_value_heads=2, head_dim=8))
        self.model.eval()
        self.prompt = torch.tensor([[20, 21, 22]])

    def test_greedy_choices_match_transformers_with_forced_token_batches(self):
        reference = self.model.generate(input_ids=self.prompt, attention_mask=torch.ones_like(self.prompt),
                        max_new_tokens=30, do_sample=False, use_cache=True,
                        eos_token_id=31, pad_token_id=31, prefix_allowed_tokens_fn=Grammar())
        actual = generate_constrained(self.model, self.prompt, Grammar(), 30, 31)
        self.assertTrue(torch.equal(reference, actual))

    def test_budget_stops_even_inside_a_forced_span(self):
        for budget in (1, 3, 4, 8):
            actual = generate_constrained(self.model, self.prompt, Grammar(), budget, 31)
            self.assertEqual(3 + budget, actual.shape[1])


if __name__ == "__main__":
    unittest.main()
