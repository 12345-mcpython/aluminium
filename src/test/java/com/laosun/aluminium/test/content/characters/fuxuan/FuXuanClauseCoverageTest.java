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
