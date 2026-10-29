package com.laosun.aluminium.test.engine;

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
 * 1408: 「施放终结技时，暴击伤害提高 50%，持续 3 回合」 - <b>Eidolon 1</b>'s second half, not a trace.
 *
 * <p>⚠ <b>CORRECTED (2026-10-19).</b> This class was written as a TRACE case and asserted the +50% at <b>E0</b>,
 * which is exactly what the shipped rule did: the clause had been attributed to 行迹 and therefore carried no Eidolon gate, so
 * a 0-Eidolon Phainon collected it for free. The audit read it back to `eidolons.json` (rank 140801, param #2 = 0.5 / #3 = 3)
 * and put `min_eidolon: 1` on that effect. What this file keeps is the CONTROL half - at E0 the same cast must change nothing -
 * and the positive half now lives in {@code PhainonEidolonOneTest}, at both ranks, which is the shape this clause needs.
 */
public class UltCritDamageTraceTest {
    private static final double EPS = 1e-9;
    private static final int OWNER = 1408;

    /**
     * At E0 the ultimate raises nothing: the clause is Eidolon 1's.
     */
    @Test
    public void withoutEidolonOneTheUltimateRaisesNothing() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();

        double before = owner.getAttribute(AttributeType.CRIT_ATTACK).get();
        Assertions.assertTrue(before > 0, "precondition: the panel reads (" + before + ")");

        Skill ult = owner.getSkills().get(SkillType.ULTRA);
        Assertions.assertNotNull(ult, "precondition: she has an ultimate");
        SkillExecutor.execute(battle, ult, owner, List.of(owner));
        battle.processRequests();

        Assertions.assertEquals(before, owner.getAttribute(AttributeType.CRIT_ATTACK).get(), EPS,
                "「施放终结技时，暴击伤害提高 50%」 是星魂 1 的后半句 ⇒ 0 星魂不该白拿 (before=" + before + ")");
    }

    /**
     * Note: Before the ultimate the trace has not started, so the panel is untouched.
     */
    @Test
    public void beforeTheUltimateThePanelIsUntouched() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();

        double before = owner.getAttribute(AttributeType.CRIT_ATTACK).get();
        Assertions.assertFalse(owner.getBuffManager().hasState("变身"), "no ultimate yet");
        Assertions.assertEquals(before, owner.getAttribute(AttributeType.CRIT_ATTACK).get(), EPS,
                "nothing has been cast, so nothing has changed");
    }
}
