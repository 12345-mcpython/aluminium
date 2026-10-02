"""`event_amount`, second attempt (2026-10-02) -- with the per-op whitelists the first attempt ran into.

Lesson from the withdrawal: adding a branch to the SHARED `grantAmount` is not enough, because each op validates its
`scale` against its OWN set. So this patch touches four places:

  1. `EffectSpec.timesFrom` (bean) + its line in `copy()` -- the judge `RuleEffectAmendmentTest` demands the latter;
  2. the `DAMAGE` arm's repeat count: `times_from: "event_amount"` repeats |amount| times;
  3. `grantAmount`'s switch: `scale: "event_amount"` = |amount| * share + flat;
  4. the whitelists that must now accept it: `SCALES` (HEAL/SHIELD) and `ENERGY_SCALES` (GAIN_ENERGY).

It sits beside the existing `cast_energy_spent` (light cone 23062's 「每消耗 1 点能量值」), which is the same shape bound
to the cast instead of the event. `MODIFY_ATTR`/`GAIN_RESOURCE`/`DAMAGE`-base whitelists are NOT touched yet: the judge
proves the two spellings here, and those ops stay registered.

ASCII only.
"""
import io
import sys

SPEC = "src/main/java/com/laosun/aluminium/beans/EffectSpec.java"
INTERP = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
JUDGE = "src/test/java/com/laosun/aluminium/test/EventAmountTest.java"

spec = io.open(SPEC, encoding="utf-8").read()
if "timesFrom" not in spec:
    ANCHOR = '    @SerializedName("times")'
    NEW = ('    /**\n'
           '     * How many times the effect repeats, read from the triggering EVENT instead of a constant (2026-10-02);\n'
           '     * the only value today is {@code "event_amount"} (\u300c\u6bcf\u6d88\u8017 1 \u70b9\u2026\u989d\u5916 1 \u6b21\u300d). {@code null} = use {@code times}.\n'
           '     */\n'
           '    @SerializedName("times_from")\n'
           '    private String timesFrom;\n'
           '\n' + ANCHOR)
    if spec.count(ANCHOR) != 1:
        print("FAIL spec: times anchor matched %d times" % spec.count(ANCHOR))
        sys.exit(1)
    spec = spec.replace(ANCHOR, NEW)
    io.open(SPEC, "w", encoding="utf-8", newline="").write(spec)
    print("ok   EffectSpec.timesFrom")

    OLD_COPY = "        copy.times = this.times;\n"
    if spec.count(OLD_COPY) != 1:
        print("FAIL spec: copy anchor matched %d times" % spec.count(OLD_COPY))
        sys.exit(1)
    spec = spec.replace(OLD_COPY, OLD_COPY + "        copy.timesFrom = this.timesFrom;\n")
    io.open(SPEC, "w", encoding="utf-8", newline="").write(spec)
    print("ok   copy() carries timesFrom (the amendment judge demands it)")
else:
    print("skip spec")

interp = io.open(INTERP, encoding="utf-8").read()
if 'case "event_amount"' in interp:
    print("skip interp")
else:
    OLD_SCALE = '            case "owner_attack" -> ownerAttributeOf(ctx, "owner_attack", AttributeType.ATTACK) * share + flat;'
    NEW_SCALE = (OLD_SCALE + '\n'
                 '            // \u2b50 \u300c\u6bcf\u6d88\u8017/\u6bcf\u635f\u5931 1 \u70b9\u2026\u300d (2026-10-02): the triggering EVENT\'s own magnitude.\n'
                 '            // The absolute value, because a spend arrives negative and "\u6bcf 1 \u70b9" counts points.\n'
                 '            case "event_amount" -> Math.abs(ctx.amount()) * share + flat;')
    if interp.count(OLD_SCALE) != 1:
        print("FAIL interp: scale anchor matched %d times" % interp.count(OLD_SCALE))
        sys.exit(1)
    interp = interp.replace(OLD_SCALE, NEW_SCALE)

    OLD_TIMES = "                int times = effect.getTimes() == null ? 1 : effect.getTimes();"
    NEW_TIMES = ("                // \u2b50 \u300c\u6bcf\u6d88\u8017 1 \u70b9\u2026\u989d\u5916 1 \u6b21\u300d (2026-10-02): the repeat count can follow the event.\n"
                 "                int times = effect.getTimes() == null ? 1 : effect.getTimes();\n"
                 "                if (effect.getTimesFrom() != null) {\n"
                 "                    if (!\"event_amount\".equals(effect.getTimesFrom().trim())) {\n"
                 "                        throw new IllegalStateException(\"times_from '\" + effect.getTimesFrom()\n"
                 "                                + \"' is not a spelling this engine has: only \\\"event_amount\\\"\");\n"
                 "                    }\n"
                 "                    times = (int) Math.abs(ctx.amount());\n"
                 "                    if (times <= 0) {\n"
                 "                        return;\n"
                 "                    }\n"
                 "                }")
    if interp.count(OLD_TIMES) != 1:
        print("FAIL interp: times anchor matched %d times" % interp.count(OLD_TIMES))
        sys.exit(1)
    interp = interp.replace(OLD_TIMES, NEW_TIMES)

    OLD_HEAL = '        Set.of("target_max_hp", "target_lost_hp", "owner_max_hp", "owner_def", "owner_attack");'
    NEW_HEAL = '        Set.of("target_max_hp", "target_lost_hp", "owner_max_hp", "owner_def", "owner_attack", "event_amount");'
    OLD_ENERGY = '    private static final Set<String> ENERGY_SCALES = Set.of("target_max_energy");'
    NEW_ENERGY = '    private static final Set<String> ENERGY_SCALES = Set.of("target_max_energy", "event_amount");'
    for old, new, label in ((OLD_HEAL, NEW_HEAL, "SCALES"), (OLD_ENERGY, NEW_ENERGY, "ENERGY_SCALES")):
        if interp.count(old) != 1:
            print("FAIL interp: %s anchor matched %d times" % (label, interp.count(old)))
            sys.exit(1)
        interp = interp.replace(old, new)
    io.open(INTERP, "w", encoding="utf-8", newline="").write(interp)
    print("ok   TriggerInterpreter: event_amount in grantAmount, the DAMAGE repeat count, and two whitelists")

JUDGE_TEXT = '''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
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
 * {@code event_amount} (2026-10-02): the triggering event's own magnitude, as a value and as a repeat count.
 *
 * <p>Readers (all with data files): 1407's talent 「我方全体每损失1点生命值遐蝶获得1点【新蕊】」, 1413's 「每消耗了1点【忆质】…」,
 * and the per-spent-point instance family 1408 / 1510 / 1513. The engine already hands the number over --
 * {@code RESOURCE_CHANGED} fires with {@code amount = delta} -- and a spend arrives NEGATIVE, so both readings take the
 * absolute value: "\u6bcf 1 \u70b9" counts points.
 */
public class EventAmountTest {
    private static final int OWNER = 1003;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** \u2b50 A magnitude off the event: 1 energy per point of health lost. */
    @Test
    public void theMagnitudeFollowsTheEvent() {
        Character owner = CharacterFactory.create(OWNER, LEVEL, false, null, null, 0);
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "GAIN_ENERGY");
        TriggerSpecs.set(effect, "scale", "event_amount");
        TriggerSpecs.set(effect, "percent", 1.0);
        TriggerSpecs.set(effect, "target", "self");
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(TriggerSpecs.rule("HP_LOST",
                List.of("actor == self"), effect))));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(owner), List.of(enemy), new Random(0));
        battle.startBattle();

        double before = owner.getCurrentEnergy();
        battle.fireTriggers(TriggerEvent.HP_LOST, owner, owner, 0, 20);
        battle.processRequests();
        Assertions.assertEquals(20, owner.getCurrentEnergy() - before, 1e-6, "1 energy per point lost");
    }

    /** \u2b50 A repeat count off the event, driven by a real resource change. */
    @Test
    public void theRepeatCountFollowsTheEvent() {
        double one = loss(1);
        double three = loss(3);
        Assertions.assertTrue(one > 0, "precondition: the instance lands (" + one + ")");
        Assertions.assertEquals(3 * one, three, one * 1e-6,
                "spending 3 repeats the instance 3 times (" + one + " -> " + three + ")");
        Assertions.assertEquals(0, loss(0), 1e-6, "and a change of 0 repeats it not at all");
    }

    // ==================================================================

    /** The enemy's HP loss when the rule repeats its instance once per point spent. */
    private static double loss(int spent) {
        Character owner = CharacterFactory.create(OWNER, LEVEL, false, null, null, 0);
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "DAMAGE");
        TriggerSpecs.set(effect, "scale", "self_attr:ATTACK");
        TriggerSpecs.set(effect, "percent", 1.0);
        TriggerSpecs.set(effect, "element", "Fire");
        TriggerSpecs.set(effect, "target", "target");
        TriggerSpecs.set(effect, "critRate", 0.0);
        TriggerSpecs.set(effect, "critDamage", 0.0);
        TriggerSpecs.set(effect, "timesFrom", "event_amount");
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(TriggerSpecs.rule("RESOURCE_CHANGED",
                List.of("actor == self", "resource_changed:\\u5145\\u80fd"), effect))));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(owner), List.of(enemy), new Random(0));
        battle.startBattle();
        double before = enemy.getCurrentHp();
        battle.fireTriggers(TriggerEvent.RESOURCE_CHANGED, owner, owner, 0, -spent);
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }
}
'''
io.open(JUDGE, "w", encoding="utf-8", newline="").write(JUDGE_TEXT)
print("ok   judge written")
