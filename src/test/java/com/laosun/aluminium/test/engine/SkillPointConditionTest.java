package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.ResourceSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The numeric variable {@code skill_point}: the team's <b>current</b> Skill Points, as a condition.
 *
 * <p>Reader: 1015 Archer (Archer)'s trace 1015103 - 「我方获得战技点后，若<b>战技点大于等于 4 点</b>…」 - and his Skill's
 * exit clause 「战技点不足以再次施放战技则退出」.
 *
 * <p>⚠ <b>The third case is the whole point.</b> The vocabulary already had {@code skill_point_max} (the CAP), and a
 * careless reading substitutes it - "the cap is 7, so 7 >= 4, the condition holds" - which is a different question with a
 * different answer whenever the pool is not full. The case therefore pins a state where the cap is raised and the count is
 * below the threshold: a cap-reading implementation passes it, a count-reading one does not.
 */
public class SkillPointConditionTest {
    private static final int OWNER = 1003;
    private static final int LEVEL = 80;
    private static final String COUNTER = "counter";
    private static final int CAP_BONUS = 2;

    /**
     * Three points: below the threshold, so nothing fires. Four: it fires once.
     */
    @Test
    public void theCurrentCountGatesTheRule() {
        Assertions.assertEquals(0, firings(3), "3 点 < 4 ⇒ 条件不成立");
        Assertions.assertEquals(1, firings(4), "4 点 ⇒ 条件成立");
    }

    /**
     * The count, not the cap: the cap is raised to 7 here and the pool holds 3.
     */
    @Test
    public void theCapIsNotTheCount() {
        Assertions.assertEquals(0, firingsWithRaisedCap(3),
                "上限被抬到 7、而手里只有 3 点 ⇒ 「战技点大于等于 4」**不成立**（拿上限冒充会读成 7 ✓ 那就红了）");
        Assertions.assertEquals(1, firingsWithRaisedCap(4), "而手里有 4 点（上限 7）⇒ 成立");
    }

    // ==================================================================

    private static int firings(int skillPoints) {
        return firings(skillPoints, 0);
    }

    private static int firingsWithRaisedCap(int skillPoints) {
        return firings(skillPoints, CAP_BONUS);
    }

    /**
     * How many times the gated rule fired, with the pool forced to {@code skillPoints} and the cap raised by
     * {@code capBonus}.
     *
     * <p>Note: The pool is moved through {@code battle.skillPointPolicy} on purpose - the public
     * {@code gainSkillPoint} raises the event this test is about, and setting up the premise with it would fire the rule
     * before the measurement (measured: the counter read 1 before the case even fired it).
     */
    private static int firings(int skillPoints, int capBonus) {
        Character owner = CharacterFactory.create(OWNER, LEVEL, false, null, null, 0);
        owner.getResources().register(COUNTER, 99, 0);

        EffectSpec gain = new EffectSpec();
        TriggerSpecs.set(gain, "op", "GAIN_RESOURCE");
        TriggerSpecs.set(gain, "resource", COUNTER);
        TriggerSpecs.set(gain, "amount", 1.0);
        TriggerSpecs.set(gain, "target", "self");

        EffectSpec raiseCap = new EffectSpec();
        TriggerSpecs.set(raiseCap, "op", "RAISE_SKILL_POINT_CAP");
        TriggerSpecs.set(raiseCap, "amount", (double) CAP_BONUS);
        TriggerSpecs.set(raiseCap, "target", "self");

        owner.setTriggerTable(new TriggerTable(OWNER, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(), raiseCap),
                TriggerSpecs.rule("SKILL_POINT_GAINED", List.of("skill_point >= 4"), gain)),
                List.of(new ResourceSpec(COUNTER, 99, 0, null, null, null, null))));

        Battle battle = new Battle(List.of(owner), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();

        while (battle.getSkillPoints() > skillPoints) {
            battle.getSkillPointPolicy().spend();
        }
        while (battle.getSkillPoints() < skillPoints) {
            battle.getSkillPointPolicy().gain(1);
        }
        Assertions.assertEquals(skillPoints, battle.getSkillPoints(), "precondition: the pool holds what the case asks");
        Assertions.assertEquals(5 + CAP_BONUS, battle.getSkillPointMax(), "precondition: the cap was raised");

        battle.fireTriggers(TriggerEvent.SKILL_POINT_GAINED, owner, owner, 0, 1);
        battle.processRequests();
        return battle.characters().getFirst().getResources().value(COUNTER);
    }
}
