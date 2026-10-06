package com.laosun.aluminium.test.content.characters;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1208 Fu Xuan, from her own file: the team-wide damage reduction of [避厄] and the two numbers [鉴知] hands out.
 */
public class FuXuanTest {
    private static final int FUXUAN = 1208;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Note: [避厄]'s 18% reduction, in the measured convention: damage x (1 - 0.18), compared with a hand-built -36% rule. */
    @Test
    public void misfortuneAvoidanceCutsTheDamageTheTeamTakes() {
        double plain = hitLoss(false);
        double shielded = hitLoss(true);
        double reference = referenceHitLoss();

        Assertions.assertTrue(plain > 0, "the fixture must deal damage");
        Assertions.assertEquals(0.82, shielded / plain, 0.02,
                "18% less damage is damage x 0.82: " + shielded + " vs " + plain);
        Assertions.assertEquals(0.5, (1.0 - shielded / plain) / (1.0 - reference / plain), 0.05,
                "18% against a hand-built 36% must be half the reduction");
    }

    /** Note: [鉴知]: 6% of HER max HP as extra max HP, and +12% crit rate, on an ally. */
    @Test
    public void knowledgeRaisesMaxHpByHerShareAndCritRate() {
        Character fuxuan = CharacterFactory.create(FUXUAN, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(fuxuan, ally), List.of(enemy), fixed());
        battle.startBattle();
        double hpBefore = ally.getAttribute(AttributeType.HEALTH).get();
        double critBefore = ally.getAttribute(AttributeType.CRIT_CHANCE).get();
        double expected = fuxuan.getAttribute(AttributeType.HEALTH).get() * 0.06;

        battle.castImmediate(fuxuan.getSkills().get(SkillType.SKILL), fuxuan, List.of(ally));

        Assertions.assertTrue(ally.getBuffManager().hasState("鉴知"),
                "「处于【穷观阵】的我方全体获得【鉴知】」 (all allies inside the Matrix of Prescience (【穷观阵】) gain Knowledge (【鉴知】))");
        Assertions.assertEquals(expected, ally.getAttribute(AttributeType.HEALTH).get() - hpBefore, expected * 0.02,
                "6% of HER max HP: expected " + expected);
        Assertions.assertEquals(0.12, ally.getAttribute(AttributeType.CRIT_CHANCE).get() - critBefore, 1e-6,
                "「暴击率提高12.00%」 (CRIT Rate is raised by 12.00%)");
    }

    /** One fixed hit against an ally, with her talent active or not. */
    private static double hitLoss(boolean talent) {
        Character fuxuan = CharacterFactory.create(FUXUAN, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(fuxuan, ally), List.of(enemy), fixed());
        if (talent) {
            battle.startBattle();
        }
        double before = ally.getCurrentHp();
        battle.applyDamage(ally, new Damage(enemy, ally, com.laosun.aluminium.enums.DamageElement.PHYSICAL,
                com.laosun.aluminium.enums.DamageType.NORMAL, 1000));
        return before - ally.getCurrentHp();
    }

    /** The same hit with a hand-built -36% damage-taken rule, for the scale of comparison. */
    private static double referenceHitLoss() {
        Character fuxuan = CharacterFactory.create(FUXUAN, LEVEL);
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_DAMAGE_TAKEN");
        TriggerSpecs.set(effect, "percent", -0.36);
        TriggerSpecs.set(effect, "permanent", true);
        TriggerSpecs.set(effect, "target", "all_allies");
        fuxuan.setTriggerTable(new TriggerTable(FUXUAN, List.of(TriggerSpecs.rule(
                TriggerEvent.BATTLE_START.name(), List.of(), effect))));
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(fuxuan, ally), List.of(enemy), fixed());
        battle.startBattle();
        double before = ally.getCurrentHp();
        battle.applyDamage(ally, new Damage(enemy, ally, com.laosun.aluminium.enums.DamageElement.PHYSICAL,
                com.laosun.aluminium.enums.DamageType.NORMAL, 1000));
        return before - ally.getCurrentHp();
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
    }
}
