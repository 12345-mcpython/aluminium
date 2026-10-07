"""1506's skill LEVELS: what the project's convention requires, and what she actually has.

The project pins skill levels with a `level_convention` rule per character (1002/1004/1005 each carry one), because a
skill STARTS at level 1 and nothing else raises it. 1506 may have no such rule - in which case every number of hers is
being computed at Lv1 rather than at the Lv10/Lv15 the corpus table states, which would be a real defect rather than a
matter of my new rules.
"""
import json
import pathlib

DATA = pathlib.Path("src/main/resources/data")
CHARS = pathlib.Path("src/main/resources/characters")
OUT = pathlib.Path("tools/_1506_levels_truth.txt")


def main():
    lines = []
    skills = json.loads((DATA / "skills.json").read_text(encoding="utf-8"))

    # Who carries a level_convention, and what it raises.
    carriers = []
    for path in sorted(CHARS.glob("*.json")):
        body = json.loads(path.read_text(encoding="utf-8"))
        for rule in (body["rules"] if isinstance(body, dict) else body):
            if rule.get("id") == "level_convention" or "level_convention" in str(rule.get("id", "")):
                carriers.append((path.stem, [e for e in rule.get("do", [])]))
    lines.append(f"characters carrying a level_convention rule: {len(carriers)} of "
                 f"{len(list(CHARS.glob('*.json')))}")
    for cid, effects in carriers[:6]:
        lines.append(f"   {cid}: " + ", ".join(f"{e.get('skill')}+{e.get('amount')}" for e in effects))

    lines.append("")
    lines.append("== 1506: does she carry one? ==")
    body = json.loads((CHARS / "1506.json").read_text(encoding="utf-8"))
    hers = [r["id"] for r in body["rules"] if "level" in r["id"]]
    lines.append(f"   her rules mentioning level: {hers or 'NONE'}")

    lines.append("")
    lines.append("== 1506's skill tables: how many rows, i.e. the maximum level ==")
    for slot, row in sorted(skills["1506"].items(), key=lambda kv: int(kv[0])):
        params = row.get("param_list") or []
        lines.append(f"   slot {slot:>2} | rows {len(params):>3} | Lv1 {params[0] if params else '-'} | "
                     f"last {params[-1] if params else '-'}")

    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"written {OUT}")


if __name__ == "__main__":
    main()
