"""Engine: `APPLY_BUFF` can apply a state as a STACKABLE one (2026-10-02, item 60).

The corpus says 【好活当赏】 carries a count (「将本次阿哈时刻的笑点计入该状态」) and 1505's 「开不败」 uses that count
(「将其中的 50% 转化为自身的【好活当赏】」). Measured last round: neither existing state kind can do it -- a plain
`StateBuff` REFRESHES on re-application (its `isSameKind` compares names, deliberately), and `ADD_STACK`'s `StackBuff`
accumulates but is not a `StateBuff`, so its expiry never announces `STATE_ENDED`.

So: a new spelling `"stackable": true` on `APPLY_BUFF`, which applies `StackableStateBuff` (the class added alongside this
patch). Five edits, each with an exactly-once ASCII anchor:

  1. `EffectSpec.stackable` -- the spelling;
  2. `EffectSpec.copy()` -- the guard test demands every field survive a copy (`RuleEffectAmendmentTest`);
  3. `requireNoStackArguments` -- `max_stacks` is refused on every op but MODIFY_ATTR; a stackable state needs a cap, so
     the refusal is relaxed exactly when `stackable` is stated;
  4. `applyState` -- the ternary gains the stackable arm;
  5. the import.
"""
import io
import sys

SPEC = "src/main/java/com/laosun/aluminium/beans/EffectSpec.java"
INTERP = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"


def patch(path, old, new, label, count=1):
    text = io.open(path, encoding="utf-8").read()
    found = text.count(old)
    if found != count:
        sys.stderr.write("REFUSING %s: the anchor appears %d times (want %d)\n" % (label, found, count))
        raise SystemExit(1)
    io.open(path, "w", encoding="utf-8", newline="\n").write(text.replace(old, new, count))
    print("ok   %s" % label)


patch(
    SPEC,
    '    @SerializedName("stacks")\n    private Integer stacks;',
    '    @SerializedName("stacks")\n    private Integer stacks;\n\n'
    '    /**\n'
    '     * \u2b50 \u300c\u5c06\u7b11\u70b9**\u8ba1\u5165\u8be5\u72b6\u6001**\u300d: apply this state as a <b>stackable</b> one (2026-10-02; reader: 1505\'s \u3010\u597d\u6d3b\u5f53\u8d4f\u3011).\n'
    '     *\n'
    '     * <p>A plain state REFRESHES when re-applied (its identity is its name, deliberately), so a count cannot ride on it;\n'
    '     * this flag selects {@link com.laosun.aluminium.models.buff.StackableStateBuff}, whose instances accumulate and are\n'
    '     * counted by name, while still being the `StateBuff` that announces its own end.\n'
    '     */\n'
    '    @SerializedName("stackable")\n    private Boolean stackable;',
    "EffectSpec.stackable: the spelling",
)

patch(
    SPEC,
    "        copy.defersDeath = this.defersDeath;",
    "        copy.defersDeath = this.defersDeath;\n        copy.stackable = this.stackable;",
    "EffectSpec.copy: the new field survives a copy",
)

patch(
    INTERP,
    "        if (effect.getMaxStacks() != null || effect.getStacks() != null) {",
    "        // \u26a0 Relaxed for a STACKABLE STATE (2026-10-02): `max_stacks` is otherwise refused on every op but MODIFY_ATTR, and a\n"
    "        // stackable state needs a cap -- that is the whole point of \"counted into that state\".\n"
    "        if (!Boolean.TRUE.equals(effect.getStackable())\n"
    "                && (effect.getMaxStacks() != null || effect.getStacks() != null)) {",
    "requireNoStackArguments: a stackable state may state max_stacks",
)

patch(
    INTERP,
    "                withLifetime(Boolean.TRUE.equals(effect.getDefersDeath())\n"
    "                        ? new DeferredDeathBuff(state, turns, permanent)\n"
    "                        : new StateBuff(state, turns, permanent), effect), effect, ctx), ctx);",
    "                withLifetime(Boolean.TRUE.equals(effect.getStackable())\n"
    "                        ? new StackableStateBuff(state, turns, permanent,\n"
    "                                effect.getMaxStacks() == null ? 1 : effect.getMaxStacks())\n"
    "                        : Boolean.TRUE.equals(effect.getDefersDeath())\n"
    "                        ? new DeferredDeathBuff(state, turns, permanent)\n"
    "                        : new StateBuff(state, turns, permanent), effect), effect, ctx), ctx);",
    "applyState: the stackable arm",
)

patch(
    INTERP,
    "import com.laosun.aluminium.models.buff.DeferredDeathBuff;",
    "import com.laosun.aluminium.models.buff.DeferredDeathBuff;\nimport com.laosun.aluminium.models.buff.StackableStateBuff;",
    "the import",
)
