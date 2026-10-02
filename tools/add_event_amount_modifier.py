"""`event_amount` on `MODIFY_ATTR` (2026-10-02) -- path B: one branch, no whitelist to touch.

`MODIFY_ATTR` validates only that a `scale` is PRESENT (the closed-set check lives in `derivedMagnitude`, which throws
"passed validation but has no implementation" otherwise), so the whole change is the branch itself -- placed BEFORE the
attribute fallback, exactly as `cast_energy_spent`'s comment explains, because a non-attribute source would otherwise get
a message about missing unit data.

Reader: 1413's 「本次攻击每消耗了1点【忆质】额外使长夜月的速度提高 1%，最多计算 40 点」. The judge proves it with the flat
shape the same branch serves: 1 speed per 100 points lost (percent 0.01).

Extends the existing judge rather than adding a new file. ASCII only.
"""
import io
import sys

INTERP = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
JUDGE = "src/test/java/com/laosun/aluminium/test/EventAmountTest.java"

interp = io.open(INTERP, encoding="utf-8").read()
if 'EVENT_AMOUNT' in interp:
    print("skip interp")
else:
    OLD = ("        if (SELF_MAX_ENERGY.equals(effect.getScale().trim())) {\n")
    NEW = ("        if (EVENT_AMOUNT.equals(effect.getScale().trim())) {\n"
           "            // \u2b50 \u300c\u672c\u6b21\u653b\u51fb\u6bcf\u6d88\u8017\u4e86 1 \u70b9\u3010\u4ebf\u8d28\u3011\u989d\u5916\u4f7f\u957f\u591c\u6708\u7684\u901f\u5ea6\u63d0\u9ad8 1%\u300d (2026-10-02): the\n"
           "            // triggering event's own magnitude, taken as a quantity (a spend arrives negative). Before the\n"
           "            // attribute branch below, for the reason `cast_energy_spent` states: this source is not an attribute.\n"
           "            return effect.getPercent() * Math.abs(ctx.amount())\n"
           "                    + (effect.getAmount() == null ? 0 : effect.getAmount());\n"
           "        }\n"
           + OLD)
    if interp.count(OLD) != 1:
        print("FAIL interp: anchor matched %d times" % interp.count(OLD))
        sys.exit(1)
    interp = interp.replace(OLD, NEW)

    ANCHOR = '    private static final String CAST_ENERGY_SPENT = "cast_energy_spent";\n'
    NEW_CONST = (ANCHOR + '\n'
                 '    /**\n'
                 '     * \u300c\u6bcf\u6d88\u8017/\u6bcf\u635f\u5931 1 \u70b9\u2026\u300d (2026-10-02): a magnitude that follows the <b>triggering event</b>.\n'
                 '     *\n'
                 '     * <p>Sits beside {@link #CAST_ENERGY_SPENT} on purpose -- that one is the same shape bound to the cast\n'
                 '     * instead of the event -- and, like it, is deliberately not an {@code AttributeType}.\n'
                 '     */\n'
                 '    private static final String EVENT_AMOUNT = "event_amount";\n')
    if interp.count(ANCHOR) != 1:
        print("FAIL interp: constant anchor matched %d times" % interp.count(ANCHOR))
        sys.exit(1)
    interp = interp.replace(ANCHOR, NEW_CONST)
    io.open(INTERP, "w", encoding="utf-8", newline="").write(interp)
    print("ok   derivedMagnitude: event_amount branch + its constant")

judge = io.open(JUDGE, encoding="utf-8").read()
if "MODIFY_ATTR" in judge:
    print("skip judge")
else:
    MARKER = "    // ==================================================================\n"
    CASE = '''    /** \u2b50 The same number as a MODIFIER: 1 speed per 100 points of health lost. */
    @Test
    public void theModifierFollowsTheEvent() {
        Character owner = CharacterFactory.create(OWNER, LEVEL, false, null, null, 0);
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "SPEED");
        TriggerSpecs.set(effect, "scale", "event_amount");
        TriggerSpecs.set(effect, "percent", 0.01);
        TriggerSpecs.set(effect, "permanent", true);
        TriggerSpecs.set(effect, "target", "self");
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(TriggerSpecs.rule("HP_LOST",
                List.of("actor == self"), effect))));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(owner), List.of(enemy), new Random(0));
        battle.startBattle();

        double before = owner.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get();
        battle.fireTriggers(TriggerEvent.HP_LOST, owner, owner, 0, 100);
        battle.processRequests();
        Assertions.assertEquals(1.0, owner.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get() - before,
                1e-6, "100 points lost at 1% each is +1 speed");
    }

'''
    if judge.count(MARKER) != 1:
        print("FAIL judge: marker matched %d times" % judge.count(MARKER))
        sys.exit(1)
    judge = judge.replace(MARKER, CASE + MARKER)
    io.open(JUDGE, "w", encoding="utf-8", newline="").write(judge)
    print("ok   judge extended with the MODIFY_ATTR case")
