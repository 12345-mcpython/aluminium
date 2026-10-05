package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.enums.SkillType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1412: "when charge reaches 6 points, automatically upgrade the character's [军功] to [爵位] and remove their control-class negative states".
 *
 * <p>THE TWO-WAY THAT PROVES "control class" AND NOT "any debuff": the ally carries Frozen (a control, per the document's
 * own glossary) AND Shock (触电) (a DOT, not a control). Promotion must remove the first and LEAVE THE SECOND -- a `DISPEL` would
 * have wiped both, which is exactly the approximation this judge exists to catch.
 *
 * <p>Note: The ally's table is rebuilt here on purpose. GAPS records that a rebuilt table drops `level_convention` and once caused a
 * factor-2 misreading -- but that trap is about DAMAGE numbers, and this judge reads STATES only.
 */
public class PeerageDispelsControlTest {
    private static final int OWNER = 1412;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String FREEZE = "冻结";
    private static final String SHOCK = "电触";
    private static final String PEERAGE = "爵位";

    /** Promotion strips the control and keeps everything else. */
    @Test
    public void promotionDispelsTheControlOnly() {
        Assertions.assertFalse(stateAfter(FREEZE, 6), "「remove their control-class negative states」 -- Frozen must be gone");
        Assertions.assertTrue(stateAfter(SHOCK, 6), "Note: a DOT is not a control, so it must survive");
    }

    /** Note: Below the threshold there is no promotion, so the control stays. */
    @Test
    public void belowSixChargeTheControlStays() {
        Assertions.assertTrue(stateAfter(FREEZE, 1), "one cast is not a promotion");
    }

    // ==================================================================

    private static boolean stateAfter(String state, int casts) {
        Character owner = CharacterFactory.create(OWNER, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));

        // Note: The ally's own table is replaced: this judge needs the ally to CARRY a control and a DOT, and no shipped file
        // gives it one. The state names are the document's (Frozen from the control list, Shocked as a non-control debuff).
        ally.setTriggerTable(new TriggerTable(ALLY, List.of(
                TriggerSpecs.rule(TriggerEvent.BATTLE_START.name(), List.of(),
                        permanent(FREEZE), permanent(SHOCK)))));

        battle.startBattle();
        battle.processRequests();
        Assertions.assertTrue(ally.getBuffManager().hasState(FREEZE), "precondition: the control landed");
        Assertions.assertTrue(ally.getBuffManager().hasState(SHOCK), "precondition: the DOT landed");

        Skill skill = owner.getSkills().get(SkillType.SKILL);
        for (int i = 0; i < casts; i++) {
            SkillExecutor.execute(battle, skill, owner, List.of(ally));
            battle.processRequests();
        }
        if (casts >= 6) {
            Assertions.assertTrue(ally.getBuffManager().hasState(PEERAGE), "precondition: six casts promote");
        }
        return ally.getBuffManager().hasState(state);
    }

    private static EffectSpec permanent(String state) {
        EffectSpec e = new EffectSpec();
        TriggerSpecs.set(e, "op", "APPLY_BUFF");
        TriggerSpecs.set(e, "buff", state);
        TriggerSpecs.set(e, "permanent", true);
        TriggerSpecs.set(e, "target", "self");
        return e;
    }
}
