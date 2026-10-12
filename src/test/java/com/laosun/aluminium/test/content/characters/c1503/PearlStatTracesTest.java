package com.laosun.aluminium.test.content.characters.c1503;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Character 1503 (Pearl): the ten stat traces the dump carries for her reach her PANEL.
 *
 * <p><b>Why these are content at all.</b> The corpus says they are "panel attributes handled by {@code SkillTrace}, so
 * they need no content rules" - true about the PATH, false about the DATA. Measured: her rows in
 * {@code AvatarSkillTreeConfig} really do carry the ten {@code StatusAddList} entries, but the generated
 * {@code skill_traces.json} (which is what {@code SkillTrace} reads) has 93 entries with 1502/1505/1001 at 13 each and
 * <b>1503 empty</b>. So her panel was short all ten, with no error anywhere - the shape this project keeps catching.
 *
 * <p><b>How this judge reads it.</b> As an ABSOLUTE increment, not "greater than zero". A baseline reading would need
 * a Pearl without her traces, which does not exist: {@code SkillTrace} is wired into character construction and reads
 * the generated file, so clearing the trigger table changes nothing (measured: a diff against a table-emptied Pearl
 * read a flat 0, which is what led to finding the empty generated file in the first place). The numbers below are the
 * sum of the data rows themselves:
 * <pre>
 *   DEFENCE              0.05 + 0.075 + 0.10 = 0.225
 *   SPEED                2 + 3 + 4           = +9
 *   EFFECT_RESISTANCE    0.04 + 0.06         = 0.10
 *   ELATION_DAMAGE_BOOST 0.04 + 0.06         = 0.10
 * </pre>
 */
public class PearlStatTracesTest {
    private static final int PEARL = 1503;
    private static final int ALLY = 1204;
    private static final int LEVEL = 80;
    private static final double EPS = 1e-6;

    /**
     * The three trivially attributable rows are asserted exactly; Defence is asserted as the ratio the data states.
     *
     * <p>Note: Defence and Elation are RATIO attributes (their unit is a fraction), so the data's
     * "Defence +5.00%/+7.50%/+10.00%" is a 22.5% raise on her own base - which is exactly what {@code percent} means
     * for them, and why {@code MODIFY_ATTR} is the right op rather than a flat {@code amount}.
     */
    @Test
    public void theTenDataRowsReachThePanel() {
        Character pearl = CharacterFactory.create(PEARL, LEVEL);
        Battle battle = new Battle(List.of(pearl, CharacterFactory.create(ALLY, LEVEL)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character her = battle.characters.getFirst();

        double effectRes = her.getAttribute(AttributeType.EFFECT_RESISTANCE).get();
        double elation = her.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
        // Note: The table-emptied twin is printed too: it is what identifies a contribution that does NOT come from
        // her file (measured while mutating the Elation row: the panel stayed at 0.10 with the row at 0.06, i.e.
        // something else was supplying the rest).
        double defence = her.getAttribute(AttributeType.DEFENCE).get();
        double speedWithTraces = speedOf(true);
        double defenceNoRules = defenceOfPearlWithNoRules();
        double elationNoRules = elationOfPearlWithNoRules();
        System.out.println("[pearl_stat_traces] effectRES=" + effectRes + " elation=" + elation
                + " (with her table emptied: " + elationNoRules + ")"
                + " defence=" + defence + " (emptied: " + defenceNoRules + ") speed=" + speedWithTraces);

        // Note: EFFECT_RESISTANCE is a 4%/6% trace and nothing else grants it, so the panel value IS the trace sum.
        Assertions.assertEquals(0.10, effectRes, EPS,
                "effect RES = 4% + 6% from her trace rows (1503203 / 1503207)");
        // Note: 洞察万物 (a content rule this file also ships) adds Elation at DEF >= 2400; a plain Lv80 panel is
        // below that, so the 10% here is the trace sum alone. PearlInsightTraceTest covers the other clause.
        Assertions.assertEquals(0.10, elation, 1e-4,
                "Elation = 4% + 6% from her trace rows (1503204 / 1503208), the DEF gate not being met yet");
        Assertions.assertEquals(0.225, defence / defenceNoRules - 1, 1e-6,
                "Defence = 5% + 7.5% + 10% on her own base (1503201 / 1503205 / 1503210)");
    }

    /**
     * The Speed rows are an ABSOLUTE delta of nine points, and that is worth pinning exactly.
     *
     * <p>{@code SpeedDelta} carries {@code 2/3/4} (not 2%/3%/4%), so it has to be an {@code amount} rather than a
     * {@code percent}: written as a percentage those three points would become a share of her base speed - a number
     * an order of magnitude larger, and one that still looks plausible on a panel.
     *
     * <p>Note: This reads her BASE speed rather than a hard-coded total, because the total moves with level, light
     * cone and relics while the clause's contribution does not. The first version of this case asserted only
     * {@code speed > 9}, which the percentage mistake satisfies as well - measured, that mutant survived.
     */
    @Test
    public void theSpeedRowsAreNineFlatPoints() {
        // Note: Two panels in one place - Pearl WITH her file and Pearl whose whole table is emptied - so the
        // difference is the traces and nothing else. Measured why the obvious version does not work: reading
        // `baseValue()` off the ALREADY-boosted unit gives the boosted number rather than her raw base, so the
        // subtraction came out 0. The first version of this case asserted only `speed > 9`, which the percentage
        // mistake satisfies as well (measured: that mutant survived).
        double withTraces = speedOf(true);
        double without = speedOf(false);
        System.out.println("[pearl_stat_traces] Speed with her traces = " + withTraces + " ; table emptied = "
                + without);

        Assertions.assertTrue(without > 0, "precondition: a base speed to measure against (" + without + ")");
        Assertions.assertEquals(9.0, withTraces - without, 1e-6,
                "the +9 flat Speed from her three trace rows - a flat delta, not a share of her base (2%/3%/4% of "
                        + without + " would be " + (0.09 * without) + ")");
    }

    /**
     * Her Speed with the stat-trace clause in place, or with her whole trigger table emptied.
     */
    private static double speedOf(boolean withTraces) {
        Character pearl = CharacterFactory.create(PEARL, LEVEL);
        if (!withTraces) {
            pearl.setTriggerTable(TriggerTable.EMPTY);
        }
        Battle battle = new Battle(List.of(pearl), List.of(BattleFixture.monster()),
                BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return battle.characters.getFirst().getAttribute(AttributeType.SPEED).get();
    }

    /**
     * Her Elation with no rules of hers at all - the part of the panel that does not come from this file.
     */
    private static double elationOfPearlWithNoRules() {
        return attributeWithNoRules(AttributeType.ELATION_DAMAGE_BOOST);
    }

    /**
     * Her Defence with no rules of hers at all - the base the 22.5% is measured against.
     */
    private static double defenceOfPearlWithNoRules() {
        return attributeWithNoRules(AttributeType.DEFENCE);
    }

    private static double attributeWithNoRules(AttributeType attribute) {
        Character pearl = CharacterFactory.create(PEARL, LEVEL);
        pearl.setTriggerTable(TriggerTable.EMPTY);
        Battle battle = new Battle(List.of(pearl), List.of(BattleFixture.monster()),
                BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return battle.characters.getFirst().getAttribute(attribute).get();
    }
}
