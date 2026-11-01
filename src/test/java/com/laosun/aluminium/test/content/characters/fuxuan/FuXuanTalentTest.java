package com.laosun.aluminium.test.content.characters.fuxuan;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1208 符玄's talent, second half: 「当符玄当前生命值百分比 ≤ 50% 时触发生命回复，使自身回复等同于已损失生命值 90% 的生命值…
 * 初始拥有 1 次触发次数，最多可拥有 2 次」.
 *
 * <p>The ward half (【避厄】, -18%) was already in the file; this judge is about the heal and its charge counter.
 */
public class FuXuanTalentTest {

    private static final int LEVEL = 80;
    private static final int FUXUAN = 1208;
    private static final String CHARGE = "避厄治疗次";

    @Test
    public void theHealFiresAtHalfHpAndSpendsACharge() {
        Fixture f = new Fixture();
        Assertions.assertEquals(1, f.fuxuan.getBuffManager().stacksOf(CHARGE), "「初始拥有 1 次触发次数」");

        // Down to 45% WITHOUT an HP_LOST event (consumeHp), then take one real hit: that hit is what triggers the heal.
        double max = f.fuxuan.getMaxHp();
        f.fuxuan.consumeHp(max * 0.55);
        double before = f.fuxuan.getCurrentHp();
        Assertions.assertTrue(before / max < 0.5, "precondition: she is under half");

        double[] healed = new double[1];
        f.battle.addHealObserver((healer, target, amount) -> healed[0] += amount);
        double dealt = f.battle.applyTrueDamage(f.enemy, f.fuxuan, DamageElement.PHYSICAL, max * 0.05);
        f.battle.processRequests();

        double missingAfterHit = max - (before - dealt);
        System.out.println("[1208] missing=" + missingAfterHit + " healed=" + healed[0]
                + " charges=" + f.fuxuan.getBuffManager().stacksOf(CHARGE));
        Assertions.assertEquals(0.9 * missingAfterHit, healed[0], 1e-6, "「回复等同于已损失生命值 90%」");
        Assertions.assertEquals(0, f.fuxuan.getBuffManager().stacksOf(CHARGE), "one charge spent");
    }

    /**
     * The counter caps the effect: with no charge left, the same hit heals nothing.
     */
    @Test
    public void withoutAChargeTheHealDoesNothing() {
        Fixture f = new Fixture();
        double max = f.fuxuan.getMaxHp();
        f.fuxuan.consumeHp(max * 0.55);
        f.fuxuan.getBuffManager().removeState(CHARGE);

        double[] healed = new double[1];
        f.battle.addHealObserver((healer, target, amount) -> healed[0] += amount);
        f.battle.applyTrueDamage(f.enemy, f.fuxuan, DamageElement.PHYSICAL, max * 0.05);
        f.battle.processRequests();
        Assertions.assertEquals(0.0, healed[0], 1e-9, "the charge is the gate");
    }

    /**
     * The Ultimate grants the second charge, and the cap holds at two.
     */
    @Test
    public void theUltimateGrantsTheSecondCharge() {
        Fixture f = new Fixture();
        Assertions.assertEquals(1, f.fuxuan.getBuffManager().stacksOf(CHARGE), "precondition: one at battle start");

        f.ultimate();
        Assertions.assertEquals(2, f.fuxuan.getBuffManager().stacksOf(CHARGE),
                "「获得 1 次天赋的生命回复触发次数」");

        f.ultimate();
        Assertions.assertEquals(2, f.fuxuan.getBuffManager().stacksOf(CHARGE), "「最多可拥有 2 次」 - the cap holds");
    }
    // ==================================================================

    private static final class Fixture {
        private final Character fuxuan;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private void ultimate() {
            battle.castImmediate(fuxuan.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA), fuxuan,
                    java.util.List.of(enemy));
        }

        private Fixture() {
            fuxuan = CharacterFactory.create(FUXUAN, LEVEL);
            battle = new Battle(new ArrayList<>(List.of(fuxuan, CharacterFactory.create(1001, LEVEL))),
                    new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.99));
            battle.startBattle();
        }
    }
}

