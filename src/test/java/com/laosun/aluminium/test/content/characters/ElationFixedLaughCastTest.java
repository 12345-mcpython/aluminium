package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 8009/8010's ultimate clause: 「若目标拥有欢愉技，目标额外获得 10 点【好活当赏】，并使其立即施放 1 次<b>固定计入 20 笑点</b>的欢愉技，
 * 若欢愉技施放前敌方目标被消灭则对新入场的敌方目标发动欢愉技。」
 *
 * <p><b>What this file pins.</b> The commanded cast is settled with the fixed 20, not with the snapshot the 好活当赏 grant
 * froze - the ratio between it and the ally's own next Elation cast has to be exactly
 * {@code factor(20) / factor(the frozen count)}. Plus the two halves that note used to call registered: the count rides the
 * CAST_SKILL effect, and 「若…被消灭则对新入场的敌方目标发动」 is the rule-level {@code defer_until_wave}.
 *
 * <p>⚠ The sentence is the SAME in both Trailblazer files, so both are read here: a twin that drifted would otherwise ship
 * a different number for the same text.
 */
public class ElationFixedLaughCastTest {
    private static final List<Integer> TWINS = List.of(8009, 8010);
    private static final int ELATION_ALLY = 1505;
        private static final String RULE = "ult_elation_gift_and_cast";
    private static final int FIXED = 20;

    /**
     * The commanded cast folds the FIXED count while the ally's own next cast folds the frozen snapshot.
     *
     * <p>Her own cast is the control, and the frozen count is read from the battle rather than assumed, so the ratio does
     * not depend on which of the ultimate's rules ran first.
     */
    @Test
    public void theCommandedElationCastCountsTheFixedTwenty() {
        Battle battle = newBattle();
        Character harmony = battle.characters.getFirst();
        Character ally = battle.characters.get(1);
        Enemy enemy = battle.enemyUnits().getFirst();

        double before = enemy.getCurrentHp();
        harmony.setCurrentEnergy(harmony.getMaxEnergy());
        battle.castUltra(harmony, List.of(ally));
        battle.processRequests();
        double commanded = before - enemy.getCurrentHp();

        Assertions.assertEquals(10, ally.getResources().value("好活当赏"),
                "precondition: the ultimate handed the ally the 10 points of 【好活当赏】");
        int frozen = battle.countedLaughPoints();
        Assertions.assertTrue(frozen > 0, "precondition: that grant froze the counted laughs (" + frozen + ")");

        double beforeOwn = enemy.getCurrentHp();
        battle.castImmediate(ally.getSkills().get(SkillType.ELATION_SKILL), ally, List.of(enemy));
        battle.processRequests();
        double own = beforeOwn - enemy.getCurrentHp();

        double expected = factor(FIXED) / factor(frozen);
        System.out.println("[8009] commanded " + commanded + " (fixed " + FIXED + ") ; her own " + own + " (frozen "
                + frozen + ") ; ratio " + (commanded / own) + " (expected " + expected + ")");
        Assertions.assertEquals(expected, commanded / own, 1e-6,
                "the commanded cast is settled with the fixed 20, not with the snapshot the gift froze");
    }

    /** Both halves of the registered sentence, read off the compiled rule - in BOTH twin files. */
    @Test
    public void bothTwinsCarryTheCountAndTheWaveRedirect() {
        for (int cid : TWINS) {
            Character unit = CharacterFactory.create(cid, 80);
            var rule = unit.getTriggerTable().rulesFor(TriggerEvent.ULT_CAST).stream()
                    .filter(r -> r.id().equals(RULE)).findFirst()
                    .orElseThrow(() -> new AssertionError(cid + " does not carry " + RULE));
            var cast = rule.effects().stream()
                    .filter(effect -> "CAST_SKILL".equals(effect.getOp())).findFirst()
                    .orElseThrow(() -> new AssertionError(cid + "'s rule commands no cast"));
            System.out.println("[8009] " + cid + " cast_counted_laughs=" + cast.getCastCountedLaughs()
                    + " defer_until_wave=" + rule.deferUntilWave()
                    + " when=" + rule.conditions().stream().map(c -> c.source()).toList());
            Assertions.assertEquals(FIXED, cast.getCastCountedLaughs(),
                    "「固定计入 20 笑点」 rides the cast this effect commands");
            Assertions.assertEquals("ELATION_SKILL", cast.getSkill(), "and the cast is the ally's Elation skill");
            Assertions.assertTrue(rule.deferUntilWave(),
                    "「若欢愉技施放前敌方目标被消灭则对新入场的敌方目标发动」 is the rule-level wave redirect");
        }
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** The laugh factor the engine folds: {@code 1 + 5n/(n+240)}, the same curve both readings go through. */
    private static double factor(int laughs) {
        return 1 + laughs * 5.0 / (laughs + 240);
    }

    /**
     * 8009 with an ally that has an Elation skill.
     *
     * <p>⚠ The ally's own trigger table is replaced with an inert rule, and the CRIT_CHANCE is pinned to 0: her shipped
     * rules would fold their own resources and buffs into the comparison, and a crit roll would turn the ratio into a
     * coin toss. The Elation SKILL itself still comes from her character data, which is what the commanded cast reads.
     */
    private static Battle newBattle() {
        Character harmony = CharacterFactory.create(TWINS.getFirst(), 80);
        Character ally = CharacterFactory.create(ELATION_ALLY, 80);
        ally.setAttribute(AttributeType.CRIT_CHANCE, new DoubleValue(0));
        ally.setTriggerTable(new TriggerTable(ally.getCid(), List.of(
                TriggerSpecs.rule(TriggerEvent.KILL.name(), List.of(), TriggerSpecs.gainEnergy(1)))));
        Battle battle = new Battle(List.of(harmony, ally), List.of(BattleFixture.monster()),
                new Random(0));
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}
