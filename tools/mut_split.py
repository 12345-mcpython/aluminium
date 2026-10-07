"""Mutations for `split` (「由敌方全体均分」 - the stated magnitude is a TOTAL).

  no_split          the box's 90% goes back to the per-victim reading            (content)
  no_divide         the share is never divided: every victim takes the total     (engine)
  count_the_dead    the divisor counts corpses too, so a share vanishes          (engine)
  no_split_check    the loader stops refusing the two meaningless combinations   (engine)

Every variant must turn a judge red; `restore` puts the files back with `git checkout`, which is safe because this work is
committed (`git checkout` reverts to the last COMMIT and would silently delete uncommitted engine work - it did exactly
that once, and the failure is recorded in GAPS).

Usage:
    python tools/mut_split.py <variant>
    python tools/mut_split.py restore
"""
import json
import pathlib
import subprocess
import sys

CONTENT = pathlib.Path("src/main/resources/characters/1506.json")
INTERPRETER = pathlib.Path("src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java")
BOX = "p1506_zone_loot_box_on_a_spent_point"

DIVIDE = """          if (divisor > 1) {
              base /= divisor;
          }
"""
DEAD_COUNT = """                        divisor = 0;
                        for (CanHit victim : victims) {
                            if (victim != null && !victim.isDeath()) {
                                divisor++;
                            }
                        }
                        divisor = Math.max(1, divisor);
"""
SPLIT_CHECK = """                if (Boolean.TRUE.equals(effect.getSplit())) {
                    if (Boolean.TRUE.equals(effect.getPerTarget())) {
                        throw new IllegalArgumentException(
                                "Op DAMAGE states both \\"split\\" and \\"per_target\\": one divides the stated total "
                                        + "among the victims and the other multiplies it by how many were hit, so the "
                                        + "pair has two readings (source: " + spec.getSource() + ")");
                    }
                    String splitTarget = normalizeTarget(effect);
                    if (!TARGET_ALL_ENEMIES.equals(splitTarget)) {
                        throw new IllegalArgumentException(
                                "Op DAMAGE states \\"split\\" with target \\"" + splitTarget + "\\": the divisor IS how "
                                        + "many units the effect reaches, so the field only says something for a "
                                        + "selector that reaches several - write \\"" + TARGET_ALL_ENEMIES
                                        + "\\" (source: " + spec.getSource() + ")");
                    }
                }
"""


def load():
    return json.loads(CONTENT.read_text(encoding="utf-8"))


def save(data):
    CONTENT.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def patch(path, old, new):
    text = path.read_text(encoding="utf-8")
    if text.count(old) != 1:
        raise SystemExit("anchor hit %d times in %s: %r" % (text.count(old), path, old[:70]))
    path.write_text(text.replace(old, new), encoding="utf-8")


def apply(variant):
    if variant == "no_split":
        data = load()
        rule = next(r for r in data["rules"] if r["id"] == BOX)
        rule["do"][0].pop("split", None)
        save(data)
    elif variant == "no_divide":
        patch(INTERPRETER, DIVIDE, "")
    elif variant == "count_the_dead":
        patch(INTERPRETER, DEAD_COUNT, "                        divisor = victims.size();\n")
    elif variant == "no_split_check":
        patch(INTERPRETER, SPLIT_CHECK, "")
    else:
        return None
    return variant


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "restore":
        subprocess.run(["git", "checkout", "--", str(CONTENT), str(INTERPRETER)], check=True)
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
