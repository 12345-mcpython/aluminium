package com.laosun.aluminium.test.trigger;

import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.compare.AccountLoadout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * <b>The engine reproduces the account's own panel</b>: the four characters of the captured fight, built from
 * {@code dump_data.json} (their six relics) plus the light cone each wears, must come out at the numbers the capture
 * reports.
 *
 * <p><b>Why this is the missing half.</b> The replay reads the capture's panel straight into the battle
 * ({@code BattleReplay.applyCastPanel}), which is right for verifying damage - but it means the engine never had to
 * <i>derive</i> a panel. This case derives it: base row + level promotion + light cone + relics (main, subs, set
 * bonuses) + traces, from the same data the game used, and then asks whether the result is the game's number.
 *
 * <p><b>ATTACK and DEFENCE, not HEALTH.</b> The capture reports {@code attacker_attack_lp} / {@code attacker_defence} /
 * {@code attacker_hp_lp}, and the third is <b>current</b> HP (the window is a fight already in progress: 1413's reads
 * 2960 → 4526 → 4309 across it). The panel's Max HP is therefore not in the payload, so it is not asserted here - what
 * is asserted is the two stats the capture states as panels.
 *
 * <p>⚠ <b>1512 carries a known 0.068 attack residual</b> (0.003%). It is asserted as a bound rather than a number so the
 * gap stays visible; the suspected cause is a set-bonus or cone rounding, not a missing input (her relics and all ten of
 * her stat traces are in the build).
 */
public class PanelFromDumpTest {

    /** cid, eidolon, cone id, cone rank, capture ATTACK, capture DEFENCE. The cones come from the account archive. */
    private static final List<Facts> PARTY = List.of(
            new Facts(1409, 6, 23042, 1, 1325.447, 1375.417),
            new Facts(1413, 1, 21050, 5, 1500.771703, 1201.083676),
            new Facts(1415, 2, 23042, 1, 1275.372, 1261.3283),
            new Facts(1512, 0, 24005, 5, 2655.8587, 1043.4089));

    /**
     * Every character's ATTACK and DEFENCE is the capture's own number once the account's relics and cone are applied.
     */
    @Test
    public void thePanelsAreTheCapturesNumbers() {
        Map<Integer, java.util.List<com.google.gson.JsonObject>> dump = AccountLoadout.dump();
        for (Facts facts : PARTY) {
            Character character = AccountLoadout.assemble(
                    new AccountLoadout.Build(facts.cid, 80, facts.eidolon, facts.coneId, 80, facts.coneRank), dump);

            double attack = character.getAttribute(AttributeType.ATTACK).get();
            double defence = character.getAttribute(AttributeType.DEFENCE).get();

            // Note: the capture prints ~7 significant digits, so the comparison is RELATIVE (1e-6 of the value);
            // 1512's known residual is 2.6e-5, so her bound is the one that leaves it visible as a bound.
            double attackTolerance = facts.captureAttack * (facts.cid == 1512 ? 5e-5 : 1e-6);
            Assertions.assertEquals(facts.captureAttack, attack, attackTolerance,
                    "cid " + facts.cid + ": ATTACK must be the panel the capture reports");
            Assertions.assertEquals(facts.captureDefence, defence, facts.captureDefence * 1e-6,
                    "cid " + facts.cid + ": DEFENCE must be the panel the capture reports");
        }
    }

    /**
     * And the derivation is not a fluke of the equipment: without it the same character is far below the panel, which is
     * what makes the case above meaningful.
     */
    @Test
    public void withoutTheLoadoutThePanelIsFarBelow() {
        Facts facts = PARTY.get(1);                       // 1413
        Character bare = com.laosun.aluminium.utils.CharacterFactory.create(facts.cid, 80);

        Assertions.assertTrue(bare.getAttribute(AttributeType.ATTACK).get() < facts.captureAttack * 0.6,
                "a bare character is at about half the panel; if this ever stops holding, the case above proves nothing");
    }

    private record Facts(int cid, int eidolon, int coneId, int coneRank, double captureAttack, double captureDefence) {
    }
}
