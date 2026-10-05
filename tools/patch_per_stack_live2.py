"""Continuation: the interpreter half of `per_stack_live`, with simpler anchors.

The first script had to stop at an anchor that appears twice. It also revealed that the snapshot multiply does NOT need
touching: the live modifier ignores the stored `value` and asks the supplier for `magnitude * stacks`, so a multiplied
`applied` is simply unused. Three patches remain:

  1. the two ctx-free helpers + the set of per-hit count forms;
  2. marking the built buff live (and refusing the count forms loudly, right there);
  3. nothing else -- the existing per_stack line stays exactly as it is.
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
    "    private static final Set<String> SCALES =",
    """    /** The `per_stack` forms that count something about the CURRENT hit: they have no ctx-free reader (2026-10-02). */
    private static final Set<String> COUNT_FORMS = Set.of(
            "target_debuff_count", "target_dot_count", "target_weakness_count", "shielded_count");

    private static final Set<String> SCALES =""",
    "COUNT_FORMS: the per-hit count forms",
)

patch(
    "    private static double perStackFactor(EffectSpec effect, CanHit target, TriggerContext ctx) {",
    """    /** Whether `per_stack` names something a supplier can read later, without an event context (2026-10-02). */
    private static boolean ctxFreeCounter(EffectSpec effect) {
        return effect.getPerStack() != null && !effect.getPerStack().isBlank()
                && !COUNT_FORMS.contains(effect.getPerStack().trim());
    }

    /** The counter name behind `self_stacks:<NAME>` (or the bare name), for a live reader. */
    private static String counterName(EffectSpec effect) {
        String stated = effect.getPerStack().trim();
        return stated.startsWith("self_stacks:") ? stated.substring("self_stacks:".length()).trim() : stated;
    }

    private static double perStackFactor(EffectSpec effect, CanHit target, TriggerContext ctx) {""",
    "the two helpers",
)

patch(
    """            if (effect.getBuff() != null && !effect.getBuff().isBlank()) {
                buff.setBuffName(effect.getBuff().trim());""",
    """            if (Boolean.TRUE.equals(effect.getPerStackLive())) {
                // ⭐ 「每拥有 1 层…提高 X%」 as a SUSTAINED aura (2026-10-02; reader family: fourteen documents). The share is
                // asked for on every read, so the aura follows the count -- a snapshot would only be right at the instant it
                // was taken, and re-attaching on every change would stack the buff itself.
                if (!ctxFreeCounter(effect)) {
                    throw new IllegalStateException(
                            "per_stack_live only exists for the counter forms (self_stacks:<NAME> or a bare name), but this "
                                    + "rule's per_stack is '" + effect.getPerStack() + "'; the per-hit count forms are read "
                                    + "from an event context and (source: " + ctx.ruleId() + ")");
                }
                if (buff instanceof com.laosun.aluminium.models.buff.StatModifierBuff liveBuff) {
                    String counter = counterName(effect);
                    liveBuff.makeLive(() -> magnitude * target.getBuffManager().stacksOf(counter));
                }
            }
            if (effect.getBuff() != null && !effect.getBuff().isBlank()) {
                buff.setBuffName(effect.getBuff().trim());""",
    "the built buff is marked live",
)
