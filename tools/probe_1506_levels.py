"""Is RAISE_SKILL_LEVEL actually running for 1506, and what level is a skill BEFORE any rule?

Two candidate explanations for "every slot reads level 1 at E3 and E0 alike":
  * the rules never fire (a gate problem), or
  * skills START at level 1 and the factory does not raise them, in which case E0 and E3 differ only if the rule runs.
This measures both, plus whether a rule of hers is selected at all.
"""
import json
import pathlib

OUT = pathlib.Path("tools/_1506_levels_probe.txt")
CHARS = pathlib.Path("src/main/resources/characters")


def main():
    data = json.loads((CHARS / "1506.json").read_text(encoding="utf-8"))
    lines = ["== the two level rules as the FILE states them =="]
    for rule in data["rules"]:
        if rule["id"] in ("p1506_e3_raises_skill_levels", "p1506_e5_raises_skill_levels"):
            lines.append(f"   {rule['id']}: on={rule['on']} min_eidolon={rule.get('min_eidolon')}")
            for effect in rule["do"]:
                lines.append(f"      {effect}")

    lines.append("")
    lines.append("== every rule of hers that raises a level (any character, for the spelling) ==")
    for path in sorted(CHARS.glob("*.json")):
        body = json.loads(path.read_text(encoding="utf-8"))
        for rule in (body["rules"] if isinstance(body, dict) else body):
            for effect in rule.get("do", []):
                if effect.get("op") == "RAISE_SKILL_LEVEL":
                    lines.append(f"   {path.stem} {rule.get('id', rule.get('on'))}: {effect}")

    lines.append("")
    lines.append("== does any judge assert a raised level, so the level convention is known? ==")
    tests = pathlib.Path("src/test/java/com/laosun/aluminium/test")
    for path in sorted(tests.rglob("*.java")):
        text = path.read_text(encoding="utf-8")
        if "RAISE_SKILL_LEVEL" in text or "levelConvention" in text or "getLevel()" in text:
            lines.append(f"   {path.name}")

    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"written {OUT}")


if __name__ == "__main__":
    main()
