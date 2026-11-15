package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The ultimate's cast threshold: once {@code sp_need} is reached the ultimate can be cast; the
 * {@code maxEnergy} bar does not have to be full.
 *
 * <p>5 of the 93 characters have a threshold below their cap (the data agrees with the character docs):
 *
 * <pre>
 *   Yunli (云璃) 1221  needs 120 / cap 240      Feixiao (飞霄) 1220  needs 6  / cap 12
 *   Argenti (银枝) 1302  needs  90 / cap 180      Cyrene (昔涟) 1415  needs 12 / cap 24 (stacks, see §9.5)
 *   Evanescia (绯英) 1505  needs 240 / cap 480
 * </pre>
 *
 * <p>The character docs say "energy required to cast 120 (cap 240)" - "required" is the threshold.
 * Before the fix the engine tested {@code currentEnergy >= maxEnergy}, so Yunli would only cast after piling up to
 * 240.
 *
 * <p><b>L3:</b> the single-clause cases are grouped into the six scenarios below, one {@code assertAll} per fixture
 * and clause so a failing clause still names itself: the threshold read out of the data (the below-cap character and
 * a regular one), the invariant over every character together with the exhaustive count of below-cap characters,
 * Yunli (云璃)'s gate together with the bar it leaves behind, the unchanged full-bar regular character, the character
 * with no energy bar, and the special-resource character that never accumulates. None of them is an exception path -
 * every refusal answers {@code false} - so no clause needs its own {@code assertThrows} test.
 */
public class UltraThresholdTest {
    private static final double EPS = 1e-9;

    // ==================================================================
    // I. The threshold value comes from the data
    // ==================================================================

    /**
     * {@code ultraEnergyCost} reads {@code sp_need}; for most characters it equals the cap. Yunli (云璃) is the
     * exception (120 against a 240 stat cap), Jing Yuan (景元) is the rule (130 against 130).
     *
     * <p>One scenario because both clauses are the same read of the same data on the same fixture shape - one
     * level-80 character in a started battle - and observe the same value, {@code ultraEnergyCost}.
     */
    @Test
    public void thresholdValueComesFromTheSkillData() {
        Battle yunliBattle = newBattle(CharacterFactory.create(1221, 80));    // Yunli (云璃)
        Character yunli = yunliBattle.characters().getFirst();
        Battle jingYuanBattle = newBattle(CharacterFactory.create(1204, 80)); // Jing Yuan (景元)
        Character jingYuan = jingYuanBattle.characters().getFirst();

        Assertions.assertAll("the threshold value comes from the skill data",
                () -> Assertions.assertEquals(240, yunli.getMaxEnergy(), EPS, "the stat cap is 240"),
                () -> Assertions.assertEquals(120, yunliBattle.ultraEnergyCost(yunli), EPS,
                        "casting the ultimate only needs 120 (data sp_need)"),
                () -> Assertions.assertEquals(130, jingYuanBattle.ultraEnergyCost(jingYuan), EPS,
                        "a regular character's threshold is its cap, 130"),
                () -> Assertions.assertEquals(jingYuan.getMaxEnergy(), jingYuanBattle.ultraEnergyCost(jingYuan), EPS,
                        "a regular character's threshold equals its cap"));
    }

    /**
     * All 93 characters: the threshold is {@code 0} when there is no energy bar, otherwise {@code >} 0 and
     * {@code <=} the cap - and exactly 5 characters have a threshold below their cap.
     *
     * <p>One scenario because every clause walks the same fixture - every character of
     * {@link Constant#CHARACTERS} at level 80 in its own started battle - and reads the same value,
     * {@code ultraEnergyCost} against the cap.
     */
    @Test
    public void thresholdInvariantsHoldForEveryCharacter() {
        int lower = 0;
        for (var entry : Constant.CHARACTERS.entrySet()) {
            int cid = entry.getKey();
            Character c = CharacterFactory.create(cid, 80);
            Battle battle = newBattle(c);
            double cost = battle.ultraEnergyCost(c);

            if (!c.hasEnergyBar()) {
                Assertions.assertAll("cid=" + cid + " has no energy bar",
                        () -> Assertions.assertEquals(0, cost, EPS, "cid=" + cid + " has no energy bar"));
                continue;
            }
            double cap = c.getMaxEnergy();
            if (cost < cap - EPS) {
                lower++;
            }
            Assertions.assertAll("cid=" + cid + " threshold against the cap",
                    () -> Assertions.assertTrue(cost > 0, "cid=" + cid + " threshold should be positive"),
                    () -> Assertions.assertTrue(cost <= cap + EPS,
                            "cid=" + cid + " threshold " + cost + " must not exceed the cap " + cap));
        }
        int belowCap = lower;
        Assertions.assertAll("the exhaustively registered below-cap list",
                () -> Assertions.assertEquals(5, belowCap,
                        "there should be 5 characters with a threshold below their cap (Yunli (云璃)/Argenti (银枝)/Evanescia (绯英)/Feixiao (飞霄)/Cyrene (昔涟))"));
    }

    // ==================================================================
    // II. The check and the consumption
    // ==================================================================

    /**
     * Core: Yunli can cast once she has 120, she does not have to wait for 240 - and after the cast the bar is
     * zeroed, so all that is left are the 5 points the ultimate itself gives back.
     *
     * <p>One scenario because both clauses drive the same fixture (Yunli (云璃) 1221 at level 80 in one started
     * battle) through the same two calls, {@code isUltraReady} and {@code castUltra}, and then read the same bar.
     */
    @Test
    public void yunliCastsAtHalfOfHerCapAndConsumesTheStoredEnergy() {
        Character yunli = CharacterFactory.create(1221, 80);
        Battle battle = newBattle(yunli);

        yunli.setCurrentEnergy(119);
        Assertions.assertAll("one point short of the threshold",
                () -> Assertions.assertFalse(battle.isUltraReady(yunli), "1 point short, it cannot be cast yet"),
                () -> Assertions.assertFalse(battle.castUltra(yunli, List.of(firstEnemy(battle))),
                        "1 point short, the cast is refused"));

        yunli.setCurrentEnergy(120);
        Assertions.assertAll("at the threshold",
                () -> Assertions.assertTrue(battle.isUltraReady(yunli), "at 120 it can be cast"),
                () -> Assertions.assertTrue(battle.castUltra(yunli, List.of(firstEnemy(battle))),
                        "at 120 the cast goes through"));

        // the engine gives 5 points back after the ultimate itself settles (onUltCast), so it is 5 and not 0
        Assertions.assertAll("what the cast leaves behind",
                () -> Assertions.assertEquals(5, yunli.getCurrentEnergy(), EPS,
                        "zeroed after casting, then the character's own 5 points come back"),
                () -> Assertions.assertFalse(battle.isUltraReady(yunli), "right after casting it cannot cast again"));
    }

    /**
     * Regular characters behave unchanged: they cast only when full.
     */
    @Test
    public void regularCharacterStillNeedsFullEnergy() {
        Character jingYuan = CharacterFactory.create(1204, 80);
        Battle battle = newBattle(jingYuan);

        jingYuan.setCurrentEnergy(129);
        Assertions.assertAll("one point short of the cap",
                () -> Assertions.assertFalse(battle.isUltraReady(jingYuan), "129 of 130, so it cannot be cast yet"),
                () -> Assertions.assertFalse(battle.castUltra(jingYuan, List.of(firstEnemy(battle))),
                        "129 of 130, the cast is refused"));

        jingYuan.setCurrentEnergy(130);
        Assertions.assertAll("at the cap",
                () -> Assertions.assertTrue(battle.isUltraReady(jingYuan), "at 130 it can be cast"),
                () -> Assertions.assertTrue(battle.castUltra(jingYuan, List.of(firstEnemy(battle))),
                        "at 130 the cast goes through"));
    }

    /**
     * A character with no energy bar (Castorice (遐蝶) 140) can never cast - even her {@code hasEnergyBar()} is false.
     */
    @Test
    public void noEnergyBarStillCannotCast() {
        Character castorice = CharacterFactory.create(1407, 80);
        Battle battle = newBattle(castorice);

        castorice.setCurrentEnergy(9999);        // even force-feeding energy does not help
        Assertions.assertAll("no energy bar, so no ultimate",
                () -> Assertions.assertFalse(castorice.hasEnergyBar(), "Castorice (遐蝶) has no energy bar"),
                () -> Assertions.assertFalse(battle.isUltraReady(castorice),
                        "force-fed to 9999, it still cannot be cast"),
                () -> Assertions.assertFalse(battle.castUltra(castorice, List.of(firstEnemy(battle))),
                        "force-fed to 9999, the cast is still refused"));
    }

    /**
     * A special-resource character cannot cast even when force-fed to the cap - the provider gives her no energy,
     * but this verifies that the check itself does not let her through either
     * (her {@code sp_need} is 12 and her cap is 24, so feeding her to 20 would actually be "enough for the
     * threshold" - which is exactly why the real line of defence is {@code NoConventionalEnergyProvider} keeping
     * her from accumulating at all).
     */
    @Test
    public void specialResourceCharacterCannotAccumulateInRealBattle() {
        Character cyrene = CharacterFactory.create(1415, 80);
        Battle battle = newBattle(cyrene);

        // run a few rounds of a real battle: her energy must stay at 0, so the threshold is never met
        for (int i = 0; i < 8 && !battle.isOver(); i++) {
            battle.stepForward();
            if (battle.currentMove() == null) {
                break;
            }
            var actor = battle.currentMove().getCanHit();
            battle.beforeMove();
            if (actor == cyrene) {
                Assertions.assertAll("Cyrene's turn: the threshold is never met",
                        () -> Assertions.assertFalse(battle.isUltraReady(cyrene),
                                "energy is always 0 → it must never be ready, actual " + cyrene.getCurrentEnergy()));
            }
            battle.afterMove();
        }
        Assertions.assertAll("no accumulation in a real battle",
                () -> Assertions.assertEquals(0, cyrene.getCurrentEnergy(), EPS,
                        "the special-resource provider never gives her energy"));
    }

    // ==================================================================

    private static Battle newBattle(Character hero) {
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        Battle battle = BattleFixture.battle(List.of(hero), List.of(enemy));
        battle.startBattle();
        return battle;
    }

    private static Enemy firstEnemy(Battle battle) {
        return battle.enemyUnits().getFirst();
    }
}
