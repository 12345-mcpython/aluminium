package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * "extends all of your own positive effects by 1 turn" (2026-10-02): `EXTEND_BUFF` with `kind: all`.
 *
 * <p>ONE VARIABLE: A (甲) puts a two-turn buff on B (乙) in both runs, and B's own extender differs only in HOW it says what to lengthen.
 */
public class ExtendAllBuffsTest {
    private static final int FIRST = 1002;
    private static final int SECOND = 1003;
    private static final int MONSTER = 1002011;
    private static final String MARK = "probe_mark";

    /** `kind: all` lengthens a buff that somebody ELSE applied. */
    @Test
    public void kindAllLengthensAnotherUnitsBuff() {
        Assertions.assertTrue(survivesTheTicks(ExtendKind.ALL),
                "「使自身**所有**增益效果延长 1 回合」-- the buff came from 甲, and it must still be lengthened");
    }

    /** Note: Naming the buff keeps the shipped origin filter: B (乙) did not apply it, A (甲) did. */
    @Test
    public void namingTheBuffKeepsTheOriginFilter() {
        Assertions.assertFalse(survivesTheTicks(ExtendKind.BY_NAME),
                "⚠ the origin filter is the old behaviour: 乙’s rule may only lengthen 乙’s own buffs");
    }

    private enum ExtendKind { ALL, BY_NAME }

    // ==================================================================

    /** builds the scene, extends, takes two ticks, and asks whether A (甲)'s buff is still there. */
    private static boolean survivesTheTicks(ExtendKind kind) {
        Character first = CharacterFactory.create(FIRST, 80);
        first.setTriggerTable(new TriggerTable(FIRST, List.of(
                TriggerSpecs.rule(TriggerEvent.SKILL_CAST.name(), List.of("actor == self"), mark()))));
        Character second = CharacterFactory.create(SECOND, 80);
        second.setTriggerTable(new TriggerTable(SECOND, List.of(
                TriggerSpecs.rule(TriggerEvent.SKILL_CAST.name(), List.of("actor == self"), extend(kind)))));

        Battle battle = new Battle(List.of(first, second),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Skill theirs = first.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(theirs, "precondition: 甲 has a skill");
        SkillExecutor.execute(battle, theirs, first, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        Assertions.assertTrue(second.getBuffManager().hasState(MARK), "precondition: 乙 carries 甲’s buff");

        Skill hers = second.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(hers, "precondition: 乙 has a skill");
        SkillExecutor.execute(battle, hers, second, List.of(battle.enemies.getFirst()));
        battle.processRequests();

        // Note: The decrement site, measured in round 1619: `BuffManager.beforeMove()` -> `processBuffTick`.
        for (int tick = 0; tick < 2; tick++) {
            second.getBuffManager().beforeMove();
            second.getBuffManager().afterMove();
        }
        return second.getBuffManager().hasState(MARK);
    }

    /** A (甲)'s buff: two turns, put on the OTHER ally. */
    private static EffectSpec mark() {
        EffectSpec e = new EffectSpec();
        TriggerSpecs.set(e, "op", "APPLY_BUFF");
        TriggerSpecs.set(e, "buff", MARK);
        TriggerSpecs.set(e, "turns", 2);
        TriggerSpecs.set(e, "target", "other_allies");
        return e;
    }

    /** B (乙)'s extender: one turn, either "all" (所有) or the shipped name form -- the only difference between the runs. */
    private static EffectSpec extend(ExtendKind kind) {
        EffectSpec e = new EffectSpec();
        TriggerSpecs.set(e, "op", "EXTEND_BUFF");
        if (kind == ExtendKind.ALL) {
            TriggerSpecs.set(e, "kind", "all");
        } else {
            TriggerSpecs.set(e, "buff", MARK);
        }
        TriggerSpecs.set(e, "turns", 1);
        TriggerSpecs.set(e, "target", "self");
        return e;
    }
}
