"""Wire the live modifier into content: `per_stack_live` on MODIFY_ATTR (2026-10-02, item 63).

Reader: the family GAPS measured at FOURTEEN documents -- 「每拥有 1 层…提高 X%」 auras, whose number has to follow the count
while the aura is up. The engine half shipped as item 62 (`DoubleValue.Modifier.livePercent` + `get()` recomputing when a
live modifier is attached); this is the content spelling.

Measured before patching:
  * the persistent path computes `applied *= perStackFactor(...)` ONCE, where the buff is built (TriggerInterpreter ~2141),
    and hands a number to `StatModifierBuff.of(...)`;
  * `StatModifierBuff.applyEffect` attaches `new DoubleValue.Modifier(modifierType, value, sourceRole, id)` -- the one place a
    live share can take that modifier's place;
  * `perStackFactor` supports the ctx-free forms (`self_stacks:<NAME>` and a bare counter name), which is what a supplier can
    read later; the count forms (`target_debuff_count` and friends) are per-hit numbers and stay registered, so the loader
    REFUSES `per_stack_live` with them rather than silently snapshotting.

\u26a0 Fail-safe: the script refuses to run at all if `StatModifierBuff` carries Lombok's `@AllArgsConstructor`, because a new
field would silently change that generated signature (the trap item 62 hit in `DoubleValue.Modifier`).
"""
import io
import sys

SPEC = "src/main/java/com/laosun/aluminium/beans/EffectSpec.java"
BUFF = "src/main/java/com/laosun/aluminium/models/buff/StatModifierBuff.java"
INTERP = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"

buff_text = io.open(BUFF, encoding="utf-8").read()
if "@AllArgsConstructor" in buff_text:
    sys.stderr.write("REFUSING: StatModifierBuff has @AllArgsConstructor; adding a field would change its signature\n")
    raise SystemExit(1)


def patch(path, old, new, label, count=1):
    text = io.open(path, encoding="utf-8").read()
    found = text.count(old)
    if found != count:
        sys.stderr.write("REFUSING %s: the anchor appears %d times\n" % (label, found))
        raise SystemExit(1)
    io.open(path, "w", encoding="utf-8", newline="\n").write(text.replace(old, new, count))
    print("ok   %s" % label)


# 1. StatModifierBuff: an optional live share, and one branch where the modifier is attached
patch(
    BUFF,
    "    private final int maxStacks;",
    "    private final int maxStacks;\n"
    "    /**\n"
    "     * \u2b50 When set, this modifier asks for its share on every read instead of holding the number it was built with\n"
    "     * (2026-10-02; reader: the fourteen \u300c\u6bcf\u62e5\u6709 1 \u5c42\u2026\u63d0\u9ad8 X%\u300d auras). Only add-percent modifiers may be live -- a\n"
    "     * pure or multiply share has no spelling for \u300c\u968f\u5c42\u6570\u53d8\u5316\u300d yet, and this refuses rather than guessing.\n"
    "     */\n"
    "    private java.util.function.DoubleSupplier liveShare;",
    "StatModifierBuff.liveShare: the optional supplier",
)

patch(
    BUFF,
    """    @Override
    public void applyEffect(CanHit target) {
        DoubleValue attributeValue = target.getAttribute(attribute);
        attributeValue.addModifier(
                new DoubleValue.Modifier(modifierType, value, sourceRole, id));""",
    """    /**
     * Makes this modifier LIVE: its share is asked for on every read (2026-10-02).
     *
     * @throws IllegalStateException when the modifier is not an add-percent one, because the live kind only exists for
     *                               that type -- silently keeping the stored number would be a wrong number with no symptom
     */
    public void makeLive(java.util.function.DoubleSupplier share) {
        if (modifierType != DoubleValue.Modifier.ModifierType.ADD_PERCENT) {
            throw new IllegalStateException(
                    "A live share only exists for add_percent modifiers, but this one is " + modifierType);
        }
        this.liveShare = share;
    }

    /** Whether the share is re-read on every computation. */
    public boolean isLive() {
        return liveShare != null;
    }

    @Override
    public void applyEffect(CanHit target) {
        DoubleValue attributeValue = target.getAttribute(attribute);
        attributeValue.addModifier(liveShare != null
                ? DoubleValue.Modifier.livePercent(liveShare, sourceRole, id)
                : new DoubleValue.Modifier(modifierType, value, sourceRole, id));""",
    "StatModifierBuff.applyEffect: attach the live kind when asked",
)

# 2. EffectSpec: the spelling
patch(
    SPEC,
    '    @SerializedName("stackable")\n    private Boolean stackable;',
    '    @SerializedName("stackable")\n    private Boolean stackable;\n\n'
    '    /**\n'
    '     * \u2b50 `per_stack` resolved at READ time instead of when the modifier is attached (2026-10-02).\n'
    '     *\n'
    '     * <p>\u300c\u827e\u4e1d\u59b2\u6bcf\u62e5\u6709 1 \u5c42\u84c4\u80fd\uff0c\u4f1a\u4f7f\u6211\u65b9\u5168\u4f53\u653b\u51fb\u529b\u63d0\u9ad8 14.00%\uff0c\u6700\u591a 5 \u5c42\u300d: a sustained aura whose number has to follow the count.\n'
    '     * A snapshot is right only at the instant it is taken, and re-attaching on every change would stack the buff\n'
    '     * itself. Only the ctx-free forms of `per_stack` may be live, and the loader refuses the others rather than\n'
    '     * silently keeping a snapshot.\n'
    '     */\n'
    '    @SerializedName("per_stack_live")\n    private Boolean perStackLive;',
    "EffectSpec.perStackLive: the spelling",
)

patch(
    SPEC,
    "        copy.stackable = this.stackable;",
    "        copy.stackable = this.stackable;\n        copy.perStackLive = this.perStackLive;",
    "EffectSpec.copy: the new field survives a copy",
)

# 3. the persistent path: do not snapshot, mark the buff live instead
patch(
    INTERP,
    """        if (effect.getPerStack() != null && !effect.getPerStack().isBlank()) {""",
    """        boolean livePerStack = Boolean.TRUE.equals(effect.getPerStackLive());
        if (livePerStack && !ctxFreeCounter(effect)) {
            throw new IllegalStateException(
                    "per_stack_live only exists for the counter forms (self_stacks:<NAME> or a bare name), but this rule\\'s "
                            + "per_stack is '" + effect.getPerStack() + "' (source: " + ctx.ruleId() + ")");
        }
        if (!livePerStack && effect.getPerStack() != null && !effect.getPerStack().isBlank()) {""",
    "the persistent path: a live share is not snapshotted",
)

patch(
    INTERP,
    """            if (effect.getBuff() != null && !effect.getBuff().isBlank()) {
                buff.setBuffName(effect.getBuff().trim());""",
    """            if (livePerStack && buff instanceof
                    com.laosun.aluminium.models.buff.StatModifierBuff liveBuff) {
                // \u2b50 The share is asked for on every read, so the aura follows the count (2026-10-02).
                String counter = counterName(effect);
                liveBuff.makeLive(() -> magnitude * target.getBuffManager().stacksOf(counter));
            }
            if (effect.getBuff() != null && !effect.getBuff().isBlank()) {
                buff.setBuffName(effect.getBuff().trim());""",
    "the persistent path: mark the buff live with a ctx-free reader",
)

# 4. the two small helpers the error message and the supplier share
patch(
    INTERP,
    """    private static double perStackFactor(EffectSpec effect, CanHit target, TriggerContext ctx) {""",
    """    /** Whether `per_stack` names something a supplier can read later without an event context (2026-10-02). */
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

# 5. the count forms, named once
patch(
    INTERP,
    """    private static final Set<String> SCALES =""",
    """    /** The `per_stack` forms that count something about the CURRENT hit: they have no ctx-free reader (2026-10-02). */
    private static final Set<String> COUNT_FORMS = Set.of(
            "target_debuff_count", "target_dot_count", "target_weakness_count", "shielded_count");

    private static final Set<String> SCALES =""",
    "COUNT_FORMS: the per-hit count forms",
)
