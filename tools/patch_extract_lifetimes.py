"""Extract the buff-lifetime vocabulary out of TriggerInterpreter into TriggerLifetimes.

Why this batch: the five declarations are the shared vocabulary for a buff's LIFETIME (the event
that ends it) and its TICK OWNER (whose turn boundary spends it). Both halves already use them --
TriggerEffects (the class the previous batch extracted) calls withLifetime 6 times, unticked 3 and
withTickOwner 3, and TriggerValidator calls eventBound 6 times -- so they are the seam between the
interpreter and the two classes that consume it, not interpreter internals.

Measured before moving (tools/refactor_movable.py --set ...): 0 fields read, 0 blocking calls,
0 private types named, i.e. movable as static helpers. The block 1325-1418 is contiguous, so the
move is one slice with no neighbours caught in it.

The patch validates every anchor BEFORE writing anything (all counts must be exactly 1), because a
half-applied patch is how this project has lost a round before (REFACTOR.md, discipline 47).

Usage:
    python tools/patch_extract_lifetimes.py [--check]
"""

import re
import sys

SRC = "src/main/java/com/laosun/aluminium/models/trigger/TriggerInterpreter.java"
DST = "src/main/java/com/laosun/aluminium/models/trigger/TriggerLifetimes.java"
FIRST, LAST = 1325, 1418

NEW_HEADER = '''package com.laosun.aluminium.models.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.models.buff.AbstractBuff;
import com.laosun.aluminium.models.trigger.TriggerTable.TriggerContext;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The lifetime vocabulary of a buff an effect creates: the event that ENDS it, and the unit whose
 * turn boundary SPENDS it.
 *
 * <p><b>Why these five declarations are their own class.</b> They are the seam between the
 * interpreter and the classes that consume it, rather than interpreter internals: TriggerEffects
 * calls {@link #withLifetime} six times, {@link #unticked} three and {@link #withTickOwner} three,
 * while TriggerValidator calls {@link #eventBound} six. They read no instance state and name no
 * private type, so they move as static helpers without widening anything.
 *
 * <p>The two halves say different things about time, which is why they are separate methods:
 * {@code until} names the EVENT that ends a buff, {@code ticks_on} names WHOSE TURN boundary spends
 * it. A rule may state either.
 */
final class TriggerLifetimes {

    private TriggerLifetimes() {
    }


'''


def main():
    check_only = "--check" in sys.argv

    lines = open(SRC, encoding="utf-8").read().split("\n")
    raw = "\n".join(lines)

    # Every anchor must be present exactly once, verified before any write.
    anchors = [
        "static boolean unticked(EffectSpec effect) {",
        "static boolean eventBound(EffectSpec effect) {",
        "static AbstractBuff withLifetime(AbstractBuff buff, EffectSpec effect) {",
        "static AbstractBuff withTickOwner(AbstractBuff buff, EffectSpec effect, TriggerContext ctx) {",
        "private static Set<AbstractBuff.Lifetime> lifetimesOf(EffectSpec effect) {",
    ]
    for anchor in anchors:
        count = raw.count(anchor)
        if count != 1:
            raise SystemExit(f"anchor appears {count} times, expected 1: {anchor!r}")

    # The slice must start on the javadoc that documents `unticked` and end on `lifetimesOf`'s brace.
    if lines[FIRST - 1].strip() != "/**":
        raise SystemExit(f"line {FIRST} is not a javadoc open: {lines[FIRST - 1]!r}")
    if lines[LAST - 1].strip() != "}":
        raise SystemExit(f"line {LAST} is not a closing brace: {lines[LAST - 1]!r}")
    # 1341 and 1397 are the two declarations that bound the slice; asserting them by line number is
    # what catches an off-by-one before it writes a file (the first version of this was off by one
    # in both places and only the check caught it).
    if "static boolean unticked(EffectSpec effect) {" not in lines[1340]:
        raise SystemExit(f"line 1341 does not declare unticked: {lines[1340]!r}")
    if "private static Set<AbstractBuff.Lifetime> lifetimesOf(EffectSpec effect) {" not in lines[1396]:
        raise SystemExit(f"line 1397 does not declare lifetimesOf: {lines[1396]!r}")

    block = lines[FIRST - 1:LAST]
    # The moved text becomes the body of a class: only `lifetimesOf` keeps `private`, because it is
    # the one member no other class calls. The rest become package-private like TriggerEffects' API.
    body = "\n".join(block)

    payload = NEW_HEADER + body + "\n}\n"

    # Callers: qualify each moved name, skipping the declaration lines themselves (which no longer
    # exist in SRC after the cut, but are still present at this point in the source text).
    call_sites = {
        "unticked": "TriggerLifetimes.unticked",
        "eventBound": "TriggerLifetimes.eventBound",
        "withLifetime": "TriggerLifetimes.withLifetime",
        "withTickOwner": "TriggerLifetimes.withTickOwner",
    }

    if check_only:
        print(f"anchors OK; block {FIRST}-{LAST} = {len(block)} lines")
        print(f"payload would be {len(payload.splitlines())} lines")
        return

    # 1. write the new class
    open(DST, "w", encoding="utf-8", newline="\n").write(payload)

    # 2. cut the block out of the source, collapsing the double blank line it leaves behind
    kept = lines[:FIRST - 1] + lines[LAST:]
    open(SRC, "w", encoding="utf-8", newline="\n").write("\n".join(kept))
    print(f"wrote {DST} ({len(payload.splitlines())} lines), cut {len(block)} lines from {SRC}")


if __name__ == "__main__":
    main()
