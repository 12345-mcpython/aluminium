package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import com.laosun.aluminium.models.EnemySkill;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * P5-3 acceptance: enemy skills (data-driven + fallback).
 *
 * <p>⚠ For the source of the multipliers see the notes in {@code enemy_skills.json}: there is **no**
 * enemy skill table in the data source, these multipliers are guesses.
 * So what is asserted is "the engine settles correctly by the multiplier", not "this multiplier is
 * the true game value".
 *
 * <p>Expected values are always **derived from the actual panel** (attack power × multiplier ×
 * defence zone), never hardcoded — it was hardcoded once, and that ended up swapping the enemy's
 * defence and ours.
 */
public class EnemySkillTest {
    private static final double EPS = 1e-9;
    /** Victim defence 1000, Lv80 → defence zone = (200 + 10×80) / (1000 + 1000) = 0.5. */
    private static final double VICTIM_DEFENCE = 1000;

    @Test
    public void everyEnemyGetsAnAttackFromDataOrFallback() {
        Enemy iceEdge = EnemyFactory.create(1002011, 90, 1);

        EnemySkill attack = (EnemySkill) iceEdge.getSkills().get(SkillType.COMMON);
        Assertions.assertNotNull(attack, "an enemy MUST have a basic attack");

        var data = Constant.ENEMY_SKILLS.get(1002011);
        Assertions.assertNotNull(data, "Ice Edge has an entry in enemy_skills.json (the key is the monster-instance id)");
        Assertions.assertTrue(data.guessed(), "the multiplier is a guess, and the data says so faithfully");
        Assertions.assertEquals(100201101, data.id());
        Assertions.assertEquals(data.multiplier(), attack.getMultiplier(), EPS);
        Assertions.assertEquals(data.hits(), attack.getHits());
        Assertions.assertEquals(DamageElement.ICE, attack.getElement(), "the table says Ice");
        Assertions.assertNull(attack.getData(), "EnemySkill does not go through a character's multiplier table");
    }

    /**
     * Fallback: a monster with no entry in the table must still be able to attack (multiplier 1.0,
     * single hit, element taken from its own stance_type).
     */
    @Test
    public void unknownMonsterFallsBackToNeutralAttack() {
        // Thunder-cry Creation 8001040: it really exists, but is not in enemy_skills.json
        Enemy grunt = EnemyFactory.create(8001040, 80, 1);

        EnemySkill attack = (EnemySkill) grunt.getSkills().get(SkillType.COMMON);
        Assertions.assertNotNull(attack, "with no data there must still be a fallback basic attack; it cannot just stand there");
        Assertions.assertEquals(1.0, attack.getMultiplier(), EPS);
        Assertions.assertEquals(1, attack.getHits());
        Assertions.assertNull(Constant.ENEMY_SKILLS.get(8001040), "confirm it really is not in the table");
    }

    @Test
    public void attackSettlesAttackPowerTimesMultiplierThroughTheDefenceZone() {
        Enemy iceEdge = EnemyFactory.create(1002011, 90, 1);
        Character victim = victim();
        Battle battle = new Battle(List.of(victim), List.of(iceEdge), new Random(0));

        double attack = iceEdge.getAttribute(AttributeType.ATTACK).get();
        double settled = battle.applyDamage(victim,
                new Damage(iceEdge, victim, DamageElement.ICE, DamageType.NORMAL, attack * 1.0));

        double defenceZone = defenceZone(iceEdge.getLevel(), VICTIM_DEFENCE);
        Assertions.assertEquals(attack * 1.0 * defenceZone, settled, 0.1);
    }

    /**
     * Multi-hit: {@link EnemySkill} implements N hits as N independent settlements, so damage
     * accumulates and the taking-a-hit energy gain is also issued once per hit (each hit is an
     * independent attack action).
     */
    @Test
    public void multiHitAttackSettlesEverySegment() {
        Enemy trampler = EnemyFactory.create(8013010, 80, 1);
        EnemySkill attack = (EnemySkill) trampler.getSkills().get(SkillType.COMMON);
        Assertions.assertEquals(2, attack.getHits(), "the table says 2 hits");

        Character victim = victim();
        victim.setMaxEnergy(120);
        Battle battle = new Battle(List.of(victim), List.of(trampler), new Random(0));

        double attackPower = trampler.getAttribute(AttributeType.ATTACK).get();
        double hpBefore = victim.getCurrentHp();
        attack.execute(battle, trampler, List.of(victim));

        double perHit = attackPower * 1.0 * defenceZone(trampler.getLevel(), VICTIM_DEFENCE);
        Assertions.assertEquals(perHit * 2, hpBefore - victim.getCurrentHp(), 0.2, "each of the two hits settles once");
        Assertions.assertEquals(20, victim.getCurrentEnergy(), 1e-6, "each of the two hits grants one taking-a-hit energy gain (standard 10)");
    }

    @Test
    public void enemyAttackUsesTheSharedDamagePipeline() {
        // Enemy crit chance is 0 → no crit; so the settled value = attack power × multiplier × defence zone × resistance zone (characters have no resistance table = 1)
        Enemy iceEdge = EnemyFactory.create(1002011, 90, 1);
        Character victim = victim();
        Battle battle = new Battle(List.of(victim), List.of(iceEdge), new Random(0));

        Assertions.assertEquals(0, iceEdge.getAttribute(AttributeType.CRIT_CHANCE).get(), EPS);
        double settled = battle.applyDamage(victim,
                new Damage(iceEdge, victim, DamageElement.ICE, DamageType.NORMAL, 1000));

        Assertions.assertEquals(1000 * defenceZone(iceEdge.getLevel(), VICTIM_DEFENCE), settled, 0.1);
    }

    // ==================================================================

    private static Character victim() {
        return Character.fromAttributes("victim", 100_000, VICTIM_DEFENCE, 100, 100);
    }

    /** Defence zone = (200 + 10 × attacker level) / (defender defence + 200 + 10 × attacker level). */
    private static double defenceZone(int attackerLevel, double defenderDefence) {
        double levelTerm = Constant.DEFENCE_CONST + Constant.DEFENCE_PER_LEVEL * attackerLevel;
        return levelTerm / (defenderDefence + levelTerm);
    }
}
