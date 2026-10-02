"""The `hp_at_most` condition, second attempt (2026-10-02).

First attempt was withdrawn for two reasons, both fixed here: the judge passed an Integer to a Double field
(`TriggerSpecs.set` reflects on the field's type), and the caller's "red suite -> git checkout" undid the ENGINE patch
for a JUDGE bug. This time the judge writes `1.0`, and the caller reverts only the file that is actually broken.

Readers (8+ clauses, four files, every one of them "less than or equal"): 1102's talent and trace, 1102's two "against a
target at or below 80%", 1008's four, 1217's two, 1205's 无尽形寿.
ASCII only apart from the doc comments.
"""
import io
import sys

TABLE = "src/main/java/com/laosun/aluminium/models/TriggerTable.java"
JUDGE = "src/test/java/com/laosun/aluminium/test/HpAtMostTest.java"
text = io.open(TABLE, encoding="utf-8").read()

if "HP_AT_MOST" not in text:
    PATTERN_ANCHOR = "    private static final Pattern STATE_ENDED_KEYWORD =\n"
    PATTERN_NEW = (
        "    /**\n"
        "     * The keyword of the \"that unit's health is at most X of its maximum\" condition (2026-10-02).\n"
        "     *\n"
        "     * <p>The value is a FRACTION in (0, 1] -- the documents say \"50%\", the files say {@code 0.5}, exactly like\n"
        "     * every other percentage here. Boundary-guarded like the rest.\n"
        "     */\n"
        "    private static final Pattern HP_AT_MOST =\n"
        "            Pattern.compile(\"(?<![\\\\w])(?<subject>self|actor|target)_hp_at_most:(?<percent>[0-9.]+)\",\n"
        "                    Pattern.CASE_INSENSITIVE);\n"
        "\n")
    PARSE_ANCHOR = "        Matcher stateEnded = STATE_ENDED_KEYWORD.matcher(text);\n"
    PARSE_NEW = (
        "        Matcher hpAtMost = HP_AT_MOST.matcher(text);\n"
        "        if (hpAtMost.find()) {\n"
        "            String subject = normalize(hpAtMost.group(\"subject\"));\n"
        "            double percent = Double.parseDouble(hpAtMost.group(\"percent\"));\n"
        "            if (percent <= 0 || percent > 1) {\n"
        "                throw new IllegalArgumentException(\n"
        "                        \"Condition '\" + raw + \"' states a health share of \" + percent + \", but this is a FRACTION \"\n"
        "                                + \"(0.5 = 50%): the documents write percentages, the files write fractions, and a \"\n"
        "                                + \"whole number here would silently mean 100x too much \"\n"
        "                                + \"(source: \" + spec.getSource() + \")\");\n"
        "            }\n"
        "            return new HpAtMost(requireCarriedParty(requireStateSubject(subject, raw, spec), raw, spec), percent,\n"
        "                    raw, spec);\n"
        "        }\n"
        "\n")
    CLASS_ANCHOR = "    /**\n     * \u300c\u3010X\u3011\u7ed3\u675f\u65f6\u300d: the state NAMED X has just left the subject (2026-10-02).\n"
    CLASS_NEW = (
        "    /**\n"
        "     * \u300c\u5f53\u524d\u751f\u547d\u503c\u767e\u5206\u6bd4\u5c0f\u4e8e\u7b49\u4e8e X\u300d: the subject's health is at most X of its maximum\n"
        "     * (2026-10-02; readers: 1102's talent and trace, 1102's two \"against a target at or below 80%\", 1008's four,\n"
        "     * 1217's two, 1205's \u65e0\u5c3d\u5f62\u5bff).\n"
        "     *\n"
        "     * <p>\u26a0 The boundary is INCLUSIVE, because every one of those clauses says \u300c\u5c0f\u4e8e\u7b49\u4e8e\u300d.\n"
        "     */\n"
        "    private static final class HpAtMost implements Condition, PartyCondition {\n"
        "\n"
        "        private final String subject;\n"
        "        private final double percent;\n"
        "        private final String raw;\n"
        "\n"
        "        HpAtMost(String subject, double percent, String raw, TriggerSpec spec) {\n"
        "            this.subject = subject;\n"
        "            this.percent = percent;\n"
        "            this.raw = raw;\n"
        "        }\n"
        "\n"
        "        @Override\n"
        "        public CanHit partyOf(TriggerContext ctx) {\n"
        "            return switch (subject) {\n"
        "                case \"self\" -> ctx.owner();\n"
        "                case \"actor\" -> ctx.actor();\n"
        "                case \"target\" -> ctx.target();\n"
        "                default -> null;\n"
        "            };\n"
        "        }\n"
        "\n"
        "        @Override\n"
        "        public boolean test(TriggerContext ctx) {\n"
        "            CanHit unit = partyOf(ctx);\n"
        "            if (unit == null) {\n"
        "                return false;\n"
        "            }\n"
        "            double max = unit.getMaxHp();\n"
        "            return max > 0 && unit.getCurrentHp() / max <= percent;\n"
        "        }\n"
        "\n"
        "        @Override\n"
        "        public String source() {\n"
        "            return raw;\n"
        "        }\n"
        "    }\n"
        "\n")
    for anchor, replacement, label in ((PATTERN_ANCHOR, PATTERN_NEW, "pattern"),
                                       (PARSE_ANCHOR, PARSE_NEW, "parse branch"),
                                       (CLASS_ANCHOR, CLASS_NEW, "condition class")):
        if text.count(anchor) != 1:
            print("FAIL %s: anchor matched %d times" % (label, text.count(anchor)))
            sys.exit(1)
        text = text.replace(anchor, replacement + anchor)
    io.open(TABLE, "w", encoding="utf-8", newline="").write(text)
    print("ok   hp_at_most: keyword + parse branch + HpAtMost condition")
else:
    print("skip engine: already there")

JUDGE_TEXT = '''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The {@code hp_at_most} condition (2026-10-02): 「当前生命值百分比小于等于 X」, read off the unit itself.
 *
 * <p>Eight-plus clauses state it and every one of them says 小于等于, so the boundary is pinned from BOTH sides: just
 * below the share fires, just above it does not, and EXACTLY at it does.
 */
public class HpAtMostTest {
    private static final int OWNER = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Pay 1 energy whenever this unit starts a turn at or below half health. */
    @Test
    public void theBoundaryIsInclusive() {
        double share = 0.5;
        Assertions.assertTrue(fires(share * 0.98), "just below the share fires");
        Assertions.assertTrue(fires(share), "EXACTLY at the share fires (every clause says inclusive)");
        Assertions.assertFalse(fires(share * 1.02), "just above the share does not fire");
    }

    // ==================================================================

    /** True when the rule paid out on a turn start at {@code share} of the unit's maximum health. */
    private static boolean fires(double share) {
        Character owner = CharacterFactory.create(OWNER, LEVEL, false, null, null, 0);
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "GAIN_ENERGY");
        TriggerSpecs.set(effect, "amount", 1.0);
        TriggerSpecs.set(effect, "target", "self");
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(TriggerSpecs.rule("TURN_START",
                List.of("actor == self", "self_hp_at_most:0.5"), effect))));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(owner), List.of(enemy), new Random(0));
        battle.startBattle();

        battle.applyTrueDamage(enemy, owner, DamageElement.FIRE, owner.getMaxHp() * (1 - share));
        double before = owner.getCurrentEnergy();
        battle.fireTriggers(TriggerEvent.TURN_START, owner, owner, 0, 0);
        battle.processRequests();
        return owner.getCurrentEnergy() > before;
    }
}
'''
io.open(JUDGE, "w", encoding="utf-8", newline="").write(JUDGE_TEXT)
print("ok   judge written (amount is a Double this time)")
