"""Offline question-author contract. Contains no personal records or model code."""
import json
import random
import re

VERSION = "question-author-v1"
TYPES = ("COLOR", "PICTURE", "NUMBER")
FOCUSES = (*TYPES, "BALANCED", "INSUFFICIENT_DATA")
COLORS = ("BLUE", "ORANGE", "GREEN", "PURPLE")
PICTURES = ("CIRCLE", "SQUARE", "TRIANGLE", "STAR", "HEART", "MOON", "FLOWER", "HOUSE",
            "TREE", "FISH", "APPLE", "CUP", "KEY", "UMBRELLA", "BELL", "CAR")
CATALOG = {
    "COLOR": [f"C{i:02}" for i in range(16)],
    "PICTURE": [f"P{i:02}" for i in range(16)],
    "NUMBER": [f"N{i}" for i in range(10, 100)],
}


def assert_game_catalog(source):
    """Detect drift from the existing game's material definitions before training."""
    colors = re.search(r"enum class BaseColor.*?\{(.*?)\}", source, re.S)
    pictures = re.search(r"enum class PictureSymbol\s*\{(.*?)\}", source, re.S)
    number_range = re.search(r"require\(value in (\d+)\.\.(\d+)\)", source)
    if not colors or tuple(re.findall(r"\b([A-Z_]+)\(0x", colors.group(1))) != COLORS:
        raise ValueError("Game color catalog changed; review the model contract")
    if not pictures or tuple(re.findall(r"\b[A-Z][A-Z_]+\b", pictures.group(1))) != PICTURES:
        raise ValueError("Game picture catalog changed; review the model contract")
    if not number_range or number_range.groups() != ("10", "99"):
        raise ValueError("Game number range changed; review the model contract")

SYSTEM = """You compose a 10-question offline memory game using the supplied materials.
Read the context; call submit_question_plan exactly once. Do not output prose.
The app fixes slot types and all time/choice limits. Never change these.
Each question is an array: target ID FIRST, then distinct distractor IDs.
Its length must equal option_count. Use only the IDs of that slot's type.
COLOR: C00..C15 are ordered color pairs, index=left*4+right; colors in order
BLUE, ORANGE, GREEN, PURPLE. PICTURE: P00..P15 in the given symbol order.
NUMBER: N10..N99 are two-digit numbers. Never invent IDs or repeat an option.
Choose focus from COLOR, PICTURE, NUMBER, BALANCED, INSUFFICIENT_DATA.
With fewer than 12 comparable observations in any type, use INSUFFICIENT_DATA.
Otherwise focus on the lowest first-correct rate only if it is at least 0.20
below the next-lowest type; else BALANCED. Slow correct answers alone do not
prove a weak type. Counts describe this game only, not a medical condition.
For an evidenced focus, revisit its recorded confusion target at least once,
with the confused alternative included among distractors. Mix other targets too.
Do not repeat targets within a type in this game or any listed recent target.
The confusion target in these requests is eligible (not a recent target).
All other target/distractor combinations are allowed. Do not add more slots
of the focus type. The app, not you, shuffles options and scores answers.
"""

TOOLS = [{"type": "function", "function": {
    "name": "submit_question_plan",
    "description": "Propose one complete plan for app validation; does not change stored records.",
    "parameters": {
        "type": "object", "additionalProperties": False,
        "properties": {
            "focus": {"type": "string", "enum": list(FOCUSES)},
            "questions": {"type": "array", "minItems": 10, "maxItems": 10,
                          "items": {"type": "array", "items": {"type": "string"}}},
        },
        "required": ["focus", "questions"],
    },
}}]


def compact(value):
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def messages(context):
    return [{"role": "system", "content": SYSTEM},
            {"role": "user", "content": compact(context)}]


def tool_text(plan):
    return "<tool_call>\n" + compact({"name": "submit_question_plan", "arguments": plan}) + "\n</tool_call>"


def parse_tool(text):
    """No extraction/repair of arbitrary prose; accept exactly one bounded tool call."""
    if not isinstance(text, str) or len(text) > 16000:
        raise ValueError("response_size")
    text = text.strip()
    if not text.startswith("<tool_call>") or not text.endswith("</tool_call>"):
        raise ValueError("tool_envelope")
    payload = text[len("<tool_call>"):-len("</tool_call>")].strip()

    def unique_object(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError("duplicate_json_key")
            result[key] = value
        return result

    data = json.loads(payload, object_pairs_hook=unique_object,
                      parse_constant=lambda _: (_ for _ in ()).throw(ValueError("non_finite_json")))
    if not isinstance(data, dict) or set(data) != {"name", "arguments"}:
        raise ValueError("tool_fields")
    if data["name"] != "submit_question_plan":
        raise ValueError("unknown_tool")
    return data["arguments"]


def validate_context(context):
    """Fail closed if a training/evaluation request would change the app contract."""
    if context.get("contract") != VERSION:
        raise ValueError("contract_version")
    count = context.get("option_count")
    if type(count) is not int or count not in (4, 6, 9, 12, 16):
        raise ValueError("option_count")
    slots = context.get("slots")
    if not isinstance(slots, list) or len(slots) != 10 or sorted(slots.count(t) for t in TYPES) != [3, 3, 4]:
        raise ValueError("slot_plan")
    if any(t not in TYPES for t in slots):
        raise ValueError("slot_type")
    records = context.get("records")
    if not isinstance(records, dict) or set(records) != set(TYPES):
        raise ValueError("record_types")
    for kind, record in records.items():
        n, correct = record.get("n"), record.get("first_correct")
        if type(n) is not int or type(correct) is not int or not 0 <= correct <= n <= 1000:
            raise ValueError("record_counts")
        if type(record.get("mean_correct_ms")) is not int or record["mean_correct_ms"] < 0:
            raise ValueError("record_time")
        recent = record.get("recent")
        if not isinstance(recent, list) or len(recent) > 4 or any(x not in CATALOG[kind] for x in recent):
            raise ValueError("recent_targets")
        confusion = record.get("confusion")
        if confusion is not None:
            if not isinstance(confusion, list) or len(confusion) != 3:
                raise ValueError("confusion_shape")
            target, other, errors = confusion
            if target not in CATALOG[kind] or other not in CATALOG[kind] or target == other:
                raise ValueError("confusion_materials")
            if type(errors) is not int or not 1 <= errors <= n - correct or target in recent:
                raise ValueError("confusion_evidence")


def validate_plan(context, plan):
    """Structural correctness only; educational utility is evaluated separately."""
    validate_context(context)
    if not isinstance(plan, dict) or set(plan) != {"focus", "questions"}:
        raise ValueError("plan_fields")
    if plan["focus"] not in FOCUSES:
        raise ValueError("focus_value")
    questions = plan["questions"]
    if not isinstance(questions, list) or len(questions) != 10:
        raise ValueError("question_count")
    for kind, options in zip(context["slots"], questions):
        if not isinstance(options, list) or len(options) != context["option_count"]:
            raise ValueError("choice_count")
        if any(not isinstance(item, str) or item not in CATALOG[kind] for item in options):
            raise ValueError("material_type_or_id")
        if len(set(options)) != len(options):
            raise ValueError("duplicate_material")
    return plan


def expected_focus(context):
    """Synthetic curriculum label, not a validated measure of a person's ability."""
    records = context["records"]
    if any(r["n"] < 12 for r in records.values()):
        return "INSUFFICIENT_DATA"
    ordered = sorted(TYPES, key=lambda t: records[t]["first_correct"] / records[t]["n"])
    first, second = (records[t] for t in ordered[:2])
    # Integer comparison avoids a float boundary at exactly a 0.20 gap.
    gap_numerator = second["first_correct"] * first["n"] - first["first_correct"] * second["n"]
    return ordered[0] if gap_numerator * 5 >= first["n"] * second["n"] else "BALANCED"


def quality(context, plan):
    focus = expected_focus(context)
    seen = {t: set() for t in TYPES}
    repeated = recent = 0
    reviewed = False
    pair = context["records"][focus]["confusion"] if focus in TYPES else None
    for kind, options in zip(context["slots"], plan["questions"]):
        target = options[0]
        repeated += target in seen[kind]
        recent += target in context["records"][kind]["recent"]
        seen[kind].add(target)
        if kind == focus and pair and target == pair[0] and pair[1] in options[1:]:
            reviewed = True
    return {"focus_correct": plan["focus"] == focus, "repeated_targets": repeated,
            "recent_targets": recent, "review_expected": bool(pair), "reviewed": reviewed}


def teacher_plan(context, seed):
    """Author-generated teaching policy. Its labels are hypotheses, not user data."""
    validate_context(context)
    rng = random.Random(seed)
    focus = expected_focus(context)
    used = {t: set() for t in TYPES}
    questions = []
    for kind in context["slots"]:
        record = context["records"][kind]
        available = [x for x in CATALOG[kind] if x not in record["recent"] and x not in used[kind]]
        pair = record["confusion"] if kind == focus and not used[kind] else None
        target = pair[0] if pair else rng.choice(available)
        others = [x for x in CATALOG[kind] if x != target]
        rng.shuffle(others)
        if pair:
            others.remove(pair[1])
            others.insert(0, pair[1])
        questions.append([target, *others[:context["option_count"] - 1]])
        used[kind].add(target)
    plan = {"focus": focus, "questions": questions}
    validate_plan(context, plan)
    return plan


def random_plan(context, seed):
    rng = random.Random(seed)
    return {"focus": "BALANCED", "questions": [rng.sample(CATALOG[t], context["option_count"]) for t in context["slots"]]}
