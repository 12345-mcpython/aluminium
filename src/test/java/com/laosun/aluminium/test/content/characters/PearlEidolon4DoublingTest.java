package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.test.support.BattleFixture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Character 1503 (Pearl): Eidolon 4 doubles the Elation damage the Elation skill hands the team.
 *
 * <p>「欢愉技为我方全体目标提供的欢愉伤害倍率提高原倍率的 100%」 (rank 150304, Param [1]) - so 10%/15%/20%/40% become
 * 20%/30%/40%/80%.
 *
 * <p>What this pins, and why the reading is a RATIO between two Eidolon ranks of the SAME team: the payout is only
 * visible as the difference an armed attack carries over an unarmed one, and Eidolon 4's clause is an ADDITIONAL
 * `ADD_DAMAGE` rather than a replacement. So the case measures "how much more does arming pay at E4 than at E0", which
 * is exactly the doubling, with the team, the attacker and the enemy held fixed.
 *
 * <p>⚠ The trap this case exists against is arithmetic, not wiring: `Damage.addFlat` ACCUMULATES, so a clause written
 * as "2x the tier" on top of the shipped tier would pay THREE times, not two. Asserting the ratio of 2 is what tells
 * those two implementations apart.
 */
public class PearlEidolon4DoublingTest {
    private static final int PEARL = 1503;
    private static final int ELATION = 1502;
    private static final int HUNTER = 1204;
        /**
     * At E4 the arming pays twice what it pays at E3 - the two ranks whose Elation-skill level is the SAME.
     *
     * <p>Note: Why E3 is the control rather than E0, which is what the first version used. Eidolon 3 grants
     * 「欢愉技等级+1」, so the Elation skill's level goes 1 -> 2 at E3 and the `elation_base` TABLE LOOKUP changes with it
     * (measured, as a ratio of 1.976 at the four-ally tier and 1.235 at the one-ally tier, neither of which is the 2.0
     * the doubling alone would give). E3 and E4 differ in exactly one thing - the doubling clause - so their ratio is
     * the doubling and nothing else.
     */
    @Test
    public void eidolonFourDoublesThePayout() {
        double atE3 = payout(3, 4);
        double atE4 = payout(4, 4);
        System.out.println("[pearl_e4] the payout at E3 = " + atE3 + " ; at E4 = " + atE4
                + " ; ratio = " + (atE4 / atE3));
        Assertions.assertTrue(atE3 > 0, "precondition: the Elation skill pays at all (" + atE3 + ")");
        Assertions.assertEquals(2.0, atE4 / atE3, 1e-6,
                "「提高原倍率的 100%」: the same percentages again, so twice the payout");
    }

    /**
     * The four added clauses are gated on the Eidolon, and they carry the SAME conditions as the clauses they double.
     *
     * <p>Note: Why this is a content invariant rather than another pair of readings. Every Eidolon below 4 already
     * changes the payout for a reason of its own - E1 raises the team's Elation, E2 raises 增笑, E3 raises the Elation
     * skill's level (and therefore the {@code elation_base} table) - so there is no pair of ranks below 4 whose
     * difference is "the doubling is absent". Measured, E2 vs E3 differ by exactly that skill-level step. What can be
     * asserted is the shape that makes the gate real: each added clause names the rank in its CONTENT (not only in the
     * rule-level field, which the duplicate guard cannot see) and repeats the conditions of its sibling.
     *
     * <p>Read from the FILE rather than from the parsed table, the same way {@code ContentDuplicateGuardTest} reads it -
     * the guard compares raw conditions, so this asserts the same thing the guard does.
     */
    @Test
    public void theAddedClausesRepeatTheirSiblingsConditionsAndNameTheRank() throws Exception {
        var path = java.nio.file.Path.of("src/main/resources/characters/" + PEARL + ".json");
        var root = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path))
                .getAsJsonObject();
        int checked = 0;
        for (var element : root.getAsJsonArray("rules")) {
            var rule = element.getAsJsonObject();
            String id = rule.get("id").getAsString();
            if (!id.startsWith("eidolon4_doubles")) {
                continue;
            }
            checked++;
            java.util.List<String> when = new java.util.ArrayList<>();
            for (var condition : rule.getAsJsonArray("when")) {
                when.add(condition.getAsString());
            }
            Assertions.assertTrue(when.contains("eidolon >= 4"),
                    id + " names the rank in its content: " + when);
            Assertions.assertTrue(when.stream().anyMatch(c -> c.contains("渲染理性")),
                    id + " repeats its sibling's armed-attacker condition: " + when);
            Assertions.assertTrue(when.stream().anyMatch(c -> c.startsWith("allies_with_path")),
                    id + " repeats its sibling's tier condition: " + when);
            Assertions.assertEquals(4, rule.get("min_eidolon").getAsInt(),
                    id + " carries the rule-level gate too");
        }
        System.out.println("[pearl_e4] doubling clauses checked = " + checked);
        Assertions.assertEquals(4, checked, "one added clause per tier");
    }

    /** The doubling follows the tier the team qualifies for, not a single flat number. */
    @Test
    public void theDoublingFollowsTheTier() {
        for (int allies : new int[] {1, 2, 3, 4}) {
            double atE3 = payout(3, allies);
            double atE4 = payout(4, allies);
            System.out.println("[pearl_e4] " + allies + " Elation allies: E3 = " + atE3 + " ; E4 = " + atE4);
            Assertions.assertTrue(atE3 > 0, "precondition: the skill pays at the " + allies + "-ally tier");
            Assertions.assertEquals(2.0, atE4 / atE3, 1e-6,
                    "the doubling applies at the " + allies + "-ally tier as well");
        }
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** What arming the team pays: (armed - unarmed) for one ally's basic attack on the same enemy. */
    private static double payout(int eidolonRank, int elationAllies) {
        double unarmed = attackingDamage(eidolonRank, elationAllies, false);
        double armed = attackingDamage(eidolonRank, elationAllies, true);
        return armed - unarmed;
    }

    private static double attackingDamage(int eidolonRank, int elationAllies, boolean armTheTeam) {
        List<Character> team = new ArrayList<>();
        team.add(CharacterFactory.create(PEARL, 80, false, null, null, eidolonRank));
        for (int i = 1; i < elationAllies; i++) {
            team.add(CharacterFactory.create(ELATION, 80));
        }
        team.add(CharacterFactory.create(HUNTER, 80));
        Battle battle = new Battle(team, List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character pearl = battle.characters.get(0);
        Character attacker = battle.characters.get(battle.characters.size() - 1);
        if (armTheTeam) {
            battle.fireTriggers(TriggerEvent.CAST_SETUP, pearl, pearl, 0, 0, SkillCategory.ELATION_DAMAGE);
        }
        double before = battle.enemies.getFirst().getCurrentHp();
        battle.castImmediate(attacker.getSkills().get(SkillType.COMMON), attacker,
                List.of(battle.enemies.getFirst()));
        return before - battle.enemies.getFirst().getCurrentHp();
    }
}
