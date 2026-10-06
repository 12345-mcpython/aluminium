"""Apply one of the holder_attr mutations, or restore the file. Written as a script file for the reason
HANDOFF discipline 38 gives: inline multi-line edits have broken this tree six times.

Usage:
    python tools/mut_holder_attr.py                 # list the mutations
    python tools/mut_holder_attr.py m1              # apply mutation 1 (after making a .bak)
    python tools/mut_holder_attr.py restore         # put the original back

Every mutation is applied with an ANCHOR CHECK first: the whole file is read, every anchor is counted, and
nothing is written unless every count is exactly 1. A half-applied edit is what discipline 47 is about.
"""
import sys
import shutil
import pathlib

TARGET = pathlib.Path("src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java")
BACKUP = pathlib.Path("src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java.bak")

# The runtime branch, exactly as it is in the file (leading whitespace included).
RUNTIME = (
    "            return shareOf(effect, ctx) * holder.getAttribute(holderAttrAttribute(effect)).get()\n"
    "                    + (effect.getAmount() == null ? 0 : effect.getAmount());"
)

MUTATIONS = {
    # M1: read the RULE OWNER's attribute instead of the state-holder's. This is the mistake every sibling
    # spelling in the family (self_attr:) would make, and the three-way judge is built to catch it.
    "m1": (RUNTIME, RUNTIME.replace("holder.getAttribute", "owner.getAttribute")),
    # M2: read the holder but ignore WHICH attribute was named (always Defence). Catches a parser that
    # discards the attribute half of the spelling.
    "m2": (RUNTIME,
           RUNTIME.replace("holderAttrAttribute(effect)", "com.laosun.aluminium.enums.AttributeType.DEFENCE")),
    # M3: nobody-holding-it answers the owner instead of refusing: the silent-zero failure mode.
    "m3": (
        "            CanHit holder = holderOfOrNull(ctx.battle(), holderAttrState(effect), owner);",
        "            CanHit holder = holderOfOrNull(ctx.battle(), holderAttrState(effect), owner) == null\n"
        "                    ? owner : holderOfOrNull(ctx.battle(), holderAttrState(effect), owner);",
    ),
}


def main():
    if len(sys.argv) != 2 or sys.argv[1] not in ("restore",) + tuple(MUTATIONS):
        print(__doc__)
        print("mutations:", ", ".join(sorted(MUTATIONS)))
        return 1

    if sys.argv[1] == "restore":
        if not BACKUP.exists():
            print("no backup to restore from:", BACKUP)
            return 1
        shutil.copyfile(BACKUP, TARGET)
        print("restored", TARGET, "from", BACKUP)
        return 0

    if not BACKUP.exists():
        shutil.copyfile(TARGET, BACKUP)
        print("backup written to", BACKUP)

    text = TARGET.read_text(encoding="utf-8")
    old, new = MUTATIONS[sys.argv[1]]
    count = text.count(old)
    if count != 1:
        print("ANCHOR NOT UNIQUE for", sys.argv[1], "- found", count, "occurrences; nothing written")
        return 2
    TARGET.write_text(text.replace(old, new), encoding="utf-8")
    print("applied", sys.argv[1])
    return 0


if __name__ == "__main__":
    sys.exit(main())
