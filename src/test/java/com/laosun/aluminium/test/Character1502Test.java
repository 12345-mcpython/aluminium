package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1502 Yao Guang's (爻光) ultimate on both counts (2026-09-30): "获得 5 个笑点…并使我方全体目标全属性抗性穿透提高 10%，持续 3 回合".
 *
 * <p>The laughs are a PARTY counter (the capability shipped in the previous round), so the judge reads them from the BATTLE and
 * checks that the caster does not carry a private copy -- the same discriminator that made the party store necessary.
 */
public class Character1502Test {
    private static final int WEARER = 1502;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String LAUGH = "笑点";

    private Character yaoguang;
    private Character ally;
    private Battle battle;

    private void build() {
        yaoguang = CharacterFactory.create(WEARER, LEVEL);
        ally = CharacterFactory.create(ALLY, LEVEL);
        battle = new Battle(List.of(yaoguang, ally), List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
    }

    @Test
    public void theUltimateLiftsTheWholePartysResistancePenetration() {
        build();
        double selfBefore = yaoguang.getAttribute(AttributeType.RESISTANCE_REDUCTION).get();
        double allyBefore = ally.getAttribute(AttributeType.RESISTANCE_REDUCTION).get();
        battle.castImmediate(yaoguang.getSkills().get(SkillType.ULTRA), yaoguang, List.of());
        double selfAfter = yaoguang.getAttribute(AttributeType.RESISTANCE_REDUCTION).get();
        double allyAfter = ally.getAttribute(AttributeType.RESISTANCE_REDUCTION).get();
        System.out.println("[1502] resistance penetration: herself " + selfBefore + " -> " + selfAfter
                + " ; the ally " + allyBefore + " -> " + allyAfter);
        Assertions.assertEquals(selfBefore + 0.1, selfAfter, 1e-9, "the caster gains 10%");
        Assertions.assertEquals(allyBefore + 0.1, allyAfter, 1e-9, "and so does every ally (我方全体目标)");
    }

    @Test
    public void theUltimateAlsoGivesThePartyFiveLaughs() {
        build();
        int before = battle.partyResourceValue(LAUGH);
        battle.castImmediate(yaoguang.getSkills().get(SkillType.ULTRA), yaoguang, List.of());
        int after = battle.partyResourceValue(LAUGH);
        System.out.println("[1502] 笑点 on the battle: " + before + " -> " + after
                + " ; the caster holds it herself? " + yaoguang.getResources().has(LAUGH)
                + " ; the ally? " + ally.getResources().has(LAUGH));
        Assertions.assertEquals(0, before, "the battle starts with none");
        Assertions.assertEquals(5, after, "the ultimate grants five to the SHARED counter");
        Assertions.assertFalse(yaoguang.getResources().has(LAUGH), "which no single unit owns");
    }

    /** The shipped rule, read off the compiled table (discipline 232). */
    @Test
    public void theShippedRuleCarriesTheNumbersAndDuration() {
        build();
        var rules = yaoguang.getTriggerTable().rulesFor(com.laosun.aluminium.enums.TriggerEvent.CAST_SETUP).stream()
                .filter(rule -> rule.id().startsWith("p1502_")).toList();
        Assertions.assertEquals(1, rules.size(), "one shipped rule on a cast setup");
        var effects = rules.getFirst().effects();
        System.out.println("[1502] spec " + rules.getFirst().id() + " effects=" + effects.size()
                + " percent=" + effects.getFirst().getPercent() + " turns=" + effects.getFirst().getTurns()
                + " target=" + effects.getFirst().getTarget());
        Assertions.assertEquals(0.1, effects.getFirst().getPercent(), 1e-9, "10% penetration");
        Assertions.assertEquals(3.0, effects.getFirst().getTurns(), 1e-9, "for three turns");
        Assertions.assertEquals("all_allies", effects.getFirst().getTarget(), "on the whole party");
        Assertions.assertEquals(5.0, effects.get(1).getAmount(), 1e-9, "and the second effect is the five laughs");
    }
}
