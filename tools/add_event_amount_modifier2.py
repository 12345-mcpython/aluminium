"""`event_amount` as a MODIFIER scale (2026-10-02), second attempt -- and why it is NOT the duplicate withdrawn earlier.

Measured readers (all with data files): 1306:159/161 「我方目标每消耗1点战技点，则使我方全体造成的伤害提高6.00%…最多3层」,
1413:343 「本次攻击每消耗了1点【忆质】额外使长夜月的速度提高1%，最多计算40点」, 1415:542 「每消耗1%溢出值…伤害倍率提高0.24%」.

The withdrawal: `scale: "event_amount"` was removed from `grantAmount` because the "magnitude from the event" spelling
ALREADY existed -- `amount_from_event` (+ `amount_percent`), read in `gainResource` only. That conclusion holds for
`GAIN_RESOURCE` and for nothing else: `MODIFY_ATTR`/`BOOST_DAMAGE` have no such spelling, and these three readers are all
modifiers. So the branch goes where modifiers read their magnitude (`derivedMagnitude`) plus its early-exit list.

Judge: file-driven character (1306), one hand-built rule on the existing `SKILL_POINT_SPENT` event.
ASCII only.
"""
import io
import sys

INTERP = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
JUDGE = "src/test/java/com/laosun/aluminium/test/EventAmountModifierTest.java"

text = io.open(INTERP, encoding="utf-8").read()
if "EVENT_AMOUNT" in text:
    print("skip interp")
else:
    OLD = "        if (SELF_MAX_ENERGY.equals(effect.getScale().trim())) {\n"
    NEW = ("        if (EVENT_AMOUNT.equals(effect.getScale().trim())) {\n"
           "            // ⭐ 「每消耗 1 点…提高 X%」 (2026-10-02): the triggering event's own magnitude, as a\n"
           "            // modifier. ⚠ Not a duplicate of `amount_from_event`: that spelling is read in `gainResource` ONLY\n"
           "            // (it was withdrawn from `grantAmount` for exactly that reason), while these readers are all modifiers.\n"
           "            // Before the attribute branch below, for the reason `cast_energy_spent` gives.\n"
           "            return effect.getPercent() * Math.abs(ctx.amount())\n"
           "                    + (effect.getAmount() == null ? 0 : effect.getAmount());\n"
           "        }\n"
           + OLD)
    if text.count(OLD) != 1:
        print("FAIL interp: magnitude anchor matched %d times" % text.count(OLD))
        sys.exit(1)
    text = text.replace(OLD, NEW)

    OLD_EARLY = '        if (SELF_MAX_ENERGY.equals(raw)) {\n'
    NEW_EARLY = '        if (SELF_MAX_ENERGY.equals(raw) || EVENT_AMOUNT.equals(raw)) {\n'
    if text.count(OLD_EARLY) != 1:
        print("FAIL interp: early-exit anchor matched %d times" % text.count(OLD_EARLY))
        sys.exit(1)
    text = text.replace(OLD_EARLY, NEW_EARLY)

    ANCHOR = '    private static final String CAST_ENERGY_SPENT = "cast_energy_spent";\n'
    CONST = (ANCHOR + '\n'
             '    /**\n'
             '     * 「每消耗/每损失 1 点…提高 X%」 (2026-10-02): a modifier’s magnitude that follows the event.\n'
             '     *\n'
             '     * <p>Sits beside {@link #CAST_ENERGY_SPENT} on purpose — the same shape bound to the cast — and, like it,\n'
             '     * is deliberately not an {@code AttributeType}.\n'
             '     */\n'
             '    private static final String EVENT_AMOUNT = "event_amount";\n')
    if text.count(ANCHOR) != 1:
        print("FAIL interp: constant anchor matched %d times" % text.count(ANCHOR))
        sys.exit(1)
    text = text.replace(ANCHOR, CONST)
    io.open(INTERP, "w", encoding="utf-8", newline="").write(text)
    print("ok   derivedMagnitude + scaleAttribute accept event_amount")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 「每消耗1点战技点…造成的伤害提高 6%」 (1306:159, 2026-10-02): `scale: "event_amount"` on a MODIFIER.
 *
 * <p>File-driven character, one hand-built rule on the existing `SKILL_POINT_SPENT` event, and the amount spent is the
 * magnitude -- so 3 points is 3x one point, and no spend is no change at all.
 */
public class EventAmountModifierTest {
    private static final int OWNER = 1306;
    private static final int MONSTER = 1002011;

    /** ⭐ Three points spent raise the modifier three times as far as one. */
    @Test
    public void theModifierFollowsTheEvent() {
        double one = boostAfterSpending(1);
        double three = boostAfterSpending(3);
        Assertions.assertTrue(one > 0, "precondition: the modifier lands (" + one + ")");
        Assertions.assertEquals(3 * one, three, one * 1e-6,
                "3 points spent is 3x one point (" + one + " -> " + three + ")");
        Assertions.assertEquals(0, boostAfterSpending(0), 1e-9, "and spending nothing changes nothing");
    }

    // ==================================================================

    private static double boostAfterSpending(int spent) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "ALL_DAMAGE_TYPE_BOOST");
        TriggerSpecs.set(effect, "scale", "event_amount");
        TriggerSpecs.set(effect, "percent", 0.06);
        TriggerSpecs.set(effect, "permanent", true);
        TriggerSpecs.set(effect, "target", "self");
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(TriggerSpecs.rule("SKILL_POINT_SPENT",
                List.of("actor == self"), effect))));

        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        double before = owner.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        battle.fireTriggers(TriggerEvent.SKILL_POINT_SPENT, owner, owner, 0, spent);
        battle.processRequests();
        return owner.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - before;
    }
}
''')
print("ok   judge written")
