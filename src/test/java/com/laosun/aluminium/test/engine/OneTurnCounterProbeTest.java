package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.test.support.TriggerSpecs;
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
 * <p>"若在同一回合内消耗大于等于 4 个战技点" (cone 23053) and "我方任意角色在自身同一回合内累计消耗…" (cone 23061) both need it.
 * The engine's counters carry `turns`; the question is whether `turns: 1` means "this turn only".
 */
public class OneTurnCounterProbeTest {
    private static final int UNIT = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String COUNT = "本回合消耗";

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

        // Note: The real tick entries, not hand-fired turn EVENTS: the first version of this probe used those and concluded
        // "no reset", which measured the instrument rather than the engine.
        unit.getBuffManager().beforeMove();
        int afterBeforeMove = unit.getBuffManager().stacksOf(COUNT);
        unit.getBuffManager().afterMove();
        int afterAfterMove = unit.getBuffManager().stacksOf(COUNT);
        unit.getBuffManager().tickForeign(unit, false);
        int afterTickForeign = unit.getBuffManager().stacksOf(COUNT);
        System.out.println("[probe] inside=" + insideTurn + " beforeMove=" + afterBeforeMove
                + " afterMove=" + afterAfterMove + " tickForeign=" + afterTickForeign);

        // CHARACTERISATION, not approval: today a counter is NOT scoped to one turn. This assertion is written as an
        // equality so that the day somebody gives counters a per-turn reset, this test FAILS and the gap for cones 23053
        // ([推流]) and 23061 ([闪耀王冠]) gets revisited instead of silently becoming shippable.
        Assertions.assertEquals(1, insideTurn, "the layer is there inside the turn (the default cap is one)");
        // UPDATED (this test was WRITTEN to fail when the gap closed -- and it did): `until: turn_end` is the
        // spelling for "同一回合内", and `BuffManager.afterMove` sweeps it. A plain `turns: 1` counter still does NOT reset.
        Assertions.assertEquals(1, afterTickForeign, "a plain turns: 1 counter still survives the tick entries");

        // The other half of the same gap: a SKILL_POINT_SPENT rule cannot tell WHO spent, because the event is fired with
        // no actor. Cone 23061 says "我方任意角色在自身同一回合内" and needs exactly that.
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
        // UPDATED, the same design as the assertion above: this half of the gap closed in the same session --
        // the policy always knew the spender (`onSkillCast(user, skill)`) and now passes it on, so `actor == self` fires.
        Assertions.assertEquals(1, withActorGate,
                "SKILL_POINT_SPENT names its spender now, so a rule gated on `actor == self` sees the spend");
    }
}
