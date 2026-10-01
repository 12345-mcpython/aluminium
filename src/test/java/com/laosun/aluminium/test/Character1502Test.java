package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1502 \u723b\u5149\u2019s ultimate (2026-09-30): \u300c\u4f7f\u6211\u65b9\u5168\u4f53\u76ee\u6807\u5168\u5c5e\u6027\u6297\u6027\u7a7f\u900f\u63d0\u9ad8 10%\uff0c\u6301\u7eed 3 \u56de\u5408\u300d.
 *
 * <p>\u2b50 Her kit is mostly the Elation system (party-level \u7b11\u70b9, Aha\u2019s extra turn, random targets), so only the clause whose numbers
 * the text states completely is shipped here -- and the judge reads it on the PARTY\u2019s panels, not on the caster alone.
 */
public class Character1502Test {
    private static final int WEARER = 1502;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

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
        Assertions.assertEquals(allyBefore + 0.1, allyAfter, 1e-9, "and so does every ally (\u6211\u65b9\u5168\u4f53\u76ee\u6807)");
    }

    /** \u2605 The shipped rule, read off the compiled table (discipline 232). */
    @Test
    public void theShippedRuleCarriesTheNumbersAndDuration() {
        build();
        var rules = yaoguang.getTriggerTable().rulesFor(TriggerEvent.CAST_SETUP).stream()
                .filter(rule -> rule.id().startsWith("p1502_")).toList();
        Assertions.assertEquals(1, rules.size(), "one shipped rule on a cast setup");
        var effect = rules.getFirst().effects().getFirst();
        System.out.println("[1502] spec " + rules.getFirst().id() + " attribute=" + effect.getAttribute()
                + " percent=" + effect.getPercent() + " turns=" + effect.getTurns() + " target=" + effect.getTarget());
        Assertions.assertEquals(0.1, effect.getPercent(), 1e-9, "10% penetration");
        Assertions.assertEquals(3.0, effect.getTurns(), 1e-9, "for three turns");
        Assertions.assertEquals("all_allies", effect.getTarget(), "on the whole party");
    }
}
