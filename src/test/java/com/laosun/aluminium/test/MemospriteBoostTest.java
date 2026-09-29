package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The memosprite-SCOPED damage boost: `MEMOSPRITE_DAMAGE_BOOST`, judged through a memosprite's own damage.
 *
 * <p>⚠ Why the boost is granted by a synthetic rule rather than by a light cone: its real reader is 1413 长夜月's
 * 「我方忆灵造成的伤害为原伤害的120%/125%/130%/150%」, which still needs an enemy-count condition the vocabulary does not have.
 * The capability is what this class judges, and a synthetic grant adds no stats, so with-versus-without isolates it.
 *
 * <p>⚠ And why the gate is not `instanceof Summon`: the documents distinguish 忆灵 from ordinary 召唤物, so the predicate is
 * "this attacker IS what its master's memospriteOf returns" -- pinned here directly.
 *
 * <p>Disciplines 55/56: the victim has ~900k HP, the fixture asserts the hit neither kills it nor empties its bar, and the
 * generator is pinned at no-crit. Removing the gate collapses the ratio to 1.0, which is the mutation this must catch.
 */
public class MemospriteBoostTest {
    private static final int SUMMONER = 1413;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double BOOST = 0.48;

    @Test
    public void theMemospriteBoostRaisesItsDamage() {
        double plain = memospriteDamage(false);
        double boosted = memospriteDamage(true);
        Assertions.assertTrue(plain > 0, "precondition: the memosprite landed a hit: " + plain);
        double ratio = boosted / plain;
        // ⚠ Measured to the digit: this hit's boost zone holds nothing else, so the ratio IS 1 + BOOST (round 52's
        // compression was a NORMAL skill hit, whose zone already had contributions). A missing gate gives exactly 1.0.
        Assertions.assertEquals(1 + BOOST, ratio, 1e-9,
                "a " + BOOST + " memosprite-scoped boost raises this hit by exactly that: "
                        + plain + " -> " + boosted + " (" + ratio + ")");
    }

    @Test
    public void theMemospriteIsTheOneThatQualifies() {
        Character master = CharacterFactory.create(SUMMONER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(master), List.of(enemy), new Random(0));
        battle.startBattle();
        Summon memosprite = battle.summonMemosprite(master);
        Assertions.assertNotNull(memosprite, "precondition: the 忆灵 is out");
        Assertions.assertSame(memosprite, battle.memospriteOf(master),
                "the gate's predicate: this unit IS what memospriteOf returns for its master");
        Assertions.assertNotSame(master, battle.memospriteOf(master), "and the master is not its own memosprite");
    }

    /** One hit by the memosprite, against a victim that cannot die, with or without the synthetic grant. */
    private static double memospriteDamage(boolean boosted) {
        Character master = CharacterFactory.create(SUMMONER, LEVEL);
        if (boosted) {
            EffectSpec grant = new EffectSpec();
            TriggerSpecs.set(grant, "op", "MODIFY_ATTR");
            TriggerSpecs.set(grant, "attribute", "MEMOSPRITE_DAMAGE_BOOST");
            TriggerSpecs.set(grant, "percent", BOOST);
            TriggerSpecs.set(grant, "permanent", true);
            TriggerSpecs.set(grant, "target", "self");
            master.setTriggerTable(new TriggerTable(9401, List.of(TriggerSpecs.rule(
                    TriggerEvent.BATTLE_START.name(), List.of(), grant))));
        }
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(900000));
        enemy.heal(900000);
        Random noCrit = new Random(0) {
            @Override
            public double nextDouble() {
                return 0.99;
            }
        };
        Battle battle = new Battle(List.of(master), List.of(enemy), noCrit);
        battle.startBattle();
        Summon memosprite = battle.summonMemosprite(master);
        Assertions.assertNotNull(memosprite, "precondition: the 忆灵 is out");
        double before = enemy.getCurrentHp();
        // ⚠ COMMON, not SKILL: a memosprite's stated attack is installed in its COMMON slot (MemospriteAttackTest:78).
        battle.castImmediate(memosprite.getSkills().get(SkillType.COMMON), memosprite, List.of(enemy));
        double dealt = before - enemy.getCurrentHp();
        Assertions.assertFalse(enemy.isDeath(), "the judged hit must not kill the victim");
        Assertions.assertTrue(dealt > 0 && dealt < 0.4 * before,
                "a real measurement, not the whole bar: " + dealt + " of " + before);
        return dealt;
    }
}
