import copy
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from build_data import make_example
from contract import FOCUSES, compact
from focus_probe import LabelGrammar, summary


class Characters:
    eos_token_id = 0
    def encode(self, text, add_special_tokens=False):
        return list(map(ord, text))


class FocusProbeTests(unittest.TestCase):
    def test_summary_contains_observations_without_teacher_labels(self):
        context = make_example("validation", 4)["input"]
        original = copy.deepcopy(context)
        data = summary(context)
        self.assertEqual(set(context["records"]), set(data))
        for kind, record in context["records"].items():
            self.assertEqual({"n", "first_correct_percent"}, set(data[kind]))
            self.assertEqual(record["n"], data[kind]["n"])
            self.assertEqual(round(100 * record["first_correct"] / record["n"], 4), data[kind]["first_correct_percent"])
        self.assertEqual(original, context)

    def test_label_grammar_accepts_every_model_choice_and_rejects_other_tokens(self):
        for prefix in (None, '<tool_call>\n{"focus":'):
            for label in FOCUSES:
                grammar = LabelGrammar(Characters(), prefix)
                text = label if prefix is None else prefix + compact(label)
                generated = []
                for token in map(ord, text):
                    self.assertIn(token, grammar.allowed(generated))
                    generated.append(token)
                self.assertEqual([0], grammar.allowed(generated))
            with self.assertRaises(ValueError):
                LabelGrammar(Characters(), prefix).allowed([999999])


if __name__ == "__main__":
    unittest.main()
