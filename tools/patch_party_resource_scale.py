"""A scale that reads a battle-level PARTY resource (2026-10-02, item 69).

Reader: 1513's `elation_moment_reward`, which must hand 【好活当赏】 the number of 【笑点】 the Aha moment spent. Measured: `笑点` is
declared `scope: PARTY` (the battle owns it, any ally may add to it -- see `gainResource`'s own comment), and `resolveScale` knew
only `stackScale`, `target_max_hp`, `max_energy` and attributes. So a rule could READ a unit's counter but not the party's.

Spelling: `party_resource:<name>`, which is the vocabulary's existing shape for "a number that lives somewhere named"
(`self_attr:`, `self_stacks:`, `cast_applied:`). It multiplies by `percent` like every other scale, and an unknown name is a
loud failure -- a counter that silently answers 0 is a wrong number with no symptom.
"""
import io
import sys

INTERP = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"


def patch(old, new, label, count=1):
    text = io.open(INTERP, encoding="utf-8").read()
    found = text.count(old)
    if found != count:
        sys.stderr.write("REFUSING %s: the anchor appears %d times\n" % (label, found))
        raise SystemExit(1)
    io.open(INTERP, "w", encoding="utf-8", newline="\n").write(text.replace(old, new, count))
    print("ok   %s" % label)


patch(
    """    String key = scale == null ? "" : scale.trim();
    Double fromStacks = stackScale(key, percent, ctx);
    if (fromStacks != null) {
        return fromStacks;
    }""",
    """    String key = scale == null ? "" : scale.trim();
    Double fromStacks = stackScale(key, percent, ctx);
    if (fromStacks != null) {
        return fromStacks;
    }
    // ⭐ A battle-level PARTY counter as a magnitude (2026-10-02; reader: 1513's reward, which hands 【好活当赏】 the
    // 【笑点】 the Aha moment spent). The counter lives on the battle, not on any unit, so `self_stacks:` cannot reach it.
    if (key.startsWith("party_resource:")) {
        String counter = key.substring("party_resource:".length()).trim();
        if (ctx.battle() == null) {
            throw new IllegalStateException(
                    "a magnitude scales off the party counter '" + counter + "', but this rule ran without a battle");
        }
        if (!ctx.battle().hasPartyResource(counter)) {
            throw new IllegalStateException(
                    "a magnitude scales off the party counter '" + counter + "', which no file declares -- a counter that "
                            + "answers 0 would be a wrong number with no symptom");
        }
        return percent * ctx.battle().partyResourceValue(counter);
    }""",
    "resolveScale knows the party counter",
)

patch(
    """        if (!scales.contains(scale) && !scale.startsWith(TriggerTable.CAST_APPLIED_PREFIX)) {""",
    """        if (!scales.contains(scale) && !scale.startsWith(TriggerTable.CAST_APPLIED_PREFIX)
                && !scale.startsWith("party_resource:")) {""",
    "the validator accepts the new scale",
)
