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
 * <p>\u2b50 Two halves, because the clause is two rules on two different events: the spec half pins the ARMING rule (the spending one
 * only exists while the marker is up, i.e. in a transient state), and the behavioural half casts a Skill for real, then compares
 * a plain damage instance with and without the cone.
 */
public class Cone20018Test {
    private static final int CONE = 20018;
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

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
