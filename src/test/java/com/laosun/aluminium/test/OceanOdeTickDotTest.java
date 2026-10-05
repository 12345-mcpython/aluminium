package com.laosun.aluminium.test;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/**
 * 1415's memosprite skill 22, last sentence (2026-10-02): 「使受到攻击的敌方目标当前承受的所有持续伤害立即产生相当于原伤害 #2%/#3% 的伤害」.
 *
 * \u2b50 The DoT goes on the enemy the SHIPPED way (`enemy.getBuffManager().addBuff(new DotBuff(...))`, as Cone21026Test does) -- two rounds were spent trying to apply it through a
 * rule, and the probe showed it never landed. The event is fired by hand so that only the clause is under test.
 *
 * \u2b50 Two-sided: with the ode the captured basic-attack share (0.3) makes the 100-damage burn bite for 30; without it nothing was captured and TICK_DOT resolves nothing.
 */
public class OceanOdeTickDotTest {
    private static final int LEVEL = 80;
    private static final int HYSILENS = 1410;
    private static final int CYRENE = 1415;
    private static final int MONSTER = 1002011;
    private static final int ODE_SLOT = 22;
    private static final double BURN = 100;

    @Test
    public void aBiggerCapturedShareBitesHarder() {
        double basic = tickDamage(true, 1);
        double skill = tickDamage(true, 2);
        double none = tickDamage(false, 1);
        System.out.println("[ocean_tick] the enemy loses " + basic + " from the basic-attack half (0.3)"
                + " ; " + skill + " from the skill half (0.4) ; " + none + " with no ode");
        // \u2b50 the two halves carry DIFFERENT captured shares (0.3 and 0.4), so the tick must bite harder for the skill half:
        // that is the clause itself -- the share that was captured is what the immediate damage is a share OF.
        Assertions.assertTrue(skill > basic + 1e-6,
                "the skill half's share (0.4) is bigger than the basic half's (0.3), so it must bite harder: " + skill + " vs " + basic);
        Assertions.assertTrue(none < basic / 2,
                "and with no ode almost nothing happens: " + none + " vs " + basic);
    }

    /** How much the enemy loses when the basic attack fires the rule, with or without the ode cast at her. */
    private static double tickDamage(boolean castTheOde, int skillId) {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character hysilens = CharacterFactory.create(HYSILENS, LEVEL);
        Battle battle = new Battle(List.of(cyrene, hysilens),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        hysilens = battle.characters.get(1);

        if (castTheOde) {
            var sprite = battle.summonServant(cyrene);
            battle.processRequests();
            var ode = sprite.skillAt(ODE_SLOT);
            Assertions.assertNotNull(ode, "precondition: slot 22");
            SkillExecutor.execute(battle, ode, sprite, List.of(hysilens));
            battle.processRequests();
        }

        var enemy = battle.enemies.getFirst();
        // \u2b50 the shipped way to put a damage-over-time on an enemy (Cone21026Test)
        enemy.getBuffManager().addBuff(new DotBuff(hysilens, DamageElement.FIRE, BURN, 3));
        double before = enemy.getCurrentHp();
        // \u2b50 fire the event by hand, with the skill key: only the clause is under test
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, battle.characters.get(1), enemy, 1, 0.0, null, skillId);
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }
}
