package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
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
 * An EXPLICIT removal ends a state, and the tables hear about it (2026-10-02).
 *
 * <p>Until today only a spent duration announced {@code STATE_ENDED}: 1513's own judge proves that path by letting the Aha
 * moment run out. A rule that took a state off by hand left its readers silent -- which is exactly what 1408's
 * "…最后 1 个倒计时回合开始时改为发动最后一击并结束变身" and 1412's "奇袭结束后，刻律德菈获得 #2 点充能" need.
 *
 * <p>FILE-DRIVEN on real content: 1513's Aha moment is already a state, and her file already has a reader that pays
 * [好活当赏] when it ends. This judge takes the moment off by hand instead of waiting, and asks whether that reader ran.
 */
public class RemovalAnnouncesStateEndedTest {
    private static final int OWNER = 1513;
    private static final int MONSTER = 1002011;
    private static final String MOMENT = "阿哈时刻";
    private static final String REWARD = "好活当赏";

    /** Taking the state off by hand is an end, so the reader pays. */
    @Test
    public void anExplicitRemovalAnnounces() {
        Battle battle = elation();
        Character owner = (Character) battle.allies.get(0);
        Assertions.assertTrue(owner.getBuffManager().hasState(MOMENT), "precondition: the moment is on");
        // Note: Measured: 1513's reader grants [好活当赏] as a STATE (APPLY_BUFF), not as a resource -- the first draft of
        // this judge asserted a resource and read 0 -> 0 while the engine was in fact working.
        Assertions.assertFalse(owner.getBuffManager().hasState(REWARD), "precondition: no reward yet");
        owner.getBuffManager().removeState(MOMENT);
        battle.processRequests();
        Assertions.assertFalse(owner.getBuffManager().hasState(MOMENT), "the removal really happened");
        Assertions.assertTrue(owner.getBuffManager().hasState(REWARD),
                "the reader must hear the explicit end too");
    }

    // ==================================================================

    /** Casts her elation skill, which is what puts the Aha moment on. */
    private static Battle elation() {        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Skill skill = owner.getSkills().get(SkillType.ELATION_SKILL);
        Assertions.assertNotNull(skill, "precondition: she has an elation skill");
        SkillExecutor.execute(battle, skill, owner, List.of(battle.enemies.get(0)));
        battle.processRequests();
        return battle;
    }

    private static int reward(Character owner) {
        return owner.getResources().has(REWARD) ? owner.getResources().value(REWARD) : 0;
    }
}
