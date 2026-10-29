package com.laosun.aluminium.test.content.characters.danheng;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import com.laosun.aluminium.entity.CanHit;

/**
 * 1002 Dan Heng (丹恒) 行迹 <b>潜龙</b>: 「若当前生命值百分比小于等于 50%，则被敌方目标攻击的概率降低。」
 *
 * <p>The only rule of his kit with no case of its own until now - and the one whose number is easiest to get wrong
 * silently: a soft aggro weight of <b>-50%</b> means the weight becomes {@code base x 0.5}, not "he is never targeted"
 * (a weight of zero would take a living unit out of the draw, which {@code Battle.aggroOf} deliberately refuses).
 *
 * <p>Provenance of the two numbers: {@code ExcelOutput/AvatarSkillTreeConfig.json}, {@code PointID 1002101},
 * {@code ParamList = [{0.5}, {0.5}]} - the first is the HP threshold the prose states, the second the ratio (they are
 * equal, so their order cannot matter), and the ability {@code Avatar_DanHeng_00_SkillTree_AggroDown} writes
 * {@code AggroAddedRatio} as <b>0 − parameter</b>, i.e. −0.5.
 *
 * <h2>Both halves</h2>
 * The clause has a beginning and an end: the rule fires on {@code HP_LOST} at ≤ 50%, and a second rule takes the state
 * off on {@code HEALED} above 50%. Asserting only the first half would leave "he stays untargetable forever" invisible.
 *
 * <p>⚠ The damage is dealt through {@code Battle.applyDamage}, not {@code CanHit.takeDamage}: the latter is the raw HP
 * subtraction and fires no events, so a rule listening on {@code HP_LOST} would never see it (measured: the first
 * version of this case read 0.0 and looked like a missing rule).
 */
public class DanHengAggroDownTest {
    private static final int DANHENG = 1002;
    private static final int ALLY = 1201;
    private static final int LEVEL = 80;
    private static final double EPS = 1e-9;

    /**
     * Dropping below half HP halves his aggro weight; healing back above it restores it.
     */
    @Test
    public void theLowHpTraceHalvesHisAggroWeightUntilHeHeals() {
        Character danHeng = CharacterFactory.create(DANHENG, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(danHeng, ally)),
                new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
        battle.startBattle();

        double before = battle.aggroOf(danHeng);
        Assertions.assertEquals(0.0, danHeng.getAttribute(AttributeType.AGGRO_ADDED_RATIO).get(), EPS,
                "precondition: nothing has moved his weight yet");

        drainTo(battle, enemy, danHeng, 0.45);
        Assertions.assertTrue(hpRatio(danHeng) <= 0.5, "precondition: he is at or below half HP");
        Assertions.assertEquals(-0.5, danHeng.getAttribute(AttributeType.AGGRO_ADDED_RATIO).get(), EPS,
                "「被敌方目标攻击的概率降低」 ⇒ AggroAddedRatio = -0.5 (0 − parameter)");
        Assertions.assertEquals(before * 0.5, battle.aggroOf(danHeng), EPS,
                "a SOFT weight: base x (1 − 0.5), not zero and not \"untargetable\"");

        // Back above half HP: the cleanup rule takes the state off, so the weight returns.
        battle.heal(ally, danHeng, danHeng.getAttribute(AttributeType.HEALTH).get());
        Assertions.assertTrue(hpRatio(danHeng) > 0.5, "precondition: the heal took him above half HP");
        Assertions.assertEquals(0.0, danHeng.getAttribute(AttributeType.AGGRO_ADDED_RATIO).get(), EPS,
                "above half HP the trace no longer applies");
        Assertions.assertEquals(before, battle.aggroOf(danHeng), EPS, "and the weight is back to what it was");
        Assertions.assertFalse(danHeng.getBuffManager().hasState("潜龙"),
                "the state itself is gone (the cleanup rule names it), so a later read cannot see a stale mark");
    }

    /**
     * The control that makes the threshold meaningful: <b>above</b> half HP nothing happens, even on an HP loss.
     */
    @Test
    public void aHitThatLeavesHimAboveHalfChangesNothing() {
        Character danHeng = CharacterFactory.create(DANHENG, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(danHeng)),
                new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
        battle.startBattle();

        battle.applyDamage(danHeng, new Damage(enemy, danHeng, DamageElement.PHYSICAL, DamageType.NORMAL,
                danHeng.getAttribute(AttributeType.HEALTH).get() * 0.2, SkillCategory.UNSPECIFIED));
        Assertions.assertTrue(hpRatio(danHeng) > 0.5, "precondition: still above half HP");
        Assertions.assertEquals(0.0, danHeng.getAttribute(AttributeType.AGGRO_ADDED_RATIO).get(), EPS,
                "「小于等于 50%」 is a gate, not a welcome: the rule must not fire here");
    }

    /**
     * Deals damage in small steps until he is at or below the given HP ratio - the engine's own path, so {@code HP_LOST}
     * really fires. Small steps because a settled hit goes through defence and resistance.
     */
    private static void drainTo(Battle battle, Enemy attacker, Character victim, double targetRatio) {
        double step = victim.getAttribute(AttributeType.HEALTH).get() * 0.1;
        for (int i = 0; i < 20 && hpRatio(victim) > targetRatio; i++) {
            battle.applyDamage(victim, new Damage(attacker, victim, DamageElement.PHYSICAL, DamageType.NORMAL,
                    step, SkillCategory.UNSPECIFIED));
            Assertions.assertFalse(victim.isDeath(), "precondition: the drain must not kill him");
        }
    }

    private static double hpRatio(Character character) {
        return character.getCurrentHp() / character.getAttribute(AttributeType.HEALTH).get();
    }
}
