package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * "每消灭1个敌方目标<b>额外</b>恢复姬子5点能量" - the kill credit that rides on <b>her ultimate</b>.
 *
 * <p><b>Why the reading needed settling first.</b> The engine already credits <b>any</b> killer 5 energy
 * ({@code Constant.ENERGY_GAIN_KILL}), and this sentence also says 5 - one credit or two? The document answers it: the
 * skill's own {@code param_list} states the 5 ({@code 100303} Lv10 = {@code [1.38, 5]}), the general credit is a rule
 * about the kill happening, and the text says "额外恢复" (it would read "恢复5点能量" if it were that same 5).
 * Hence +5 on top - and this file measures that total.
 *
 * <p>Note: <b>How the first version of this case went wrong, kept as a warning</b>: it left the enemy at 20% HP, which her
 * Lv1 ultimate (1.38  x  ATK ~= 966) cannot take off a 30000-HP monster - so <b>neither</b> run killed anything, the
 * difference was 0, and the "control" was not a control at all. The fixture now leaves the enemy at 1 HP and asserts
 * the precondition it depends on.
 */
public class HimekoKillEnergyTest {
    private static final double EPS = 1e-6;

    private static final int HIMEKO = 1003;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int ULTIMATE_SLOT = 3;

    /** A kill by her ultimate pays the general 5 AND her extra 5. */
    @Test
    public void theUltimatesKillPaysFiveOnTopOfTheGeneralCredit() {
        double killed = ultimateEnergy(true);
        double survived = ultimateEnergy(false);

        // Note: Measured: killed = 10, survived = 5 (2026-09-28). The difference is HER five and nothing else -- the
        // general kill credit is folded into the attack's own energy («one attack grants energy only once»,
        // Battle.grantHitAndKillEnergy), so "额外" really is an extra credit rather than a relabelling of the
        // standard one. That is the reading this case exists to pin.
        Assertions.assertEquals(5, killed - survived, 1.0,
                "「每消灭1个敌方目标额外恢复姬子5点能量」 adds exactly 5 on top of the cast's own energy ("
                        + killed + " vs " + survived + ")");
    }

    /**
     * Note: A kill she did not cause pays her nothing: the general credit follows the killer, and her sentence names her
     * ultimate.
     */
    @Test
    public void aKillFromAnotherSourceDoesNotPayHerExtra() {
        Character hero = CharacterFactory.create(HIMEKO, LEVEL);
        Enemy enemy = weakEnemy();
        Battle battle = new Battle(List.of(hero), List.of(enemy), fixed());
        battle.startBattle();
        enemy.takeDamage(enemy.getMaxHp() - 1);
        double before = hero.getCurrentEnergy();

        Character ally = CharacterFactory.create(1002, LEVEL);
        ally.setTriggerTable(new TriggerTable(1002, List.of()));
        battle.applyDamage(enemy, new Damage(ally, enemy, DamageElement.FIRE, DamageType.NORMAL, 1_000,
                SkillCategory.NORMAL));

        Assertions.assertTrue(enemy.isDeath(), "precondition: the ally's hit killed it");
        Assertions.assertEquals(0, hero.getCurrentEnergy() - before, EPS,
                "姬子 gains nothing from a kill she did not cause -- the general credit goes to the killer");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** Her ultimate against the same enemy, one hit away from death or at full HP. */
    private static double ultimateEnergy(boolean theEnemyDies) {
        Character hero = CharacterFactory.create(HIMEKO, LEVEL);
        Enemy enemy = weakEnemy();
        Battle battle = new Battle(List.of(hero), List.of(enemy), fixed());
        battle.startBattle();
        if (theEnemyDies) {
            enemy.takeDamage(enemy.getMaxHp() - 1);
        }
        double before = hero.getCurrentEnergy();

        battle.castImmediate(new DefaultSkill(HIMEKO, ULTIMATE_SLOT, 1), hero, List.of(enemy));

        Assertions.assertEquals(theEnemyDies, enemy.isDeath(),
                "precondition: the fixture really is the case it claims to be");
        return hero.getCurrentEnergy() - before;
    }

    private static Enemy weakEnemy() {
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        enemy.setStanceWeak(Set.of(DamageElement.FIRE));
        return enemy;
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
