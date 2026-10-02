package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 「敌方目标的弱点被击破时，受到的痛击破伤害提高 2%」 (1317:440, 2026-10-02).
 *
 * <p>⭐ SELECTIVITY is the point: `damage_type` is not validated at load, so the judge deals the SAME base damage twice --
 * once as BREAK and once as NORMAL -- and the rule must amplify only the first. A silently ignored field would move both.
 */
public class BreakDamageTakenTest {
    private static final int OWNER = 1317;
    private static final int MONSTER = 1002011;

    /** ⭐ Break damage is raised, normal damage is not. */
    @Test
    public void onlyBreakDamageIsRaised() {
        double breakDealt = dealt(DamageType.BREAK);
        double normalDealt = dealt(DamageType.NORMAL);
        Assertions.assertTrue(normalDealt > 0, "precondition: the hit lands (" + normalDealt + ")");
        Assertions.assertTrue(breakDealt > normalDealt * 1.005,
                "break damage must be raised by the rule (" + breakDealt + " vs " + normalDealt + ")");
    }

    // ==================================================================

    private static double dealt(DamageType type) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(owner), List.of(enemy), new Random(0));
        battle.startBattle();
        double before = enemy.getCurrentHp();
        battle.applyDamage(enemy, new Damage(owner, enemy, DamageElement.IMAGINARY, type, 200));
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }
}
