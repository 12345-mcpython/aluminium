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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Light cone 21040: after an attack, if TWO OR MORE of the targets it connected with share its element's weakness, the
 * wearer's crit damage rises 20% for 2 turns.
 *
 * <p>⭐ The event is {@code ALLY_ATTACK} because both the hit count and the weakness count are CAST-LEVEL facts: measured,
 * they are 0 on the per-target damage event. The plumbing is proven from REAL casts -- the caster is 1003 (slot 2 is Fire +
 * Blast) and the targets are 1002011 (whose data lists Fire), and two probes make the count itself readable.
 */
public class Cone21040Test {
    private static final int CONE = 21040;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int FIRE_BLAST = 1003;
    private static final int WEAK_MONSTER = 1002011;

    private Character wearer;
    private Enemy enemy;
    private Battle battle;

    private void build(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
    }

    private boolean matches(int weakHitCount) {
        // ★ A compact constructor + the copy helper: the record's canonical constructor grew a component, and
        // naming all twelve arguments by hand is what the helper exists to avoid.
        var context = new TriggerTable.TriggerContext(wearer, wearer, enemy, 1, 0, null, battle, null)
                .withWeakHitCount(weakHitCount);
        return !wearer.getTriggerTable().matching(TriggerEvent.ALLY_ATTACK, context).isEmpty();
    }

    @Test
    public void theFloorIsTwo() {
        build(true);
        System.out.println("[21040] weak targets 0/1/2/3 -> "
                + matches(0) + "/" + matches(1) + "/" + matches(2) + "/" + matches(3));
        Assertions.assertFalse(matches(0), "no weak target: nothing");
        Assertions.assertFalse(matches(1), "one is not 不少于２个");
        Assertions.assertTrue(matches(2), "two is");
        Assertions.assertTrue(matches(3), "and three still is");
    }

    @Test
    public void theSpecPinsTheShareAndTheDuration() {
        build(true);
        int pinned = 0;
        var context = new TriggerTable.TriggerContext(wearer, wearer, enemy, 1, 0, null, battle, null)
                .withWeakHitCount(2);
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.ALLY_ATTACK, context)) {
            if (!rule.id().startsWith("cone21040_")) {
                continue;
            }
            for (var effect : rule.effects()) {
                pinned++;
                System.out.println("[21040] spec attribute=" + effect.getAttribute() + " percent=" + effect.getPercent()
                        + " turns=" + effect.getTurns() + " target=" + effect.getTarget());
                Assertions.assertEquals("CRIT_ATTACK", effect.getAttribute(), "crit damage, not crit rate");
                Assertions.assertEquals(0.2, effect.getPercent(), 1e-9, "20% at rank 1");
                Assertions.assertEquals(2, effect.getTurns(), "for 2 turns");
            }
        }
        Assertions.assertEquals(1, pinned, "one rule from this cone");
    }

    @Test
    public void withoutTheConeNoRuleExists() {
        build(false);
        System.out.println("[21040] without the cone, weak targets 2 -> " + matches(2));
        Assertions.assertFalse(matches(2), "no cone, no rule (false case)");
    }

    /**
     * ★ The PLUMBING, from REAL casts: two probes make the engine's own count readable (one fires at ">= 1", one at
     * ">= 2"), so which probes fired says what it counted. A negative-only reading would pass even if the count were
     * hard-wired to 0 -- which is exactly the trap this replaces.
     */
    @Test
    public void theCountComesFromARealCast() {
        Assertions.assertEquals(2, probesFired(2), "two Fire-weak targets: both probes fire, so the count is 2");
        Assertions.assertEquals(1, probesFired(1), "one target: only the lower probe fires, so the count is 1");
    }

    private int probesFired(int targets) {
        Character caster = CharacterFactory.create(FIRE_BLAST, LEVEL);
        caster.setTriggerTable(new TriggerTable(FIRE_BLAST, List.of(
                TriggerSpecs.rule("ALLY_ATTACK", List.of("actor == self", "weakness_hit_count >= 1"),
                        probe("弱点一", 1)),
                TriggerSpecs.rule("ALLY_ATTACK", List.of("actor == self", "weakness_hit_count >= 2"),
                        probe("弱点二", 1)))));
        List<Enemy> foes = new ArrayList<>();
        for (int i = 0; i < targets; i++) {
            foes.add(EnemyFactory.create(WEAK_MONSTER, 90, 1));
        }
        Battle fight = new Battle(List.of(caster, CharacterFactory.create(ALLY, LEVEL)), foes, new Random(0));
        fight.startBattle();
        var skill = caster.getSkills().values().stream()
                .filter(candidate -> candidate.getData() != null
                        && candidate.getData().getCategory() == SkillCategory.BPSKILL)
                .findFirst().orElseThrow();
        fight.castImmediate(skill, caster, List.copyOf(foes));
        int fired = 0;
        if (caster.getBuffManager().stacksOf("弱点一") > 0) {
            fired++;
        }
        if (caster.getBuffManager().stacksOf("弱点二") > 0) {
            fired++;
        }
        System.out.println("[21040] real cast on " + targets + " Fire-weak target(s): probes fired = " + fired);
        return fired;
    }

    private com.laosun.aluminium.beans.EffectSpec probe(String name, int amount) {
        var effect = new com.laosun.aluminium.beans.EffectSpec();
        TriggerSpecs.set(effect, "op", "ADD_STACK");
        TriggerSpecs.set(effect, "buff", name);
        TriggerSpecs.set(effect, "amount", (double) amount);
        TriggerSpecs.set(effect, "permanent", true);
        TriggerSpecs.set(effect, "target", "self");
        return effect;
    }
}
