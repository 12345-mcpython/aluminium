package com.laosun.aluminium.test;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
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
 * `MODIFY_RULE{effect_percent_from_resource}` sizes an amendment from a resource (2026-10-02; reader: 114151"每消耗 1% 溢出值…").
 */
public class AmendmentFromResourceTest {
    private static final int LEVEL = 80;
    private static final int SPARE = 1002;
    private static final int MONSTER = 1002011;
    private static final String SIZED_BY = "定大小的资源";
    private static final String TARGET = "a_rule_with_a_percent";

    private static TriggerTable table() {
        EffectSpec target = new EffectSpec();
        TriggerSpecs.set(target, "op", "MODIFY_ATTR");
        TriggerSpecs.set(target, "attribute", "ATTACK");
        TriggerSpecs.set(target, "percent", 0.5);
        TriggerSpecs.set(target, "permanent", Boolean.TRUE);
        TriggerSpecs.set(target, "target", "self");
        TriggerSpec targetRule = TriggerSpecs.rule("TURN_START", List.of(), target);
        // TriggerSpec is a POJO with no setter: the id goes in through the same reflection helper the rest of the tests use.
        TriggerSpecs.set(targetRule, "id", TARGET);

        EffectSpec amend = new EffectSpec();
        TriggerSpecs.set(amend, "op", "MODIFY_RULE");
        TriggerSpecs.set(amend, "rule", TARGET);
        TriggerSpecs.set(amend, "effectPercentFromResource", SIZED_BY);
        // BATTLE_START first (the amendment is filed), then the turn that fires the amended rule.
        return new TriggerTable(SPARE, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(), amend),
                targetRule));
    }

    @Test
    public void theAmendmentIsAsBigAsTheResource() {
        double sized = gained(8000);
        double own = gained(0);
        System.out.println("[amend_resource] the amended rule granted " + sized
                + " with 8000 basis points ; " + own + " with none");
        // `MODIFY_ATTR` scales the ATTRIBUTE, so the reading is `percent x base`: 0.5 alone, and 0.5 + 0.8 with 8000 basis points so the ratio is exactly 13/5.
        Assertions.assertEquals(13.0 / 5.0, sized / own, 1e-6,
                "0.5 of its own plus 8000 basis points = 1.3, and 1.3 / 0.5 = 13/5");
    }

    private static double gained(int basisPoints) {
        Character spare = CharacterFactory.create(SPARE, LEVEL);
        Battle battle = new Battle(List.of(spare),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        spare = battle.characters.getFirst();
        spare.getResources().register(SIZED_BY, 99999, 0);
        spare.getResources().gain(SIZED_BY, basisPoints);
        spare.setTriggerTable(table());
        battle.fireTriggers(TriggerEvent.BATTLE_START, spare, null, 0, 0);
        battle.processRequests();
        double before = spare.getAttribute(AttributeType.ATTACK).get();
        battle.fireTriggers(TriggerEvent.TURN_START, spare, null, 0, 0);
        battle.processRequests();
        return battle.characters.getFirst().getAttribute(AttributeType.ATTACK).get() - before;
    }
}
