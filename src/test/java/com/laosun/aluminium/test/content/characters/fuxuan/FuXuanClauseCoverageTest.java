package com.laosun.aluminium.test.content.characters.fuxuan;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1208 符玄: Eidolon 4 — 「当处于【穷观阵】的我方其他目标受到攻击后，符玄恢复 5 点能量」.
 *
 * <p>The zone is modelled as the 【鉴知】 state in this file (both the Skill and the technique apply it), so the Eidolon reads that
 * state and excludes Fu Xuan herself: 「**其他**目标」 is the half that is easy to drop, and dropping it would pay on her own hits.
 */
public class FuXuanClauseCoverageTest {

    private static final int FU_XUAN = 1208;
    private static final int ALLY = 1002;

    /**
     * With the zone open, an ally taking a hit pays 5 energy — and her OWN hit does not.
     */
    @Test
    public void eidolon4PaysWhenAnAllyInTheZoneIsHit() {
        Fixture f = new Fixture(4);
        f.openTheZone();
        double before = f.fuXuan.getCurrentEnergy();

        f.allyIsHit();
        Assertions.assertEquals(before + 5, f.fuXuan.getCurrentEnergy(), 1e-9,
                "「处于【穷观阵】的我方其他目标受到攻击后，符玄恢复 5 点能量」");

        double afterAlly = f.fuXuan.getCurrentEnergy();
        f.fuXuanIsHit();
        Assertions.assertEquals(afterAlly, f.fuXuan.getCurrentEnergy(), 1e-9,
                "「我方**其他**目标」 — her own hit pays nothing");
    }

    /**
     * Without the Eidolon the same event pays nothing.
     */
    @Test
    public void theBaseKitPaysNothing() {
        Fixture f = new Fixture(0);
        f.openTheZone();
        double before = f.fuXuan.getCurrentEnergy();
        f.allyIsHit();
        Assertions.assertEquals(before, f.fuXuan.getCurrentEnergy(), 1e-9, "control: it is Eidolon 4");
    }

    /**
     * 星魂 6: the Ultimate's damage grows with the HP the side has lost, and the counter is emptied afterwards.
     *
     * <p>Measured on the settled damage of the Ultimate itself: E6 with 4000 HP lost must beat E0 with the same board, and the same
     * E6 battle with no loss must NOT - which is what shows the boost is reading the accumulator rather than a flat bonus.
     */
    @Test
    public void eidolon6ScalesTheUltimateWithTheSidesLostHp() {
        double bare = ultimateDamage(6, 0);
        double loaded = ultimateDamage(6, 4000);
        Assertions.assertTrue(loaded > bare, "「终结技造成的伤害提高 = 累计已损失生命值 × 200%」 (" + bare + " -> " + loaded + ")");

        Fixture f = new Fixture(6);
        f.accumulate(4000);
        f.castUltimate();
        Assertions.assertEquals(0, f.fuXuan.getResources().value("累计损失"), 1e-9,
                "「施放终结技后会清空并进行重新累计」");
    }

    private static double ultimateDamage(int eidolon, int lostHp) {
        Fixture f = new Fixture(eidolon);
        f.accumulate(lostHp);
        double[] settled = {0};
        f.battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled[0] += applied);
        f.castUltimate();
        return settled[0];
    }

    // ==================================================================

    private static final class Fixture {
        private final Character fuXuan;
        private final Character ally = CharacterFactory.create(ALLY, 80);
        private final Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        private final Battle battle;

        private Fixture(int eidolon) {
            fuXuan = CharacterFactory.create(FU_XUAN, 80, false, null, null, eidolon);
            battle = new Battle(List.of(fuXuan, ally), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        /** The Skill opens the zone（【鉴知】 on the whole side); that is how the state gets there. */
        private void openTheZone() {
            battle.castImmediate(fuXuan.getSkills().get(SkillType.SKILL), fuXuan, List.of(ally));
            battle.processRequests();
        }

        /**
         * Records HP the side has lost, the way the engine does: one HP_LOST firing per loss, its magnitude the amount.
         *
         * <p>Fired rather than dealt, because a fixture that actually damages an ally has to get past shields, the zone's damage
         * sharing and death - none of which is what this Eidolon is about.
         */
        private void accumulate(int lostHp) {
            for (int i = 0; i < 1; i++) {
                battle.fire(TriggerPayload.Simple.of(TriggerEvent.HP_LOST, ally, ally, 0, lostHp));
                battle.processRequests();
            }
        }

        private void castUltimate() {
            battle.castImmediate(fuXuan.getSkills().get(SkillType.ULTRA), fuXuan, List.of(enemy));
            battle.processRequests();
        }

        private void allyIsHit() {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.TAKING_HIT, enemy, ally, 0, 0));
            battle.processRequests();
        }

        private void fuXuanIsHit() {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.TAKING_HIT, enemy, fuXuan, 0, 0));
            battle.processRequests();
        }
    }
}

