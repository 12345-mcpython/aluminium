package com.laosun.aluminium.test.content.characters.pearl;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1503 真珠's enhanced basic, the heal clause -- <b>how much</b>, not just "it heals".
 *
 * <p>「为我方全体目标回复等同于真珠 <b>8.00%</b> 防御力 + <b>160</b> 的生命值」: those two numbers are the
 * <b>low-level row</b>. This file's own {@code level_convention} says the corpus quotes <b>Lv10</b>, and
 * {@code 150308} / {@code 150310}'s Lv10 row is {@code [1.4, 0.112, 1, 224]} - i.e. <b>11.2% DEF + 224</b>.
 *
 * <p>⚠ The rule used to carry {@code percent: 0.08} / {@code amount: 160} as literals, i.e. about 28% short at max
 * level, with no symptom: 160 <i>does</i> appear in the description, so the number looked sourced. The relational
 * judge next door ({@code PearlEnhancedBasicHealTest}) could not see it - it only asserts "heals twice" - which is
 * exactly the "assert on the proxy, not on the amount" shape {@code AGENT.md} §1.5 warns about.
 */
public class PearlEnhancedBasicHealAmountTest {

    private static final int PEARL = 1503;
    private static final int ALLY = 1204;
    /** The Lv10 row of {@code 150308} / {@code 150310}: {@code #2} and {@code #4}. */
    private static final double SHARE_AT_LV10 = 0.112;
    private static final double FLAT_AT_LV10 = 224;

    @Test
    public void theHealIsTheLv10RowOfDefenceSharePlusFlat() {
        Battle battle = new Battle(List.of(CharacterFactory.create(PEARL, 80), CharacterFactory.create(ALLY, 80)),
                List.of(EnemyFactory.create(1002011, 100, 1)), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character pearl = battle.characters.get(0);
        Character other = battle.characters.get(1);
        // Leave room for the heal on both, so the amount is what the rule delivers and not the cap.
        pearl.takeDamage(pearl.getMaxHp() * 0.6);
        other.takeDamage(other.getMaxHp() * 0.8);

        double before = pearl.getCurrentHp();
        battle.fireTriggers(TriggerEvent.BASIC_ATTACK, pearl, battle.enemies.getFirst(), 1, 0,
                (SkillCategory) null, 8);
        double healed = pearl.getCurrentHp() - before;

        double base = SHARE_AT_LV10 * pearl.getAttribute(AttributeType.DEFENCE).get() + FLAT_AT_LV10;
        // The rule states the BASE; the engine's heal zone then applies her outgoing-healing boost, which is a real
        // multiplier in the chain (measured: 323.83358 -> 330.31025, i.e. x1.02). Asserting the raw row would
        // silently re-encode "no heal zone", which is not what this clause is about.
        double expected = base * (1 + pearl.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get());
        Assertions.assertEquals(expected, healed, Math.max(1e-6, expected * 1e-9),
                "「回复等同于真珠 8.00% 防御力 + 160」 is the LOW-LEVEL row: at Lv10 the row is 11.2% + 224. "
                        + "DEF=" + pearl.getAttribute(AttributeType.DEFENCE).get() + ", base " + base
                        + ", expected (x heal zone) " + expected + ", healed " + healed);
    }
}
