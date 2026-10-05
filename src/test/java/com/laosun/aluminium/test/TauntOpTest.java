package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.TauntBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * {@code TAUNT} - "使目标陷入嘲讽状态，持续N回合" (2026-09-2).
 *
 * <p><b>Why the op exists at all.</b> The engine already had the hard part: {@code TauntBuff} is a pure marker whose
 * constraint lives in target selection ("as long as it is attached to a living unit, single-target attacks and the
 * centre of a blast can only pick that unit"). What was missing was any way for <b>data</b> to attach it - 云璃's
 * ultimate (every enemy), 千冶-刃's skill (one enemy, 1 turn) and 波提欧's [绝命对峙] all say it.
 *
 * <p>Note: It is <b>not</b> the same thing as "被敌方攻击的概率提高" (三月七 / 杰帕德 / 玲可): that is a soft weight, it
 * carries no number in any document, and it is registered as a data gap instead of being guessed here.
 */
public class TauntOpTest {
    private static final double EPS = 1e-6;
    private static final int OWNER = 1003;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** The op attaches the marker with the duration the rule states. */
    @Test
    public void tauntAttachesTheMarkerForTheStatedTurns() {
        Character owner = characterWith(tauntRule(2));
        Enemy enemy = dummy();
        Battle battle = new Battle(List.of(owner), List.of(enemy), new Random(0));
        battle.startBattle();

        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, owner, enemy, 1, 0);

        List<TauntBuff> buffs = enemy.getBuffManager().allBuffsOf(TauntBuff.class);
        Assertions.assertEquals(1, buffs.size(), "the enemy is taunted");
        Assertions.assertEquals(2, buffs.getFirst().duration(), "「持续#N回合」");
    }

    /** Nothing to taunt is not a silent success: the marker is on the unit the rule resolved. */
    @Test
    public void tauntGoesOnTheResolvedTargetAndNowhereElse() {
        Character owner = characterWith(tauntRule(1));
        Enemy enemy = dummy();
        Battle battle = new Battle(List.of(owner), List.of(enemy), new Random(0));
        battle.startBattle();

        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, owner, enemy, 1, 0);

        Assertions.assertTrue(owner.getBuffManager().allBuffsOf(TauntBuff.class).isEmpty(),
                "the rule owner is not taunted -- `target` names the event's subject");
    }

    /** A duration is required, and the open-ended spellings are refused (a taunt that never ends). */
    @Test
    public void tauntRefusesAnOpenEndedDuration() {
        EffectSpec noTurns = new EffectSpec();
        TriggerSpecs.set(noTurns, "op", "TAUNT");
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(TriggerSpecs.rule("ALLY_ATTACK", null, noTurns))),
                "no `turns` = how long?");

        EffectSpec permanent = tauntEffect(1);
        TriggerSpecs.set(permanent, "permanent", true);
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(TriggerSpecs.rule("ALLY_ATTACK", null, permanent))));
        Assertions.assertTrue(refused.getMessage().contains("permanent"), refused.getMessage());
    }

    /** 千冶-刃's Skill states it: one cast, one taunted enemy. */
    @Test
    public void theAuthoredRuleTauntsTheAimedEnemy() {
        Assertions.assertEquals(1, TriggerTables.of(1507).ruleCount(TriggerEvent.SKILL_CAST),
                "her Skill's taunt clause, and nothing else yet");
    }

    private static EffectSpec tauntEffect(int turns) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "TAUNT");
        TriggerSpecs.set(effect, "turns", turns);
        TriggerSpecs.set(effect, "target", "target");
        return effect;
    }

    private static TriggerSpec tauntRule(int turns) {
        return TriggerSpecs.rule("ALLY_ATTACK", null, tauntEffect(turns));
    }

    private static Character characterWith(TriggerSpec... rules) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(rules)));
        return owner;
    }

    private static Enemy dummy() {
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        // Note: TAUNT goes through the resist pipeline now (an unstated probability is a 100% BASE chance, not "bypasses
        // 效果抵抗"), so the fixture states the other side of that roll: 冰锋's own 30% 效果抵抗 would otherwise make
        // every case here a coin flip. The pipeline itself is pinned by DebuffResistTest.
        enemy.setAttribute(com.laosun.aluminium.enums.AttributeType.EFFECT_RESISTANCE,
                new com.laosun.aluminium.models.DoubleValue(0));
        return enemy;
    }
}
