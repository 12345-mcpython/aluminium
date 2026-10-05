package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 20018: after a Skill, the wearer's NEXT basic attack deals extra damage equal to 60% of its own attack.
 *
 * <p>Two halves, because the clause is two rules on two different events: the spec half pins the ARMING rule (the spending one
 * only exists while the marker is up, i.e. in a transient state), and the behavioural half casts a Skill for real, then compares
 * a plain damage instance with and without the cone.
 */
public class Cone20018Test {
    private static final int CONE = 20018;
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double RANK_ONE = 0.6;
    private static final double RANK_TWO = 0.75;

    private Character wearer;
    private Enemy enemy;

    private Battle battle(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));
        battle.startBattle();
        wearer.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(DoubleValue.Modifier.pure(-1.0, DoubleValue.Modifier.ModifierSource.BUFF, 200181));
        return battle;
    }

    private double extraDamageAtRank(int rank) {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
        Enemy target = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(target), new Random(0));
        battle.startBattle();
        unit.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(DoubleValue.Modifier.pure(-1.0, DoubleValue.Modifier.ModifierSource.BUFF, 200183));
        double before = battle.applyDamage(target,
                new Damage(unit, target, DamageElement.FIRE, DamageType.NORMAL, 1000));
        var skill = unit.getSkills().values().stream()
                .filter(candidate -> candidate.getData() != null
                        && candidate.getData().getCategory() == SkillCategory.BPSKILL)
                .findFirst().orElseThrow();
        battle.castImmediate(skill, unit, List.of(target));
        double after = battle.applyDamage(target,
                new Damage(unit, target, DamageElement.FIRE, DamageType.NORMAL, 1000));
        return after - before;
    }

    private void castASkill(Battle battle) {
        var skill = wearer.getSkills().values().stream()
                .filter(candidate -> candidate.getData() != null
                        && candidate.getData().getCategory() == SkillCategory.BPSKILL)
                .findFirst().orElseThrow();
        battle.castImmediate(skill, wearer, List.of(enemy));
    }

    private double hit(Battle battle) {
        return battle.applyDamage(enemy, new Damage(wearer, enemy, DamageElement.FIRE, DamageType.NORMAL, 1000));
    }

    @Test
    public void theSpecPinsTheArmingRule() {
        Battle battle = battle(true);
        int pinned = 0;
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.SKILL_CAST,
                new TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0, null, battle, null))) {
            if (!rule.id().startsWith("cone20018_")) {
                continue;
            }
            for (var effect : rule.effects()) {
                pinned++;
                System.out.println("[20018] spec armed by " + rule.id() + " op=" + effect.getOp()
                        + " until=" + effect.getUntil() + " target=" + effect.getTarget());
                Assertions.assertEquals("ADD_STACK", effect.getOp(), "the arming rule marks the wearer");
                Assertions.assertTrue(String.valueOf(effect.getUntil()).contains("next_attack"),
                        "for the next attack only");
            }
        }
        Assertions.assertEquals(1, pinned, "the arming rule (the spending one is transient -- judged by behaviour)");
    }

    @Test
    public void theNextHitIsBigger() {
        Battle plain = battle(false);
        double plainHit = hit(plain);

        Battle armed = battle(true);
        double beforeAnySkill = hit(armed);
        castASkill(armed);
        double afterSkill = hit(armed);
        System.out.println("[20018] plain=" + plainHit + " ; with the cone before a Skill=" + beforeAnySkill
                + " after a Skill=" + afterSkill);
        Assertions.assertEquals(plainHit, beforeAnySkill, plainHit * 1e-9,
                "before the Skill the cone changes nothing");
        Assertions.assertTrue(afterSkill > beforeAnySkill,
                "after a real Skill cast the next hit carries the extra damage");
        // The SHARE, judged by scaling (discipline 200): a direction-only assertion holds for ANY non-zero share, so the
        // `60% -> 30%` mutation was 0 red. An absolute expectation is unavailable (the extra damage has its own defence zone),
        // and a HAND-BUILT half share is not comparable either -- its unit has a different table and a different attack
        // (measured: 245.95 vs .62, i.e. 3.2:1). Two RANKS of the SAME cone on the SAME wearer are comparable, so the ratio
        // of their extras must be the ratio of their shares.
        double full = afterSkill - beforeAnySkill;
        double otherRank = extraDamageAtRank(2);
        System.out.println("[20018] extra at rank 1 (" + RANK_ONE + ") = " + full + " ; rank 2 (" + RANK_TWO + ") = "
                + otherRank);
        Assertions.assertEquals(RANK_TWO / RANK_ONE, otherRank / full, 0.02,
                "the two ranks' extras must be in the ratio of their shares");
    }

    @Test
    public void withoutTheConeNothingIsArmed() {
        Battle battle = battle(false);
        int pinned = 0;
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.SKILL_CAST,
                new TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0, null, battle, null))) {
            if (rule.id().startsWith("cone20018_")) {
                pinned++;
            }
        }
        System.out.println("[20018] without the cone, rules matching: " + pinned);
        Assertions.assertEquals(0, pinned, "no cone, no rule (false case)");
    }
}
