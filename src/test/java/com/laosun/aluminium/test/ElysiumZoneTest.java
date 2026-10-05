package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1415 昔涟's 结界 (2026-10-02): "结界持续期间，我方全体目标每造成 1 次伤害，都会再额外造成 1 次等同于原伤害 24% 的真实伤害".
 *
 * <p>Her own file carries both halves: the skill marks 结界 ({@code APPLY_BUFF} with {@code ticks_on: "self"}), and the
 * rider listens on {@code DAMAGE_SETTLED}. The claim is therefore "with the zone up, an ALLY's attack costs the enemy
 * exactly 24% more than the ally's own settled damage" - the zone itself deals no damage, so the whole difference is
 * the rider's.
 */
public class ElysiumZoneTest {
    private static final int CYRENE = 1415;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** With 结界 up, an ally's attack is followed by a true-damage rider of 24% of what it settled. */
    @Test
    public void theZoneAddsAQuarterOfTheAllysDamage() {
        double with = enemyLoss(true);
        double without = enemyLoss(false);

        Assertions.assertTrue(without > 0, "precondition: the ally's attack deals damage (" + without + ")");
        Assertions.assertEquals(0.24 * without, with - without, without * 1e-6,
                "结界's rider is 24% of the settled instance it triggered: " + without + " -> " + with);
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    /** The enemy's total HP loss when {@code ALLY} attacks, with 昔涟's skill cast first (or not). */
    private static double enemyLoss(boolean zone) {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL, false, null, null, 0);
        Character ally = CharacterFactory.create(ALLY, LEVEL, false, null, null, 0);
        ally.setTriggerTable(new TriggerTable(ALLY, List.of()));   // the ally contributes nothing of its own
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(cyrene, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        if (zone) {
            // Her skill is 辅助 (no damage of its own), so announcing the cast is exactly what the marker rule needs.
            battle.fireTriggers(TriggerEvent.SKILL_CAST, cyrene, enemy, 0, 0);
        }
        double before = enemy.getCurrentHp();
        ally.getSkills().get(SkillType.COMMON).execute(battle, ally, List.of(enemy));
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }
}
