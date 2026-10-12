"""Point the moved lifetime helpers' call sites at TriggerLifetimes.

Run after tools/patch_extract_lifetimes.py. Three files call the four moved methods:

  * TriggerInterpreter -- 6 call sites, UNQUALIFIED (they were same-class calls);
  * TriggerEffects    -- 12 call sites, already written `TriggerInterpreter.<name>`;
  * TriggerValidator  -- 6 call sites, already written `TriggerInterpreter.<name>`.

Per REFACTOR.md's test-sweep lesson, the expected number of rewrites per file is pinned in this
script and asserted, so a pattern that silently misses one becomes a hard failure instead of a
wrong file 12 edits later (the alternative -- one pattern over the whole tree -- is what silently
rewrote a file outside the batch last time).

The count is measured, not assumed: it is the number of matches each pattern has right now.

Usage:
    python tools/patch_lifetimes_callers.py [--check]
"""

import re
import sys

MOVE = ("unticked", "eventBound", "withLifetime", "withTickOwner")

TARGETS = {
    # file -> expected number of CALLS (not lines: `withSource(withTickOwner(withLifetime(` on one
    # line is three sites, and TriggerInterpreter has two such lines, so its 6 lines are 8 calls).
    "src/main/java/com/laosun/aluminium/models/trigger/TriggerInterpreter.java": 8,
    "src/main/java/com/laosun/aluminium/models/trigger/TriggerEffects.java": 12,
    "src/main/java/com/laosun/aluminium/models/trigger/TriggerValidator.java": 6,
}

# A call is `name(` not preceded by `.` or a word character. The negative lookbehind for `.` is what
# keeps already-qualified calls (`TriggerInterpreter.unticked(`) out of the first pattern.
UNQUALIFIED = re.compile(r"(?<![\w.])(" + "|".join(MOVE) + r")\s*\(")
QUALIFIED = re.compile(r"TriggerInterpreter\.(" + "|".join(MOVE) + r")\s*\(")


def main():
    check_only = "--check" in sys.argv
    total = 0
    for path, expected in TARGETS.items():
        text = open(path, encoding="utf-8").read()
        qualified_hits = len(QUALIFIED.findall(text))
        # The unqualified form must not match the qualified one; strip the qualified prefix first so
        # the two counts cannot both claim the same site.
        stripped = QUALIFIED.sub("", text)
        unqualified_hits = len(UNQUALIFIED.findall(stripped))
        hits = qualified_hits + unqualified_hits

        if hits != expected:
            raise SystemExit(
                f"{path}: found {hits} call site(s) (qualified {qualified_hits} + unqualified "
                f"{unqualified_hits}), expected {expected} -- refusing to write")

        new_text = QUALIFIED.sub(lambda m: f"TriggerLifetimes.{m.group(1)}(", text)
        new_text = UNQUALIFIED.sub(lambda m: f"TriggerLifetimes.{m.group(1)}(", new_text)
        remaining = len(UNQUALIFIED.findall(QUALIFIED.sub("", new_text)))
        if remaining:
            raise SystemExit(f"{path}: {remaining} unqualified call site(s) left after the rewrite")

        if not check_only:
            open(path, "w", encoding="utf-8", newline="\n").write(new_text)
        print(f"{path}: {hits} call site(s) -> TriggerLifetimes"
              f"{'  (check only)' if check_only else ''}")
        total += hits
    print(f"total rewrites: {total}")


if __name__ == "__main__":
    main()
