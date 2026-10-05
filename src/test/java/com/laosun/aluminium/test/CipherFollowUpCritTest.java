package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1406's trace "偷天换日"(1406103): "raise the CRIT DMG dealt by the talent's follow-up attacks by 100%".
 *
 * <p>A guaranteed crit (CRIT_CHANCE >= 1) rather than fixedCrit, which would bypass the crit zone and hide the modifier entirely.
 */
public class CipherFollowUpCritTest {
    private static final int WEARER = 1406;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theRuleScopesCritDamageToFollowUpInstances() {
        Character unit = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        int seen = 0;
        for (var rule : unit.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE,
                new TriggerTable.TriggerContext(unit, unit, enemy, 0, 0))) {
            for (var effect : rule.effects()) {
                if ("MODIFY_ATTR".equals(effect.getOp()) && "CRIT_ATTACK".equals(effect.getAttribute())) {
                    seen++;
                    System.out.println("[1406] id=" + rule.id() + " scope=" + effect.getDamageType()
                            + " percent=" + effect.getPercent() + " instance=" + effect.getInstance());
                    Assertions.assertEquals("ADDITIONAL", effect.getDamageType(), "scoped to follow-up damage");
                    Assertions.assertEquals(1.0, effect.getPercent(), 1e-9, "the data row states 100%");
                    Assertions.assertTrue(Boolean.TRUE.equals(effect.getInstance()), "it belongs to the instance");
                }
            }
        }
        Assertions.assertEquals(1, seen, "exactly one such modifier");
    }

    @Test
    public void followUpCritDamageDoublesTheCritBonus() {
        Character unit = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        unit.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(DoubleValue.Modifier.pure(2.0, DoubleValue.Modifier.ModifierSource.BUFF, 140601));
        Assertions.assertTrue(unit.getAttribute(AttributeType.CRIT_CHANCE).get() >= 1.0,
                "precondition: every hit crits");
        double normal = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.QUANTUM, DamageType.NORMAL, 1000));
        double additional = battle.applyAdditionalDamage(unit, enemy, DamageElement.QUANTUM, 1000, null, null);
        System.out.println("[1406] normal=" + normal + " additional=" + additional + " ratio=" + (additional / normal));
        // MEASURED first, then pinned: with a guaranteed crit the factor is (1.5 + 1.0) / 1.5, i.e. the modifier
        // really is 100 PERCENTAGE POINTS of crit damage.
        Assertions.assertEquals(1.6666666667, additional / normal, 1e-6,
                "the follow-up instance crits for exactly 100 points more");
    }
}
