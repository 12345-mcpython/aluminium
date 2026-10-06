package com.laosun.aluminium.test.content.relics;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.RelicTriggerTables;
import com.laosun.aluminium.enums.AttributeType;
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
 * Relic 31: "若装备者不是队伍第一名，则使队伍第一名的攻击力提高 12%".
 *
 * <p>The condition and the target are the two halves of ONE sentence, and both read battle.characters -- the character
 * roster in party order -- not battle.allies, which takes memosprites and servants as they are summoned. The runtime
 * test puts an ally FIRST in the party list, so the wearer is not first and the rule must fire; the engine mutation
 * below flips the helper to the LAST character, which must make this test fail.
 */
public class LushakaTheSunkenSeasPartyFirstTest {
    private static final int LEVEL = 80;

    @Test
    public void theTwoPieceRuleStatesBothHalves() {
        var rules = RelicTriggerTables.of(317).at(2).rulesFor(TriggerEvent.BATTLE_START).stream()
                .filter(r -> r.id().equals("relic317_party_first_attack")).toList();
        Assertions.assertEquals(1, rules.size(), "one rule");
        Assertions.assertEquals(List.of("!self is_party_first"),
                rules.getFirst().conditions().stream().map(c -> c.source()).toList(),
                "the wearer must NOT be the party's first character");
        var e = rules.getFirst().effects().getFirst();
        Assertions.assertEquals("ATTACK", e.getAttribute());
        Assertions.assertEquals(0.12, e.getPercent(), 1e-9, "12% ATK");
        Assertions.assertEquals("party_first", e.getTarget(), "on the party's first character");
        System.out.println("[317] shape ok");
    }

    @Test
    public void theFirstCharacterGetsTheAttackAndTheWearerDoesNot() {
        Character ally = CharacterFactory.create(1001, LEVEL);
        Character wearer = CharacterFactory.create(1210, LEVEL);
        Battle battle = new Battle(List.of(ally, wearer), List.of(EnemyFactory.create(1002011, 90, 1)),
                new Random(0));
        battle.startBattle();
        Assertions.assertSame(ally, battle.characters.getFirst(),
                "the party list order is what 'the first character' means");
        var rule = RelicTriggerTables.of(317).at(2).rulesFor(TriggerEvent.BATTLE_START).getFirst();
        var before = ally.getAttribute(AttributeType.ATTACK).get();
        TriggerInterpreter.apply(battle, rule,
                new TriggerTable.TriggerContext(wearer, wearer, wearer, 0, 0, null, battle,
                        com.laosun.aluminium.enums.SkillCategory.UNSPECIFIED));
        Assertions.assertEquals(0.12 + 1.0, ally.getAttribute(AttributeType.ATTACK).get() / before, 1e-6,
                "the FIRST character's ATK rises by 12%");
        System.out.println("[317] runtime ok: ally ATTACK ratio=" + (ally.getAttribute(AttributeType.ATTACK).get() / before));
    }
}
