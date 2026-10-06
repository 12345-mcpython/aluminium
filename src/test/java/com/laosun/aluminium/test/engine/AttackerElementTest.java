package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/**
 * {@code element: "attacker"} -- the element of the unit that produced the instance.
 *
 * <p>Two-sided, and about SCOPE as much as behaviour: a DAMAGE rule may name it (Pearl's Elation skill arms
 * every ally to deal "Elation DMG of their own Type"), while APPLY_DOT must refuse it, because a DOT is the
 * rule's own instance and "the attacker's element" would be a sentence with no subject. Refusal is asserted,
 * not hoped for: the loader is the thing that has to say no.
 */
public class AttackerElementTest {
    private static final int PEARL = 1503;
    private static final int MONSTER = 1002011;

    @Test
    public void adamageRuleResolvesTheAttackersElement() {
        Character pearl = CharacterFactory.create(PEARL, 80);
        var enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(pearl, CharacterFactory.create(1204, 80)), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Character mine = battle.characters.getFirst();

        EffectSpec instance = TriggerSpecs.damage(null, 0, null, "target");
        TriggerSpecs.set(instance, "scale", "elation_base");
        TriggerSpecs.set(instance, "percent", 1.0);
        TriggerSpecs.set(instance, "element", "attacker");
        TriggerSpecs.set(instance, "damageType", "ELATION");
        mine.setTriggerTable(new TriggerTable(PEARL, List.of(
                TriggerSpecs.rule("ULT_CAST", List.of("actor == self"), instance))));

        double before = enemy.getCurrentHp();
        battle.fireTriggers(com.laosun.aluminium.enums.TriggerEvent.ULT_CAST, mine, battle.enemies.getFirst(), 0, 0);
        double dealt = before - enemy.getCurrentHp();
        System.out.println("[attacker_element] pearl is Ice; the instance dealt " + dealt);
        Assertions.assertTrue(dealt > 0, "the reserved word resolved to her own element and the hit landed");
    }

    @Test
    public void aDotMayNotUseIt() {
        EffectSpec dot = TriggerSpecs.dot("Ice", 100.0, null, null, 2, null);
        TriggerSpecs.set(dot, "element", "attacker");
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(PEARL, List.of(
                        TriggerSpecs.rule("ULT_CAST", List.of("actor == self"), dot))));
        System.out.println("[attacker_element] APPLY_DOT refused it: " + refused.getMessage().substring(0, 60));
        Assertions.assertTrue(refused.getMessage().contains("element"),
                "and the refusal names the element slot (got: " + refused.getMessage() + ")");
    }
}
