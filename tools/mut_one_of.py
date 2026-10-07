"""Mutations for `one_of`: the two wrong implementations the judge exists to catch."""
import pathlib
import subprocess
import sys

PATH = pathlib.Path("src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java")

VARIANTS = {
    # Rolls per effect instead of once: somewhere between zero and N effects run.
    "per_effect_roll": (
        "            if (chosen >= 0 && effectIndex != chosen) {",
        "            chosen = battle.getRng().nextInt(rule.effects().size());  // MUTANT: re-roll per effect\n"
        "            if (chosen >= 0 && effectIndex != chosen) {"),
    # Picks nothing: the loop skips every effect.
    "skips_everything": (
        "            if (chosen >= 0 && effectIndex != chosen) {",
        "            if (true) {  // MUTANT: skip everything"),
}


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "restore":
        subprocess.run(["git", "checkout", "HEAD", "--", str(PATH)], capture_output=True, text=True)
        print("restored from HEAD")
        return 0

    if len(sys.argv) != 2 or sys.argv[1] not in VARIANTS:
        print(__doc__)
        print("variants:", ", ".join(sorted(VARIANTS)))
        return 1

    old, new = VARIANTS[sys.argv[1]]
    text = PATH.read_text(encoding="utf-8")
    if text.count(old) != 1:
        print(f"anchor occurs {text.count(old)} times, expected 1")
        return 2
    PATH.write_text(text.replace(old, new, 1), encoding="utf-8")
    print("applied", sys.argv[1])
    return 0


if __name__ == "__main__":
    sys.exit(main())
