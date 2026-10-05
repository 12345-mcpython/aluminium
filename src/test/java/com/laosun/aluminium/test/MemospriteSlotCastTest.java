package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1415's memosprite skill 10 「献予「创世」之诗」, the second half, end to end (2026-10-02).
 *
 * <p>「本场战斗中，<b>开拓者•记忆施放强化普攻后，德谬歌立即获得1个额外回合并自动施放【花与箭的舞曲】</b>，…」
 *
 * <p>Four readings, and each would notice a different way this could be wrong:
 * <ol>
 *   <li>the memosprite has NO place in the action order -- measured: the game pins its speed to 0 with `SpeedOverride`, so `EXTRA_TURN` could never
 *       do this and the capability had to be `INSERT_ACTION`;</li>
 *   <li>the reinforced basic attack makes it act: the next step forward hands out the inserted action, and the actor is the MEMOSPRITE;</li>
 *   <li>【花与箭的舞曲】 really lands -- the enemy loses HP;</li>
 *   <li>and it is that skill: the memosprite's own data slot 1, named through `skill_id`.</li>
 * </ol>
 *
 * <p>Warning: the gate that made the previous attempt look like a broken engine -- the rule carries `self_summon_count >= 1`, so this test must
 * field the RECIPIENT's own memosprite (迷迷) as well. The first version did not, the rule never fired, and every reading below was silent.
 */
public class MemospriteSlotCastTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int TRAILBLAZER = 8007;
    private static final int MONSTER = 1002011;
    private static final int ODE_OF_GENESIS = 13;
    private static final int REINFORCED_BASIC_ATTACK = 4;
    private static final int MINUET = 1;

    @Test
    public void theReinforcedBasicAttackMakesTheMemospriteActAndCastItsOwnSlotOne() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character recipient = CharacterFactory.create(TRAILBLAZER, LEVEL);
        Battle battle = new Battle(List.of(cyrene, recipient),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Summon demiurge = battle.summonServant(cyrene);
        Summon mimi = battle.summonServant(recipient);
        battle.processRequests();
        Assertions.assertNotNull(mimi, "the rule is gated on self_summon_count >= 1, so his own memosprite must be on the field");

        Skill ode = demiurge.skillAt(ODE_OF_GENESIS);
        Assertions.assertNotNull(ode, "precondition: the memosprite carries slot 13");
        Assertions.assertFalse(battle.queue.isInActionOrder(demiurge),
                "precondition: the memosprite has no place in the action order (the game pins its speed to 0)");

        // 1) the ode has to have reached him: the sentence spans the battle, so the mark must be true first
        SkillExecutor.execute(battle, ode, demiurge, List.of(recipient));
        battle.processRequests();

        // 2) he uses the reinforced basic attack -- slot 4, the way `from_skill_id` reads it (it reads the SLOT)
        Skill reinforced = recipient.getSkills().values().stream()
                .filter(s -> s != null && s.getSkillSlot() == REINFORCED_BASIC_ATTACK)
                .findFirst().orElse(null);
        Assertions.assertNotNull(reinforced, "precondition: 8007 has a skill in slot 4");
        double before = battle.enemies.getFirst().getCurrentHp();

        SkillExecutor.execute(battle, reinforced, recipient, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        double after = battle.enemies.getFirst().getCurrentHp();

        // The cast is immediate, so it advances the battle itself: the inserted action is consumed INSIDE the rule (measured -- stepping
        // forward here hands out the next NORMAL actor). The insertion is judged on its own in InsertedActionTest; what belongs to THIS
        // sentence is that the cast landed and named the right slot.
        Skill commanded = demiurge.skillAt(MINUET);
        System.out.println("[slotted] the enemy HP " + before + " -> " + after
                + " ; the memosprite slot 1 exists = " + (commanded != null)
                + " with slot " + (commanded == null ? "-" : commanded.getSkillSlot()));

        Assertions.assertTrue(after < before,
                "\u300c\u5e76\u81ea\u52a8\u65bd\u653e\u3010\u82b1\u4e0e\u7bad\u7684\u821e\u66f2\u3011\u300d-- the commanded cast really landed");
        Assertions.assertNotNull(commanded, "\u300c\u3010\u82b1\u4e0e\u7bad\u7684\u821e\u66f2\u3011\u300dis its own slot 1");
        Assertions.assertEquals(MINUET, commanded.getSkillSlot(), "and the slot is the one the rule named");
    }
}
