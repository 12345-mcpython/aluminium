package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.RelicTriggerTables;
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
import com.laosun.aluminium.utils.RelicFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Relic 119's four-piece: 「击破特攻 ≥150% 时，造成的击破伤害无视其 10% 防御力；≥250% 时，超击破伤害额外无视 15%」.
 *
 * <p>New vocabulary: a damage-type scope on the instance route of MODIFY_ATTR. The wearer must WEAR the set (a relic rule is not loaded by CharacterFactory
 * alone), and the two readings isolate the two variables: same suit, same type, only the threshold differs; then same wearer, only the type differs.
 */
public class Relic119FourPieceTest {
    private static final int SET = 119;
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private Character wearer(boolean aboveThreshold) {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, null, RelicFactory.suit(SET, 5, 15));
        if (aboveThreshold) {
            unit.getAttribute(AttributeType.BREAKING_EFFECT)
                    .addModifier(DoubleValue.Modifier.pure(1.5, DoubleValue.Modifier.ModifierSource.BUFF, 11901));
        }
        return unit;
    }

    @Test
    public void theTwoRulesNameTheirDamageTypesAndShares() {
        Character unit = wearer(true);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Assertions.assertTrue(unit.getAttribute(AttributeType.BREAKING_EFFECT).get() >= 1.5,
                "precondition: matching() evaluates conditions, so the wearer must meet the threshold");
        int seen = 0;
        for (var rule : RelicTriggerTables.of(SET).at(4).matching(TriggerEvent.DEALING_DAMAGE,
                new TriggerTable.TriggerContext(unit, unit, enemy, 0, 0))) {
            for (var effect : rule.effects()) {
                if ("MODIFY_ATTR".equals(effect.getOp()) && "DEFENCE_IGNORE".equals(effect.getAttribute())) {
                    seen++;
                    System.out.println("[119/4] id=" + rule.id() + " scope=" + effect.getDamageType()
                            + " percent=" + effect.getPercent() + " instance=" + effect.getInstance());
                    Assertions.assertTrue(Boolean.TRUE.equals(effect.getInstance()), "it must be instance-scoped");
                    Assertions.assertTrue("BREAK".equals(effect.getDamageType())
                            || "SUPER_BREAK".equals(effect.getDamageType()), "scope is break or super break");
                }
            }
        }
        Assertions.assertEquals(1, seen, "only the 150% rule is satisfiable at this threshold");
    }

    @Test
    public void breakDamageIsBoostedAndPlainDamageIsNot() {
        Character above = wearer(true);
        Character below = wearer(false);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(above, below), List.of(enemy), new Random(0));
        battle.startBattle();
        // ⚠ fixedCrit: four settlements in a row draw from the same Random, so crit luck would differ between the readings
        // and swamp the 5% the defence-ignore is worth (measured: a 1.5x swing).
        double breakAbove = battle.applyDamage(enemy, new Damage(above, enemy, DamageElement.PHYSICAL, DamageType.BREAK, 1000)
                .fixedCrit(true, 1.5));
        // ⚠ fixedCrit: four settlements in a row draw from the same Random, so crit luck would differ between the readings
        // and swamp the 5% the defence-ignore is worth (measured: a 1.5x swing).
        double breakBelow = battle.applyDamage(enemy, new Damage(below, enemy, DamageElement.PHYSICAL, DamageType.BREAK, 1000)
                .fixedCrit(true, 1.5));
        // ⚠ fixedCrit: four settlements in a row draw from the same Random, so crit luck would differ between the readings
        // and swamp the 5% the defence-ignore is worth (measured: a 1.5x swing).
        double normalAbove = battle.applyDamage(enemy, new Damage(above, enemy, DamageElement.PHYSICAL, DamageType.NORMAL, 1000)
                .fixedCrit(true, 1.5));
        // ⚠ fixedCrit: four settlements in a row draw from the same Random, so crit luck would differ between the readings
        // and swamp the 5% the defence-ignore is worth (measured: a 1.5x swing).
        double normalBelow = battle.applyDamage(enemy, new Damage(below, enemy, DamageElement.PHYSICAL, DamageType.NORMAL, 1000)
                .fixedCrit(true, 1.5));
        System.out.println("[119/4] breakAbove=" + breakAbove + " breakBelow=" + breakBelow
                + " normalAbove=" + normalAbove + " normalBelow=" + normalBelow
                + " breakRatio=" + (breakAbove / breakBelow) + " normalRatio=" + (normalAbove / normalBelow));
        // The factor was MEASURED first (10 percent of defence ignored, against this monster) and then pinned:
        // a share mutation has to move it, otherwise the assertion only proves "something happened".
        Assertions.assertEquals(1.0552763806, breakAbove / breakBelow, 1e-6,
                "above the threshold, break damage ignores 10 percent of defence");
        Assertions.assertEquals(1.0, normalAbove / normalBelow, 1e-6,
                "a plain instance is untouched by the break-scoped rule");
    }
}
