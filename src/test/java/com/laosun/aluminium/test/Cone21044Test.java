package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
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
 * Light cone 21044: against a target whose DEFENCE was lowered OR whose SPEED was lowered, the wearer's crit damage is 24%
 * higher. Two rules, because the vocabulary has no OR -- and the judge checks each, plus the plain target.
 */
public class Cone21044Test {
    private static final int CONE = 21044;
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int MODIFIER_ID = 990003;

    private Character wearer;
    private Enemy enemy;
    private Battle battle;

    private void build() {
        wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1));
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));
        battle.startBattle();
    }

    private int critRules() {
        return (int) wearer.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE,
                        new TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0, null, battle, null))
                .stream().filter(rule -> rule.id().startsWith("cone21044_")).count();
    }

    private void lower(AttributeType attribute) {
        enemy.getAttribute(attribute)
                .addModifier(DoubleValue.Modifier.addPercent(-0.3, DoubleValue.Modifier.ModifierSource.DEBUFF, MODIFIER_ID));
    }

    @Test
    public void bothKindsOfLoweringCountAndNothingElse() {
        build();
        int plain = critRules();
        lower(AttributeType.DEFENCE);
        int lowered = critRules();
        lower(AttributeType.SPEED);
        int both = critRules();
        System.out.println("[21044] rules matching -- plain=" + plain + " defence lowered=" + lowered
                + " defence+speed lowered=" + both);
        Assertions.assertEquals(0, plain, "a plain target has no rule");
        Assertions.assertEquals(1, lowered, "\u9632\u5fa1\u964d\u4f4e is one of them");
        Assertions.assertEquals(2, both, "\u6216 \u51cf\u901f is the other");
    }

    @Test
    public void theSpecPinsTheShareAndTheAttribute() {
        build();
        int pinned = 0;
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE,
                new TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0, null, battle, null))) {
            if (!rule.id().startsWith("cone21044_")) {
                continue;
            }
            for (var effect : rule.effects()) {
                pinned++;
                System.out.println("[21044] spec " + rule.id() + " attribute=" + effect.getAttribute()
                        + " percent=" + effect.getPercent());
                Assertions.assertEquals("CRIT_ATTACK", effect.getAttribute(), "crit DAMAGE, not crit rate");
                Assertions.assertEquals(0.24, effect.getPercent(), 1e-9, "24% at rank 1");
            }
        }
        Assertions.assertEquals(0, pinned, "the effect lives on the condition-bearing rule, primed above");
    }
}
