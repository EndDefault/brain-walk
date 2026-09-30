import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from build_data import build, make_example
from contract import CATALOG, TYPES, assert_game_catalog, expected_focus, parse_tool, quality, tool_text, validate_context, validate_plan
from constrained import QuestionGrammar


class ContractTests(unittest.TestCase):
    def setUp(self):
        self.row = make_example("validation", 2)
        self.context = copy.deepcopy(self.row["input"])
        self.plan = copy.deepcopy(self.row["expected"])

    def test_materials_match_existing_android_game(self):
        source = (Path(__file__).resolve().parents[3] / "app/src/main/java/com/example/memorysteps/game/MemoryItem.kt").read_text(encoding="utf-8")
        assert_game_catalog(source)
        with self.assertRaises(ValueError):
            assert_game_catalog(source.replace("BLUE(0x", "RED(0x"))
        with self.assertRaises(ValueError):
            assert_game_catalog(source.replace("CIRCLE, SQUARE", "SQUARE, CIRCLE"))
        with self.assertRaises(ValueError):
            assert_game_catalog(source.replace("10..99", "0..99"))

    def test_synthetic_labels_and_plans_across_all_stages(self):
        for i in range(100):
            row = make_example("train", i)
            plan = validate_plan(row["input"], parse_tool(tool_text(row["expected"])))
            result = quality(row["input"], plan)
            self.assertTrue(result["focus_correct"])
            self.assertEqual(0, result["repeated_targets"])
            self.assertEqual(0, result["recent_targets"])
            if result["review_expected"]:
                self.assertTrue(result["reviewed"])

    def test_invalid_outputs_are_rejected(self):
        def reject(change):
            plan = copy.deepcopy(self.plan)
            change(plan)
            with self.assertRaises((ValueError, TypeError)):
                validate_plan(self.context, plan)
        reject(lambda p: p.update(memory_ms=1))
        reject(lambda p: p.update(focus="diagnosis"))
        reject(lambda p: p["questions"].pop())
        reject(lambda p: p["questions"][0].pop())
        reject(lambda p: p["questions"][0].__setitem__(1, p["questions"][0][0]))
        reject(lambda p: p["questions"][0].__setitem__(0, "N100"))
        reject(lambda p: p["questions"][0].__setitem__(0, 12))
        kind = self.context["slots"][0]
        other = next(t for t in TYPES if t != kind)
        reject(lambda p: p["questions"][0].__setitem__(0, CATALOG[other][0]))

    def test_strict_tool_envelope_and_duplicate_json_keys(self):
        invalid = ["prose " + tool_text(self.plan), tool_text(self.plan) * 2,
                   tool_text(self.plan).replace("submit_question_plan", "delete_records"),
                   '<tool_call>{"name":"submit_question_plan","name":"bad","arguments":{}}</tool_call>',
                   '<tool_call>{"name":"submit_question_plan","arguments":NaN}</tool_call>']
        for text in invalid:
            with self.assertRaises(ValueError):
                parse_tool(text)

    def test_slow_correct_is_not_labeled_weakness(self):
        for r in self.context["records"].values():
            r.update(n=20, first_correct=18, confusion=None)
        self.context["records"]["COLOR"]["mean_correct_ms"] = 30000
        self.assertEqual("BALANCED", expected_focus(self.context))

    def test_rates_not_raw_correct_counts(self):
        for kind, n, correct in [("COLOR", 12, 10), ("PICTURE", 80, 64), ("NUMBER", 40, 10)]:
            self.context["records"][kind].update(n=n, first_correct=correct, confusion=None)
        self.assertEqual("NUMBER", expected_focus(self.context))

    def test_sparse_records_do_not_claim_a_weak_type(self):
        self.context["records"]["NUMBER"].update(n=2, first_correct=0, confusion=None)
        self.assertEqual("INSUFFICIENT_DATA", expected_focus(self.context))

    def test_context_rejects_illegal_counts_and_slots(self):
        self.context["slots"][0] = "UNKNOWN"
        with self.assertRaises(ValueError):
            validate_context(self.context)
        self.context = copy.deepcopy(self.row["input"])
        self.context["records"]["COLOR"]["first_correct"] = 1001
        with self.assertRaises(ValueError):
            validate_context(self.context)

    def test_dataset_is_reproducible_and_learners_do_not_cross_splits(self):
        with tempfile.TemporaryDirectory() as folder:
            one, two = Path(folder) / "one", Path(folder) / "two"
            sizes = {"train": 50, "validation": 10, "test": 10}
            self.assertEqual(build(one, sizes), build(two, sizes))
            groups = []
            for split in sizes:
                rows = [json.loads(line) for line in (one / f"{split}.jsonl").read_text().splitlines()]
                groups.append({r["learner_id"] for r in rows})
                self.assertEqual(10, len({r["scenario"] for r in rows}))
            for i in range(len(groups)):
                for j in range(i + 1, len(groups)):
                    self.assertFalse(groups[i] & groups[j])

    def test_grammar_keeps_material_decisions_separate_from_validity(self):
        class Characters:
            eos_token_id = 0
            def encode(self, text, add_special_tokens=False):
                return list(map(ord, text))
        for i in range(10):
            context = make_example("test", i)["input"]
            grammar = QuestionGrammar(Characters(), context, 0)
            generated = []
            for _ in range(10000):
                allowed = grammar.allowed(generated)
                if allowed == [0]:
                    break
                generated.append(allowed[0])
            else:
                self.fail("Grammar never finished")
            plan = validate_plan(context, parse_tool("".join(map(chr, generated))))
            metrics = quality(context, plan)
            self.assertEqual(0, metrics["recent_targets"])
            self.assertEqual(0, metrics["repeated_targets"])

    def test_grammar_rejects_tokens_outside_tool_contract(self):
        class Characters:
            eos_token_id = 0
            def encode(self, text, add_special_tokens=False):
                return list(map(ord, text))
        grammar = QuestionGrammar(Characters(), self.context, 0)
        with self.assertRaises(ValueError):
            grammar.allowed([ord("!")])


if __name__ == "__main__":
    unittest.main()
