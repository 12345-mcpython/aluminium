"""Mutations for 1506's 【超超超大剑】 and the scale it reads (`settled_damage_this_event`).

  tenth            the share is 10% instead of 20%          (content)
  attack_stand_in  back to the discarded stand-in: 20% of her ATK, not of the box's total   (content)
  no_reset         the running total is never emptied, so the second sword reads three boxes (engine)
  last_instance    the running total is the LAST instance instead of their sum              (engine)
  no_true_check    the loader stops refusing a zoned rider over a settled total              (engine)

Every variant must turn a judge red; `restore` puts both files back with `git checkout`, which is safe here because the
work is committed (`git checkout` reverts to the last COMMIT and would silently delete uncommitted engine work - it did
exactly that once).

Usage:
    python tools/mut_sword_basis.py <variant>
    python tools/mut_sword_basis.py restore
"""
import json
import pathlib
import subprocess
import sys

CONTENT = pathlib.Path("src/main/resources/characters/1506.json")
BATTLE = pathlib.Path("src/main/java/com/laosun/aluminium/Battle.java")
INTERPRETER = pathlib.Path("src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java")
SWORD_RULE = "p1506_loot_box_picks_one_effect"

NO_RESET = """        if (triggerDepth == 0) {
            settledDamageThisEvent = 0;
        }
"""
SUM = "settledDamageThisEvent += settled;"
TRUE_CHECK = "                        requireTrueDamage(effect, op, spec);\n"


def content_effect():
    data = json.loads(CONTENT.read_text(encoding="utf-8"))
    rule = next(r for r in data["rules"] if r["id"] == SWORD_RULE)
    return data, rule["do"][0]


def save(data):
    CONTENT.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def patch(path, old, new):
    text = path.read_text(encoding="utf-8")
    if text.count(old) != 1:
        raise SystemExit("anchor hit %d times in %s: %r" % (text.count(old), path, old[:60]))
    path.write_text(text.replace(old, new), encoding="utf-8")


def apply(variant):
    if variant == "tenth":
        data, effect = content_effect()
        effect["percent"] = 0.1
        save(data)
    elif variant == "attack_stand_in":
        data, effect = content_effect()
        effect["scale"] = "self_attr:ATTACK"
        save(data)
    elif variant == "no_reset":
        patch(BATTLE, NO_RESET, "")
    elif variant == "last_instance":
        patch(BATTLE, SUM, "settledDamageThisEvent = settled;")
    elif variant == "no_true_check":
        patch(INTERPRETER, TRUE_CHECK, "")
    else:
        return None
    return variant


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "restore":
        subprocess.run(["git", "checkout", "--", str(CONTENT), str(BATTLE), str(INTERPRETER)], check=True)
        print("restored with git checkout")
        return 0
    if len(sys.argv) != 2:
        print(__doc__)
        return 1
    applied = apply(sys.argv[1])
    if applied is None:
        print("unknown variant:", sys.argv[1])
        return 2
    print("applied", applied)
    return 0


if __name__ == "__main__":
    sys.exit(main())
