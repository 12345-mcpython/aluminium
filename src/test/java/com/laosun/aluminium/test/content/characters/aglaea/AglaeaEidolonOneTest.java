package com.laosun.aluminium.test.content.characters.aglaea;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.buff.VulnerabilityBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1402 阿格莱雅's Eidolon 1:「处于【间隙织线】状态下的敌人受到的伤害提高 15%，阿格莱雅或衣匠攻击该目标后额外恢复 20 点能量」.
 *
 * <p>The state is applied by an outside helper rule so that the question is "what does the Eidolon add to an <b>already
 * threaded</b> target", not "does the talent thread it" (that is {@code AglaeaThreadStateTest}'s job).
 */
public class AglaeaEidolonOneTest {
    private static final int AGLAEA = 1402;
    private static final int LEVEL = 80;
    private static final String THREAD = "间隙织线";

    /**
     * The 15%: the same attack on the same threaded enemy, once at E0 and once at E1, differs by exactly 1.15. A ratio is
     * the right instrument because crit and defence multiply both sides alike.
     */
    @Test
    public void theThreadedTargetTakesFifteenPercentMore() {
        double withoutEidolon = damageOnAThreadedTarget(0);
        double withEidolon = damageOnAThreadedTarget(1);
        Assertions.assertTrue(withoutEidolon > 0, "precondition: the hit lands: " + withoutEidolon);
        Assertions.assertEquals(1.15, withEidolon / withoutEidolon, 1e-6,
                "「受到的伤害提高 15%」: " + withoutEidolon + " -> " + withEidolon);
    }

    /**
     * 「仅对最新被施加的目标生效」 applied to the Eidolon's modifier: it MOVES with the state instead of piling up. Both
     * sides are asserted - the new holder has it and the old one does not - because "the new one has it" alone would pass
     * even if the old copy never went away.
     */
    @Test
    public void theVulnerabilityMovesWithTheState() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL, false, null, null, 1);
        Enemy left = BattleFixture.monster();
        Enemy right = BattleFixture.monster();
        Battle battle = new Battle(List.of(aglaea), List.of(left, right), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.summonMemosprite(aglaea);

        battle.castImmediate(aglaea.getSkills().get(SkillType.COMMON), aglaea, List.of(left));
        Assertions.assertEquals(1, left.getBuffManager().allBuffsOf(VulnerabilityBuff.class).size(),
                "the attacked target carries the 15%");
        Assertions.assertEquals(0, right.getBuffManager().allBuffsOf(VulnerabilityBuff.class).size(),
                "and only it");

        battle.castImmediate(aglaea.getSkills().get(SkillType.COMMON), aglaea, List.of(right));
        Assertions.assertEquals(1, right.getBuffManager().allBuffsOf(VulnerabilityBuff.class).size(),
                "the modifier followed the state to the new target");
        Assertions.assertEquals(0, left.getBuffManager().allBuffsOf(VulnerabilityBuff.class).size(),
                "and the old holder lost it");
    }

    /** 「阿格莱雅**或衣匠**攻击该目标后额外恢复 20 点能量」 - both halves, each measured as a delta against E0. */
    @Test
    public void aThreadedAttackPaysTwentyEnergy() {
        Assertions.assertEquals(20.0, energyAfterAttack(1, false) - energyAfterAttack(0, false), 1e-9,
                "her own attack on a threaded target pays 20");
        Assertions.assertEquals(20.0, energyAfterAttack(1, true) - energyAfterAttack(0, true), 1e-9,
                "and so does the Garmentmaker's attack");
    }

    /**
     * @param eidolon       star rank
     * @param byGarmentmaker whether the finishing attack is the memosprite's rather than hers
     * @return her energy after one attack on a pre-threaded enemy
     */
    private static double energyAfterAttack(int eidolon, boolean byGarmentmaker) {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL, false, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        Character ally = CharacterFactory.create(1001, LEVEL);
        // An outside rule stamps 【间隙织线】, so the Eidolon's clause is the only difference between the two runs.
        EffectSpec thread = new EffectSpec();
        TriggerSpecs.set(thread, "op", "APPLY_BUFF");
        TriggerSpecs.set(thread, "buff", THREAD);
        TriggerSpecs.set(thread, "turns", 5);
        TriggerSpecs.set(thread, "target", "target");
        ally.setTriggerTable(new TriggerTable(9702, List.of(TriggerSpecs.rule(
                TriggerEvent.ALLY_ATTACK.name(), List.of(), thread))));
        Battle battle = new Battle(List.of(aglaea, ally), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.summonMemosprite(aglaea);
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, enemy, 1, 0));
        Assertions.assertTrue(enemy.getBuffManager().hasState(THREAD), "precondition: the victim is threaded");

        double before = aglaea.getCurrentEnergy();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ATTACK_FINISHED,
                byGarmentmaker ? battle.summonOf(aglaea) : aglaea, enemy, 1, 0));
        return aglaea.getCurrentEnergy() - before;
    }

    /** One basic attack on a threaded enemy, as the HP it actually removes, at the given Eidolon rank. */
    private static double damageOnAThreadedTarget(int eidolon) {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL, false, null, null, eidolon);
        Character ally = CharacterFactory.create(1001, LEVEL);
        EffectSpec thread = new EffectSpec();
        TriggerSpecs.set(thread, "op", "APPLY_BUFF");
        TriggerSpecs.set(thread, "buff", THREAD);
        TriggerSpecs.set(thread, "turns", 5);
        TriggerSpecs.set(thread, "target", "target");
        ally.setTriggerTable(new TriggerTable(9703, List.of(TriggerSpecs.rule(
                TriggerEvent.ALLY_ATTACK.name(), List.of(), thread))));
        Enemy enemy = BattleFixture.monster();
        // A fixed roll keeps crit out of the ratio.
        Random noCrit = BattleFixture.alwaysRoll(0.99);
        Battle battle = new Battle(List.of(aglaea, ally), List.of(enemy), noCrit);
        battle.startBattle();
        battle.summonMemosprite(aglaea);
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, enemy, 1, 0));
        double before = enemy.getCurrentHp();
        battle.castImmediate(aglaea.getSkills().get(SkillType.COMMON), aglaea, List.of(enemy));
        double dealt = before - enemy.getCurrentHp();
        Assertions.assertFalse(enemy.isDeath(), "the judged hit must not kill the victim");
        return dealt;
    }
}
