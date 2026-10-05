package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
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
 * Light cone 20024: "当拥有的笑点 >= 10 时，装备者的暴击伤害提高 20%".
 *
 * <p>Three things had to exist together, which is why this took two rounds: the resource must be DECLARED (a cone file
 * may now carry a top-level {@code "resources"} array, and {@code TriggerTable.plus} already carried declarations across a
 * merge), the panel must be re-stated on change ({@code RESOURCE_CHANGED}), and the layer it reads must be re-written with
 * it (a cleared counter does not recompute a written modifier).
 */
public class LingeringTearTest {
    private static final int CONE = 20024;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int THRESHOLD = 10;
    private static final double BONUS = 0.2;
    private static final String LAUGHTER = "笑点";

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

    private void changed(Battle battle) {
        battle.fireTriggers(TriggerEvent.RESOURCE_CHANGED, wearer, wearer, 0, 0);
    }

    @Test
    public void theDeclarationReachesTheWearer() {
        Battle battle = battle(true);
        Assertions.assertTrue(wearer.getResources().has(LAUGHTER),
                "the cone's file declares the resource and the merge registers it");
        Assertions.assertEquals(0, wearer.getResources().value(LAUGHTER), "declared with initial 0");
    }

    @Test
    public void thePanelOpensAtTenAndClosesBelowIt() {
        Battle battle = battle(true);
        double base = wearer.getAttribute(AttributeType.CRIT_ATTACK).get();
        wearer.getResources().gain(LAUGHTER, THRESHOLD);
        changed(battle);
        double opened = wearer.getAttribute(AttributeType.CRIT_ATTACK).get() - base;
        wearer.getResources().spend(LAUGHTER, THRESHOLD);
        changed(battle);
        double closed = wearer.getAttribute(AttributeType.CRIT_ATTACK).get() - base;
        System.out.println("[20024] at 10: +" + opened + " ; back to 0: +" + closed);
        Assertions.assertEquals(BONUS, opened, 1e-9, "at the threshold the panel is worth 20% crit damage");
        Assertions.assertEquals(0.0, closed, 1e-9, "and it CLOSES again when the resource falls back below it");
    }

    /**
     * One direction at a time: {@code matching} EVALUATES conditions, so with the resource at 10 only the "met" rule can
     * match and with it at 0 only the "lost" one -- the two cannot both be seen from one context. Each half is filtered by
     * rule id as well, because a table also holds the character's own rules.
     */
    @Test
    public void theSpecPinsBothDirections() {
        Battle battle = battle(true);
        wearer.getResources().gain(LAUGHTER, THRESHOLD);
        int met = 0;
        int lost = 0;
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.RESOURCE_CHANGED,
                new TriggerTable.TriggerContext(wearer, wearer, wearer, 0, 0, null, battle, null))) {
            if (!rule.id().startsWith("cone20024_")) {
                continue;
            }
            boolean adds = rule.effects().stream().anyMatch(e -> "ADD_STACK".equals(e.getOp()));
            boolean removes = rule.effects().stream().anyMatch(e -> "REMOVE_STACK".equals(e.getOp()));
            System.out.println("[20024] spec (resource=" + wearer.getResources().value(LAUGHTER) + ") rule=" + rule.id()
                    + " adds=" + adds + " removes=" + removes);
            Assertions.assertTrue(adds ^ removes, "each rule either opens the panel or closes it, never both");
            Assertions.assertTrue(adds, "with the resource at the threshold, the OPENING rule is the one that matches");
            met++;
            for (var effect : rule.effects()) {
                if ("MODIFY_ATTR".equals(effect.getOp())) {
                    Assertions.assertEquals(BONUS, effect.getPercent(), 1e-9, "20% at rank 1");
                    Assertions.assertEquals(LAUGHTER + "达标", effect.getPerStack(), "read from the panel's layer");
                }
                if ("ADD_STACK".equals(effect.getOp())) {
                    Assertions.assertEquals(1, effect.getMaxStacks(), "the panel's layer is capped at one");
                }
            }
        }
        wearer.getResources().spend(LAUGHTER, THRESHOLD);
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.RESOURCE_CHANGED,
                new TriggerTable.TriggerContext(wearer, wearer, wearer, 0, 0, null, battle, null))) {
            if (!rule.id().startsWith("cone20024_")) {
                continue;
            }
            boolean removes = rule.effects().stream().anyMatch(e -> "REMOVE_STACK".equals(e.getOp()));
            System.out.println("[20024] spec (resource=" + wearer.getResources().value(LAUGHTER) + ") rule=" + rule.id()
                    + " removes=" + removes);
            Assertions.assertTrue(removes, "below the threshold, the CLOSING rule is the one that matches");
            lost++;
        }
        Assertions.assertEquals(1, met, "one rule per direction");
        Assertions.assertEquals(1, lost, "one rule per direction");
    }

    @Test
    public void withoutTheConeNothingMoves() {
        Battle battle = battle(false);
        double base = wearer.getAttribute(AttributeType.CRIT_ATTACK).get();
        wearer.getResources().gain(LAUGHTER, THRESHOLD);
        changed(battle);
        Assertions.assertEquals(base, wearer.getAttribute(AttributeType.CRIT_ATTACK).get(), 1e-9,
                "no cone, no panel (false case)");
    }

    /** The WIRING test: the resource moves through a real GAIN_RESOURCE, which is what must fire the event. */
    @Test
    public void aRealOpFiresTheEvent() {
        Character unit = CharacterFactory.create(ALLY, LEVEL);
        // Note: Declared HERE because 1002's own file declares nothing: an undeclared resource absorbs a gain silently
        // (round 23's finding), which is exactly why a cone's file needed a way to declare one.
        unit.getResources().register(LAUGHTER, 999, 0);
        EffectSpec gain = TriggerSpecs.gainResource(LAUGHTER, THRESHOLD);
        // Note: `GAIN_RESOURCE` credits `resolveTarget(effect, ctx)` -- a hand-built rule must say who (measured round 23:
        // without a target the resource never moved, while the event did fire).
        TriggerSpecs.set(gain, "target", "self");
        var pour = TriggerSpecs.rule("TURN_START", null, gain);
        var watch = TriggerSpecs.rule("RESOURCE_CHANGED", List.of("actor == self"),
                TriggerSpecs.modifyAttr("CRIT_ATTACK", BONUS, 3));
        unit.setTriggerTable(new TriggerTable(ALLY, List.of(pour, watch)));
        Enemy target = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(target), new Random(0));
        battle.startBattle();
        double base = unit.getAttribute(AttributeType.CRIT_ATTACK).get();
        battle.fireTriggers(TriggerEvent.TURN_START, unit, unit, 0, 0);
        double delta = unit.getAttribute(AttributeType.CRIT_ATTACK).get() - base;
        System.out.println("[20024] real GAIN_RESOURCE: value=" + unit.getResources().value(LAUGHTER)
                + " crit damage +" + delta);
        Assertions.assertEquals(THRESHOLD, unit.getResources().value(LAUGHTER), "the op moved the resource");
        Assertions.assertEquals(BONUS, delta, 1e-9,
                "and the op's own change fired RESOURCE_CHANGED -- that is the wiring, not the content");
    }
}
