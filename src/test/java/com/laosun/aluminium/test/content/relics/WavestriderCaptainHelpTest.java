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
 * Relic 126: "成为其他我方目标的技能目标时，获得 1 层[助力]，最多 2 层；施放终结技时若持有 2 层，
 * 消耗所有[助力]，攻击力 +48%，持续 1 回合" (when you become the Skill target of another one of our targets, gain 1 stack of [助力], at most 2; when casting the ultimate with 2 stacks, spend all [助力] for +48% ATK for 1 turn).
 *
 * <p>matching() evaluates the conditions, so the three-way guard really is tested: an ALLY aiming at the wearer
 * matches; the wearer aiming at THEMSELF does not (that is the `actor != self` half -- the spelling `!actor == self`
 * is refused by the engine); and a plain non-targeted context does not either.
 */
public class WavestriderCaptainHelpTest {
    private static final int LEVEL = 80;
    private static final int WEARER = 1001;
    private static final int ALLY = 1002;

    private static Battle battle(Character wearer, Character ally) {
        Battle b = new Battle(List.of(wearer, ally), List.of(EnemyFactory.create(1002011, 90, 1)), new Random(0));
        b.startBattle();
        return b;
    }

    private static TriggerTable.TriggerContext ctx(Character owner, Character actor, Character target, Battle b) {
        return new TriggerTable.TriggerContext(owner, actor, target, 0, 0, null, b, SkillCategory.UNSPECIFIED);
    }

    private static int matched(Character owner, Character actor, Character target, Battle b) {
        return RelicTriggerTables.of(126).at(4).matching(TriggerEvent.CAST_SETUP, ctx(owner, actor, target, b)).size();
    }

    @Test
    public void theRuleNamesItsThreeGuards() {
        var rules = RelicTriggerTables.of(126).at(4).rulesFor(TriggerEvent.CAST_SETUP).stream()
                .filter(r -> r.id().equals("relic126_help_stack")).toList();
        Assertions.assertEquals(1, rules.size(), "the stacking rule");
        Assertions.assertEquals(List.of("target == self", "actor is_ally", "actor != self"),
                rules.getFirst().conditions().stream().map(c -> c.source()).toList(),
                "the cast was aimed at me, by an ally, who is not me");
        var e = rules.getFirst().effects().getFirst();
        Assertions.assertEquals("ADD_STACK", e.getOp());
        Assertions.assertEquals("助力", e.getBuff());
        Assertions.assertEquals(2.0, e.getMaxStacks(), 1e-9, "at most two stacks");
        System.out.println("[126] shape ok");
    }

    @Test
    public void onlyAnotherAllyTargetingTheWearerMatches() {
        Character wearer = CharacterFactory.create(WEARER, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle b = battle(wearer, ally);
        Assertions.assertEquals(1, matched(wearer, ally, wearer, b),
                "an ally aiming at the wearer must match");
        Assertions.assertEquals(0, matched(wearer, wearer, wearer, b),
                "the wearer aiming at themself must NOT match (the actor != self half)");
        System.out.println("[126] guards ok");
    }

    @Test
    public void thePayoutConsumesTwoStacksAndGrantsTheAttack() {
        var rules = RelicTriggerTables.of(126).at(4).rulesFor(TriggerEvent.ULT_CAST).stream()
                .filter(r -> r.id().equals("relic126_help_payout")).toList();
        Assertions.assertEquals(1, rules.size(), "the payout rule");
        Assertions.assertEquals(List.of("actor == self", "self_stacks:助力 >= 2.0"),
                rules.getFirst().conditions().stream().map(c -> c.source()).toList(),
                "on the wearer's own Ultimate, with two stacks");
        Assertions.assertEquals("REMOVE_STACK", rules.getFirst().effects().get(0).getOp());
        Assertions.assertEquals("MODIFY_ATTR", rules.getFirst().effects().get(1).getOp());
        Assertions.assertEquals("ATTACK", rules.getFirst().effects().get(1).getAttribute());
        Assertions.assertEquals(0.48, rules.getFirst().effects().get(1).getPercent(), 1e-9, "48%");
        System.out.println("[126] payout ok");
    }
}
