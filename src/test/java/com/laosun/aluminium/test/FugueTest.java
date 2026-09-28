package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1225 Fugue, from her own file (2026-09-29, round 217): the Foxian Prayer mark and the four things it drives.
 */
public class FugueTest {
    private static final int FUGUE = 1225;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** \u26a0 The Skill marks the ally, puts 【炽灼】 on HER, and hands the ally 30% Break Effect -- a share, so it is compared with her own base. */
    @Test
    public void theSkillMarksTheAllyAndHandsOverBreakEffect() {
        Character fugue = CharacterFactory.create(FUGUE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(fugue, ally), List.of(enemy), fixed());
        battle.startBattle();
        double before = ally.getAttribute(AttributeType.BREAKING_EFFECT).get();

        battle.castImmediate(fugue.getSkills().get(SkillType.SKILL), fugue, List.of(ally));

        Assertions.assertTrue(ally.getBuffManager().hasState("\u72d0\u7948"),
                "\u300c\u4f7f\u6307\u5b9a\u6211\u65b9\u5355\u4f53\u83b7\u5f97\u3010\u72d0\u7948\u3011\u300d");
        Assertions.assertTrue(fugue.getBuffManager().hasState("\u70bd\u707c"),
                "\u300c\u5e76\u4f7f\u81ea\u8eab\u8fdb\u5165\u3010\u70bd\u707c\u3011\u72b6\u6001\u300d -- on HERSELF");
        Assertions.assertEquals(0.3, ally.getAttribute(AttributeType.BREAKING_EFFECT).get() - before, 1e-6,
                "\u300c\u6301\u6709\u3010\u72d0\u7948\u3011\u7684\u6211\u65b9\u76ee\u6807\uff0c\u51fb\u7834\u7279\u653b\u63d0\u9ad830%\u300d");
    }

    /** \u26a0 The reaction: the mark is on the ATTACKER, and the shredded enemy is the one attacked. */
    @Test
    public void theDefenceShredNeedsAMarkedAttacker() {
        double unmarked = defenceLoss(false);
        double marked = defenceLoss(true);

        Assertions.assertEquals(0.0, unmarked, 1e-9,
                "\u300c\u6301\u6709\u3010\u72d0\u7948\u3011\u7684\u6211\u65b9\u76ee\u6807\u6bcf\u6b21\u65bd\u653e\u653b\u51fb\u65f6\u300d -- unmarked, nothing");
        Assertions.assertEquals(0.18, marked, 1e-6,
                "\u300c\u4f7f\u53d7\u5230\u653b\u51fb\u7684\u654c\u65b9\u76ee\u6807\u9632\u5fa1\u529b\u964d\u4f4e18%\u300d of the enemy's own defence");
    }

    /** \u26a0 The technique advances her by 40%: round 216's recipe says the remaining wait becomes 0.6 of what it was. */
    @Test
    public void theTechniqueLeavesSixtyPercentOfTheWait() {
        Character fugue = CharacterFactory.create(FUGUE, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(fugue), List.of(enemy), fixed());
        battle.markTechniqueUsed(fugue);
        battle.startBattle();
        double after = remaining(battle, fugue);
        // The battle start already applied the advance; re-derive what it was by inverting the documented fraction.
        Assertions.assertTrue(after >= 0, "the queue must hold her: " + after);

        Character plain = CharacterFactory.create(FUGUE, LEVEL);
        Enemy plainEnemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle plainBattle = new Battle(List.of(plain), List.of(plainEnemy), fixed());
        plainBattle.startBattle();
        double untouched = remaining(plainBattle, plain);

        Assertions.assertEquals(0.6, after / untouched, 0.02,
                "\u300c\u8fdb\u5165\u6218\u6597\u540e\u5fd8\u5f52\u4eba\u884c\u52a8\u63d0\u524d40%\u300d: " + untouched + " -> " + after);
    }

    /** The enemy's defence drop after one attack by an ally, marked or not. */
    private static double defenceLoss(boolean mark) {
        Character fugue = CharacterFactory.create(FUGUE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(fugue, ally), List.of(enemy), fixed());
        battle.startBattle();
        double before = enemy.getAttribute(AttributeType.DEFENCE).get();
        if (mark) {
            battle.castImmediate(fugue.getSkills().get(SkillType.SKILL), fugue, List.of(ally));
            before = enemy.getAttribute(AttributeType.DEFENCE).get();
        }
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0);
        double after = enemy.getAttribute(AttributeType.DEFENCE).get();
        return (before - after) / before;
    }

    /** The unit's remaining wait, read off the queue's public snapshot (round 216's recipe). */
    private static double remaining(Battle battle, Character unit) {
        for (Signal signal : battle.queue.snapshot()) {
            if (signal.getCanHit() == unit) {
                return battle.queue.getTimeRemaining(signal);
            }
        }
        throw new IllegalStateException("the unit is not in the action queue");
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
    }
}
