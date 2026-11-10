package com.laosun.aluminium.test.content.characters.blade;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1205 刃's Eidolon 4: 「当前生命值百分比从大于 50% 降低至小于等于 50% 时，生命上限提高 20%，该效果最多叠加 2 层。」
 *
 * <p><b>Why a criterion.</b> Three readings have to be separated, and each has a plausible wrong number:
 * <ul>
 *   <li>a CROSSING, not "is below half" - the second hit below the line must pay nothing;</li>
 *   <li>two HP-loss CHANNELS - damage ({@code HP_LOST}) and the HP he pays himself ({@code HP_CONSUMED}, the Skill's
 *       30%), and the sentence says 「当前生命值百分比降低」 without naming the cause;</li>
 *   <li>a CAP of two layers - a third crossing must stop at two.</li>
 * </ul>
 * Measured on Max HP. Note that 「+20%」 is a share of <b>base</b> HP (the engine's convention for every percentage
 * attribute bonus, which is also the game's: an HP% bonus scales off base HP, not off the current maximum), so the
 * expectation is {@code max + 0.2 x base}, not {@code 1.2 x max}.
 */
public class BladeEidolonFourTest {
    private static final int BLADE = 1205;
    private static final int LEVEL = 80;

    /**
     * One crossing raises Max HP by 20% of base; at E3 the same crossing changes nothing.
     */
    @Test
    public void aCrossingRaisesHisMaxHpOnlyAtEidolonFour() {
        double e3 = crossOnce(3, 0.60);
        double e4 = crossOnce(4, 0.60);
        System.out.println("[1205-E4] raise / base HP: E3=" + e3 + " ; E4=" + e4);
        Assertions.assertEquals(0.0, e3, 1e-9, "E3 states no such raise");
        Assertions.assertEquals(0.2, e4, 1e-9, "「生命上限提高 20%」");
    }

    /**
     * Two crossings pay two layers, and a third stops at the cap.
     */
    @Test
    public void theRaiseStacksTwiceAndThenStops() {
        Character blade = CharacterFactory.create(BLADE, LEVEL);
        blade.setEidolonRank(4);
        Enemy enemy = BattleFixture.dummy();
        Battle battle = fixture(blade, enemy);
        double maxHp = blade.getMaxHp();
        double baseHp = baseHp(blade);
        bringTo(blade, 0.60);

        crossByDamage(battle, blade, enemy);
        Assertions.assertEquals(maxHp + 0.2 * baseHp, blade.getMaxHp(), 1e-6, "first layer");

        // A second hit while he is already below half is NOT a crossing.
        battle.applyDamage(blade, damage(enemy, blade, 0.05));
        Assertions.assertEquals(maxHp + 0.2 * baseHp, blade.getMaxHp(), 1e-6,
                "「降到」is the crossing, not the state");

        blade.heal(maxHp * 2);
        bringTo(blade, 0.60);
        crossByDamage(battle, blade, enemy);
        Assertions.assertEquals(maxHp + 0.4 * baseHp, blade.getMaxHp(), 1e-6, "second layer - still additive");

        // A third crossing must stop at the cap.
        bringTo(blade, 0.60);
        crossByDamage(battle, blade, enemy);
        Assertions.assertEquals(maxHp + 0.4 * baseHp, blade.getMaxHp(), 1e-6, "「最多叠加 2 层」");
    }

    /**
     * His own HP cost is the second channel: the Skill pays 30% of Max HP, which can push him across the line.
     */
    @Test
    public void payingHpWithTheSkillAlsoCrossesTheLine() {
        Character blade = CharacterFactory.create(BLADE, LEVEL);
        blade.setEidolonRank(4);
        Enemy enemy = BattleFixture.dummy();
        Battle battle = fixture(blade, enemy);
        double maxHp = blade.getMaxHp();
        double baseHp = baseHp(blade);
        // 60% -> the Skill's 30% cost lands at 30%, i.e. across half.
        blade.takeDamage(maxHp * 0.4);

        battle.fire(TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, blade, enemy, 0, 0));

        Assertions.assertTrue(blade.getCurrentHp() / blade.getMaxHp() <= 0.5,
                "precondition: the cost took him below half: " + (blade.getCurrentHp() / blade.getMaxHp()));
        Assertions.assertEquals(maxHp + 0.2 * baseHp, blade.getMaxHp(), 1e-6,
                "「消耗生命值」is still 「当前生命值百分比降低」 - the same sentence, the other channel");
    }

    // ==================================================================

    /** How much one crossing raised Max HP, in units of base HP. */
    private static double crossOnce(int eidolon, double startFraction) {
        Character blade = CharacterFactory.create(BLADE, LEVEL);
        blade.setEidolonRank(eidolon);
        Enemy enemy = BattleFixture.dummy();
        Battle battle = fixture(blade, enemy);
        double maxHp = blade.getMaxHp();
        double baseHp = baseHp(blade);
        blade.takeDamage(maxHp * (1 - startFraction));
        crossByDamage(battle, blade, enemy);
        return (blade.getMaxHp() - maxHp) / baseHp;
    }

    private static double baseHp(Character blade) {
        return blade.getAttribute(AttributeType.HEALTH).baseValue();
    }

    /** Full HP, then down to the stated fraction by a RAW hit - the fixture must not run the clause while it is set up. */
    private static void bringTo(Character unit, double fraction) {
        unit.heal(unit.getMaxHp());
        unit.takeDamage(unit.getMaxHp() * (1 - fraction));
    }

    private static void crossByDamage(Battle battle, Character blade, Enemy enemy) {
        battle.applyDamage(blade, damage(enemy, blade, 0.5));
    }

    private static Damage damage(Enemy from, Character to, double maxHpShare) {
        return new Damage(from, to, DamageElement.PHYSICAL, DamageType.NORMAL, to.getMaxHp() * maxHpShare);
    }

    private static Battle fixture(Character blade, Enemy enemy) {
        Battle battle = new Battle(List.of(blade), List.of(enemy), fixed());
        battle.startBattle();
        return battle;
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(1.0);
    }
}
