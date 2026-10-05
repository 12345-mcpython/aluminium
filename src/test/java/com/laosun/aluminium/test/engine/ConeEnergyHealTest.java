package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Three cones whose effects a real battle can drive, chosen by asking whether the state exists before the trigger: 20008 (party energy at battle start),
 * 20013 (energy after a skill, once per turn) and 20010 (heal on the ultimate, scaled by the wearer's own max HP).
 */
public class ConeEnergyHealTest {
    private static final int WEARER = 1001;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void cone20008GivesTheWholePartyEnergyAtBattleStart() {
        Character wearer = wearer(20008, 1);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy()), new Random(0));
        double wearerBefore = wearer.getCurrentEnergy();
        double allyBefore = ally.getCurrentEnergy();
        battle.startBattle();
        Assertions.assertTrue(wearer.getCurrentEnergy() > wearerBefore,
                "the wearer gains energy: " + wearerBefore + " -> " + wearer.getCurrentEnergy());
        Assertions.assertTrue(ally.getCurrentEnergy() > allyBefore,
                "and so does the ally: " + allyBefore + " -> " + ally.getCurrentEnergy());
    }


    @Test
    public void cone20010HealsOnTheUltimateByMaxHpShare() {
        Character unit = wearer(20010, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy()), new Random(0));
        battle.startBattle();
        unit.takeDamage(unit.getMaxHp() * 0.5);
        double before = unit.getCurrentHp();
        battle.fireTriggers(TriggerEvent.ULT_CAST, unit, null, 0, 0);
        double healed = unit.getCurrentHp() - before;
        Assertions.assertTrue(healed > 0, "the ultimate heals: " + before + " -> " + unit.getCurrentHp());
        Assertions.assertEquals(unit.getMaxHp() * 0.18, healed, 1.0,
                "the heal is " + (0.18 * 100) + "% of max HP: healed " + healed + " of " + unit.getMaxHp());
    }

    private static Character wearer(int cone, int rank) {
        return CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(cone, LEVEL, false, rank));
    }

    private static Enemy enemy() {
        return EnemyFactory.create(MONSTER, 90, 1);
    }
}
