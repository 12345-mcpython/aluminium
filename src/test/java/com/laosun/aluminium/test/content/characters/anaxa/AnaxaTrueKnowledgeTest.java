package com.laosun.aluminium.test.content.characters.anaxa;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1405 那刻夏's Talent: an enemy with at least 5 different weaknesses gets 【质性揭露】, and his damage against it is 30% higher.
 *
 * <p>Note: the standard fixture monster already has one weakness (Fire), so "4 total" is three added elements and
 * "5 total" is four - asserted through {@code weaknessCount()} rather than assumed.
 */
public class AnaxaTrueKnowledgeTest {

    private static final int ANAXA = 1405;
    private static final int LEVEL = 80;
    private static final String STATE = "质性揭露";
    private static final DamageElement[] EXTRA = {DamageElement.ICE, DamageElement.THUNDER, DamageElement.WIND,
            DamageElement.QUANTUM, DamageElement.IMAGINARY, DamageElement.PHYSICAL};

    @Test
    public void onlyAnEnemyWithFiveWeaknessesGetsTheState() {
        Assertions.assertFalse(stateOn(4), "4 weaknesses: nothing");
        Assertions.assertTrue(stateOn(5), "「至少 5 个不同属性弱点」 => the state is applied");
    }

    @Test
    public void hisDamageAgainstItIsHigher() {
        double bare = boostArea(4);
        double marked = boostArea(5);
        System.out.println("[1405] boost zone " + bare + " -> " + marked);
        Assertions.assertEquals(0.3, marked - bare, 1e-6, "「造成的伤害提高 30%」");
    }

    private static boolean stateOn(int total) {
        return field(total).getBuffManager().hasState(STATE);
    }

    /**
     * @param total how many weaknesses the enemy should end up with
     * @return the enemy, after one of his turn starts (when the Talent re-checks the field)
     */
    private static Enemy field(int total) {
        Character anaxa = CharacterFactory.create(ANAXA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(anaxa, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        // Top it up to the wanted TOTAL rather than assuming what the fixture starts with.
        for (DamageElement element : EXTRA) {
            if (enemy.weaknessCount() >= total) {
                break;
            }
            if (!enemy.isWeakTo(element)) {
                enemy.addWeakness(element);
            }
        }
        Assertions.assertEquals(total, enemy.weaknessCount(), "precondition: the enemy has " + total + " weaknesses");
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, anaxa, anaxa, 0, 0));
        return enemy;
    }

    private static double boostArea(int total) {
        Character anaxa = CharacterFactory.create(ANAXA, LEVEL);
        Enemy enemy = field(total);
        Battle battle = new Battle(new ArrayList<>(List.of(anaxa, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        Damage damage = new Damage(anaxa, enemy, DamageElement.WIND, DamageType.NORMAL, 100);
        battle.applyDamage(enemy, damage);
        return damage.breakdown().getOrDefault("BoostArea", 0.0);
    }
}
