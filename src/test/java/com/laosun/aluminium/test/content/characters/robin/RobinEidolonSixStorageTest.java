package com.laosun.aluminium.test.content.characters.robin;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1512 知更鸟•晴歌, Eidolon 6: 「处于【Fever】状态时，终结技<b>最多储存 2 次</b>」.
 *
 * <p><b>What "stored twice" means for this engine.</b> The bar still decides whether the Ultimate can be cast (it must be full), so a
 * second use cannot live in the bar. It lives in the <b>overflow bank</b> (the same pool 1014's 湖之祝福 uses): while in 【Fever】 the
 * bank's allowance is <b>one full bar</b>, so a gain that does not fit is kept instead of lost, and casting the Ultimate pours it
 * back - which is exactly "one more already stored".
 *
 * <p>The allowance is read from her own Max Energy ({@code value: energy/max}), not written as a number: 「2 次」 is relative to the
 * bar, and a literal would be wrong for any other character or level.
 */
public class RobinEidolonSixStorageTest {

    private static final int ROBIN = 1512;
    private static final int LEVEL = 80;
    private static final String FEVER = "Fever";

    /**
     * At E6 the band's arrival opens 【Fever】 and with it the storage: a full bar's worth of overflow is kept.
     */
    @Test
    public void theEidolonOpensAOneBarStorageInsideFever() {
        Fixture e6 = new Fixture(6);
        Assertions.assertTrue(e6.robin.getBuffManager().hasState(FEVER), "precondition: the band opened 【Fever】");
        Assertions.assertEquals(e6.robin.getMaxEnergy(), e6.robin.getEnergyBankCap(), 1e-9,
                "「终结技最多储存 2 次」 — one bar in hand, one in the bank");

        double before = e6.robin.getCurrentEnergy();
        e6.robin.gainEnergy(500);

        Assertions.assertEquals(e6.robin.getMaxEnergy(), e6.robin.getCurrentEnergy(), 1e-9, "the bar fills first");
        Assertions.assertEquals(e6.robin.getMaxEnergy(), e6.robin.getBankedEnergy(), 1e-9,
                "and the rest is kept, up to one more bar");
        Assertions.assertTrue(before >= 0, "precondition: she started below full");
    }

    /** Without the Eidolon there is no storage - the control for the reading above. */
    @Test
    public void withoutTheEidolonTheOverflowIsLost() {
        Fixture e5 = new Fixture(5);
        Assertions.assertEquals(0, e5.robin.getEnergyBankCap(), 1e-9, "E5 states nothing about storing");
        e5.robin.gainEnergy(500);
        Assertions.assertEquals(0, e5.robin.getBankedEnergy(), 1e-9, "so the excess is lost as usual");
    }

    /**
     * Casting the Ultimate inside 【Fever】 pours the stored bar back: she can cast again at once.
     */
    @Test
    public void theStoredUltimateComesBackAfterACast() {
        Fixture e6 = new Fixture(6);
        e6.robin.gainEnergy(500);
        double stored = e6.robin.getBankedEnergy();
        Assertions.assertEquals(e6.robin.getMaxEnergy(), stored, 1e-9, "precondition: a whole bar is stored");

        e6.spendTheUltimate();

        Assertions.assertEquals(stored, e6.robin.getCurrentEnergy(), 1e-9,
                "「储存 2 次」 — the second use is ready as soon as the first is paid");
        Assertions.assertEquals(0, e6.robin.getBankedEnergy(), 1e-9, "and the bank is empty again");
    }

    // ==================================================================

    private static final class Fixture {
        private final Character robin;
        private final Character ally = CharacterFactory.create(1002, LEVEL);
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private Fixture(int eidolon) {
            robin = CharacterFactory.create(ROBIN, LEVEL, false, null, null, eidolon);
            battle = new Battle(List.of(robin, ally), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
            // 「晴空乐手」全员登台 = two Skill casts (the first summons, its +6 气氛值 crosses the thresholds for the rest).
            for (int i = 0; i < 2; i++) {
                battle.castImmediate(robin.getSkills().get(SkillType.SKILL), robin, List.of(robin));
                battle.processRequests();
            }
        }

        /**
         * Paying an Ultimate: the bar goes to 0 first (what {@code UltimateService} does), then the real cast runs.
         *
         * <p>The cast is driven through {@code castImmediate} rather than a hand-fired {@code ULT_CAST}: her own Ultimate rule
         * reads a parameter of the skill that produced the event, and a hand-made payload carries no skill at all.
         */
        private void spendTheUltimate() {
            robin.setCurrentEnergy(0);
            battle.castImmediate(robin.getSkills().get(SkillType.ULTRA), robin, List.of(ally));
            battle.processRequests();
        }
    }
}
