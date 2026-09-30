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

import java.util.List;
import java.util.Random;

/**
 * Light cone 23010: the wearer's SKILL and ULTIMATE damage are 18% higher (its crit damage is the row's own props).
 *
 * <p>\u2b50 The two halves are two rules, because {@code from_category} takes a single value. The judge checks both directions: each
 * category must be lifted, and a plain attack must not be -- a rule written without the condition would lift all three.
 */
public class Cone23010Test {
    private static final int CONE = 23010;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double SHARE = 0.18;

    private Character wearer;
    private Enemy enemy;

    private Battle battle(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    private int rulesFor(SkillCategory category) {
        int seen = 0;
        // \u26a0 Build first: reading wearer on the same line as its own construction is an NPE (measured).
        Battle battle = battle(true);
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE,
                new TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0, null, battle, category))) {
            if (!rule.id().startsWith("cone23010_")) {
                continue;
            }
            for (var effect : rule.effects()) {
                System.out.println("[23010] category=" + category + " rule=" + rule.id()
                        + " op=" + effect.getOp() + " percent=" + effect.getPercent()
                        + " conditions=" + rule.conditions());
                Assertions.assertEquals(SHARE, effect.getPercent(), 1e-9, "18% at rank 1");
                seen++;
            }
        }
        return seen;
    }

    @Test
    public void bothCategoriesAreCoveredAndOnlyThose() {
        Assertions.assertEquals(1, rulesFor(SkillCategory.BPSKILL), "one rule speaks about Skills");
        Assertions.assertEquals(1, rulesFor(SkillCategory.ULTRA), "and one about Ultimates");
        Assertions.assertEquals(0, rulesFor(SkillCategory.NORMAL), "a plain attack has no rule (false case)");
    }
}
