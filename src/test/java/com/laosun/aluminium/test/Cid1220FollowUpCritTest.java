package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1220's 行迹"解形"(1220102): "追加攻击的暴击伤害提高 36%".
 *
 * <p>Judged on one wearer with both readings fixed-crit, so the only difference between them is the damage type the rule is scoped to.
 */
public class Cid1220FollowUpCritTest {
    private static final int WEARER = 1220;
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
                    System.out.println("[1220] id=" + rule.id() + " scope=" + effect.getDamageType()
                            + " percent=" + effect.getPercent() + " instance=" + effect.getInstance());
                    Assertions.assertEquals("ADDITIONAL", effect.getDamageType(), "scoped to follow-up damage");
                    Assertions.assertEquals(0.36, effect.getPercent(), 1e-9, "the data row states 0.36");
                    Assertions.assertTrue(Boolean.TRUE.equals(effect.getInstance()), "it belongs to the instance");
                }
            }
        }
        Assertions.assertEquals(1, seen, "exactly one such modifier");
    }

    @Test
    public void followUpCritDamageRisesAndPlainDamageDoesNot() {
        Character unit = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        // NOT fixedCrit: that path bypasses the crit zone, so an instance's extra crit damage stays invisible
        // (measured: the ratio stayed exactly 1.0). A guaranteed crit instead -- the zone runs and the roll cannot fail.
        unit.getAttribute(com.laosun.aluminium.enums.AttributeType.CRIT_CHANCE)
                .addModifier(com.laosun.aluminium.models.DoubleValue.Modifier.pure(
                        2.0, com.laosun.aluminium.models.DoubleValue.Modifier.ModifierSource.BUFF, 122001));
        Assertions.assertTrue(unit.getAttribute(com.laosun.aluminium.enums.AttributeType.CRIT_CHANCE).get() >= 1.0,
                "precondition: every hit crits");
        double normal = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.WIND, DamageType.NORMAL, 1000));
        double additional = battle.applyAdditionalDamage(unit, enemy, DamageElement.WIND, 1000, null, null);
        System.out.println("[1220] normal=" + normal + " additional=" + additional + " ratio=" + (additional / normal));
        // MEASURED first, then pinned: with the guaranteed crit the factor is exactly (1.5 + 0.36) / 1.5, i.e. the
        // modifier really is 36 PERCENTAGE POINTS of crit damage rather than a share of anything.
        Assertions.assertEquals(1.24, additional / normal, 1e-6,
                "the follow-up instance crits for exactly 36 points more");
    }
}
