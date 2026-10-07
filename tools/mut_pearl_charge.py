"""Mutations for Pearl's charge counter - the state duration that is a count of USES, not of turns.

  to_max_adds        re-entering the state adds three instead of restoring to the ceiling (the bug `to_max` prevents)
  no_spend           an enhanced basic does not spend a charge
  spend_two          an enhanced basic spends two, so the state ends a use early
  never_ends         the state is never removed when the counter empties

Restore is `git checkout HEAD -- <path>`.

Usage:
    python tools/mut_pearl_charge.py <variant>
    python tools/mut_pearl_charge.py restore
"""
import json
import pathlib
import shutil
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
BACKUP = TARGET.with_suffix(".json.bak")
FORMS = ("form_a_spends_a_charge", "form_b_spends_a_charge")
ENDS = "deep_study_ends_when_the_charges_run_out"
REFILL = "ult_refills_the_deep_study_charges"


def load():
    return json.loads(TARGET.read_text(encoding="utf-8"))


def save(data):
    TARGET.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def rule(data, rule_id):
    return next(r for r in data["rules"] if r["id"] == rule_id)


def apply(variant):
    data = load()
    if variant == "to_max_adds":
        # The state already there: plain +3 rather than "raise to the ceiling".
        rule(data, REFILL)["do"][0].pop("to_max", None)
    elif variant == "no_spend":
        for name in FORMS:
            rule(data, name)["do"][0]["amount"] = 0
    elif variant == "spend_two":
        for name in FORMS:
            rule(data, name)["do"][0]["amount"] = 2
    elif variant == "never_ends":
        data["rules"] = [r for r in data["rules"] if r["id"] != ENDS]
    else:
        return None
    return data


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "restore":
        if not BACKUP.exists():
            print("no backup at", BACKUP)
            return 1
        shutil.copyfile(BACKUP, TARGET)
        print("restored from", BACKUP)
        return 0

    if len(sys.argv) != 2:
        print(__doc__)
        return 1

    data = apply(sys.argv[1])
    if data is None:
        print("unknown variant:", sys.argv[1])
        return 2

    if not BACKUP.exists():
        shutil.copyfile(TARGET, BACKUP)
    before = TARGET.read_text(encoding="utf-8")
    save(data)
    after = load()
    if len(after["rules"]) == 0:
        shutil.copyfile(BACKUP, TARGET)
        print("EDIT WENT WRONG - restored")
        return 3
    print("applied", sys.argv[1])
    return 0


if __name__ == "__main__":
    sys.exit(main())
