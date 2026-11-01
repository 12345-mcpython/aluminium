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

    /**
     * E1 「敌方全体受到的伤害提高 24%」: exactly ONE vulnerability layer appears when the rank is reached, and none
     * before it.
     *
     * <p>⚠ Read as a LAYER COUNT, not as a damage ratio: the settled-damage ratio is (1 + z + 0.24) / (1 + z), so it is
     * not 1.24 whenever anything else already sits in that zone. And read the FILE first - this clause already had a
     * rule (`eidolon1_enemies_take_more`); a second one written beside it made the enemy carry two layers and
     * looked exactly like an engine that applied the clause twice.
     */
    @Test
    public void eidolonOneAddsExactlyOneVulnerabilityLayer() {
        Assertions.assertEquals(0, vulnerabilityLayers(0), "control: nothing before Eidolon 1");
        Assertions.assertEquals(1, vulnerabilityLayers(1), "E1's own 24%, exactly one layer");
    }

    /** How many VulnerabilityBuff layers the enemy carries right after the battle starts. */
    private static int vulnerabilityLayers(int eidolon) {
        Character owner = CharacterFactory.create(OWNER, LEVEL, true, null, null, eidolon);
        Battle battle = new Battle(new ArrayList<>(List.of(owner)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        int count = 0;
        for (var buff : battle.enemyUnits().getFirst().getBuffManager().activeBuffs()) {
            if (buff instanceof com.laosun.aluminium.buff.VulnerabilityBuff) {
                count++;
            }
        }
        return count;
    }

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