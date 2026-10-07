"""Mutations for the absorption primitive and Pearl's rule that uses it.

Each mutation targets one decision the implementation makes, and the judge must go red for each - otherwise the case is
not actually pinning that decision.

  unit_amount   the exchange rate: 200 -> 1 (one point per point of damage)
  percent       the share: 0.6 -> 1.0 (block the whole hit)
  amount_only   the share is capped by available points rather than blocking the share itself
  payer         read the VICTIM's counter instead of the payer's (simulated by removing the payer plumbing)
  before_zones  absorb the RAW instance instead of the settled amount

`before_zones` is the interesting one: it is the mistake the first run of the judge appeared to show, and the case that
distinguishes the two is `theShareIsBlockedWhenTheCounterCanPay`, whose numbers differ between the two models (600 vs
401.04 blocked).

Restore is `git checkout HEAD -- <path>`.

Usage:
    python tools/mut_absorb_damage.py <variant>
    python tools/mut_absorb_damage.py restore
"""
import pathlib
import subprocess
import sys

CONTENT = pathlib.Path("src/main/resources/characters/1503.json")
BATTLE = pathlib.Path("src/main/java/com/laosun/aluminium/Battle.java")

VARIANTS = {
    # content-side: the exchange rate
    "unit_amount": (CONTENT, '"unit_amount": 200.0', '"unit_amount": 1.0'),
    # content-side: the share. The anchor spans three lines and is unique whole-file - a bare `"percent": 0.6` occurs
    # twice (the other is a different rule), and a mutation whose anchor is ambiguous is one that silently never
    # applies while reading exactly like a surviving mutant.
    "percent": (CONTENT,
                '"resource": "好活当赏",\n          "unit_amount": 200.0,\n          "percent": 0.6',
                '"resource": "好活当赏",\n          "unit_amount": 200.0,\n          "percent": 1.0'),
    # engine-side: the share stops being a cap and becomes "spend everything you have"
    "amount_only": (BATTLE,
                    "double share = settled * absorber.getPercent();      // what this absorber may block at most",
                    "double share = settled;      // MUTANT: no share cap"),
    # engine-side: the payer stops mattering (everything reads the victim's own pool)
    "payer": (BATTLE,
              "int affordable = payer.getResources().value(absorber.getResource());",
              "int affordable = victim.getResources().value(absorber.getResource());  // MUTANT: the victim pays"),
}


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "restore":
        for path in {CONTENT, BATTLE}:
            result = subprocess.run(["git", "checkout", "HEAD", "--", str(path)],
                                    capture_output=True, text=True)
            print(f"restored {path.name}" if result.returncode == 0 else f"FAILED {path}: {result.stderr.strip()}")
        return 0

    if len(sys.argv) != 2 or sys.argv[1] not in VARIANTS:
        print(__doc__)
        print("variants:", ", ".join(sorted(VARIANTS)))
        return 1

    path, old, new = VARIANTS[sys.argv[1]]
    text = path.read_text(encoding="utf-8")
    if text.count(old) != 1:
        print(f"NOT APPLIED: the anchor occurs {text.count(old)} times in {path.name}, expected 1")
        return 2
    path.write_text(text.replace(old, new), encoding="utf-8")
    if new not in path.read_text(encoding="utf-8"):
        print("EDIT DID NOT LAND - restoring")
        subprocess.run(["git", "checkout", "HEAD", "--", str(path)], capture_output=True, text=True)
        return 3
    print(f"applied {sys.argv[1]} to {path.name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
