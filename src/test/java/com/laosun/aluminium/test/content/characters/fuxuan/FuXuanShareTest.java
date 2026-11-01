package com.laosun.aluminium.test.content.characters.fuxuan;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1208 符玄's zone share: 「使符玄队友所承受的未受护盾抵挡之前的伤害的 65% 分摊给符玄」.
 *
 * <p>Measured on SETTLED amounts, not HP loss: the ally may carry a shield, and a shield changes what comes out of HP without
 * changing what the hit settled for - the sentence shares 「未受护盾抵挡之前的伤害」, which is exactly the settled number.
 */
public class FuXuanShareTest {

    private static final int LEVEL = 80;
    private static final int FUXUAN = 1208;
    private static final int ALLY = 1001;
    private static final double BASE = 1000.0;

    @Test
    public void theAllyKeepsThirtyFiveAndShePaysSixtyFive() {
        Fixture zone = new Fixture(true);
        zone.hitAlly();
        Fixture bare = new Fixture(false);
        bare.hitAlly();
        bare.hitHerself();

        System.out.println("[1208] zone: ally=" + zone.allySettled + " her=" + zone.herSettled
                + " ; bare: ally=" + bare.allySettled + " her=" + bare.herSettled);
        Assertions.assertEquals(0.35, zone.allySettled / bare.allySettled, 1e-6, "the ally keeps 35%");
        Assertions.assertEquals(0.65, zone.herSettled / bare.herSettled, 1e-6,
                "「65% 分摊给符玄」 - the same base, through her own zones");
    }

    @Test
    public void withoutTheZoneNobodyShares() {
        Fixture bare = new Fixture(false);
        bare.hitAlly();
        Assertions.assertEquals(0.0, bare.herSettled, 1e-9, "no 【鉴知】, no share");
    }

    // ==================================================================

    private static final class Fixture {
        private final Character fuxuan;
        private final Character ally;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;
        private double allySettled;
        private double herSettled;

        private Fixture(boolean zone) {
            fuxuan = CharacterFactory.create(FUXUAN, LEVEL);
            ally = CharacterFactory.create(ALLY, LEVEL);
            battle = new Battle(new ArrayList<>(List.of(fuxuan, ally)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.alwaysRoll(0.99));
            battle.addDamageObserver((damage, applied, hpLoss, shield, died) -> {
                if (damage.getDefender() == ally) {
                    allySettled += applied;
                } else if (damage.getDefender() == fuxuan) {
                    herSettled += applied;
                }
            });
            battle.startBattle();
            if (zone) {
                battle.castImmediate(fuxuan.getSkills().get(SkillType.SKILL), fuxuan, List.of(ally));
            }
        }

        private void hitAlly() {
            battle.applyDamage(ally, new Damage(enemy, ally, DamageElement.PHYSICAL, DamageType.NORMAL, BASE));
        }

        private void hitHerself() {
            battle.applyDamage(fuxuan, new Damage(enemy, fuxuan, DamageElement.PHYSICAL, DamageType.NORMAL, BASE));
        }
    }
}
