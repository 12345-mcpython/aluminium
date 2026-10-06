"""Resolve the mutation anchors for Pearl (1503) into line numbers, and rewrite them into
tools/mut_pearl_traces.py. Line numbers shift every time the file is edited, and a stale one reads as a
SURVIVING MUTANT - which is exactly the confusion this script exists to remove.
"""
import json
import pathlib
import re

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
MUT = pathlib.Path("tools/mut_pearl_traces.py")

# name -> (search text, replacement). The line is located by scanning the file, not by being memorised.
ANCHORS = {
    "insight_threshold": ('"self_attr:DEFENCE >= 2400"', '"self_attr:DEFENCE >= 3400"'),
    "insight_per_point": ('"percent": 0.0003,', '"percent": 0.0006,'),
    "insight_base": ('"amount": 0.32,', '"amount": 0.42,'),
    "insight_cap": ('"cap_amount": 0.68', '"cap_amount": 0.98'),
    "insight_healing": ('"percent": 0.2,', '"percent": 0.3,'),
    "tolerance_gate": ('"self_resource:好活当赏 >= 1"', '"self_resource:好活当赏 >= 999"'),
    "tolerance_percent": ('"percent": 0.5,', '"percent": 0.4,'),
    "eidolon6_percent": ('"percent": 0.2,', '"percent": 0.3,'),
    "eidolon6_gate": ('"self has_state 深度学习"', '"self has_state 美学底本"'),
}

# For the anchors whose text appears more than once, the rule id whose block the line must fall inside.
OWNER = {
    "insight_per_point": "trace_insight_raises_elation_at_2400_defence",
    "insight_base": "trace_insight_raises_elation_at_2400_defence",
    "insight_cap": "trace_insight_raises_elation_at_2400_defence",
    "insight_threshold": "trace_insight_raises_elation_at_2400_defence",
    "insight_healing": "trace_insight_links_healing_to_elation",
    "tolerance_gate": "trace_tolerance_raises_effect_resistance_while_holding",
    "tolerance_percent": "trace_tolerance_raises_effect_resistance_while_holding",    "eidolon6_percent": "eidolon6_raises_all_type_resistance_penetration",
    "eidolon6_gate": "eidolon6_raises_all_type_resistance_penetration",
}


def rule_span(lines, rule_id):
    """The [start, end] line range (1-based, inclusive) of one rule object, found by brace depth.

    The scan starts at the opening brace ABOVE the id line, because the rule object begins there: starting at
    the id line itself balances to zero on the very next line and reports a one-line "rule".
    """
    id_line = None
    for i, line in enumerate(lines):
        if f'"id": "{rule_id}"' in line:
            id_line = i
            break
    if id_line is None:
        raise SystemExit(f"rule {rule_id} not found")
    start = id_line
    while start > 0 and "{" not in lines[start - 1]:
        start -= 1
    start = max(0, start - 1)
    depth = 0
    for i in range(start, len(lines)):
        depth += lines[i].count("{") - lines[i].count("}")
        if depth == 0:
            return start + 1, i + 1
    raise SystemExit(f"unterminated rule {rule_id}")

def main():
    lines = TARGET.read_text(encoding="utf-8").splitlines()
    resolved = {}
    for name, (needle, replacement) in ANCHORS.items():
        if name in OWNER:
            lo, hi = rule_span(lines, OWNER[name])
        else:
            lo, hi = 1, len(lines)
        hits = [i + 1 for i in range(lo - 1, hi) if lines[i].strip() == needle]
        if len(hits) != 1:
            raise SystemExit(f"{name}: expected exactly one hit inside {lo}-{hi}, found {hits}")
        resolved[name] = (hits[0], needle, replacement)

    # Rewrite the EDITS dict in the mutation script from the resolved numbers.
    text = MUT.read_text(encoding="utf-8")
    blocks = []
    for name, (line_no, needle, replacement) in resolved.items():
        blocks.append(f'    "{name}": ({line_no}, {needle!r}, {replacement!r}),')
    body = "EDITS = {\n" + "\n".join(blocks) + "\n}\n"
    text = re.sub(r"EDITS = \{.*?\n\}\n", body, text, flags=re.S)
    MUT.write_text(text, encoding="utf-8")

    for name, (line_no, needle, _) in resolved.items():
        print(f"{name:20s} line {line_no:4d}  {needle}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
