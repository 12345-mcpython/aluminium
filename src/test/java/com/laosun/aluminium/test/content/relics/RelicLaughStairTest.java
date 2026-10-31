package com.laosun.aluminium.test.content.relics;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.BattleResources;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.RelicFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Relic 129 (闪耀功勋的魔法少女) 4-piece, second sentence: 「我方每累计获得 <b>5 点</b>笑点，造成的欢愉伤害额外无视 <b>1%</b> 防御，
 * 最多 <b>10 层</b>」.
 *
 * <p><b>What made this possible.</b> 笑点 used to be declared only by 1502's own file, so a party without 1502 had no such counter
 * and the stair refused to fire (measured: {@code a weight step reads "笑点", which is neither an attribute … nor this party's
 * counters}). It is now declared once in {@code game/battle_resources.json} - battle-level, initial 0, no cap (the recorder
 * confirmed 「笑点是全局的、无上限」) - so <b>every</b> party has it, and this test builds one WITHOUT 1502 on purpose.
 *
 * <p>Only the shipped numbers are asserted, not a damage value: 笑点 itself lifts Elation damage (the laugh factor), so every
 * behavioural reading of "one more layer" is entangled with that lift (measured: a twin battle's with/without ratio swung from
 * 1.06 to 1.75 merely by gaining four points).
 */
public class RelicLaughStairTest {

    private static final int LEVEL = 80;
    private static final int RELIC_LEVEL = 15;

    /**
     * The counter exists for a party that declares nothing itself.
     */
    @Test
    public void theCounterBelongsToTheBattleNotToOneCharacter() {
        Character wearer = CharacterFactory.create(1002, LEVEL, true, null, RelicFactory.suit(129, 4, RELIC_LEVEL));
        Character plain = CharacterFactory.create(1003, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(wearer, plain)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
        battle.startBattle();

        Assertions.assertFalse(BattleResources.declared().isEmpty(), "the pack declares at least one battle counter");
        Assertions.assertNotNull(battle.partyResource("笑点"),
                "every battle has 笑点, whether or not a character declares it");
        Assertions.assertEquals(0, battle.partyResourceValue("笑点"), "and it starts empty");
    }

    /**
     * The shipped rule states all three numbers, and reads the battle's counter.
     */
    @Test
    public void theShippedRuleCarriesTheNumbers() {
        Character wearer = CharacterFactory.create(1002, LEVEL, true, null, RelicFactory.suit(129, 4, RELIC_LEVEL));
        Battle battle = new Battle(new ArrayList<>(List.of(wearer)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
        battle.startBattle();

        var stairs = wearer.getTriggerTable().rulesFor(TriggerEvent.DEALING_DAMAGE).stream()
                .filter(rule -> rule.id().equals("relic129_every_five_laughs_adds_one_percent")).toList();
        Assertions.assertEquals(1, stairs.size(), "one shipped rule");
        var effect = stairs.getFirst().effects().getFirst();
        Assertions.assertEquals(0.01, effect.getPercent(), 1e-9, "1% per layer");
        Assertions.assertEquals("笑点:5", effect.getPerSteps(), "five points per layer");
        Assertions.assertEquals(10, effect.getPerStepsCap(), "「最多 10 层」");
        Assertions.assertEquals("ELATION", effect.getDamageType(), "scoped to Elation damage");
        Assertions.assertEquals(Boolean.TRUE, effect.getInstance(), "as an instance modifier");
    }
}
