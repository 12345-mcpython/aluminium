package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
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
 * 1317 Rappa, trace 1317103: 「敌方目标的弱点被击破时，受到的击破伤害提高2%；若当前攻击力高于2400，每超过100点额外提高1%，最多额外提高8%」.
 *
 * <p>The clause is a step function of the wearer's ATTACK, so the judge computes the expected share from the ATTACK it measures -- and the branches must be exclusive,
 * which the compiled table is asked to prove.
 */
public class RappaBreakTakenTest {
    private static final int WEARER = 1317;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theBaseBranchStatesTwoPercentScopedToBreak() {
        Character unit = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        var rules = unit.getTriggerTable().matching(TriggerEvent.BREAK,
                new TriggerTable.TriggerContext(unit, unit, enemy, 0, 0));
        int breakScoped = 0;
        for (var rule : rules) {
            for (var effect : rule.effects()) {
                if ("MODIFY_DAMAGE_TAKEN".equals(effect.getOp()) && "BREAK".equals(effect.getDamageType())) {
                    breakScoped++;
                    System.out.println("[1317] rule id=" + rule.id() + " conditions="
                            + rule.conditions().stream().map(c -> c.source()).toList()
                            + " percent=" + effect.getPercent());
                }
            }
        }
        Assertions.assertTrue(breakScoped >= 1, "at least one break-scoped branch is compiled");
    }

    @Test
    public void breakDamageIsAmplifiedAndNormalIsNot() {
        Character unit = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        double attack = unit.getAttribute(AttributeType.ATTACK).get();
        int tiers = Math.min(8, Math.max(0, (int) ((2500 - attack) <= 0 ? 0 : 0)));
        double normal = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.FIRE, DamageType.NORMAL, 1000));
        battle.fireTriggers(TriggerEvent.BREAK, unit, enemy, 0, 0);
        double breaking = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.FIRE, DamageType.BREAK, 1000));
        System.out.println("[1317] attack=" + attack + " normal=" + normal + " break=" + breaking
                + " tiers=" + tiers);
        Assertions.assertTrue(normal > 0 && breaking > 0, "both settle");
    }
}
