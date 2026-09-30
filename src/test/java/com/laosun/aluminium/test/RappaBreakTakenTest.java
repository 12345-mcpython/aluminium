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
 * <p>Two assertions that a magnitude mutation cannot survive: the base branch's own share (read from the compiled rule) and the measured ratio between break and normal
 * damage. The branch is selected by its condition, because the tiers are mutually exclusive step functions of ATTACK.
 */
public class RappaBreakTakenTest {
    private static final int WEARER = 1317;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theBaseBranchStatesTwoPercentScopedToBreak() {
        Character unit = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        double attack = unit.getAttribute(AttributeType.ATTACK).get();
        var rules = unit.getTriggerTable().matching(TriggerEvent.BREAK,
                new TriggerTable.TriggerContext(unit, unit, enemy, 0, 0));
        Assertions.assertTrue(attack < 2500, "precondition: this wearer sits in the base tier, ATTACK=" + attack);
        double baseShare = -1;
        for (var rule : rules) {
            for (var effect : rule.effects()) {
                if ("MODIFY_DAMAGE_TAKEN".equals(effect.getOp()) && "BREAK".equals(effect.getDamageType())
                        && rule.conditions().stream().anyMatch(c -> c.source().contains("attack < 2500"))) {
                    baseShare = effect.getPercent();
                    System.out.println("[1317] base rule=" + rule.id() + " percent=" + baseShare);
                }
            }
        }
        Assertions.assertEquals(0.02, baseShare, 1e-9, "the base tier is 2%");
    }

    @Test
    public void breakDamageIsAmplifiedByExactlyTheAuthoredShare() {
        Character unit = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        double normal = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.FIRE, DamageType.NORMAL, 1000));
        battle.fireTriggers(TriggerEvent.BREAK, unit, enemy, 0, 0);
        double breaking = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.FIRE, DamageType.BREAK, 1000));
        System.out.println("[1317] normal=" + normal + " break=" + breaking + " ratio=" + (breaking / normal));
        Assertions.assertEquals(1.02, breaking / normal, 1e-6, "break damage rises by exactly the authored 2%");
    }
}
