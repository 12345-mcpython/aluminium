package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.RelicTriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerInterpreter;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Relic 32(2026-09-30): "进入战斗时，若装备者与另一名队友均为开拓同行角色，暴击伤害 +32%".
 *
 * <p>The companion group is TWENTY ids (开拓者 has ten forms, 三月七 two), matched by character id -- never by name,
 * because 姬子 is a prefix of 姬子-启行. Membership has no data marker, so the set is transcribed from the docs; the
 * mutation below removes one member and this test must notice.
 */
public class Relic327CompanionTest {
    private static final int LEVEL = 80;

    @Test
    public void theRuleNamesBothHalves() {
        var rules = RelicTriggerTables.of(327).at(2).rulesFor(TriggerEvent.BATTLE_START).stream()
                .filter(r -> r.id().equals("relic327_companion_crit_dmg")).toList();
        Assertions.assertEquals(1, rules.size(), "one rule");
        Assertions.assertEquals(List.of("self is_companion", "has_companion_ally"),
                rules.getFirst().conditions().stream().map(c -> c.source()).toList(),
                "both halves: the wearer, and another ally");
        var e = rules.getFirst().effects().getFirst();
        Assertions.assertEquals("CRIT_ATTACK", e.getAttribute());
        Assertions.assertEquals(0.32, e.getPercent(), 1e-9, "32% -- the authoritative param, not the old 24%");
        Assertions.assertEquals("self", e.getTarget());
        System.out.println("[327] shape ok");
    }

    @Test
    public void membershipDecidesIt() {
        // 1001 三月七 and 1002 丹恒 are both companions; 1210 (桂乃芬) is not.
        Character wearer = CharacterFactory.create(1001, LEVEL);
        Character ally = CharacterFactory.create(1002, LEVEL);
        Battle battle = new Battle(List.of(wearer, ally), List.of(EnemyFactory.create(1002011, 90, 1)),
                new Random(0));
        battle.startBattle();
        TriggerTable.TriggerContext ctx = new TriggerTable.TriggerContext(wearer, wearer, wearer, 0, 0, null,
                battle, SkillCategory.UNSPECIFIED);
        // Note: matching() EVALUATES the conditions; apply() does not. The first version of this test used apply() and
        // was therefore blind to a change in the companion set -- measured, not assumed.
        Assertions.assertEquals(1, RelicTriggerTables.of(327).at(2).matching(TriggerEvent.BATTLE_START, ctx).size(),
                "two companions must match");

        Character lonely = CharacterFactory.create(1210, LEVEL);
        Battle other = new Battle(List.of(lonely, ally), List.of(EnemyFactory.create(1002011, 90, 1)),
                new Random(0));
        other.startBattle();
        TriggerTable.TriggerContext ctx2 = new TriggerTable.TriggerContext(lonely, lonely, lonely, 0, 0, null,
                other, SkillCategory.UNSPECIFIED);
        Assertions.assertEquals(0, RelicTriggerTables.of(327).at(2).matching(TriggerEvent.BATTLE_START, ctx2).size(),
                "a wearer outside the group must NOT match, even with a companion ally");
        System.out.println("[327] membership ok");
    }
}
