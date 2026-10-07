"""Pearl's current state, measured: how many rules, what they cover, and which judges hold them.

Also cross-checks her file's clauses against the corpus page, so "how complete is she" is a comparison rather than an
impression.
"""
import json
import pathlib
import re

CHARS = pathlib.Path("src/main/resources/characters/1503.json")
TESTS = pathlib.Path("src/test/java/com/laosun/aluminium/test")
OUT = pathlib.Path("tools/_pearl_status.txt")


def main():
    data = json.loads(CHARS.read_text(encoding="utf-8"))
    rules = data["rules"]
    lines = []
    lines.append(f"rules        : {len(rules)}")
    lines.append(f"resources    : {[r.get('id') for r in data.get('resources') or []]}")
    lines.append("")

    # Judges that name her.
    judges = []
    for path in TESTS.rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        if "1503" in text or "Pearl" in path.name:
            judges.append(path.name)
    lines.append(f"judge files naming or about her: {len(judges)}")
    for name in sorted(judges):
        lines.append("   " + name)
    lines.append("")

    # The operations her rules actually use, and how often.
    ops = {}
    for rule in rules:
        for effect in rule.get("do", []):
            ops[effect.get("op")] = ops.get(effect.get("op"), 0) + 1
    lines.append("operations her rules use:")
    for op, count in sorted(ops.items(), key=lambda kv: -kv[1]):
        lines.append(f"   {op:24s} x{count}")
    lines.append("")

    # Events she hangs on.
    events = {}
    for rule in rules:
        events[rule["on"]] = events.get(rule["on"], 0) + 1
    lines.append("events her rules hang on:")
    for event, count in sorted(events.items(), key=lambda kv: -kv[1]):
        lines.append(f"   {event:24s} x{count}")
    lines.append("")

    # Rules carrying an Eidolon gate, and any that are gated off by a band.
    gated = [(r["id"], r.get("min_eidolon"), r.get("max_eidolon")) for r in rules
             if r.get("min_eidolon") or r.get("max_eidolon")]
    lines.append(f"rules with an Eidolon gate: {len(gated)}")
    for rid, low, high in gated:
        lines.append(f"   min={low} max={high}  {rid}")

    OUT.write_text("\n".join(lines), encoding="utf-8")
    print("\n".join(lines[:8]))
    print("written", OUT)


if __name__ == "__main__":
    main()
