"""The derived half of the live family: `scale: self_stacks:<NAME>` can be live too (2026-10-02, item 64).

Measured last round on 1314: the CRIT_ATTACK clause spelled with `scale: self_stacks:当品` does NOT follow layers gained later
(+0.0 over four added layers), because `derivedMagnitude` -> `stackScale(...)` yields an ABSOLUTE number computed once.

Measured this round: the modifier kind for that path is `StatModifierBuff.of(attribute, absolute || attribute.isPercent
? "pure" : "add_percent", ...)` -- so a ratio attribute like CRIT_ATTACK gets a PURE modifier, which `makeLive` refused.

Three patches:
  1. `DoubleValue.Modifier.livePure` (compute() already reads `rate()` for pure values, so that is the whole engine side);
  2. `StatModifierBuff.makeLive` accepts PURE_VALUE as well, and `applyEffect` picks the matching live factory;
  3. the interpreter: the live marker now also covers a `scale: self_stacks:<NAME>` clause, and the supplier is built by
     SHAPE -- `percent x layers (+ amount)` for the derived form, `magnitude x layers` for the `per_stack` form. Using one
     formula for both would square the count.
"""
import io
import sys

DOUBLE = "src/main/java/com/laosun/aluminium/models/DoubleValue.java"
BUFF = "src/main/java/com/laosun/aluminium/models/buff/StatModifierBuff.java"
INTERP = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"

for path in (BUFF,):
    if "@AllArgsConstructor" in io.open(path, encoding="utf-8").read():
        sys.exit("REFUSING: %s has @AllArgsConstructor; a new field would change its signature" % path)


def patch(path, old, new, label, count=1):
    text = io.open(path, encoding="utf-8").read()
    found = text.count(old)
    if found != count:
        sys.stderr.write("REFUSING %s: the anchor appears %d times\n" % (label, found))
        raise SystemExit(1)
    io.open(path, "w", encoding="utf-8", newline="\n").write(text.replace(old, new, count))
    print("ok   %s" % label)


patch(
    DOUBLE,
    """        /** Whether this modifier re-resolves its magnitude on every computation. */""",
    """        /**
         * \u2b50 The same, for a modifier whose magnitude is an ABSOLUTE number in the attribute's own units.
         *
         * <p>The derived form \u300c\u6bcf\u5c42\u3010\u5f53\u54c1\u3011\u4f7f\u66b4\u51fb\u4f24\u5bb3\u63d0\u9ad8 2.40%\u300d is absolute, and a ratio attribute takes it as
         * a pure value -- so the live kind needs a pure factory as well as a percentage one.
         */
        public static Modifier livePure(java.util.function.DoubleSupplier value,
                                        ModifierSource source, int sourceRoleId) {
            Modifier modifier = new Modifier(ModifierType.PURE_VALUE, 0, source, sourceRoleId);
            modifier.live = value;
            return modifier;
        }

        /** Whether this modifier re-resolves its magnitude on every computation. */""",
    "Modifier.livePure: the absolute counterpart",
)

patch(
    BUFF,
    """        if (modifierType != DoubleValue.Modifier.ModifierType.ADD_PERCENT) {
            throw new IllegalStateException(
                    "A live share only exists for add_percent modifiers, but this one is " + modifierType);
        }""",
    """        if (modifierType != DoubleValue.Modifier.ModifierType.ADD_PERCENT
                && modifierType != DoubleValue.Modifier.ModifierType.PURE_VALUE) {
            throw new IllegalStateException(
                    "A live magnitude only exists for add_percent and pure modifiers, but this one is " + modifierType);
        }""",
    "makeLive accepts the pure kind",
)

patch(
    BUFF,
    """        attributeValue.addModifier(liveShare != null
                ? DoubleValue.Modifier.livePercent(liveShare, sourceRole, id)
                : new DoubleValue.Modifier(modifierType, value, sourceRole, id));""",
    """        attributeValue.addModifier(liveShare != null
                ? (modifierType == DoubleValue.Modifier.ModifierType.PURE_VALUE
                ? DoubleValue.Modifier.livePure(liveShare, sourceRole, id)
                : DoubleValue.Modifier.livePercent(liveShare, sourceRole, id))
                : new DoubleValue.Modifier(modifierType, value, sourceRole, id));""",
    "applyEffect picks the matching live factory",
)

patch(
    INTERP,
    """            if (Boolean.TRUE.equals(effect.getPerStackLive())) {""",
    """            if (Boolean.TRUE.equals(effect.getPerStackLive())) {""",
    "the live marker (anchor check)",
)

patch(
    INTERP,
    """                if (!ctxFreeCounter(effect)) {
                    throw new IllegalStateException(
                            "per_stack_live only exists for the counter forms (self_stacks:<NAME> or a bare name), but this "
                                    + "rule's per_stack is '" + effect.getPerStack() + "'; the per-hit count forms are read "
                                    + "from an event context and (source: " + ctx.ruleId() + ")");
                }
                if (buff instanceof com.laosun.aluminium.models.buff.StatModifierBuff liveBuff) {
                    String counter = counterName(effect);
                    liveBuff.makeLive(() -> magnitude * target.getBuffManager().stacksOf(counter));
                }""",
    """                boolean derivedCounter = effect.getScale() != null
                        && effect.getScale().trim().startsWith("self_stacks:");
                if (!derivedCounter && !ctxFreeCounter(effect)) {
                    throw new IllegalStateException(
                            "per_stack_live only exists for the counter forms (self_stacks:<NAME> or a bare name), but this "
                                    + "rule states per_stack='" + effect.getPerStack() + "' and scale='" + effect.getScale()
                                    + "'; the per-hit count forms are read from an event context and (source: "
                                    + ctx.ruleId() + ")");
                }
                if (buff instanceof com.laosun.aluminium.models.buff.StatModifierBuff liveBuff) {
                    if (derivedCounter) {
                        // \u2b50 The derived form is ABSOLUTE: percent x layers (+ amount), read on every computation. Building it from
                        // `magnitude` would square the count, because magnitude already contains it.
                        String own = effect.getScale().trim().substring("self_stacks:".length()).trim();
                        double extra = effect.getAmount() == null ? 0 : effect.getAmount();
                        double pct = effect.getPercent() == null ? 0 : effect.getPercent();
                        liveBuff.makeLive(() -> pct * target.getBuffManager().stacksOf(own) + extra);
                    } else {
                        String counter = counterName(effect);
                        liveBuff.makeLive(() -> magnitude * target.getBuffManager().stacksOf(counter));
                    }
                }""",
    "the supplier is built by shape",
)
