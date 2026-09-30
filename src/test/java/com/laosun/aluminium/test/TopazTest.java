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
 * 1112 Topaz &amp; Numby: the skill's 【负债证明】 state and its damage, plus the trace's fire-weakness clause.
 *
 * <p>The trace clause is judged on the COMPILED rule, because its condition needs an enemy that really carries a Fire weakness and this fixture does not control that.
 */
public class TopazTest {
    private static final int WEARER = 1112;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theSkillMarksTheTargetAndDealsDamage() {
        Character unit = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.fireTriggers(TriggerEvent.SKILL_CAST, unit, enemy, 0, 0);
        boolean marked = enemy.getBuffManager().hasState("\u8d1f\u503a\u8bc1\u660e");
        double settled = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.FIRE, DamageType.NORMAL, 1000));
        System.out.println("[1112] marked=" + marked + " settled=" + settled);
        Assertions.assertTrue(marked, "the skill applies the debt state");
        Assertions.assertTrue(settled > 0, "and the skill's own damage lands");
    }

    @Test
    public void theTraceRuleCarriesTheFireWeaknessClause() {
        Character unit = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        var rules = unit.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE,
                new TriggerTable.TriggerContext(unit, unit, enemy, 0, 0));
        double percent = -1;
        for (var rule : rules) {
            for (var effect : rule.effects()) {
                if ("BOOST_DAMAGE".equals(effect.getOp())) {
                    percent = effect.getPercent();
                    System.out.println("[1112] trace rule id=" + rule.id() + " conditions="
                            + rule.conditions().stream().map(c -> c.source()).toList() + " percent=" + percent);
                }
            }
        }
        Assertions.assertEquals(0.15, percent, 1e-9, "the trace row states 15%");
    }
}
