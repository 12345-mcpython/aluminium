package com.laosun.aluminium.test.content.characters.herta1013;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1013 黑塔: the Skill hits harder on a target above half HP («对生命值百分比大于等于50%的目标…伤害提高 20%»).
 *
 * <p>⚠ The measured ratio is NOT 1.2: {@code BOOST_DAMAGE} adds into a zone that already carries something, so the ratio is
 * {@code (1.2 + B) / (1 + B)} — measured 1.163, which fits B ≈ 0.23. The judge therefore pins a BAND (well above 1.0, below
 * a plain 1.2): that still fails loudly if the clause ever stops applying, which is what an earlier note wrongly claimed it
 * already had.
 */
public class Herta13HealthyTargetTest {

    @Test
    public void theSkillHitsHarderAboveHalfHp() {
        double high = damageAt(1.0);
        double low = damageAt(0.3);
        System.out.println("[1013] skill damage: full HP " + high + " ; low HP " + low + " ; ratio " + (high / low));
        Assertions.assertTrue(high > low, "「对高血量目标伤害提高」 must land");
        double ratio = high / low;
        Assertions.assertTrue(ratio > 1.10 && ratio <= 1.2,
                "an additive +20% into a zone that already carries something lands between 1.10 and 1.20, got " + ratio);
    }

    private static double damageAt(double fraction) {
        Character her = CharacterFactory.create(1013, 80);
        Enemy enemy = BattleFixture.dummy();
        Battle battle = new Battle(new ArrayList<>(List.of(her)), new ArrayList<>(List.of(enemy)),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        for (int step = 0; step < 200 && enemy.getCurrentHp() > enemy.getMaxHp() * fraction; step++) {
            battle.applyDamage(enemy, new com.laosun.aluminium.damage.Damage(her, enemy,
                    com.laosun.aluminium.enums.DamageElement.ICE,
                    com.laosun.aluminium.enums.DamageType.NORMAL, enemy.getMaxHp() * 0.01));
            if (enemy.isDeath()) {
                break;
            }
        }
        double before = enemy.getCurrentHp();
        battle.castImmediate(her.getSkills().get(SkillType.SKILL), her, List.of(enemy));
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }
}