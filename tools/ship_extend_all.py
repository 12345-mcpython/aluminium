"""「使自身**所有**增益效果延长 1 回合」 -- the filter-less EXTEND_BUFF (2026-10-02, item 44).

Readers, verbatim (1506 银狼LV.999, two sentences):
  * 「**每回合首次触发该效果时**，使自身**所有**增益效果延长 1 回合。」
  * 「星魂 2…**进入【无敌玩家】状态后**，使自身**所有**增益效果延长 1 回合。」

WHY IT NEEDED AN ENGINE PIECE (measured, not assumed): `BuffManager.extendBuffsFrom` filters by ORIGIN --
`if (buff.isPermanent() || buff.getSource() != source || !isNamed(...)) continue;` -- and its own note names the shape it was built
for (「战技提供的护盾」). So 「所有」 (whoever applied it) had no spelling. Stating NEITHER `buff` NOR `attribute` used to be a
silent no-op (`extendBuffsFrom` returns 0 when both are null), and **all eight** shipped EXTEND_BUFF effects name one or the other
(measured), so that spelling was free to take the unfiltered meaning.

THE JUDGE IS THE DISCRIMINATOR ITSELF: the two runs differ in ONE thing -- which spelling the extender uses. A buff applied by
ANOTHER unit (`甲` -> `乙`) is lengthened only by the new, filter-less form; the origin-filtered form cannot touch it.
Expiry is observed the way round 1619 measured it: `BuffManager.beforeMove()` -> `processBuffTick` is the decrement.
ASCII only.
"""
import io

JUDGE = "src/test/java/com/laosun/aluminium/test/ExtendAllBuffsTest.java"

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
 * \u300c\u4f7f\u81ea\u8eab**\u6240\u6709**\u589e\u76ca\u6548\u679c\u5ef6\u957f 1 \u56de\u5408\u300d (2026-10-02): the filter-less `EXTEND_BUFF`.
 *
 * <p>\u2b50 ONE VARIABLE: both runs have \u7532 put a two-turn buff on \u4e59; the extender on \u4e59's own table differs only in HOW it names what
 * to lengthen -- `buff: "probe_mark"` (the shipped, origin-filtered form) versus naming nothing (the new, unfiltered form).
 */
public class ExtendAllBuffsTest {
    private static final int FIRST = 1002;
    private static final int SECOND = 1003;
    private static final int MONSTER = 1002011;
    private static final String MARK = "probe_mark";

    /** \u2b50 The unfiltered form lengthens a buff that somebody ELSE applied. */
    @Test
    public void theUnfilteredFormLengthensAnotherUnitsBuff() {
        Assertions.assertTrue(survivesTheExtraTick(null),
                "\u300c\u4f7f\u81ea\u8eab**\u6240\u6709**\u589e\u76ca\u6548\u679c\u5ef6\u957f 1 \u56de\u5408\u300d-- the buff came from \u7532, and it must still be lengthened");
    }

    /** \u26a0 The shipped, origin-filtered form cannot: \u4e59 did not apply that buff, \u7532 did. */
    @Test
    public void theOriginFilteredFormCannot() {
        Assertions.assertFalse(survivesTheExtraTick(MARK),
                "\u26a0 the origin filter is the old behaviour: \u4e59\u2019s own rule may only lengthen \u4e59\u2019s own buffs");
    }

    // ==================================================================

    /** \u4e59\u2019s extender names {@code namedBuff} (or nothing at all), then both take the same tick, and we ask whether \u7532\u2019s buff is still there. */
    private static boolean survivesTheExtraTick(String namedBuff) {
        Character first = CharacterFactory.create(FIRST, 80);
        first.setTriggerTable(new TriggerTable(FIRST, List.of(
                TriggerSpecs.rule(TriggerEvent.SKILL_CAST.name(), List.of("actor == self"), mark()))));
        Character second = CharacterFactory.create(SECOND, 80);
        second.setTriggerTable(new TriggerTable(SECOND, List.of(
                TriggerSpecs.rule(TriggerEvent.SKILL_CAST.name(), List.of("actor == self"), extend(namedBuff)))));

        Battle battle = new Battle(List.of(first, second),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        // \u7532 puts the two-turn buff on \u4e59.
        Skill theirs = first.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(theirs, "precondition: \u7532 has a skill");
        SkillExecutor.execute(battle, theirs, first, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        Assertions.assertTrue(second.getBuffManager().hasState(MARK), "precondition: \u4e59 carries \u7532\u2019s buff");

        // \u4e59 lengthens -- by name (filtered) or by saying nothing (unfiltered).
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

    /** \u4e59\u2019s extender: one turn, optionally naming the buff (that is the only difference between the two runs). */
    private static EffectSpec extend(String namedBuff) {
        EffectSpec e = new EffectSpec();
        TriggerSpecs.set(e, "op", "EXTEND_BUFF");
        if (namedBuff != null) {
            TriggerSpecs.set(e, "buff", namedBuff);
        }
        TriggerSpecs.set(e, "turns", 1);
        TriggerSpecs.set(e, "target", "self");
        return e;
    }
}
''')
print("ok   judge written")
