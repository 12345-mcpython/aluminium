package com.laosun.aluminium.test.content.lightcones;

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
 * <p>The two halves are two rules, because {@code from_category} takes a single value. The judge checks both directions: each
 * category must be lifted, and a plain attack must not be -- a rule written without the condition would lift all three.
 */
public class BeforeDawnTest {
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
        // Note: Build first: reading wearer on the same line as its own construction is an NPE (measured).
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

    /**
     * The third sentence: after an Ultimate the wearer's FOLLOW-UP damage is 48% higher for one turn. It is a written
     * modifier on the category attribute, not a damage-type scope -- measured: the attribute exists
     * (`FOLLOW_UP_DAMAGE_BOOST`), which is what an earlier note of mine wrongly called missing.
     */
    @Test
    public void theUltimateArmsTheFollowUp() {
        Battle battle = battle(true);
        double before = wearer.getAttribute(com.laosun.aluminium.enums.AttributeType.FOLLOW_UP_DAMAGE_BOOST).get();
        battle.fireTriggers(TriggerEvent.ULT_CAST, wearer, enemy, 0, 0);
        double after = wearer.getAttribute(com.laosun.aluminium.enums.AttributeType.FOLLOW_UP_DAMAGE_BOOST).get();
        int pinned = 0;
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.ULT_CAST,
                new TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0, null, battle, null))) {
            if (!rule.id().startsWith("cone23010_")) {
                continue;
            }
            for (var effect : rule.effects()) {
                pinned++;
                System.out.println("[23010] ult rule: attribute=" + effect.getAttribute() + " percent="
                        + effect.getPercent() + " turns=" + effect.getTurns());
                // Note: `getAttribute()` is a String, not the enum (measured): compare the spelling.
                Assertions.assertEquals("FOLLOW_UP_DAMAGE_BOOST", effect.getAttribute(),
                        "the follow-up dimension is an attribute");
                Assertions.assertEquals(0.48, effect.getPercent(), 1e-9, "48% at rank 1");
                Assertions.assertEquals(1, effect.getTurns(), "for one turn");
            }
        }
        Assertions.assertEquals(1, pinned, "one rule from the third sentence");
        Assertions.assertTrue(after > before, "and firing the Ultimate arms it");
    }

    @Test
    public void bothCategoriesAreCoveredAndOnlyThose() {
        Assertions.assertEquals(1, rulesFor(SkillCategory.BPSKILL), "one rule speaks about Skills");
        Assertions.assertEquals(1, rulesFor(SkillCategory.ULTRA), "and one about Ultimates");
        Assertions.assertEquals(0, rulesFor(SkillCategory.NORMAL), "a plain attack has no rule (false case)");
    }
}
