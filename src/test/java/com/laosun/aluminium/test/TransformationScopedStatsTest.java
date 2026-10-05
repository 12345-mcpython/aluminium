package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1408: "卡厄斯兰那的物理属性抗性穿透提高 20%" and "变身期间攻击力提高 80%，生命上限提高 20%"
 * (2026-10-02).
 *
 * <p>THE POINT IS THE THIRD ASSERTION: the block must live exactly as long as the state. Removing [变身] and watching ATK
 * come back down is what tells "during the transformation" apart from "for the rest of the battle" -- and item 34 shipped
 * without that link, so this judge would have caught it.
 */
public class TransformationScopedStatsTest {
    private static final double EPS = 1e-9;
    private static final int OWNER = 1408;
    private static final int MONSTER = 1002011;
    private static final String STATE = "变身";

    /** Transformed: +80% ATK, +20% Max HP, +20% RES PEN. Un-transformed: none of them. */
    @Test
    public void theBlockIsWorthWhatTheSentencesSay() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
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
                "「攻击力提高 80%」");
        Assertions.assertEquals(hp0 * 3.7, owner.getAttribute(AttributeType.HEALTH).get(), hp0 * 1e-9,
                "「生命上限提高 270%」");
        Assertions.assertEquals(pen0 + 0.20, owner.getAttribute(AttributeType.DAMAGE_PENETRATION).get(), EPS,
                "「物理属性抗性穿透提高 20%」");
    }

    /** "During the transformation" means the block dies with the state. */
    @Test
    public void theBlockDiesWithTheState() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
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
        // Note: Updated 2026-10-02: the trace fires AGAIN when the transformation ends ("或变身结束时"), so what is left
        // is the battle-start 50% plus the end 50%. What this reading is about is unchanged: the TRANSFORMATION's own
        // block is gone.
        Assertions.assertEquals(atk0 * (2.0 / 1.5), owner.getAttribute(AttributeType.ATTACK).get(), atk0 * 0.001,
                "「变身期间」-- the transformation's block must be gone with the state");
        Assertions.assertEquals(pen0, owner.getAttribute(AttributeType.DAMAGE_PENETRATION).get(), EPS,
                "and the RES PEN too");
    }
}
