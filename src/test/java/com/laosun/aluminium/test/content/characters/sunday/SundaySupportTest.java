package com.laosun.aluminium.test.content.characters.sunday;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1313 星期日's support half, as far as the document's sentences can be written today: the talent's CRIT Rate, the two
 * traces that are exact, and 星魂 4's energy.
 *
 * <p>Written 2026-11-06 as part of the section-by-section audit: before it, the file carried only the Skill, the Ultimate
 * and the Eidolon levels, and its four `_gaps` entries said "not read, not written".
 */
public class SundaySupportTest {
    private static final int SUNDAY = 1313;
    private static final int ALLY = 1204;          // a party member with no summon of its own
    private static final int LEVEL = 80;
    private static final double EPS = 1e-9;

    /** 天赋「施放战技时，使目标的暴击率提高 20%，持续 3 回合」: the ally gains it, he does not. */
    @Test
    public void theTalentRaisesTheTargetsCritRate() {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(sunday, ally), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        double before = ally.getAttribute(AttributeType.CRIT_CHANCE).get();

        battle.castImmediate(sunday.getSkills().get(SkillType.SKILL), sunday, List.of(ally));
        battle.processRequests();

        Assertions.assertEquals(before + 0.2, ally.getAttribute(AttributeType.CRIT_CHANCE).get(), 1e-6,
                "「使目标的暴击率提高 20%（Lv10）」");
        Assertions.assertEquals(0.05, sunday.getAttribute(AttributeType.CRIT_CHANCE).get(), 1e-6,
                "…and not on himself: the sentence says 目标");
    }

    /** 行迹 崇高拂尘「战斗开始时，星期日恢复 25 点能量」. */
    @Test
    public void theWhiskPaysTwentyFiveEnergyAtBattleStart() {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL);
        Battle battle = new Battle(List.of(sunday), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        Assertions.assertEquals(25.0, sunday.getCurrentEnergy(), EPS,
                "「战斗开始时，星期日恢复 25 点能量」 (no other rule pays him anything at start)");
    }

    /** 行迹 掌中安港「施放战技时，解除目标的 1 个负面效果」. */
    @Test
    public void theHarbourDispelsOneDebuff() {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(sunday, ally), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        // Real debuffs: a bare StateBuff is not counted by debuffCount (it carries no debuff role), so the first version
        // of this test read 0 before and 0 after and proved nothing.
        ally.getBuffManager().addBuff(com.laosun.aluminium.buff.StatModifierBuff.percentDebuff(
                AttributeType.SPEED, -0.2, 3));
        ally.getBuffManager().addBuff(com.laosun.aluminium.buff.StatModifierBuff.percentDebuff(
                AttributeType.DEFENCE, -0.2, 3));
        int before = ally.getBuffManager().debuffCount();
        Assertions.assertEquals(2, before, "precondition: two debuffs to take one from");

        battle.castImmediate(sunday.getSkills().get(SkillType.SKILL), sunday, List.of(ally));
        battle.processRequests();

        Assertions.assertEquals(before - 1, ally.getBuffManager().debuffCount(),
                "「解除目标的 1 个负面效果」 — one, not all");
    }

    /** 星魂 4「回合开始时，恢复 8 点能量」: his own turns only. */
    @Test
    public void theFourthEidolonPaysEnergyOnHisTurn() {
        Assertions.assertEquals(8.0, energyAfterOneTurn(4), EPS, "星魂 4 pays 8 at his turn start");
        Assertions.assertEquals(0.0, energyAfterOneTurn(3), EPS, "and nothing before it");
    }

    private static double energyAfterOneTurn(int eidolon) {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL, false, null, null, eidolon);
        Battle battle = new Battle(List.of(sunday), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        // The trace pays 25 at start; the reading is the DIFFERENCE a turn makes. Announced directly: the action order
        // reaching his turn is its own job, and driving a whole round here would mix its outcome into the measurement.
        double before = sunday.getCurrentEnergy();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                com.laosun.aluminium.enums.TriggerEvent.TURN_START, sunday, null, 0, 0));
        battle.processRequests();
        return sunday.getCurrentEnergy() - before;
    }
}
