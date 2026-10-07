"""What the enemy side actually has, measured - so its registration is facts rather than impressions.

Counts what the generated data offers (monsters, templates, the hand-written skill table) and what the engine builds
from it, so "the enemy line is unmeasured" can be stated with numbers.
"""
import json
import pathlib

DATA = pathlib.Path("src/main/resources/data")
OUT = pathlib.Path("tools/_enemy_survey.txt")


def load(name):
    path = DATA / name
    if not path.exists():
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def main():
    lines = []
    monsters = load("monster_config.json")
    templates = load("monster_template_config.json")
    skills = load("enemy_skills.json")

    lines.append(f"monster_config.json           : {_count(monsters)} monsters")
    lines.append(f"monster_template_config.json  : {_count(templates)} templates")
    lines.append(f"enemy_skills.json             : {_count(skills)} file keys, "
                 f"{_count(skills.get('skills') if isinstance(skills, dict) else None)} skill entries")
    lines.append("")

    if isinstance(skills, dict) and isinstance(skills.get("skills"), dict):
        lines.append("== the hand-written skill table (the ONLY enemy skills that exist) ==")
        for key, entry in skills["skills"].items():
            lines.append(f"  {key}: {entry.get('name', {}).get('chinese')} multiplier={entry.get('multiplier')}"
                         f" hits={entry.get('hits')} guessed={entry.get('guessed')}")
        lines.append("")

    if isinstance(monsters, dict) and monsters:
        first = next(iter(monsters.values()))
        keys = sorted(first.keys())
        lines.append("== what a monster config carries ==")
        lines.append("  keys: " + ", ".join(keys))
        for key in ("summon_ids", "damage_resistance", "debuff_resistance", "stance_weak"):
            present = sum(1 for m in monsters.values() if m.get(key))
            lines.append(f"  {key:22s}: non-empty in {present} of {len(monsters)}")
        lines.append("")
        lines.append("== anything resembling an AI / skill weight / phase script? ==")
        suspicious = [k for k in keys if any(word in k.lower() for word in
                                             ("ai", "weight", "phase", "skill", "priority", "behaviour", "behavior"))]
        lines.append("  config keys that look like one: " + (", ".join(suspicious) if suspicious else "(none)"))
        lines.append("")
        lines.append("== coverage ==")
        covered = set((skills or {}).get("skills", {})) if isinstance(skills, dict) else set()
        lines.append(f"  monsters with a hand-written skill: {len([m for m in covered if m in monsters])}")
        lines.append(f"  monsters falling back to multiplier 1.0 single-hit: {len(monsters) - len(covered)}")

    OUT.write_text("\n".join(lines), encoding="utf-8")
    print("written", OUT)


def _count(value):
    if isinstance(value, dict):
        return len(value)
    if isinstance(value, list):
        return len(value)
    return 0


if __name__ == "__main__":
    main()
