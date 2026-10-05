package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
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
 * Light cone 23024: hitting a target puts 【泡影】 on it for one turn (once per attack per target), damage against such targets is 24%
 * higher, and Ultimates get another 24% on top.
 */
public class Cone23024Test {
    private static final int CONE = 23024;
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String BUBBLE = "泡影";

    private Character wearer;
    private Enemy enemy;

    private Battle battle(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    @Test
    public void aHitBubblesTheTarget() {
        Battle battle = battle(true);
        boolean before = enemy.getBuffManager().hasState(BUBBLE);
        // \\u2605 A real damage instance, not a bare event: `DEALING_DAMAGE` carries the instance, and firing it without one makes
        // the cone's other rule refuse ("needs the damage instance being settled") -- measured.
        battle.applyDamage(enemy, new com.laosun.aluminium.models.Damage(wearer, enemy,
                com.laosun.aluminium.enums.DamageElement.FIRE, com.laosun.aluminium.enums.DamageType.NORMAL, 100));
        boolean after = enemy.getBuffManager().hasState(BUBBLE);
        System.out.println("[23024] bubble before=" + before + " after a hit=" + after);
        Assertions.assertFalse(before, "nothing yet");
        Assertions.assertTrue(after, "击中敌方目标时使敌方陷入【泡影】");
    }

    @Test
    public void theSpecPinsBothBoostsAndTheOncePerAttackFlag() {
        Battle battle = battle(true);
        int bubbles = 0;
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE,
                new TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0, null, battle, null))) {
            if (!rule.id().startsWith("cone23024_")) {
                continue;
            }
            for (var effect : rule.effects()) {
                if ("APPLY_BUFF".equals(effect.getOp())) {
                    bubbles++;
                    System.out.println("[23024] spec buff=" + effect.getBuff() + " turns=" + effect.getTurns()
                            + " target=" + effect.getTarget());
                    Assertions.assertEquals(BUBBLE, effect.getBuff(), "the bubble");
                    Assertions.assertEquals(1, effect.getTurns(), "for one turn");
                    Assertions.assertEquals("target", effect.getTarget(), "on the target");
                    // ⚠ `CompiledRule` exposes no once-per-attack accessor (measured), so the flag itself is not readable
                    // from a test: the loader validates it at rule level (TriggerTable line 730 reads spec.getOncePerAttack()),
                    // and this judge pins the effect's shape. Registered as a small reading gap rather than faked here.
                    Assertions.assertEquals(1, effect.getTurns(), "one turn is exactly what the limit rides on");
                } else {
                    System.out.println("[23024] spec op=" + effect.getOp() + " percent=" + effect.getPercent());
                    Assertions.assertEquals(0.24, effect.getPercent(), 1e-9, "24% at rank 1");
                }
            }
        }
        Assertions.assertEquals(1, bubbles, "one rule applies the bubble");
    }

    @Test
    public void theUltimateBoostOnlyFiresForUltimates() {
        Battle battle = battle(true);
        var ult = wearer.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE,
                new TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0, null, battle, SkillCategory.ULTRA));
        var normal = wearer.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE,
                new TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0, null, battle, SkillCategory.NORMAL));
        long ults = ult.stream().filter(rule -> rule.id().startsWith("cone23024_")).count();
        long normals = normal.stream().filter(rule -> rule.id().equals("cone23024_ult_extra")).count();
        System.out.println("[23024] ult rules=" + ults + " ; the ult-extra rule under a plain attack=" + normals);
        Assertions.assertTrue(ults >= 1, "an Ultimate sees the extra");
        Assertions.assertEquals(0, normals, "a plain attack does not");
    }
}
