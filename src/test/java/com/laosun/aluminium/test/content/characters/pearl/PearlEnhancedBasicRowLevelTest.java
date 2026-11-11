package com.laosun.aluminium.test.content.characters.pearl;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1503 Pearl (真珠)'s enhanced basic: <b>which row</b> the heal numbers come from, measured through the installed skill.
 *
 * <p>Her file used to write the heal as two constants, {@code 0.112} / {@code 224}, on the reading that "the corpus quotes
 * Lv10". That reading is wrong for a <b>basic attack</b>: the project pins the basic attack's documented numbers to the
 * <b>6th row</b> (AGENT §0.2 clause 29) - her ordinary basic's 「90% 防御力」 is row 6, and the enhanced form's 「100% 防御力，
 * 回复 8% 防御力 + 160」 is row 6 of {@code 150308} / {@code 150310} too: {@code [1.0, 0.08, 1, 160]}. The constants were
 * the Lv10 row {@code [1.4, 0.112, 1, 224]}, i.e. about 28% too high and frozen against level changes.
 *
 * <p>The heal now reads the row ({@code COMMON:1} / {@code COMMON:3}) - the enhanced form is REPLACE_SKILLed into the
 * COMMON slot, so the slot really does hold it at the moment the rule fires. These two cases drive that path end to end
 * through {@code requestSkill}: first the numbers, then the fact that 「普攻等级 +1」 now moves them.
 */
public class PearlEnhancedBasicRowLevelTest {

    private static final int PEARL = 1503;
    private static final int ELATION = 1502;
    private static final int HUNTER = 1204;
    private static final int OTHER = 1101;

    /**
     * E0: the heal is the installed skill's own row at its effective level, and that row is the one the document quotes.
     */
    @Test
    public void theInstalledFormHealsByItsOwnCurrentRow() {
        Battle battle = newBattle(0);
        Character pearl = battle.characters().get(0);
        readInstalledRowAndMeasure(battle, pearl, 6, 0.08, 160.0);
    }

    /**
     * E3's 「普攻等级 +1」 moves the row, and the heal follows it - the thing constants could never do.
     */
    @Test
    public void eidolonThreeMovesTheRowAndTheHealFollows() {
        Battle battle = newBattle(3);
        Character pearl = battle.characters().get(0);
        readInstalledRowAndMeasure(battle, pearl, 7, 0.088, 176.0);
    }

    // ==================================================================

    /**
     * Installs the enhanced basic through her Ultimate, then asserts the heal equals the installed skill's row at
     * {@code skillLevel} — and that the row is the documented one ({@code share} / {@code flat}).
     */
    private static void readInstalledRowAndMeasure(Battle battle, Character pearl, int expectedLevel,
                                                   double expectedShare, double expectedFlat) {
        Character archetype = battle.characters().get(2);      // not Elation ⇒ the slot-10 form
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, pearl, archetype, 0, 0));
        battle.processRequests();

        Skill installed = pearl.getSkills().get(SkillType.COMMON);
        Assertions.assertTrue(installed.getSkillSlot() > 1,
                "precondition: the Ultimate installed the enhanced form into COMMON (slot " + installed.getSkillSlot() + ")");
        var rows = installed.getData().getSkills();
        int level = pearl.skillLevel(installed);
        Assertions.assertEquals(expectedLevel, level,
                "the slot's effective level: the convention's 6, plus one per 普攻等级 raise");
        var row = rows.get(level - 1);
        Assertions.assertEquals(expectedShare, row.get(1), 1e-9,
                "the row in use must be the one the document quotes (8% at E0, 8.8% at E3), not the Lv10 row");
        Assertions.assertEquals(expectedFlat, row.get(3), 1e-9, "and its flat part (160 at E0, 176 at E3)");

        for (Character ally : battle.characters()) {
            ally.takeDamage(ally.getMaxHp() * 0.5);
        }
        double before = pearl.getCurrentHp();
        Enemy victim = battle.enemyUnits().getFirst();
        battle.requestSkill(installed, pearl, List.of(victim));
        battle.processRequests();
        double healed = pearl.getCurrentHp() - before;

        double base = expectedShare * pearl.getAttribute(AttributeType.DEFENCE).get() + expectedFlat;
        double expected = base * (1 + pearl.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get());
        Assertions.assertEquals(expected, healed, Math.max(1e-6, expected * 1e-9),
                "「回复等同于真珠 8.00% 防御力 + 160」 — the heal is the installed row: slot "
                        + installed.getSkillSlot() + ", level " + level + ", expected " + expected + ", healed " + healed);
    }

    private static Battle newBattle(int eidolon) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(PEARL, 80, false, null, null, eidolon),
                        CharacterFactory.create(ELATION, 80), CharacterFactory.create(HUNTER, 80),
                        CharacterFactory.create(OTHER, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}
