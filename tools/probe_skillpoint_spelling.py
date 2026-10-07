"""Why is `战技点` not reachable as a resource, and is there a spelling that reads the battle's skill points?

The loot box's three effects are 「恢复 2 个战技点」, 「获得 3 个笑点」 and a true-damage rider. `笑点` is a declared
PARTY counter. `战技点` is not declared by anyone - the battle owns it (`Battle.skillPointPolicy`), and 1506 has 32
failing tests when a rule names it. This finds out what spelling, if any, reaches it.
"""
import json
import pathlib
import re

SRC = pathlib.Path("src/main/java/com/laosun/aluminium")
OUT = pathlib.Path("tools/_skillpoint_spelling.txt")


def main():
    lines = []
    lines.append("== GAIN_RESOURCE and the party store ==")
    text = (SRC / "models/TriggerInterpreter.java").read_text(encoding="utf-8")
    for match in re.finditer(r"partyCounterOrNull", text):
        start = max(0, match.start() - 200)
        lines.append("   ..." + text[start:match.start() + 200].replace("\n", " ")[:380])
        break

    lines.append("")
    lines.append("== does anything ship a rule that GAINS or SPENDS 战技点? ==")
    hits = 0
    for path in sorted(pathlib.Path("src/main/resources").rglob("*.json")):
        if "_sweep" in str(path):
            continue
        body = path.read_text(encoding="utf-8", errors="replace")
        if "战技点" not in body:
            continue
        try:
            data = json.loads(body)
        except Exception:
            continue
        for rule in (data.get("rules", []) if isinstance(data, dict) else data):
            if not isinstance(rule, dict):
                continue
            for effect in rule.get("do", []):
                if "战技点" in json.dumps(effect, ensure_ascii=False):
                    lines.append(f"   {path.parent.name}/{path.name}: {effect}")
                    hits += 1
    lines.append(f"   (hits: {hits})")

    lines.append("")
    lines.append("== the declared-resource check: where does 'not declared' get raised? ==")
    for name in ("utils/CharacterFactory.java", "models/TriggerTable.java"):
        body = (SRC / name).read_text(encoding="utf-8")
        for match in re.finditer(r"declares", body):
            start = max(0, match.start() - 120)
            lines.append(f"   {name}: ..." + body[start:match.start() + 200].replace("\n", " ")[:300])
            break

    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"written {OUT}")


if __name__ == "__main__":
    main()
