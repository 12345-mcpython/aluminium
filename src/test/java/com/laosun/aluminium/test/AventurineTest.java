package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1304 Aventurine, from his own file (2026-09-29, round 175): the opening shield his trace grants, and the ultimate's state.
 *
 * <p>The shield's magnitude is the DOCUMENT'S arithmetic — 24.00% of his DEF plus 320 — so the case asserts the number itself, and the mutation that
 * drops the flat addend makes it red.
 */
public class AventurineTest {
    private static final int AVENTURINE = 1304;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** ⚠ 「为我方全体提供…等同于砂金24.00%防御力+320伤害的护盾【坚垣筹码】，持续3回合」. */
    @Test
    public void hisOpeningShieldUsesHisDefencePlusTheFlatAddend() {
        Character aventurine = CharacterFactory.create(AVENTURINE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(aventurine, ally), List.of(enemy), fixed());
        battle.startBattle();

        double expected = aventurine.getAttribute(AttributeType.DEFENCE).get() * 0.24 + 320;
        Assertions.assertTrue(aventurine.getShield() > 0, "「我方全体提供护盾【坚垣筹码】」 -- himself");
        Assertions.assertTrue(ally.getShield() > 0, "「我方全体」 -- and his ally");
        Assertions.assertEquals(expected, aventurine.getShield(), expected * 0.02,
                "「等同于砂金24.00%防御力+320的护盾」: expected " + expected + ", shield " + aventurine.getShield());
    }

    /** ⚠ 「使指定敌方单体陷入【惊惶】状态，持续3回合」. */
    @Test
    public void hisUltimateUnnervesTheTarget() {
        Character aventurine = CharacterFactory.create(AVENTURINE, LEVEL);
        Enemy enemy = Enemy.fromAttributes("Dummy", 40000, 500, 100, 90);
        Battle battle = new Battle(List.of(aventurine), List.of(enemy), fixed());
        battle.startBattle();
        Assertions.assertFalse(enemy.getBuffManager().hasState("惊惶"), "precondition: not unnerved yet");

        battle.castImmediate(aventurine.getSkills().get(SkillType.ULTRA), aventurine, List.of(enemy));

        Assertions.assertTrue(enemy.getBuffManager().hasState("惊惶"),
                "「使指定敌方单体陷入【惊惶】状态，持续3回合」");
    }

    /** Census: the shield, the state and the level convention — and the skill's own shield is the DATA TABLE's job. */
    @Test
    public void hisFileCarriesTheClauses() {
        var table = TriggerTables.of(AVENTURINE);
        Assertions.assertEquals(2, table.ruleCount(TriggerEvent.BATTLE_START), "the opening shield and the level convention");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.ULT_CAST), "the state");
        Assertions.assertEquals(0, table.ruleCount(TriggerEvent.SKILL_CAST),
                "the Skill's shield comes from data/skill_effects.json, so a rule would double-count it");
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 1.0;
            }
        };
    }
}
