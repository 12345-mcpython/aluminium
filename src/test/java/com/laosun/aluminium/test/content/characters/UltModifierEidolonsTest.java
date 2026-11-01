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
 * 1211's Eidolon 2: 「施放终结技后，白露的治疗量提高 15%，持续 2 回合」 - the whole clause is one modifier after the
 * Ultimate.
 *
 * <p>Note: 1214's and 1222's equivalents turned out to ALREADY exist, written under the older "Eidolon clauses are stated as
 * if activated" convention (no `min_eidolon`), which is why a scan for `min_eidolon` missed them.
 */
public class UltModifierEidolonsTest {

    private static final int LEVEL = 80;

    @Test
    public void bailuRaisesHerHealing() {
        Assertions.assertEquals(0.15, gainAfterUlt(1211, 2, AttributeType.OUTGOING_HEALING_BOOST, false), 1e-9,
                "1211 E2 「白露的治疗量提高 15%」");
    }

    /**
     * @param onAlly measure the first ally instead of the character herself
     * @return how much the attribute rose across one Ultimate
     */
    private static double gainAfterUlt(int cid, int eidolon, AttributeType attribute, boolean onAlly) {
        Character unit = CharacterFactory.create(cid, LEVEL, true, null, null, eidolon);
        Character ally = CharacterFactory.create(1001, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(unit, ally)),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        Character measured = onAlly ? ally : unit;
        double before = measured.getAttribute(attribute).get();
        battle.castImmediate(unit.getSkills().get(SkillType.ULTRA), unit, List.of(enemy));
        double after = measured.getAttribute(attribute).get();
        System.out.println("[" + cid + "] " + attribute + " " + before + " -> " + after);
        return after - before;
    }
}