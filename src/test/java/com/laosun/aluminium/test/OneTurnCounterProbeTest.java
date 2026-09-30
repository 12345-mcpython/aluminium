package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * A probe, kept as a test because its answer is a fact future content depends on: can a counter be scoped to ONE TURN?
 *
 * <p>\u300c\u82e5\u5728**\u540c\u4e00\u56de\u5408\u5185**\u6d88\u8017\u5927\u4e8e\u7b49\u4e8e 4 \u4e2a\u6218\u6280\u70b9\u300d (cone 23053) and \u300c\u6211\u65b9\u4efb\u610f\u89d2\u8272\u5728\u81ea\u8eab**\u540c\u4e00\u56de\u5408\u5185**\u7d2f\u8ba1\u6d88\u8017\u2026\u300d (cone 23061) both need it.
 * The engine's counters carry `turns`; the question is whether `turns: 1` means "this turn only".
 */
public class OneTurnCounterProbeTest {
    private static final int UNIT = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String COUNT = "\u672c\u56de\u5408\u6d88\u8017";

    private static EffectSpec oneTurnCounter() {
        EffectSpec stack = new EffectSpec();
        TriggerSpecs.set(stack, "op", "ADD_STACK");
        TriggerSpecs.set(stack, "buff", COUNT);
        TriggerSpecs.set(stack, "amount", 1.0);
        TriggerSpecs.set(stack, "turns", 1);
        TriggerSpecs.set(stack, "target", "self");
        return stack;
    }

    @Test
    public void aTurnsOneCounterResetsAtTheTurnBoundary() {
        Character unit = CharacterFactory.create(UNIT, LEVEL);
        unit.setTriggerTable(new TriggerTable(UNIT, List.of(
                TriggerSpecs.rule("SKILL_POINT_SPENT", List.of("self_stacks:" + COUNT + " < 9"),
                        oneTurnCounter()))));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        var skill = unit.getSkills().values().stream()
                .filter(candidate -> candidate.getData() != null
                        && candidate.getData().getCategory() == SkillCategory.BPSKILL)
                .findFirst().orElseThrow();

        battle.gainSkillPoint(1);
        Assertions.assertTrue(battle.applySkillPointCost(skill, unit));
        battle.gainSkillPoint(1);
        Assertions.assertTrue(battle.applySkillPointCost(skill, unit));
        int insideTurn = unit.getBuffManager().stacksOf(COUNT);

        // \u26a0 The real tick entries, not hand-fired turn EVENTS: the first version of this probe used those and concluded
        // "no reset", which measured the instrument rather than the engine.
        unit.getBuffManager().beforeMove();
        int afterBeforeMove = unit.getBuffManager().stacksOf(COUNT);
        unit.getBuffManager().afterMove();
        int afterAfterMove = unit.getBuffManager().stacksOf(COUNT);
        unit.getBuffManager().tickForeign(unit, false);
        int afterTickForeign = unit.getBuffManager().stacksOf(COUNT);
        System.out.println("[probe] inside=" + insideTurn + " beforeMove=" + afterBeforeMove
                + " afterMove=" + afterAfterMove + " tickForeign=" + afterTickForeign);

        // \u2605 CHARACTERISATION, not approval: today a counter is NOT scoped to one turn. This assertion is written as an
        // equality so that the day somebody gives counters a per-turn reset, this test FAILS and the gap for cones 23053
        // (\u3010\u63a8\u6d41\u3011) and 23061 (\u3010\u95ea\u8000\u738b\u51a0\u3011) gets revisited instead of silently becoming shippable.
        Assertions.assertEquals(1, insideTurn, "the layer is there inside the turn (the default cap is one)");
        Assertions.assertEquals(1, afterTickForeign,
                "MEASURED 2026-09-30: no tick entry clears a `turns: 1` counter, i.e. \u540c\u4e00\u56de\u5408\u5185 is not "
                        + "expressible yet (gap: cones 23053 / 23061). If this line fails, that gap changed -- re-read it");

        // The other half of the same gap: a SKILL_POINT_SPENT rule cannot tell WHO spent, because the event is fired with
        // no actor. Cone 23061 says \u300c\u6211\u65b9\u4efb\u610f\u89d2\u8272\u5728\u81ea\u8eab\u540c\u4e00\u56de\u5408\u5185\u300d and needs exactly that.
        Character watcher = CharacterFactory.create(UNIT, LEVEL);
        watcher.setTriggerTable(new TriggerTable(UNIT, List.of(
                TriggerSpecs.rule("SKILL_POINT_SPENT", List.of("actor == self"), oneTurnCounter()))));
        Enemy target = EnemyFactory.create(MONSTER, 90, 1);
        Battle second = new Battle(List.of(watcher), List.of(target), new Random(0));
        second.startBattle();
        var secondSkill = watcher.getSkills().values().stream()
                .filter(candidate -> candidate.getData() != null
                        && candidate.getData().getCategory() == SkillCategory.BPSKILL)
                .findFirst().orElseThrow();
        second.gainSkillPoint(1);
        Assertions.assertTrue(second.applySkillPointCost(secondSkill, watcher));
        int withActorGate = watcher.getBuffManager().stacksOf(COUNT);
        System.out.println("[probe] a rule gated on `actor == self` saw the spend: stacks=" + withActorGate);
        Assertions.assertEquals(0, withActorGate,
                "MEASURED 2026-09-30: SKILL_POINT_SPENT is fired with no actor, so `actor == self` is false for the spender");
    }
}
