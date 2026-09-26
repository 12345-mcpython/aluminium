package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The Eidolon gate ({@code "min_eidolon"}) and the rank it is compared against.
 *
 * <p><b>What an Eidolon is, in this engine.</b> Nothing but a number plus a gate: the mechanic itself is an
 * ordinary rule in {@code resources/characters/<cid>.json}, the assembly point hands the character a rank
 * ({@code CharacterFactory.create(..., eidolonRank)}), and the interpreter skips a rule whose
 * {@code min_eidolon} is above it. So {@code eidolons.json} — which has been generated all along and never
 * loaded — stays reference material: its text is what the rule's {@code source} cites, the same as a trace's.
 *
 * <p>The cases below pin the two halves that could silently disagree: the gate reads the <b>rule owner's</b> rank
 * (not a teammate's, not a team-wide maximum), and the rank survives a copy (a copy is the same character, so an
 * Eidolon must not vanish because somebody cloned the combatant).
 */
public class EidolonGateTest {
    /** Himeko: no shipped rule file, so the table under test is the only one. */
    private static final int OWNER = 1003;
    private static final int ALLY = 1202;
    private static final int LEVEL = 80;

    // ==================================================================
    // 1. The gate
    // ==================================================================

    @Test
    public void aGatedRuleStaysQuietBelowItsRank() {
        for (int rank = 0; rank < 2; rank++) {
            Battle battle = battleWith(rank, gatedRule(2));
            Assertions.assertEquals(0, fire(battle, battle.characters.getFirst()),
                    "rank " + rank + " is below the rule's min_eidolon 2");
        }
    }

    @Test
    public void aGatedRuleFiresAtItsRankAndAbove() {
        for (int rank : List.of(2, 3, Constant.EIDOLON_MAX_RANK)) {
            Battle battle = battleWith(rank, gatedRule(2));
            Assertions.assertEquals(1, fire(battle, battle.characters.getFirst()),
                    "rank " + rank + " unlocks min_eidolon 2");
        }
    }

    @Test
    public void theGateIsPerRule() {
        Battle battle = battleWith(0, gatedRule(2), ungatedRule());
        Assertions.assertEquals(1, fire(battle, battle.characters.getFirst()),
                "the ungated rule in the same table still fires at rank 0");
    }

    /**
     * The rank belongs to the <b>owner of the table</b>: a ranked teammate must not unlock somebody else's
     * Eidolon. This is the failure a team-wide lookup would cause, and it is exactly what a rule author would
     * never notice by reading the file.
     */
    @Test
    public void theGateReadsTheRulesOwnerNotATeammate() {
        Character unranked = owner(OWNER, 0, gatedRule(2));
        Battle battle = new Battle(List.of(unranked, CharacterFactory.create(ALLY, LEVEL, true, null, null, 2)),
                List.of(dummy()), new Random(0));
        battle.startBattle();

        Assertions.assertEquals(0, battle.fireTriggers(TriggerEvent.ALLY_ATTACK, unranked, null, 1, 0),
                "a rank-2 ally's rank must not unlock the rank-0 character's Eidolon");
    }

    // ==================================================================
    // 2. The rank itself
    // ==================================================================

    @Test
    public void theRankSurvivesACopy() {
        Character original = owner(OWNER, 4, ungatedRule());
        Character copy = new Character(original);

        Assertions.assertEquals(4, copy.getEidolonRank(),
                "the rank is configuration, not battle state: a copy is the same character, so its Eidolons "
                        + "must still be there");
    }

    @Test
    public void aRankOutsideTheRangeIsRejectedAtConstruction() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> CharacterFactory.create(OWNER, LEVEL, true, null, null, -1));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> CharacterFactory.create(OWNER, LEVEL, true, null, null, Constant.EIDOLON_MAX_RANK + 1),
                "a rank nobody can reach would make the Eidolon silently non-existent");
        Assertions.assertEquals(Constant.EIDOLON_MAX_RANK,
                CharacterFactory.create(OWNER, LEVEL, true, null, null, Constant.EIDOLON_MAX_RANK)
                        .getEidolonRank());
    }

    @Test
    public void anUnreachableOrMeaninglessGateIsRejectedAtLoadTime() {
        IllegalArgumentException zero = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(gatedRule(0))));
        Assertions.assertTrue(zero.getMessage().contains("min_eidolon"), zero.getMessage());

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(gatedRule(Constant.EIDOLON_MAX_RANK + 1))));
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static TriggerSpec gatedRule(int minEidolon) {
        TriggerSpec spec = ungatedRule();
        TriggerSpecs.set(spec, "minEidolon", minEidolon);
        return spec;
    }

    private static TriggerSpec ungatedRule() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "GAIN_SKILL_POINT");
        TriggerSpecs.set(effect, "amount", 1.0);
        return TriggerSpecs.rule("ALLY_ATTACK", null, effect);
    }

    private static Battle battleWith(int rank, TriggerSpec... specs) {
        Battle battle = new Battle(List.of(owner(OWNER, rank, specs)), List.of(dummy()), new Random(0));
        battle.startBattle();
        return battle;
    }

    private static Character owner(int cid, int rank, TriggerSpec... specs) {
        Character character = CharacterFactory.create(cid, LEVEL, true, null, null, rank);
        character.setTriggerTable(new TriggerTable(cid, List.of(specs)));
        return character;
    }

    private static int fire(Battle battle, Character actor) {
        return battle.fireTriggers(TriggerEvent.ALLY_ATTACK, actor, null, 1, 0);
    }

    private static Enemy dummy() {
        return Enemy.fromAttributes("dummy", 1_000_000, 0, 100, 100);
    }
}
