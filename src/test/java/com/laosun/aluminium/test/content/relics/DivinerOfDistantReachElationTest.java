package com.laosun.aluminium.test.content.relics;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.RelicTriggerTables;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Relic 130's SECOND sentence: "每场战斗第一次施放欢愉技时，我方全体欢愉度 +10%".
 *
 * <p>matching() evaluates the conditions, so the category is actually tested: with an Elation cast the rule matches,
 * with a plain skill it must not. The spelling is ElationDamage -- the engine refused ELATION_DAMAGE and said "the
 * categories are the ones the skill data spells".
 */
public class DivinerOfDistantReachElationTest {
    private static final int LEVEL = 80;

    private static TriggerTable.TriggerContext ctx(Character owner, Battle battle, SkillCategory category) {
        return new TriggerTable.TriggerContext(owner, owner, owner, 0, 0, null, battle, category);
    }

    private static Battle battleWith(Character owner) {
        Battle battle = new Battle(List.of(owner, CharacterFactory.create(1001, LEVEL)),
                List.of(EnemyFactory.create(1002011, 90, 1)), new Random(0));
        battle.startBattle();
        return battle;
    }

    @Test
    public void threeRulesOnTheFourPieceAndOnlyTheElationOneIsGated() {
        var rules = RelicTriggerTables.of(130).at(4).rulesFor(TriggerEvent.CAST_SETUP).stream()
                .filter(r -> r.id().equals("relic130_elation_first")).toList();
        Assertions.assertEquals(1, rules.size(), "the Elation rule");
        Assertions.assertEquals(List.of("from_category ElationDamage", "!self has_state relic130_elation_used"),
                rules.getFirst().conditions().stream().map(c -> c.source()).toList(),
                "an Elation cast, and not already used this battle");
        var e = rules.getFirst().effects();
        Assertions.assertEquals("APPLY_BUFF", e.get(0).getOp(), "the one-way marker");
        Assertions.assertEquals("MODIFY_ATTR", e.get(1).getOp());
        Assertions.assertEquals("ELATION_DAMAGE_BOOST", e.get(1).getAttribute());
        Assertions.assertEquals(0.10, e.get(1).getPercent(), 1e-9, "10%");
        Assertions.assertEquals("all_allies", e.get(1).getTarget(), "the whole side");
        System.out.println("[130-elation] shape ok");
    }

    @Test
    public void theCategoryDecidesIt() {
        Character owner = CharacterFactory.create(1001, LEVEL);
        Battle battle = battleWith(owner);
        Assertions.assertEquals(1,
                RelicTriggerTables.of(130).at(4)
                        .matching(TriggerEvent.CAST_SETUP, ctx(owner, battle, SkillCategory.ELATION_DAMAGE)).size(),
                "an Elation cast must match");
        Assertions.assertEquals(0,
                RelicTriggerTables.of(130).at(4)
                        .matching(TriggerEvent.CAST_SETUP, ctx(owner, battle, SkillCategory.BPSKILL)).size(),
                "a plain skill must NOT match");
        System.out.println("[130-elation] category ok");
    }
}
