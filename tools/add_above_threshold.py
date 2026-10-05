"""`self_attr_above:<ATTRIBUTE>:<threshold>` -- "every point ABOVE a threshold" (2026-10-02).

Readers (all with data files): 1415:823 「昔涟的速度大于等于180点时…之后每超过1点速度…抗性穿透提高2%，最多计入60点」,
1502:269 「爻光的速度大于等于120时…每超过1点速度使自身欢愉度提高1%，最多计入200点」, 1513:314 (140 speed, same shape),
1317:440 「攻击力高于2400点，每超过100点攻击力可使该数值额外提高1%」 -- the last one is the same shape with a 100-point
step, which needs no new spelling: 1% per 100 points is 0.0001 per point.

Where it goes (the same three-place map `event_amount` used, all measured):
  * `derivedMagnitude` -- the magnitude branch, next to `self_max_energy` (which is this shape with the threshold
    hard-wired to MAX ENERGY, 「每超过 1 点」, relic set 328);
  * `scaleAttribute` -- its early-exit list (the source is not an AttributeType).

Judge: file-driven character, one hand-built modifier, and the reading is checked against the EXCESS (not the attribute),
which is what makes it different from `self_attr:`.
ASCII only.
"""
import io
import sys

INTERP = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
JUDGE = "src/test/java/com/laosun/aluminium/test/AboveThresholdTest.java"

text = io.open(INTERP, encoding="utf-8").read()
if "ABOVE_PREFIX" in text:
    print("skip interp")
else:
    OLD = ('        if (SELF_MAX_ENERGY.equals(effect.getScale().trim())) {\n')
    NEW = ('        if (effect.getScale().trim().startsWith(ABOVE_PREFIX)) {\n'
           '            // ⭐ 「速度大于等于 120 时…之后**每超过 1 点速度**…」 (2026-10-02): the EXCESS over a\n'
           '            // stated threshold, as a magnitude. ⚠ `self_max_energy` below is the same shape with the threshold\n'
           '            // hard-wired to MAX ENERGY; this is that, parameterised.\n'
           '            String[] parts = effect.getScale().trim().substring(ABOVE_PREFIX.length()).split(":", 2);\n'
           '            if (parts.length != 2) {\n'
           '                throw new IllegalStateException("the scale \\"" + effect.getScale()\n'
           '                        + "\\" must state <ATTRIBUTE>:<threshold>, e.g. self_attr_above:SPEED:120");\n'
           '            }\n'
           '            AttributeType over = AttributeType.fromString(parts[0].trim());\n'
           '            double threshold = Double.parseDouble(parts[1].trim());\n'
           '            double excess = Math.max(0, owner.getAttribute(over).get() - threshold);\n'
           '            return effect.getPercent() * excess + (effect.getAmount() == null ? 0 : effect.getAmount());\n'
           '        }\n'
           + OLD)
    if text.count(OLD) != 1:
        print("FAIL interp: magnitude anchor matched %d times" % text.count(OLD))
        sys.exit(1)
    text = text.replace(OLD, NEW)

    OLD_EARLY = '        if (SELF_MAX_ENERGY.equals(raw) || EVENT_AMOUNT.equals(raw)) {\n'
    NEW_EARLY = '        if (SELF_MAX_ENERGY.equals(raw) || EVENT_AMOUNT.equals(raw) || raw.startsWith(ABOVE_PREFIX)) {\n'
    if text.count(OLD_EARLY) != 1:
        print("FAIL interp: early-exit anchor matched %d times" % text.count(OLD_EARLY))
        sys.exit(1)
    text = text.replace(OLD_EARLY, NEW_EARLY)

    ANCHOR = '    private static final String EVENT_AMOUNT = "event_amount";\n'
    CONST = (ANCHOR + '\n'
             '    /**\n'
             '     * 「速度大于等于 X 时…每超过 1 点速度…」 (2026-10-02): {@code self_attr_above:<ATTRIBUTE>:<threshold>}.\n'
             '     *\n'
             '     * <p>Like {@link #SELF_MAX_ENERGY} — the same shape, with the threshold stated instead of implied by the\n'
             '     * energy cap — and, like it, not an {@code AttributeType}.\n'
             '     */\n'
             '    private static final String ABOVE_PREFIX = "self_attr_above:";\n')
    if text.count(ANCHOR) != 1:
        print("FAIL interp: constant anchor matched %d times" % text.count(ANCHOR))
        sys.exit(1)
    text = text.replace(ANCHOR, CONST)
    io.open(INTERP, "w", encoding="utf-8", newline="").write(text)
    print("ok   derivedMagnitude + scaleAttribute accept self_attr_above:")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 「速度大于等于 120 时…之后每超过 1 点速度使自身欢概度提高 1%」 (1502:269, 2026-10-02).
 *
 * <p>The reading is the EXCESS over the threshold, not the attribute -- which is exactly what separates this spelling
 * from `self_attr:`. A threshold above the character's own speed must therefore give nothing at all.
 */
public class AboveThresholdTest {
    private static final int OWNER = 1502;
    private static final int MONSTER = 1002011;

    /** ⭐ The magnitude is a share of the EXCESS, and a threshold above the attribute gives nothing. */
    @Test
    public void theMagnitudeIsTheExcessOverTheThreshold() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        double speed = owner.getAttribute(AttributeType.SPEED).get();
        // ⚠ Her own speed at Lv80 is 110 (measured), so the threshold here is 100: below 120 on purpose, because the
        // spelling is what is under test, not her ability to reach the document's number without buffs.
        Assertions.assertTrue(speed > 100, "precondition: this character is faster than 100 (" + speed + ")");

        double at100 = boost(100);
        Assertions.assertEquals(0.01 * (speed - 100), at100, 1e-6,
                "1% of the excess over 100 (" + speed + " - 100)");
        Assertions.assertEquals(0, boost(10000), 1e-9, "a threshold above the attribute gives nothing");
        Assertions.assertTrue(boost(105) < at100, "and a higher threshold gives less");
    }

    // ==================================================================

    private static double boost(double threshold) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "ALL_DAMAGE_TYPE_BOOST");
        TriggerSpecs.set(effect, "scale", "self_attr_above:SPEED:" + (int) threshold);
        TriggerSpecs.set(effect, "percent", 0.01);
        TriggerSpecs.set(effect, "permanent", true);
        TriggerSpecs.set(effect, "target", "self");
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(TriggerSpecs.rule("BATTLE_START",
                List.of(), effect))));
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        return owner.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
    }
}
''')
print("ok   judge written")
