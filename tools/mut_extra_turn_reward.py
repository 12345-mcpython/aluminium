"""Mutations for the extra-turn reward: the two amounts, and the removal's clamping.

  amount30   the 好活当赏 grant: 30 -> 1
  amount60   the 笑点 grant: 60 -> 1
  strict     the removal stops clamping and becomes a strict spend (SPEND_RESOURCE's semantics), which is the
             implementation the author's ruling rejects - it must go red on the "already empty" and "partly spent" cases

`strict` is the interesting one: it is not a typo but the OTHER plausible reading of "take the 30 back", and the two
cases that separate them exist precisely because that reading is tempting.

Restore is `git checkout HEAD -- <path>`.

Usage:
    python tools/mut_extra_turn_reward.py <variant>
    python tools/mut_extra_turn_reward.py restore
"""
import pathlib
import subprocess
import sys

CONTENT = pathlib.Path("src/main/resources/characters/1503.json")

VARIANTS = {
    "grant30": (CONTENT,
                '"op": "GAIN_RESOURCE",\n          "resource": "好活当赏",\n          "amount": 30',
                '"op": "GAIN_RESOURCE",\n          "resource": "好活当赏",\n          "amount": 1'),
    "grant60": (CONTENT,
                '"op": "GAIN_RESOURCE",\n          "resource": "笑点",\n          "amount": 60',
                '"op": "GAIN_RESOURCE",\n          "resource": "笑点",\n          "amount": 1'),
    # The removal's own number. Note: an earlier variant here raised the removal to 3000 to stand for "a strict spend",
    # and it SURVIVED - correctly, because with a clamping implementation removing 3000 and removing 30 are the same
    # thing (both empty the counter). A mutation has to differ in what the judge READS, and this one does.
    "take30": (CONTENT,
               '"op": "REMOVE_RESOURCE",\n          "resource": "好活当赏",\n          "amount": 30',
               '"op": "REMOVE_RESOURCE",\n          "resource": "好活当赏",\n          "amount": 1'),
    "take60": (CONTENT,
               '"op": "REMOVE_RESOURCE",\n          "resource": "笑点",\n          "amount": 60',
               '"op": "REMOVE_RESOURCE",\n          "resource": "笑点",\n          "amount": 1'),
}


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "restore":
        result = subprocess.run(["git", "checkout", "HEAD", "--", str(CONTENT)], capture_output=True, text=True)
        print("restored from HEAD" if result.returncode == 0 else f"FAILED: {result.stderr.strip()}")
        return 0

    if len(sys.argv) != 2 or sys.argv[1] not in VARIANTS:
        print(__doc__)
        print("variants:", ", ".join(sorted(VARIANTS)))
        return 1

    path, old, new = VARIANTS[sys.argv[1]]
    text = path.read_text(encoding="utf-8")
    if text.count(old) != 1:
        print(f"NOT APPLIED: the anchor occurs {text.count(old)} times, expected 1")
        return 2
    path.write_text(text.replace(old, new), encoding="utf-8")
    if new not in path.read_text(encoding="utf-8"):
        print("EDIT DID NOT LAND - restoring")
        subprocess.run(["git", "checkout", "HEAD", "--", str(path)], capture_output=True, text=True)
        return 3
    print(f"applied {sys.argv[1]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
