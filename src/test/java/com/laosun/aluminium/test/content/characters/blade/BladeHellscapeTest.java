package com.laosun.aluminium.test.content.characters.blade;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Signal;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.SkillCastability;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1205 刃's 【地狱变】 state, clause by clause: 「处于【地狱变】状态时无法施放战技，同时使自身造成的伤害提高 40%，
 * 普攻【支离剑】强化为【无间剑树】」.
 *
 * <p><b>Why a criterion.</b> All three clauses hang off one state and each has its own wrong-but-plausible spelling:
 * the ban is not "the Skill cannot be selected" but {@code blocks: skill} read by the one castability gate; the +40% must
 * be a <b>follow-the-state</b> modifier, not an equal-duration buff, or it survives the state; and the swap must land on
 * the Basic slot (data slot 8), not on the Skill slot. The 3-turn expire is the engine's own timer and is not measured
 * here - what is measured is that the modifier is dropped by the <b>gate</b> the moment the state is gone.
 */
public class BladeHellscapeTest {
    private static final int BLADE = 1205;
    private static final int LEVEL = 80;
    private static final String STATE = "地狱变";

    /**
     * 「无法施放战技」: the Skill slot is refused while the state holds, and only that slot.
     */
    @Test
    public void theStateForbidsTheSkillSlotAlone() {
        Fixture f = new Fixture();
        Assertions.assertTrue(SkillCastability.allows(f.skill(), f.blade, f.battle),
                "before the state the Skill is castable");

        f.enterHellscape();

        Assertions.assertFalse(SkillCastability.allows(f.skill(), f.blade, f.battle),
                "「处于【地狱变】状态时无法施放战技」");
        Assertions.assertTrue(SkillCastability.allows(f.blade.getSkills().get(SkillType.COMMON), f.blade, f.battle),
                "only the Skill is banned - the Basic still works");
    }

    /**
     * 「使自身造成的伤害提高 40%」 - and it follows the state, not a timer of its own.
     */
    @Test
    public void theDamageBoostLastsOnlyAsLongAsTheState() {
        Fixture f = new Fixture();
        double before = f.boost();
        f.enterHellscape();
        Assertions.assertEquals(0.4, f.boost() - before, 1e-9, "「伤害提高 40%」");

        f.blade.getBuffManager().removeState(STATE);
        f.battle.setCurrentMove(new Signal(f.blade));
        f.battle.beforeMove();

        Assertions.assertEquals(before, f.boost(), 1e-9,
                "the modifier is gated on the state (`while_state`), so the next turn boundary drops it");
    }

    /**
     * 「普攻【支离剑】强化为【无间剑树】」: the Basic slot is swapped to data slot 8.
     */
    @Test
    public void theStateSwapsTheBasicForTheEnhancedRow() {
        Fixture f = new Fixture();
        Assertions.assertEquals(1, f.blade.getSkills().get(SkillType.COMMON).getSkillSlot(),
                "precondition: the ordinary Basic");

        f.enterHellscape();

        Assertions.assertEquals(8, f.blade.getSkills().get(SkillType.COMMON).getSkillSlot(),
                "the Basic slot now carries 【无间剑树】 (skills.json slot 8)");
    }

    // ==================================================================

    private static final class Fixture {
        private final Character blade = CharacterFactory.create(BLADE, LEVEL);
        private final Enemy enemy = BattleFixture.dummy();
        private final Battle battle = new Battle(List.of(blade), List.of(enemy), fixed());

        Fixture() {
            battle.startBattle();
        }

        com.laosun.aluminium.skill.Skill skill() {
            return blade.getSkills().get(SkillType.SKILL);
        }

        void enterHellscape() {
            battle.castImmediate(skill(), blade, List.of(enemy));
            battle.processRequests();
        }

        double boost() {
            return blade.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        }
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(1.0);
    }
}
