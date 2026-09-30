"""Generate reproducible synthetic learners; never read a device/Room database."""
import argparse
from collections import Counter
from hashlib import sha256
import json
from pathlib import Path
import random

from contract import CATALOG, PICTURES, TYPES, VERSION, assert_game_catalog, compact, teacher_plan, validate_context

SCENARIOS = ("cold_start", "sparse", "color_weak", "picture_weak", "number_weak",
             "all_low", "all_strong", "slow_correct", "close_rates", "unequal_counts")
SPLIT_SEEDS = {"train": 417203, "validation": 983651, "test": 261947}


def make_example(split, index):
    seed = SPLIT_SEEDS[split] * 10000 + index
    rng = random.Random(seed)
    scenario = SCENARIOS[index % len(SCENARIOS)]
    counts = [0, 0, 0] if scenario == "cold_start" else [rng.randint(12, 40) for _ in TYPES]
    if scenario == "sparse":
        counts[rng.randrange(3)] = rng.randint(0, 11)
    rates = [rng.uniform(.70, .98) for _ in TYPES]
    if scenario in ("all_strong", "slow_correct"):
        rates = [rng.uniform(.84, .98) for _ in TYPES]
    if scenario.endswith("_weak"):
        rates[TYPES.index(scenario.removesuffix("_weak").upper())] = rng.uniform(.15, .40)
    elif scenario == "all_low":
        rates = [rng.uniform(.1, .25) for _ in TYPES]
    elif scenario == "close_rates":
        base = rng.uniform(.4, .8)
        rates = [base + rng.uniform(-.04, .04) for _ in TYPES]
    elif scenario == "unequal_counts":
        counts = [12, 40, 80]
        rng.shuffle(counts)
        rates = [rng.uniform(.55, .85) for _ in TYPES]
    records = {}
    for i, kind in enumerate(TYPES):
        materials = rng.sample(CATALOG[kind], 4)
        target, alternative = materials[:2]
        if kind == "NUMBER" and rng.random() < .7:
            options = [n for n in range(12, 99) if n % 10 and n // 10 != n % 10]
            n = rng.choice(options)
            target, alternative = f"N{n}", f"N{int(str(n)[::-1])}"
            materials = [target, alternative, *rng.sample([x for x in CATALOG[kind] if x not in (target, alternative)], 2)]
        n = counts[i]
        correct = min(n, round(n * rates[i]))
        errors = n - correct
        records[kind] = {
            "n": n, "first_correct": correct,
            "mean_correct_ms": rng.randint(14000, 28000) if scenario == "slow_correct" else rng.randint(1200, 11000),
            "confusion": [target, alternative, min(errors, rng.randint(1, 6))] if errors else None,
            "recent": materials[2:] if n else [],
        }
    extra = TYPES[rng.randrange(3)]
    slots = [t for t in TYPES for _ in range(4 if t == extra else 3)]
    rng.shuffle(slots)
    # Stagger option counts independently from scenario, keeping all five stages represented.
    option_count = (4, 6, 9, 12, 16)[(index // len(SCENARIOS) + index % len(SCENARIOS)) % 5]
    context = {"contract": VERSION, "option_count": option_count, "slots": slots,
               "picture_symbols_in_id_order": list(PICTURES), "records": records}
    validate_context(context)
    return {"learner_id": f"synthetic-{split}-{index:05}", "scenario": scenario,
            "source": "authored_simulation_not_real_people", "seed": seed,
            "input": context, "expected": teacher_plan(context, seed + 1)}


def build(output, sizes):
    output.mkdir(parents=True, exist_ok=True)
    summary = {"contract": VERSION, "source": "synthetic_only", "split_unit": "learner",
               "limitations": "Rule-authored targets teach the output contract; no evidence of clinical or real-user benefit.",
               "splits": {}}
    all_inputs = set()
    for split, count in sizes.items():
        rows = [make_example(split, i) for i in range(count)]
        for row in rows:
            key = sha256(compact(row["input"]).encode()).hexdigest()
            if key in all_inputs:
                raise ValueError("Duplicate request across learner splits")
            all_inputs.add(key)
        raw = "".join(compact(row) + "\n" for row in rows).encode("utf-8")
        (output / f"{split}.jsonl").write_bytes(raw)
        summary["splits"][split] = {"rows": len(rows), "sha256": sha256(raw).hexdigest(),
                                    "scenarios": dict(Counter(row["scenario"] for row in rows)),
                                    "option_counts": dict(Counter(row["input"]["option_count"] for row in rows))}
    (output / "manifest.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    return summary


if __name__ == "__main__":
    game_source = Path(__file__).resolve().parents[2] / "app/src/main/java/com/example/memorysteps/game/MemoryItem.kt"
    assert_game_catalog(game_source.read_text(encoding="utf-8"))
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--train", type=int, default=300)
    parser.add_argument("--validation", type=int, default=30)
    parser.add_argument("--test", type=int, default=50)
    args = parser.parse_args()
    sizes = {"train": args.train, "validation": args.validation, "test": args.test}
    if any(n < 10 or n > 10000 for n in sizes.values()):
        parser.error("Each split needs 10..10000 synthetic learners")
    print(json.dumps(build(args.output, sizes), indent=2))
