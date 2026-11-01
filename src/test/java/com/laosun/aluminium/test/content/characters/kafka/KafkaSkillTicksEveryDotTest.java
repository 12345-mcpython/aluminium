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
 * <p>{@code TICK_DOT} with no element settles EVERY DoT state on the target. ⚠ The target must survive the tick: the first
 * version of this judge used the standard monster, which DIED to one tick (its HP is smaller than a 75% DoT tick), so the
 * second state's settlement returned zero and the clause looked broken. {@link BattleFixture#dummy()} has 1,000,000 HP.
 */
public class KafkaSkillTicksEveryDotTest {

    private static final int KAFKA = 1005;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;

    @Test
    public void everyDotStateSettlesItsSeventyFivePercent() {
        double none = tickDamage(0);
        double one = tickDamage(1);
        double two = tickDamage(2);
        double perState = one - none;
        System.out.println("[1005] skill instant damage: 0 DoT " + none + ", 1 " + one + ", 2 " + two
                + " ; one state pays " + perState);
        Assertions.assertTrue(perState > 0, "precondition: the planted DoT settles");
        Assertions.assertEquals(perState, two - one, perState * 1e-6,
                "「其当前承受的**所有**持续伤害」: the second state pays exactly what the first did");
    }

    /** Damage the Skill settles on the main target with {@code dots} DoT states planted. */
    private static double tickDamage(int dots) {
        Character kafka = CharacterFactory.create(KAFKA, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.dummy();
        Battle battle = new Battle(new ArrayList<>(List.of(kafka, ally)),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        DamageElement[] elements = {DamageElement.FIRE, DamageElement.ICE};
        for (int i = 0; i < dots; i++) {
            enemy.getBuffManager().addBuff(new DotBuff(ally, elements[i], 20_000, 3, 0, i == 0 ? "灼烧" : "冻结伤"));
        }
        Assertions.assertFalse(enemy.isDeath(), "precondition: the target survives");
        double before = enemy.getCurrentHp();
        battle.castImmediate(kafka.getSkills().get(SkillType.SKILL), kafka, List.of(enemy));
        battle.processRequests();
        Assertions.assertFalse(enemy.isDeath(), "the target must survive the tick, or the second state would settle nothing");
        return before - enemy.getCurrentHp();
    }
}