package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.buff.StateBuff;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1415 昔涟's memosprite 德谬歌, 忆灵技能 8 "献予'纷争'之诗", third clause (2026-10-02):
 *
 * <p>"<b>本次攻击中</b>万敌的暴击伤害提高 <b>#1[i]%</b>" -- `#1` is 2 at level 10, i.e. +200%.
 *
 * <p>Why this test has to make the crit happen before it can see anything: a crit-damage boost multiplies the DAMAGE OF A CRIT, so on a
 * strike that does not crit the boost changes nothing -- which is correct behaviour, and which made an earlier attempt at this clause
 * unjudgeable ("the number did not move" could mean either the boost is missing or the roll missed). The shipped judges already spell the
 * two ways to control the roll: set `CRIT_CHANCE` directly (`AllEnemiesTargetTest`), or override the `Random` (`AnchorDeathTest`'s
 * `return 1.0; // never crits`). This one overrides `nextDouble` to 0.0, i.e. always crits.
 *
 * <p>The window and the shape come from shipped data: `MODIFY_ATTR` with `until: "cast_end"` (light cone 20001 uses exactly that), and
 * the attribute is `CRIT_ATTACK` -- the game's `CriticalDamageBase`.
 */
public class OdeToStrifeCritTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int MYDEI = 1404;
    private static final int MONSTER = 1002011;
    private static final int ODE_TO_STRIFE = 16;
    private static final String BLOODFEUD = "血仇";

    /** The strike the ode commands, with every roll a crit, carries the crit-damage clause. */
    @Test
    public void theCommandedStrikeCarriesTheCritBoost() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character mydei = CharacterFactory.create(MYDEI, LEVEL);
        // every roll crits: without this the boost has nothing to multiply
        Random alwaysCrit = new Random(0) {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
        Battle battle = new Battle(List.of(cyrene, mydei),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), alwaysCrit);
        battle.startBattle();
        battle.processRequests();
        Summon demiurge = battle.summonServant(cyrene);
        battle.processRequests();

        StateBuff bloodfeud = new StateBuff(BLOODFEUD, 3, false);
        bloodfeud.setSource(mydei);
        mydei.getBuffManager().addBuff(bloodfeud);

        CanHit enemy = battle.enemies.getFirst();
        double before = enemy.getCurrentHp();
        Skill ode = demiurge.skillAt(ODE_TO_STRIFE);
        SkillExecutor.execute(battle, ode, demiurge, List.of(mydei));
        battle.processRequests();
        double strike = before - enemy.getCurrentHp();
        System.out.println("[crit] always-crit strike = " + strike + " ; his base CRIT_ATTACK = "
                + mydei.getAttribute(com.laosun.aluminium.enums.AttributeType.CRIT_ATTACK).get());

        Assertions.assertTrue(strike > 0, "precondition: the commanded 【弑神登神】 lands");
        Assertions.assertEquals(EXPECTED, strike, EXPECTED * 1e-9,
                "「本次攻击中万敌的暴击伤害提高 #1[i]%」-- on a crit, the boost is what this number is");
    }

    /** Measured with the clause in place. */
    private static final double EXPECTED = 9080.729463951842;
}
