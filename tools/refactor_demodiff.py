"""Compare two demo outputs when their line order is not deterministic.

Why not a hash: the demo prints several `Set`/`Map`-derived collections -- enemy
`Resist {PHYSICAL=0.2, ICE=0.2, ...}` above all -- whose iteration order varies between
JVM runs of the SAME code. Measured: three consecutive runs of one build produced three
different SHA256 hashes, so a byte-for-byte guard would report a regression on every run
and be worse than no guard at all.

What is compared instead, both of which are stable:

  * the multiset of lines -- same lines, same counts, any order;
  * the sequence of lines with the variably-ordered collections sorted in place, so real
    ordering regressions in the deterministic parts still show up.

Usage:
    python tools/refactor_demodiff.py <baseline.txt> <current.txt>
"""

import io
import re
import sys
from collections import Counter

# Groups whose element ORDER comes from a Set/Map and so varies between JVM runs:
# `Resist {PHYSICAL=0.2, ICE=0.2, ...}` and `Weakness [FIRE, THUNDER]`. Sorting the
# elements inside makes the line comparable across runs without hiding which elements
# are present -- a missing or added element still shows up as a difference.
BRACES = re.compile(r"[\{\[]([^{}\[\]]*)[\}\]]")


def normalise(line):
    def sort_group(m):
        inner = m.group(1)
        opener, closer = m.group(0)[0], m.group(0)[-1]
        parts = [p.strip() for p in inner.split(",") if p.strip()]
        return opener + ", ".join(sorted(parts)) + closer

    return BRACES.sub(sort_group, line.rstrip())


def read(path):
    with io.open(path, encoding="utf-8-sig", errors="replace") as fh:
        return fh.read().split("\n")


def main():
    base, now = read(sys.argv[1]), read(sys.argv[2])

    if len(base) != len(now):
        print(f"LINE COUNT DIFFERS: baseline {len(base)}, current {len(now)}")
    else:
        print(f"line count: {len(base)} (same)")

    base_lines = [l for l in base if l.strip()]
    now_lines = [l for l in now if l.strip()]

    print(f"non-blank lines: baseline {len(base_lines)}, current {len(now_lines)}")

    # Compare on the NORMALISED text throughout: raw multisets differ across runs of
    # identical code (a `Resist {...}` line is a different string each run), so a verdict
    # built on them reports DIFFERS for a run compared with itself.
    cb, cn = Counter(normalise(l) for l in base_lines), Counter(normalise(l) for l in now_lines)

    missing = list((cb - cn).elements())
    extra = list((cn - cb).elements())
    print(f"lines only in baseline: {len(missing)}")
    for l in missing[:20]:
        print("   -", l[:120])
    print(f"lines only in current : {len(extra)}")
    for l in extra[:20]:
        print("   +", l[:120])

    # Order-sensitive comparison, with the unstable collections neutralised.
    nb = [normalise(l) for l in base_lines]
    nn = [normalise(l) for l in now_lines]
    if nb == nn:
        print("\nORDER: identical once set-valued collections are sorted")
    else:
        print("\nORDER: differs in the deterministic parts:")
        shown = 0
        for i, (a, b) in enumerate(zip(nb, nn)):
            if a != b:
                print(f"   line {i + 1}:\n     baseline: {a[:110]}\n     current : {b[:110]}")
                shown += 1
                if shown >= 15:
                    print("   ... (truncated)")
                    break

    ok = (missing == [] and extra == [] and nb == nn)
    print("\nVERDICT:", "MATCH" if ok else "DIFFERS")


if __name__ == "__main__":
    main()
