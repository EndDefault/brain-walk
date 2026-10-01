from pathlib import Path
import sys
import unittest

import torch
import torch.nn.functional as F
from transformers import AutoTokenizer, Qwen3Config, Qwen3ForCausalLM

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from build_data import make_example
from constrained import QuestionGrammar
from contract import tool_text
from decision_loss import decision_spans, encode_answer, loss_for


class DecisionLossTests(unittest.TestCase):
    def test_answer_only_logits_equal_full_sequence_weighted_causal_loss(self):
        torch.manual_seed(37)
        torch.set_num_threads(1)
        model = Qwen3ForCausalLM(Qwen3Config(vocab_size=32, hidden_size=16, intermediate_size=32,
                                           num_hidden_layers=1, num_attention_heads=2,
                                           num_key_value_heads=1, head_dim=8))
        model.eval()
        ids = [2, 3, 4, 5, 6, 7]
        row = (ids, 3, [30., 3., 1.])
        actual = loss_for(model, row, torch.device("cpu"))
        full = model(input_ids=torch.tensor([ids]), use_cache=False).logits
        losses = F.cross_entropy(full[0, 2:-1].float(), torch.tensor(ids[3:]), reduction="none")
        expected = (losses * torch.tensor(row[2])).sum() / sum(row[2])
        torch.testing.assert_close(actual, expected)
        actual.backward()
        self.assertTrue(torch.isfinite(model.lm_head.weight.grad).all())

    def test_decision_weights_target_focus_and_evidenced_review(self):
        row = make_example("train", 2)
        answer = tool_text(row["expected"])
        spans = decision_spans(row["input"], row["expected"])
        self.assertEqual('"COLOR"', answer[spans[0][0]:spans[0][1]])
        self.assertEqual(30., spans[0][2])
        review = [answer[a:b].strip('"') for a, b, weight in spans if weight == 12.]
        self.assertEqual(row["input"]["records"]["COLOR"]["confusion"][:2], review)

    def test_training_tokens_follow_actual_inference_grammar(self):
        base = Path(__file__).resolve().parents[3] / ".artifacts/question-ai/base"
        if not (base / "tokenizer.json").is_file():
            self.skipTest("Pinned tokenizer is required for this integration test")
        tokenizer = AutoTokenizer.from_pretrained(base, local_files_only=True, trust_remote_code=False)
        for i in range(10):
            row = make_example("validation", i)
            ids, weights = encode_answer(tokenizer, row["input"], row["expected"])
            self.assertEqual(tool_text(row["expected"]), tokenizer.decode(ids[:-1]))
            self.assertEqual(len(ids), len(weights))
            self.assertIn(30., weights)
            grammar = QuestionGrammar(tokenizer, row["input"], 0)
            for j, token in enumerate(ids):
                self.assertIn(token, grammar.allowed(ids[:j]))


if __name__ == "__main__":
    unittest.main()
