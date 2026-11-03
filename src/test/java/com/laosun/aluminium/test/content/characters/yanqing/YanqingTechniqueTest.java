package com.laosun.aluminium.test.content.characters.yanqing;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1209 彦卿: the technique — 「下一次战斗开始时…对当前生命值百分比 ≥50% 的敌方目标造成的伤害提高 30%，持续 2 回合」.
 *
 * <p>The clause had been registered as unwritable because 「条件与修饰单位不同」 — the condition is the TARGET's health while the
 * bonus lands on HIS damage. That is what {@code target_hp_percent} is for: it answers "the health of the unit being hit" on the
 * very event where the bonus is applied. The two cases below are the contrast that proves it is the target's health, not his.
 */
public class YanqingTechniqueTest {

    private static final int YANQING = 1209;

    @Test
    public void theTechniqueBonusOnlyLandsOnHealthyTargets() {
        double onHealthy = damageWithTechnique(1.0);
        double onWounded = damageWithTechnique(0.3);
        Assertions.assertTrue(onHealthy > onWounded,
                "「对当前生命值百分比 ≥50% 的敌方目标」 — a wounded target does not get it ("
                        + onWounded + " -> " + onHealthy + ")");
    }

    private static double shareUsed(double hpShare) {
        Character yanqing = CharacterFactory.create(YANQING, 80);
        Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        Battle battle = new Battle(List.of(yanqing), List.of(enemy), BattleFixture.alwaysRoll(0.9999));
        battle.startBattle();
        battle.processRequests();
        battle.applyDamage(enemy, new com.laosun.aluminium.damage.Damage(yanqing, enemy,
                com.laosun.aluminium.enums.DamageElement.QUANTUM, com.laosun.aluminium.enums.DamageType.TRUE,
                enemy.getMaxHp() * (1 - hpShare)));
        battle.processRequests();
        return enemy.getCurrentHp() / enemy.getMaxHp();
    }

    /** One Basic ATK against a dummy whose current HP is {@code hpShare} of its maximum. */
    private static double damageWithTechnique(double hpShare) {
        Character yanqing = CharacterFactory.create(YANQING, 80);
        Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        Battle battle = new Battle(List.of(yanqing), List.of(enemy), BattleFixture.alwaysRoll(0.9999));
        battle.markTechniqueUsed(yanqing);
        battle.startBattle();
        battle.processRequests();
        // Wound the dummy in small steps, as the handover says: a single large applyDamage instance is scaled by the damage zones
        // and removes a different share than the number asked for (measured: 70% of Max HP became 24%).
        while (enemy.getCurrentHp() / enemy.getMaxHp() > hpShare && enemy.getCurrentHp() > 1) {
            battle.applyDamage(enemy, new com.laosun.aluminium.damage.Damage(yanqing, enemy,
                    com.laosun.aluminium.enums.DamageElement.QUANTUM, com.laosun.aluminium.enums.DamageType.TRUE,
                    enemy.getMaxHp() * 0.01));
            battle.processRequests();
        }
        double[] settled = {0};
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled[0] += applied);
        battle.castImmediate(yanqing.getSkills().get(SkillType.COMMON), yanqing, List.of(enemy));
        battle.processRequests();
        return settled[0];
    }
}



