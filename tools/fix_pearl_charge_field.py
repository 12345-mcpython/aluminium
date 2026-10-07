"""Fix the field name in the charge rule: REMOVE_STATE names the state with `buff`, not `state`.

The corpus already uses `buff` for it in five files (1002, 1008, 1102, 1112, 1202), and `EffectSpec` has no `state` key at
all - so the table refused the whole file and 99 cases went red, which is the validation doing its job.
"""
import json
import pathlib
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
RULE = "deep_study_ends_when_the_charges_run_out"


def main():
    data = json.loads(TARGET.read_text(encoding="utf-8"))
    changed = 0
    for rule in data["rules"]:
        if rule["id"] != RULE:
            continue
        for effect in rule["do"]:
            if "state" in effect and effect.get("op") == "REMOVE_STATE":
                effect["buff"] = effect.pop("state")
                changed += 1
    if not changed:
        print("nothing to fix")
        return 0
    TARGET.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    check = json.loads(TARGET.read_text(encoding="utf-8"))
    rule = next(r for r in check["rules"] if r["id"] == RULE)
    print("fixed:", json.dumps(rule["do"], ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
