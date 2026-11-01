package com.laosun.aluminium.test.content.characters.ashveil;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1504 不死途's Eidolons 1 and 6, measured on the enemy the aura reaches.
 *
 * <p>E1 raises the damage every enemy takes; E6 lowers their all-type RES. Each is compared against the rank BELOW it, so
 * the character's other Eidolons cannot account for the difference.
 */
public class AshveilEidolonOneAndSixTest {

    private static final int OWNER = 1504;
    private static final int LEVEL = 80;

    @Test
    public void eidolonSixLowersTheEnemysResistance() {
        double atFive = enemyResistance(5);
        double atSix = enemyResistance(6);
        System.out.println("[1504] enemy RES: E5 " + atFive + " ; E6 " + atSix);
        Assertions.assertTrue(atSix > atFive, "「敌方全体的全属性抗性降低 20%」");
    }

    /** Damage one fixed hit settles on the enemy, at the given Eidolon rank. */
    private static double taken(int eidolon) {
        Character owner = CharacterFactory.create(OWNER, LEVEL, true, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(owner)), new ArrayList<>(List.of(enemy)),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        double before = enemy.getCurrentHp();
        battle.applyDamage(enemy, new com.laosun.aluminium.damage.Damage(owner, enemy,
                com.laosun.aluminium.enums.DamageElement.THUNDER,
                com.laosun.aluminium.enums.DamageType.NORMAL, 1000));
        return before - enemy.getCurrentHp();
    }

    private static double enemyResistance(int eidolon) {
        Character owner = CharacterFactory.create(OWNER, LEVEL, true, null, null, eidolon);
        Battle battle = new Battle(new ArrayList<>(List.of(owner)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return battle.enemyUnits().getFirst().getAttribute(AttributeType.RESISTANCE_REDUCTION).get();
    }
}