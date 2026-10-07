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
    "amount30": (CONTENT,
                 '"op": "GAIN_RESOURCE", "resource": "好活当赏", "amount": 30',
                 '"op": "GAIN_RESOURCE", "resource": "好活当赏", "amount": 1'),
    "amount60": (CONTENT,
                 '"op": "GAIN_RESOURCE", "resource": "笑点", "amount": 60',
                 '"op": "GAIN_RESOURCE", "resource": "笑点", "amount": 1'),
    # The strict reading: take exactly 30, erroring when it is not there. Expressed by raising the removal to 3000,
    # which the clamped implementation absorbs (the counter caps at 50) while a strict one could not.
    "strict": (CONTENT,
               '"op": "REMOVE_RESOURCE", "resource": "好活当赏", "amount": 30',
               '"op": "REMOVE_RESOURCE", "resource": "好活当赏", "amount": 3000'),
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
