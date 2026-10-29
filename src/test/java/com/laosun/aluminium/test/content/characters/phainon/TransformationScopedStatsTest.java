package com.laosun.aluminium.test.content.characters.phainon;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1408: "变身期间攻击力提高 80%，生命上限提高 270%" - and, at 0 Eidolons, <b>no</b> RES PEN.
 *
 * <p>THE POINT IS THE THIRD ASSERTION OF THE SECOND CASE: the block must live exactly as long as the state. Removing
 * [变身] and watching ATK come back down is what tells "during the transformation" apart from "for the rest of the battle"
 * -- and item 34 shipped without that link, so this judge would have caught it.
 *
 * <p>⚠ <b>CORRECTED (2026-10-19).</b> This class used to assert 「卡厄斯兰那的物理属性抗性穿透提高 20%」 as part of the same
 * block, at <b>0 Eidolons</b> - so the case pinned the defect as the expectation. That sentence is <b>Eidolon 2</b>'s first
 * half (see {@code PhainonEidolonTwoTest}, which pins both ranks); a 0-Eidolon Phainon has 0. What is unchanged is what
 * this case was written for: the ATK / Max HP block, and the fact that it dies with the state.
 */
public class TransformationScopedStatsTest {
    private static final double EPS = 1e-9;
    private static final int OWNER = 1408;
    private static final String STATE = "变身";

    /**
     * Transformed: +80% ATK, +270% Max HP, and no RES PEN to speak of at E0.
     */
    @Test
    public void theBlockIsWorthWhatTheSentencesSay() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();

        double atk0 = owner.getAttribute(AttributeType.ATTACK).get();
        double hp0 = owner.getAttribute(AttributeType.HEALTH).get();
        double pen0 = owner.getAttribute(AttributeType.DAMAGE_PENETRATION).get();

        Skill ult = owner.getSkills().get(SkillType.ULTRA);
        SkillExecutor.execute(battle, ult, owner, List.of(owner));
        battle.processRequests();
        Assertions.assertTrue(owner.getBuffManager().hasState(STATE), "precondition: the transformation is on");

        Assertions.assertEquals(atk0 / 1.5 * 2.3, owner.getAttribute(AttributeType.ATTACK).get(), atk0 * 1e-9,
                "\"ATK raised by 80%\" (「攻击力提高 80%」)");
        Assertions.assertEquals(hp0 * 3.7, owner.getAttribute(AttributeType.HEALTH).get(), hp0 * 1e-9,
                "\"Max HP raised by 270%\" (「生命上限提高 270%」)");
        // Note: CORRECTED (2026-10-19) -- this read `pen0 + 0.20` and thereby PINNED the bug: the 20% is Eidolon 2's
        // clause, recorded against a trace and therefore ungated, so E0 collected it. The reading at E2 is
        // PhainonEidolonTwoTest's job; here the rank is 0, so the answer is "none".
        Assertions.assertEquals(pen0, owner.getAttribute(AttributeType.DAMAGE_PENETRATION).get(), EPS,
                "0 星魂没有抗性穿透 —— 那一句是星魂 2 的（判据见 PhainonEidolonTwoTest）");
    }

    /**
     * "During the transformation" means the block dies with the state.
     */
    @Test
    public void theBlockDiesWithTheState() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();

        double atk0 = owner.getAttribute(AttributeType.ATTACK).get();
        double pen0 = owner.getAttribute(AttributeType.DAMAGE_PENETRATION).get();

        Skill ult = owner.getSkills().get(SkillType.ULTRA);
        SkillExecutor.execute(battle, ult, owner, List.of(owner));
        battle.processRequests();
        Assertions.assertTrue(owner.getAttribute(AttributeType.ATTACK).get() > atk0, "precondition: the block is on");

        owner.getBuffManager().removeState(STATE);
        battle.processRequests();
        Assertions.assertFalse(owner.getBuffManager().hasState(STATE), "the transformation is off");
        // Note: Updated: the trace fires AGAIN when the transformation ends ("或变身结束时"), so what is left
        // is the battle-start 50% plus the end 50%. What this reading is about is unchanged: the TRANSFORMATION's own
        // block is gone.
        Assertions.assertEquals(atk0 * (2.0 / 1.5), owner.getAttribute(AttributeType.ATTACK).get(), atk0 * 0.001,
                "「变身期间」 (during the transformation)-- the transformation's block must be gone with the state");
        Assertions.assertEquals(pen0, owner.getAttribute(AttributeType.DAMAGE_PENETRATION).get(), EPS,
                "and the RES PEN too");
    }
}
