package com.laosun.aluminium.test.content.relics;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.RelicTriggerTables;
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
 * Relic 31's CONDITION half (2026-09-30): "若装备者不是队伍第一名".
 *
 * <p>Note: The sibling judge (Relic31PartyFirstTest) applies the rule with TriggerInterpreter.apply, which executes the
 * effects but does NOT evaluate the conditions -- so it is blind to this half, and its engine mutation went red only
 * because the effect's TARGET (`party_first`) is resolved inside apply. matching() evaluates the conditions, and both
 * directions are asserted here: not first =&gt; the rule matches, first =&gt; it must not.
 */
public class LushakaTheSunkenSeasConditionTest {
    private static final int LEVEL = 80;

    private static TriggerTable.TriggerContext ctx(Character wearer, Battle battle) {
        return new TriggerTable.TriggerContext(wearer, wearer, wearer, 0, 0, null, battle,
                com.laosun.aluminium.enums.SkillCategory.UNSPECIFIED);
    }

    @Test
    public void theWearerMustNotBeThePartysFirstCharacter() {
        // an ally FIRST, the wearer second -- "not the first character" holds, so the rule matches
        Character ally = CharacterFactory.create(1001, LEVEL);
        Character wearer = CharacterFactory.create(1210, LEVEL);
        Battle battle = new Battle(List.of(ally, wearer), List.of(EnemyFactory.create(1002011, 90, 1)),
                new Random(0));
        battle.startBattle();
        Assertions.assertSame(ally, battle.characters.getFirst(), "the party list order is what matters");
        Assertions.assertEquals(1,
                RelicTriggerTables.of(317).at(2).matching(TriggerEvent.BATTLE_START, ctx(wearer, battle)).size(),
                "wearer is NOT first => the rule matches");

        // the wearer FIRST -- the condition is false, so nothing may match
        Character first = CharacterFactory.create(1210, LEVEL);
        Character second = CharacterFactory.create(1001, LEVEL);
        Battle other = new Battle(List.of(first, second), List.of(EnemyFactory.create(1002011, 90, 1)),
                new Random(0));
        other.startBattle();
        Assertions.assertSame(first, other.characters.getFirst(), "the wearer leads the party here");
        Assertions.assertEquals(0,
                RelicTriggerTables.of(317).at(2).matching(TriggerEvent.BATTLE_START, ctx(first, other)).size(),
                "wearer IS first => nothing may match");
        System.out.println("[317-cond] both directions ok");
    }
}
