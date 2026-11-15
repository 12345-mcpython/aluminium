package com.laosun.aluminium.test.content.characters.evanescia;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1505 Evanescia (绯英)'s 【好活当赏】 laughs as OTHER characters read them: the 8009/8010 Trailblazer twins' commanded
 * Elation cast, and 1506 Silver Wolf LV.999 (银狼LV.999)'s forwarding into [隐藏分].
 *
 * <p><b>8009/8010's ultimate clause.</b> 「若目标拥有欢愉技，目标额外获得 10 点【好活当赏】，并使其立即施放 1 次<b>固定计入 20
 * 笑点</b>的欢愉技，若欢愉技施放前敌方目标被消灭则对新入场的敌方目标发动欢愉技。」 The commanded cast is settled with the
 * fixed 20, not with the snapshot the 好活当赏 grant froze - the ratio between it and the ally's own next Elation cast has
 * to be exactly {@code factor(20) / factor(the frozen count)}. Plus the two halves that note used to call registered: the
 * count rides the CAST_SKILL effect, and 「若…被消灭则对新入场的敌方目标发动」 is the rule-level
 * {@code defer_until_wave}.
 *
 * <p>⚠ The sentence is the SAME in both Trailblazer files, so both are read here: a twin that drifted would otherwise ship
 * a different number for the same text.
 *
 * <p><b>1506's forwarding clause.</b> Resource-change forwarding: 1506 Silver Wolf LV.999 (银狼LV.999)'s "when gaining a
 * laugh, gains an equal amount of [隐藏分]". The source is 1505's skill, which grants ten SHARED laughs: the battle's laugh
 * counter moves, the change carries that resource's name and its delta, and 1506 turns it into the same number of
 * [隐藏分]. The false case is the same cast with an ally that has no such clause (1002), so the forwarding is what moves
 * the number.
 */
public class EvanesciaLaughLinksTest {
    private static final List<Integer> TWINS = List.of(8009, 8010);
    private static final int EVANESCIA = 1505;
    private static final String RULE = "ult_elation_gift_and_cast";
    private static final int FIXED = 20;
    private static final int LEVEL = 80;
    private static final String LAUGH = "笑点";
    private static final String HIDDEN = "char:1506/hidden_score";

    private Character source;
    private Character other;
    private Battle battle;

    private void build(int ally) {
        source = CharacterFactory.create(EVANESCIA, LEVEL);
        other = CharacterFactory.create(ally, LEVEL);
        battle = new Battle(List.of(source, other), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
    }

    // ==================================================================
    // 8009/8010: the commanded Elation cast with the fixed 20
    // ==================================================================

    /**
     * The commanded cast folds the FIXED count while the ally's own next cast folds the frozen snapshot.
     *
     * <p>Her own cast is the control, and the frozen count is read from the battle rather than assumed, so the ratio does
     * not depend on which of the ultimate's rules ran first.
     */
    @Test
    public void theCommandedElationCastCountsTheFixedTwenty() {
        Battle battle = newBattle();
        Character harmony = battle.characters().getFirst();
        Character ally = battle.characters().get(1);
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

    /**
     * Both halves of the registered sentence, read off the compiled rule - in BOTH twin files.
     */
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
    // 1506: laughs forwarded into [隐藏分]
    // ==================================================================

    @Test
    public void aSharedLaughBecomesHiddenPointsOneForOne() {
        build(1506);
        int laughsBefore = battle.partyResourceValue(LAUGH);
        int hiddenBefore = other.getResources().value(HIDDEN);
        battle.castImmediate(source.getSkills().get(SkillType.SKILL), source, List.of());
        int laughsAfter = battle.partyResourceValue(LAUGH);
        int hiddenAfter = other.getResources().value(HIDDEN);
        System.out.println("[forward] laughs " + laughsBefore + " -> " + laughsAfter + " ; " + 1506
                + "’s hidden points " + hiddenBefore + " -> " + hiddenAfter);
        Assertions.assertEquals(laughsBefore + 10, laughsAfter, "the skill granted ten shared laughs");
        Assertions.assertEquals(hiddenBefore + 10, hiddenAfter,
                "and every one of them became a hidden point -- the forwarding clause");
    }

    /**
     * The false case: the same cast, an ally with no forwarding clause.
     */
    @Test
    public void anAllyWithoutTheClauseGainsNothing() {
        build(1002);
        int laughsBefore = battle.partyResourceValue(LAUGH);
        int hiddenBefore = other.getResources().value(HIDDEN);
        battle.castImmediate(source.getSkills().get(SkillType.SKILL), source, List.of());
        System.out.println("[forward] with an ally that has no clause: laughs " + laughsBefore + " -> "
                + battle.partyResourceValue(LAUGH) + " ; its hidden points " + hiddenBefore + " -> "
                + other.getResources().value(HIDDEN));
        Assertions.assertEquals(10, battle.partyResourceValue(LAUGH) - laughsBefore, "the laughs still happen");
        Assertions.assertFalse(other.getResources().has(HIDDEN), "and nothing is forwarded to that ally");
    }

    /**
     * The shipped rule and its scope, read off the compiled table (discipline 232).
     */
    @Test
    public void theShippedRuleWatchesTheResourceItForwards() {
        build(1506);
        var rules = other.getTriggerTable().rulesFor(TriggerEvent.RESOURCE_CHANGED).stream()
                .filter(rule -> rule.id().startsWith("p1506_")).toList();
        // ⚠ Her table has THREE rules on this event, and each one belongs there: the forwarding clause, her talent,
        // which recomputes the CRIT conversion whenever 【隐藏分】 moves, and her Eidolon 2's milestone ledger, which
        // counts what the hidden score did while 【无敌玩家】 is up. Pinning the exact set keeps a fourth from slipping in.
        Assertions.assertEquals(
                java.util.Set.of("p1506_laughs_to_hidden", "p1506_talent_hidden_becomes_crit",
                        "p1506_e2_milestones_while_in_godmode"),
                rules.stream().map(rule -> rule.id()).collect(java.util.stream.Collectors.toSet()),
                "the forwarding rule, the talent conversion and the Eidolon 2 ledger, and nothing else");
        // ⚠ The ORDER is pinned as a snapshot of this event's table, so a new rule arrives here first. Note: it is NOT
        // a constraint any more - the ledger's first effect moves 里程碑配额, and that used to overwrite the battle's
        // `lastChangedResource` for the rules after it (the ledger therefore had to be last). That is fixed:
        // `TriggerInterpreter.fireResourceChanged` scopes the name to its own firing, pinned by ResourceChangeScopeTest.
        Assertions.assertEquals("p1506_e2_milestones_while_in_godmode",
                rules.getLast().id(), "the census reads this event's order; it is the last of the three today");
        var forwarding = rules.stream().filter(rule -> rule.id().equals("p1506_laughs_to_hidden")).findFirst()
                .orElseThrow();
        var effect = forwarding.effects().getFirst();
        System.out.println("[forward] spec " + forwarding.id() + " when=" + forwarding.conditions()
                + " resource=" + effect.getResource() + " amountFromEvent=" + effect.getAmountFromEvent());
        Assertions.assertEquals(HIDDEN, effect.getResource(), "it grants the hidden points");
        Assertions.assertEquals(Boolean.TRUE, effect.getAmountFromEvent(), "by the event’s own amount");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * The laugh factor the engine folds: {@code 1 + 5n/(n+240)}, the same curve both readings go through.
     */
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
        Character ally = CharacterFactory.create(EVANESCIA, 80);
        ally.setAttribute(AttributeType.CRIT_CHANCE, new DoubleValue(0));
        ally.setTriggerTable(new TriggerTable(ally.getCid(), List.of(
                TriggerSpecs.rule(TriggerEvent.KILL.name(), List.of(), TriggerSpecs.gainEnergy(1)))));
        Battle battle = new Battle(List.of(harmony, ally), List.of(BattleFixture.monster()),
                BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}
