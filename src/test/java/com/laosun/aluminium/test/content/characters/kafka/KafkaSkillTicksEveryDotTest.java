package com.laosun.aluminium.test.content.characters.kafka;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.DotBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1005 卡芙卡's Skill: 「若指定敌方单体处于持续伤害状态，其当前承受的<b>所有</b>持续伤害立即产生相当于原伤害 75% 的伤害」.
 *
 * <p>{@code TICK_DOT} with no element settles EVERY DoT state on the target. The check plants two DoTs of different
 * elements and compares the damage with and without the Rule's event, so a rule that only ticked one element (the shape
 * before this) would show half the delta.
 */
public class KafkaSkillTicksEveryDotTest {

    private static final int KAFKA = 1005;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;

    @Test
    public void everyDotStateSettlesItsSeventyFivePercent() {
        double withOne = instantDamage(1);
        double withTwo = instantDamage(2);
        System.out.println("[1005] skill instant: one DoT " + withOne + ", two DoTs " + withTwo);
        Assertions.assertTrue(withOne > 0, "precondition: the planted DoT settles");
        Assertions.assertEquals(withOne * 2, withTwo, withOne * 2 * 1e-6,
                "「其当前承受的**所有**持续伤害」: two states settle two instances, not one");
    }

    /** Damage the Skill settles on the main target, with {@code dots} DoT states planted. */
    private static double instantDamage(int dots) {
        Character kafka = CharacterFactory.create(KAFKA, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(kafka, ally)),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        DamageElement[] elements = {DamageElement.FIRE, DamageElement.WIND};
        for (int i = 0; i < dots; i++) {
            // A LARGE base damage so the tick dominates the Skill's own hit: this case is about how many STATES settle, not
            // about the ability's damage.
            enemy.getBuffManager().addBuff(new DotBuff(ally, elements[i], 100_000, 3, 0, i == 0 ? "灼烧" : "风化"));
        }
        long planted = enemy.getBuffManager().allBuffsOf(DotBuff.class).size();
        Assertions.assertEquals(dots, (int) planted, "precondition: " + dots + " DoT state(s) planted");
        int[] dotInstances = new int[]{0};
        battle.addDamageObserver((damage, applied, hp, shield, died) -> {
            if (damage.getType() == com.laosun.aluminium.enums.DamageType.DOT) {
                dotInstances[0]++;
            }
        });
        double before = enemy.getCurrentHp();
        battle.castImmediate(kafka.getSkills().get(SkillType.SKILL), kafka, List.of(enemy));
        battle.processRequests();
        System.out.println("[1005] DOT instances settled: " + dotInstances[0]);
        return before - enemy.getCurrentHp();
    }
}