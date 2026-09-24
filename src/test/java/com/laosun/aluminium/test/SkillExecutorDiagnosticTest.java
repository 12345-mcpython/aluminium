package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DefaultSkill;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import com.laosun.aluminium.models.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

/**
 * The **undispatched diagnostic** for non-damaging skills (item 3 of the P8-2 plan).
 *
 * <p>Background: {@code SkillExecutor.resolveHits} **silently returns** when "the skill is not a
 * damaging one" — after a heal/shield/buff/control/summon is cast **nothing at all happens** (only
 * the energy gain is still given). That is very hard to notice in a real team: in the log the skill
 * "went off", there was just no effect.
 *
 * <p>So a toggleable diagnostic log was added (labelling the owning phase as P6-2 / P10-3 / P10-6 /
 * P9-4). The plan originally wanted a bare {@code IO.println}, but the demo casts heals and shields
 * every turn — that would flood the output by default, so it was changed to an explicit switch. This
 * class verifies both "it prints when enabled" and "it does not print by default".
 */
public class SkillExecutorDiagnosticTest {
    private static final double EPS = 1e-9;

    @AfterEach
    public void restore() {
        SkillExecutor.setLogNotDispatched(false);      // do not leak the switch into other tests
    }

    /**
     * A non-damaging skill that has <b>no entry</b> in {@code skill_effects.json} is still reported.
     *
     * <p>P10-3 wired Restore and Defence, so the diagnostic narrowed to the effects that are genuinely
     * still unimplemented (buff / control / summon). Bronya's (布洛妮娅) skill is a Support skill, so it
     * exercises that path.
     *
     * <p>⚠ This test used to cast Natasha's healing skill and assert the notice, back when healing did
     * nothing. It was rewritten rather than deleted: the invariant that matters is now "anything the
     * engine cannot apply must say so", not "healing is broken".
     */
    @Test
    public void skillsWithoutATableEntryAreStillReported() {
        Character bronya = CharacterFactory.create(1101, 80);
        Battle battle = newBattle(bronya);

        String out = capture(() -> {
            SkillExecutor.setLogNotDispatched(true);
            battle.castImmediate(new DefaultSkill(1101, 2, 1), bronya, List.of(bronya));
        });

        Assertions.assertTrue(out.contains("NOT DISPATCHED"), "it should print the notice; actual: " + out);
        Assertions.assertTrue(out.contains("SUPPORT"), "it should carry the effect type; actual: " + out);
        Assertions.assertTrue(out.contains("P10-3"), "it should label the owning phase; actual: " + out);
    }

    /**
     * **Off by default**: nothing is printed. The demo heals/shields every turn, so having it on by
     * default would flood the output.
     */
    @Test
    public void diagnosticIsOffByDefault() {
        Character natasha = CharacterFactory.create(1105, 80);
        Battle battle = newBattle(natasha);

        String out = capture(() ->
                battle.castImmediate(new DefaultSkill(1105, 2, 1), natasha, List.of(natasha)));

        Assertions.assertFalse(out.contains("NOT DISPATCHED"),
                "the diagnostic should not be printed by default; actual output: " + out);
    }

    /**
     * A {@code Defence} skill with a single percentage term really grants a shield, and is no longer
     * reported as undispatched.
     *
     * <p>Gepard's (杰帕德) ultimate is {@code 1104 slot 3}: {@code scale = def}, one percentage and one
     * flat term, so it is one of the entries the engine can read unambiguously.
     *
     * <p>⚠ March 7th's (三月七) skill is deliberately <b>not</b> used here even though it is the obvious
     * shield: its row carries two percentage terms (a shield plus something else), so the ambiguity
     * guard refuses it. That is the guard working as intended, and
     * {@link #ambiguousHealEntriesAreRefusedRatherThanSummed} covers that behaviour.
     *
     * <p>⚠ Formerly {@code shieldSkillIsAlsoReported}, which asserted the opposite — that a shield
     * skill only printed a notice.
     */
    @Test
    public void singleTermShieldSkillsAreDispatchedAndNotReported() {
        Character gepard = CharacterFactory.create(1104, 80);
        Battle battle = newBattle(gepard);
        Assertions.assertEquals(0, gepard.getShield(), EPS, "no shield before the cast");

        String out = capture(() -> {
            SkillExecutor.setLogNotDispatched(true);
            battle.castImmediate(new DefaultSkill(1104, 3, 1), gepard, List.of(gepard));
        });

        Assertions.assertFalse(out.contains("NOT DISPATCHED"),
                "a skill in the table must not be reported as undispatched; actual: " + out);
        Assertions.assertTrue(gepard.getShield() > 0, "the shield should really be applied");
    }

    /**
     * A damaging skill does **not** trigger the diagnostic (it goes down the normal dispatch path).
     */
    @Test
    public void damagingSkillsDoNotTriggerTheDiagnostic() {
        Character jingYuan = CharacterFactory.create(1204, 80);
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        Battle battle = new Battle(List.of(jingYuan), List.of(enemy), new Random(0));
        battle.startBattle();

        String out = capture(() -> {
            SkillExecutor.setLogNotDispatched(true);
            battle.castImmediate(new DefaultSkill(1204, 1, 1), jingYuan, List.of(enemy));
        });

        Assertions.assertFalse(out.contains("NOT DISPATCHED"), "a damaging skill should not report; actual: " + out);
        Assertions.assertTrue(enemy.getCurrentHp() < enemy.getMaxHp(), "it really did fire");
    }

    /**
     * A healing skill really heals now, and heals for the <b>right</b> number.
     *
     * <p>⚠ This replaces {@code diagnosticDoesNotChangeBehaviour}, which asserted that turning the log
     * on did <i>not</i> make healing work. That test existed to stop "added a log" being mistaken for
     * "implemented the effect"; the effect is implemented now, so it is inverted into a test of the
     * amount instead of deleted.
     *
     * <p>The number is the point. Natasha's ultimate (1105 slot 3) is {@code [0.092, 92]} with
     * {@code scale = healer_max_hp}, i.e. <b>9.2% of her own Max HP plus a flat 92</b>. An
     * ATK-based calculation — which is what the demo used to do by hand — or a dropped flat term both
     * produce a plausible-looking wrong value, and both fail here.
     */
    @Test
    public void healingSkillsHealForTheDocumentedAmount() {
        Character natasha = CharacterFactory.create(1105, 80);
        Battle battle = newBattle(natasha);
        natasha.takeDamage(natasha.getMaxHp() / 2);      // currentHp has no setter, so create a gap
        double hpBefore = natasha.getCurrentHp();
        double expected = 0.092 * natasha.getMaxHp() + 92;   // level 1 -> param row [0.092, 92]
        Assertions.assertTrue(hpBefore + expected < natasha.getMaxHp(),
                "the gap must be bigger than the heal, or the cap hides the amount");

        battle.castImmediate(new DefaultSkill(1105, 3, 1), natasha, List.of(natasha));

        Assertions.assertEquals(expected, natasha.getCurrentHp() - hpBefore, 1.0,
                "9.2% of Natasha's OWN Max HP plus a flat 92; got "
                        + (natasha.getCurrentHp() - hpBefore) + " for maxHp " + natasha.getMaxHp()
                        + " and ATK " + natasha.getAttribute(
                                com.laosun.aluminium.enums.AttributeType.ATTACK).get());
    }

    /**
     * A heal whose row mixes the immediate heal with a heal-over-time applies <b>only the immediate
     * part</b>.
     *
     * <p>Natasha's skill is {@code [0.07, 0.048, 2, 70, 48]}: index 0/3 are the heal and 1/4 are the
     * per-turn part. The generator splits the description at the clause that introduces the per-turn
     * amount and exports only {@code [0:p, 3:f]}; summing all four would apply the heal <i>and</i> the
     * regen at once, which looks entirely plausible and is wrong.
     */
    @Test
    public void mixedHealRowsApplyOnlyTheImmediatePart() {
        Character natasha = CharacterFactory.create(1105, 80);
        Battle battle = newBattle(natasha);
        natasha.takeDamage(natasha.getMaxHp() / 2);
        double hpBefore = natasha.getCurrentHp();
        double expected = 0.07 * natasha.getMaxHp() + 70;    // level 1 -> [0.07, ...] + 70
        double withRegen = expected + 0.048 * natasha.getMaxHp() + 48;
        Assertions.assertTrue(hpBefore + withRegen < natasha.getMaxHp(),
                "the gap must be bigger than even the regen-included amount, or the cap hides the difference");

        battle.castImmediate(new DefaultSkill(1105, 2, 1), natasha, List.of(natasha));

        double healed = natasha.getCurrentHp() - hpBefore;
        Assertions.assertEquals(expected, healed, 1.0,
                "only the immediate heal (7% of Max HP + 70); got " + healed);
        Assertions.assertTrue(healed < withRegen - 1.0,
                "the per-turn part must NOT be included; healing " + healed + " would mean it was summed in");
    }

    /**
     * An entry that still mixes several percentage terms is <b>refused and reported</b> rather than
     * summed into a wrong number.
     *
     * <p>March 7th's (三月七) skill is {@code [0:p, 2:p, 3:f]} after clause-scoping — two separate
     * percentage terms survive in one sentence, and nothing in the data says which is the shield, so
     * the engine declines. This is the guard that says "silence is not an option"; when the generator
     * can separate them this test should become an amount assertion.
     */
    @Test
    public void ambiguousEntriesAreRefusedRatherThanSummed() {
        Character march7th = CharacterFactory.create(1001, 80);
        Battle battle = newBattle(march7th);

        String out = capture(() -> {
            SkillExecutor.setLogNotDispatched(true);
            battle.castImmediate(new DefaultSkill(1001, 2, 1), march7th, List.of(march7th));
        });

        Assertions.assertEquals(0, march7th.getShield(), EPS,
                "a two-percent entry must not be applied; summing them would look plausible and be wrong");
        Assertions.assertTrue(out.contains("NOT DISPATCHED"),
                "and it must say so rather than stay silent; actual: " + out);
    }

    // ==================================================================

    private static Battle newBattle(Character hero) {
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        Battle battle = new Battle(List.of(hero), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    /** Captures {@code System.out} ({@code IO.println} writes to stdout). */
    private static String capture(Runnable action) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
            action.run();
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
