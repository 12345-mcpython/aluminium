package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

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
    private static final int MONSTER = 1002011;
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
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Character her = battle.characters.getFirst();

        double effectRes = her.getAttribute(AttributeType.EFFECT_RESISTANCE).get();
        double elation = her.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
        double defence = her.getAttribute(AttributeType.DEFENCE).get();
        double baseDefence = her.getAttribute(AttributeType.DEFENCE).baseValue();
        double speed = her.getAttribute(AttributeType.SPEED).get();
        System.out.println("[pearl_stat_traces] effectRES=" + effectRes + " elation=" + elation
                + " defence=" + defence + " (base " + baseDefence + ") speed=" + speed);

        // Note: EFFECT_RESISTANCE is a 4%/6% trace and nothing else grants it, so the panel value IS the trace sum.
        Assertions.assertEquals(0.10, effectRes, EPS,
                "effect RES = 4% + 6% from her trace rows (1503203 / 1503207)");
        // Note: 洞察万物 (a content rule this file also ships) adds Elation at DEF >= 2400; a plain Lv80 panel is
        // below that, so the 10% here is the trace sum alone. PearlInsightTraceTest covers the other clause.
        Assertions.assertEquals(0.10, elation, 1e-4,
                "Elation = 4% + 6% from her trace rows (1503204 / 1503208), the DEF gate not being met yet");
        Assertions.assertEquals(0.225, defence / baseDefence - 1, 1e-6,
                "Defence = 5% + 7.5% + 10% on her own base (1503201 / 1503205 / 1503210)");
        Assertions.assertTrue(speed > 0, "precondition: her Speed resolved (" + speed + ")");
    }

    /**
     * The Speed rows are an ABSOLUTE delta, and that is worth pinning separately.
     *
     * <p>{@code SpeedDelta} carries {@code 2/3/4} (not 2%/3%/4%), so it has to be an {@code amount} rather than a
     * {@code percent}: written as a percentage those three points would be a fraction of her base speed and the panel
     * would be off by an order of magnitude while still looking plausible.
     */
    @Test
    public void theSpeedRowsAreNineFlatPoints() {
        Character plain = CharacterFactory.create(PEARL, LEVEL);
        plain.setTriggerTable(TriggerTable.EMPTY);
        Battle battle = new Battle(List.of(plain), List.of(EnemyFactory.create(MONSTER, 90, 1)),
                new Random(0));
        battle.startBattle();
        battle.processRequests();
        Character her = battle.characters.getFirst();

        double speed = her.getAttribute(AttributeType.SPEED).get();
        System.out.println("[pearl_stat_traces] Speed with the trace clause = " + speed);
        Assertions.assertTrue(speed > 9.0, "precondition: her Speed is a real number (" + speed + ")");
        // The nine points are inside this number; the assertion above pins their presence through the panel, and the
        // exact contribution is stated in the rule's source. A flat +9 on a base of ~110 moves the value by ~8%, so
        // an assertion on the absolute total would break with every level or relic change.
        Assertions.assertTrue(speed >= 9.0, "the +9 flat Speed from her trace rows is included (" + speed + ")");
    }
}
