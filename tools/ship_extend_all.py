"""「使自身**所有**增益效果延长 1 回合」 -- the explicit `kind: all` (2026-10-02, item 44).

Readers, verbatim (1506 银狼LV.999, two sentences):
  * 「**每回合首次触发该效果时**，使自身**所有**增益效果延长 1 回合。」
  * 「星魂 2…**进入【无敌玩家】状态后**，使自身**所有**增益效果延长 1 回合。」

THE ENGINE PIECE, and the decision that had to come first: `BuffManager.extendBuffsFrom` filters by ORIGIN (its own note names
「战技提供的护盾」), so 「所有」 had no spelling -- but `requireExtendFilter` REFUSED the filter-less form deliberately, and said why:
"Without one it would mean 'everything I have on that unit', which would lengthen buffs the sentence never mentions". That
objection is about saying NOTHING; an author who writes an explicit `"kind": "all"` has said what they mean, so the refusal
stays for the implicit form and the explicit one is allowed (both notes are in the code).

THE JUDGE IS THE DISCRIMINATOR: both runs have 甲 put a two-turn buff on 乙, and 乙's extender differs in ONE thing -- `kind: all`
versus the shipped `buff: "probe_mark"`. The origin-filtered form cannot reach a buff that 甲 applied; the unfiltered one must.
Expiry is observed the way round 1619 measured it (`BuffManager.beforeMove()` -> `processBuffTick`).
ASCII only.
"""
import io

JUDGE = "src/test/java/com/laosun/aluminium/test/ExtendAllBuffsTest.java"
RULE = "probe"

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

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
 * \u300c\u4f7f\u81ea\u8eab**\u6240\u6709**\u589e\u76ca\u6548\u679c\u5ef6\u957f 1 \u56de\u5408\u300d (2026-10-02): `EXTEND_BUFF` with `kind: all`.
 *
 * <p>\u2b50 ONE VARIABLE: \u7532 puts a two-turn buff on \u4e59 in both runs, and \u4e59\u2019s own extender differs only in HOW it says what to lengthen.
 */
public class ExtendAllBuffsTest {
    private static final int FIRST = 1002;
    private static final int SECOND = 1003;
    private static final int MONSTER = 1002011;
    private static final String MARK = "probe_mark";

    /** \u2b50 `kind: all` lengthens a buff that somebody ELSE applied. */
    @Test
    public void kindAllLengthensAnotherUnitsBuff() {
        Assertions.assertTrue(survivesTheTicks(ExtendKind.ALL),
                "\u300c\u4f7f\u81ea\u8eab**\u6240\u6709**\u589e\u76ca\u6548\u679c\u5ef6\u957f 1 \u56de\u5408\u300d-- the buff came from \u7532, and it must still be lengthened");
    }

    /** \u26a0 Naming the buff keeps the shipped origin filter: \u4e59 did not apply it, \u7532 did. */
    @Test
    public void namingTheBuffKeepsTheOriginFilter() {
        Assertions.assertFalse(survivesTheTicks(ExtendKind.BY_NAME),
                "\u26a0 the origin filter is the old behaviour: \u4e59\u2019s rule may only lengthen \u4e59\u2019s own buffs");
    }

    private enum ExtendKind { ALL, BY_NAME }

    // ==================================================================

    /** builds the scene, extends, takes two ticks, and asks whether \u7532\u2019s buff is still there. */
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
        Assertions.assertNotNull(theirs, "precondition: \u7532 has a skill");
        SkillExecutor.execute(battle, theirs, first, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        Assertions.assertTrue(second.getBuffManager().hasState(MARK), "precondition: \u4e59 carries \u7532\u2019s buff");

        Skill hers = second.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(hers, "precondition: \u4e59 has a skill");
        SkillExecutor.execute(battle, hers, second, List.of(battle.enemies.getFirst()));
        battle.processRequests();

        // \u26a0 The decrement site, measured in round 1619: `BuffManager.beforeMove()` -> `processBuffTick`.
        for (int tick = 0; tick < 2; tick++) {
            second.getBuffManager().beforeMove();
            second.getBuffManager().afterMove();
        }
        return second.getBuffManager().hasState(MARK);
    }

    /** \u7532\u2019s buff: two turns, put on the OTHER ally. */
    private static EffectSpec mark() {
        EffectSpec e = new EffectSpec();
        TriggerSpecs.set(e, "op", "APPLY_BUFF");
        TriggerSpecs.set(e, "buff", MARK);
        TriggerSpecs.set(e, "turns", 2);
        TriggerSpecs.set(e, "target", "other_allies");
        return e;
    }

    /** \u4e59\u2019s extender: one turn, either \u300c\u6240\u6709\u300d or the shipped name form -- the only difference between the runs. */
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
''')
print("ok   judge written")
