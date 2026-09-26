package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The ultimate's **cast threshold** (P3-4 follow-up): once {@code sp_need} is reached the ultimate can be cast; the
 * {@code maxEnergy} bar does not have to be full.
 *
 * <p>5 of the 93 characters have a threshold **below** their cap (the data agrees with the character docs):
 *
 * <pre>
 *   Yunli (云璃) 1221  needs 120 / cap 240      Feixiao (飞霄) 1220  needs 6  / cap 12
 *   Argenti (银枝) 1302  needs  90 / cap 180      Cyrene (昔涟) 1415  needs 12 / cap 24 (stacks, see §9.5)
 *   绯英 1505  needs 240 / cap 480
 * </pre>
 *
 * <p>The character docs say "**energy required to cast** 120 (cap 240)" — "required" is the threshold.
 * Before the fix the engine tested {@code currentEnergy >= maxEnergy}, so Yunli would only cast after piling up to
 * 240.
 */
public class UltraThresholdTest {
    private static final double EPS = 1e-9;

    // ==================================================================
    // I. The threshold value comes from the data
    // ==================================================================

    /**
     * {@code ultraEnergyCost} reads {@code sp_need}; for most characters it equals the cap.
     */
    @Test
    public void ultraCostComesFromTheSkillData() {
        Battle battle = newBattle(CharacterFactory.create(1221, 80));   // Yunli (云璃)
        Character yunli = battle.characters.getFirst();

        Assertions.assertEquals(240, yunli.getMaxEnergy(), EPS, "the stat cap is 240");
        Assertions.assertEquals(120, battle.ultraEnergyCost(yunli), EPS,
                "casting the ultimate only needs 120 (data sp_need)");
    }

    /**
     * Regular characters whose threshold == cap are unaffected (Jing Yuan (景元) 130/130).
     */
    @Test
    public void regularCharactersThresholdEqualsTheirCap() {
        Battle battle = newBattle(CharacterFactory.create(1204, 80));
        Character jingYuan = battle.characters.getFirst();

        Assertions.assertEquals(130, battle.ultraEnergyCost(jingYuan), EPS);
        Assertions.assertEquals(jingYuan.getMaxEnergy(), battle.ultraEnergyCost(jingYuan), EPS);
    }

    /** All 93 characters: the threshold must be ≤ the cap and > 0. */
    @Test
    public void thresholdNeverExceedsTheCap() {
        for (var entry : Constant.CHARACTERS.entrySet()) {
            Character c = CharacterFactory.create(entry.getKey(), 80);
            Battle battle = newBattle(c);
            double cost = battle.ultraEnergyCost(c);

            if (!c.hasEnergyBar()) {
                Assertions.assertEquals(0, cost, EPS, "cid=" + entry.getKey() + " has no energy bar");
                continue;
            }
            Assertions.assertTrue(cost > 0, "cid=" + entry.getKey() + " threshold should be positive");
            Assertions.assertTrue(cost <= c.getMaxEnergy() + EPS,
                    "cid=" + entry.getKey() + " threshold " + cost + " must not exceed the cap " + c.getMaxEnergy());
        }
    }

    /** Exactly 5 characters have a threshold below their cap — exhaustively registered, one more or one fewer must be changed explicitly. */
    @Test
    public void exactlyFiveCharactersHaveALowerThreshold() {
        int lower = 0;
        for (var entry : Constant.CHARACTERS.entrySet()) {
            Character c = CharacterFactory.create(entry.getKey(), 80);
            if (!c.hasEnergyBar()) {
                continue;
            }
            Battle battle = newBattle(c);
            if (battle.ultraEnergyCost(c) < c.getMaxEnergy() - EPS) {
                lower++;
            }
        }
        Assertions.assertEquals(5, lower,
                "there should be 5 characters with a threshold below their cap (Yunli (云璃)/Argenti (银枝)/Evanescia (绯英)/Feixiao (飞霄)/Cyrene (昔涟))");
    }

    // ==================================================================
    // II. The check and the consumption
    // ==================================================================

    /**
     * Core: Yunli can cast once she has **120**, she does not have to wait for 240.
     */
    @Test
    public void yunliCanCastAtHalfOfHerCap() {
        Character yunli = CharacterFactory.create(1221, 80);
        Battle battle = newBattle(yunli);

        yunli.setCurrentEnergy(119);
        Assertions.assertFalse(battle.isUltraReady(yunli), "1 point short, it cannot be cast yet");
        Assertions.assertFalse(battle.castUltra(yunli, List.of(firstEnemy(battle))));

        yunli.setCurrentEnergy(120);
        Assertions.assertTrue(battle.isUltraReady(yunli), "at 120 it can be cast");
        Assertions.assertTrue(battle.castUltra(yunli, List.of(firstEnemy(battle))));
    }

    /**
     * After casting it is **zeroed**: for a character whose threshold < cap, that is equivalent to "consuming the
     * threshold part".
     */
    @Test
    public void castingConsumesTheStoredEnergy() {
        Character yunli = CharacterFactory.create(1221, 80);
        Battle battle = newBattle(yunli);

        yunli.setCurrentEnergy(120);
        Assertions.assertTrue(battle.castUltra(yunli, List.of(firstEnemy(battle))));

        // the engine gives 5 points back after the ultimate itself settles (onUltCast), so it is 5 and not 0
        Assertions.assertEquals(5, yunli.getCurrentEnergy(), EPS,
                "zeroed after casting, then the character's own 5 points come back");
        Assertions.assertFalse(battle.isUltraReady(yunli), "right after casting it cannot cast again");
    }

    /**
     * Regular characters behave unchanged: they cast only when full.
     */
    @Test
    public void regularCharacterStillNeedsFullEnergy() {
        Character jingYuan = CharacterFactory.create(1204, 80);
        Battle battle = newBattle(jingYuan);

        jingYuan.setCurrentEnergy(129);
        Assertions.assertFalse(battle.isUltraReady(jingYuan));
        Assertions.assertFalse(battle.castUltra(jingYuan, List.of(firstEnemy(battle))));

        jingYuan.setCurrentEnergy(130);
        Assertions.assertTrue(battle.isUltraReady(jingYuan));
        Assertions.assertTrue(battle.castUltra(jingYuan, List.of(firstEnemy(battle))));
    }

    /**
     * A character with no energy bar (Castorice (遐蝶) 1407) can never cast — even her {@code hasEnergyBar()} is false.
     */
    @Test
    public void noEnergyBarStillCannotCast() {
        Character castorice = CharacterFactory.create(1407, 80);
        Battle battle = newBattle(castorice);

        Assertions.assertFalse(castorice.hasEnergyBar());
        castorice.setCurrentEnergy(9999);        // even force-feeding energy does not help
        Assertions.assertFalse(battle.isUltraReady(castorice));
        Assertions.assertFalse(battle.castUltra(castorice, List.of(firstEnemy(battle))));
    }

    /**
     * A special-resource character cannot cast even when force-fed to the cap — the provider gives her no energy,
     * but this verifies that the check itself does not let her through either
     * (her {@code sp_need} is 12 and her cap is 24, so feeding her to 20 would actually be "enough for the
     *  threshold" — which is exactly why the real line of defence is {@code NoConventionalEnergyProvider} keeping
     *  her from accumulating at all).
     */
    @Test
    public void specialResourceCharacterCannotAccumulateInRealBattle() {
        Character cyrene = CharacterFactory.create(1415, 80);
        Battle battle = newBattle(cyrene);
        Enemy enemy = firstEnemy(battle);

        // run a few rounds of a real battle: her energy must stay at 0, so the threshold is never met
        for (int i = 0; i < 8 && !battle.isOver(); i++) {
            battle.stepForward();
            if (battle.currentMove == null) {
                break;
            }
            var actor = battle.currentMove.getCanHit();
            battle.beforeMove();
            if (actor == cyrene) {
                Assertions.assertFalse(battle.isUltraReady(cyrene),
                        "energy is always 0 → it must never be ready, actual " + cyrene.getCurrentEnergy());
            }
            battle.afterMove();
        }
        Assertions.assertEquals(0, cyrene.getCurrentEnergy(), EPS);
    }

    // ==================================================================

    private static Battle newBattle(Character hero) {
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        Battle battle = new Battle(List.of(hero), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    private static Enemy firstEnemy(Battle battle) {
        return battle.enemyUnits().getFirst();
    }
}
