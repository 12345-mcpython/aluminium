package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Two Eidolons that are one modifier with two numbers: 8005's energy regeneration and 1504's attack after the Ultimate.
 */
public class TwoNumberEidolonsTest {

    private static final int LEVEL = 80;

    @Test
    public void harmonyTrailblazerRegeneratesEnergyFaster() {
        Assertions.assertEquals(0.25, battleStartGain(8005, AttributeType.ENERGY_REGENERATION_RATE), 1e-9,
                "8005 E2 「战斗开始时，能量恢复效率提高 25%」");
    }

    @Test
    public void theOtherCharacterGainsAttackAfterTheUltimate() {
        Character unit = CharacterFactory.create(1504, LEVEL, true, null, null, 4);
        double base = unit.getAttribute(AttributeType.ATTACK).baseValue();
        // Attack is a FLAT attribute, so "increase by 40%" is 40% of the BASE, not of the panel.
        Assertions.assertEquals(0.4 * base, ultGain(1504, 4, AttributeType.ATTACK), 1e-6,
                "1504 E4 「施放终结技时，攻击力提高 40%」");
    }

    private static double battleStartGain(int cid, AttributeType attribute) {
        double bare = measure(cid, 0, attribute);
        double with = measure(cid, 2, attribute);
        return with - bare;
    }

    private static double measure(int cid, int eidolon, AttributeType attribute) {
        Character unit = CharacterFactory.create(cid, LEVEL, true, null, null, eidolon);
        Battle battle = new Battle(new ArrayList<>(List.of(unit, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return unit.getAttribute(attribute).get();
    }

    private static double ultGain(int cid, int eidolon, AttributeType attribute) {
        Character unit = CharacterFactory.create(cid, LEVEL, true, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        // 1002 has no TALENT row, and 1504's kit commands an ally's talent - so pair it with someone who has one.
        Character ally = CharacterFactory.create(1101, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(unit, ally)),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        double base = unit.getAttribute(attribute).get();
        // Fire the event rather than casting: her kit's other Ultimate clauses command an ALLY's talent, and that path
        // needs a skill_effects row this judge is not about.
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                com.laosun.aluminium.enums.TriggerEvent.ULT_CAST, unit, unit, 0, 0));
        System.out.println("[1504] attack " + base + " -> " + unit.getAttribute(attribute).get());
        return unit.getAttribute(attribute).get() - base;
    }
}