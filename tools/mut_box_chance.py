"""Mutations for 1506's decaying 【头号补给盲盒】 chance (`chance_decay` + `RESET_RULE_CHANCE`).

  half_decay       the factor is 0.5 instead of 0.2                              (content)
  certain_again    the box's initial chance is 0.5 instead of 1.0                (content)
  no_decay         a successful trigger leaves the chance where it was            (engine)
  no_reset         the 【崩坏级伤害演示】 reset rule is dropped                   (content)
  no_shape_check   the loader stops refusing a reset of a rule that cannot decay  (engine)

`restore` puts both files back with `git checkout`, which is safe because the work is committed - `git checkout` reverts
to the last COMMIT and would silently delete uncommitted engine work (it did exactly that once).

Usage:
    python tools/mut_box_chance.py <variant>
    python tools/mut_box_chance.py restore
"""
import json
import pathlib
import subprocess
import sys

CONTENT = pathlib.Path("src/main/resources/characters/1506.json")
INTERPRETER = pathlib.Path("src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java")
TABLE = pathlib.Path("src/main/java/com/laosun/aluminium/models/TriggerTable.java")
BOX = "p1506_zone_loot_box_on_a_spent_point"
RESET = "p1506_honkai_demo_resets_the_box_chance"

DECAY_CALL = """            if (owner != null && rule.chanceDecay() > 0) {
                // "成功触发后": the rule really ran, so the NEXT time it is asked for, its probability is this much
                // smaller. A failed roll returned above, and a firing turned away by a condition or a limit never got
                // here at all - neither is a successful trigger.
                owner.decayRuleChance(rule.id(), rule.chance(), rule.chanceDecay());
            }
"""
SHAPE_CALL = """                    } else if ("RESET_RULE_CHANCE".equalsIgnoreCase(op)) {
                        requireDecayingRule(rule, effect, byId);
                    }
"""


def load():
    return json.loads(CONTENT.read_text(encoding="utf-8"))


def save(data):
    CONTENT.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def rule(data, rule_id):
    return next(r for r in data["rules"] if r["id"] == rule_id)


def patch(path, old, new):
    text = path.read_text(encoding="utf-8")
    if text.count(old) != 1:
        raise SystemExit("anchor hit %d times in %s: %r" % (text.count(old), path, old[:60]))
    path.write_text(text.replace(old, new), encoding="utf-8")


def apply(variant):
    if variant == "half_decay":
        data = load()
        rule(data, BOX)["chance_decay"] = 0.5
        save(data)
    elif variant == "certain_again":
        data = load()
        rule(data, BOX)["chance"] = 0.5
        save(data)
    elif variant == "no_reset":
        data = load()
        data["rules"] = [r for r in data["rules"] if r["id"] != RESET]
        save(data)
    elif variant == "no_decay":
        patch(INTERPRETER, DECAY_CALL, "")
    elif variant == "no_shape_check":
        patch(TABLE, SHAPE_CALL, """                    }
""")
    else:
        return None
    return variant


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "restore":
        subprocess.run(["git", "checkout", "--", str(CONTENT), str(INTERPRETER), str(TABLE)], check=True)
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
