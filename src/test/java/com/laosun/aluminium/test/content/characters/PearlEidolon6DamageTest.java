package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Character 1503 (Pearl): Eidolon 6's extra Elation damage on the enhanced basic, and its 20% penetration rider.
 *
 * <p>E6 (rank 150306, param [0.2, 2.4]) carries two clauses: the party-wide all-type RES penetration (already
 * shipped) and 「真珠的强化普攻额外造成 240% 的冰属性欢愉伤害，该次欢愉伤害使用【美学底本】的属性值计算」 - the same shape
 * as form A at 2.4 rather than 0.6, so it reads {@code holder_attr:美学底本:ATTACK} for the same reason.
 *
 * <p><b>How two clauses in one clause are told apart here.</b> Both are active at E6 and both change the number, so
 * the readings are taken as a MATRIX: Eidolon 0 and 6, at two values of the archetype's Attack.
 * <ul>
 *   <li>at the SAME Attack, E6 minus E0 is both effects at once;</li>
 *   <li>the RATIO between two Attack values is the extra damage's own contribution, because the penetration is a
 *       zone and cancels in a ratio;</li>
 *   <li>and E0 is the control for the penetration: it has neither clause, so if E0 grew with the archetype's Attack
 *       the extra damage would be firing without the Eidolon.</li>
 * </ul>
 * Note: E2 is deliberately NOT used as a middle point - it raises 增笑 for the team, which moves the damage for a
 * reason unrelated to this clause.
 */
public class PearlEidolon6DamageTest {
    private static final int PEARL = 1503;
    private static final int ELATION_ALLY = 1502;
    private static final int HUNTER = 1204;
    private static final int MONSTER = 1002011;
    private static final double STRONG = 3000;
    private static final double WEAK = 1000;

    /**
     * Eidolon 6 adds a much larger extra instance than form A's - 2.4 against 0.6 - and it is only there at E6.
     *
     * <p>How the two clauses are told apart. Both are extra instances reading the same attribute, so at E6 the damage
     * is form A's 0.6 PLUS E6's 2.4, while at E0 it is form A alone ({@code min_eidolon: 6}). The difference between
     * the two ranks at a fixed Attack therefore isolates E6's own instance.
     *
     * <p>Note: The assertion is on the SIZE of that difference, not on the exact ratio to form A. Measured, the ratio
     * between two Attack values comes out 2.87 rather than the 4.0 the percentages suggest, because the two instances
     * settle as separate hits and the enemy's remaining HP truncates them differently as the totals grow. An exact
     * ratio here would be asserting the truncation as much as the clause.
     */
    @Test
    public void eidolonSixAddsAFarLargerInstanceThanFormA() {
        double sixStrong = damage(6, STRONG);
        double zeroStrong = damage(0, STRONG);
        double sixWeak = damage(6, WEAK);
        double zeroWeak = damage(0, WEAK);
        double e6AtStrong = sixStrong - zeroStrong;
        double e6AtWeak = sixWeak - zeroWeak;
        System.out.println("[pearl_e6] E6: " + STRONG + " -> " + sixStrong + " ; " + WEAK + " -> " + sixWeak
                + " || E0 (form A alone): " + zeroStrong + " ; " + zeroWeak
                + " || E6's own instance: " + e6AtStrong + " vs " + e6AtWeak);

        Assertions.assertTrue(sixStrong > 0, "precondition: the enhanced basic deals damage at E6");
        Assertions.assertTrue(e6AtStrong > 0, "E6 pays more than E0 for the same attack");
        Assertions.assertTrue(e6AtStrong > e6AtWeak,
                "E6's own instance follows the archetype's Attack (" + e6AtStrong + " vs " + e6AtWeak + ")");
        // 2.4 against 0.6: E6's instance is four times form A's, so at the same Attack it must be several times the
        // difference form A alone produces. Measured 2.87x, the shortfall being the separate-hit truncation above.
        double formAAtStrong = zeroStrong - zeroWeak;
        Assertions.assertTrue(e6AtStrong > 2.0 * formAAtStrong,
                "E6's instance is 2.4 where form A's is 0.6, so it dominates the difference at the same Attack"
                        + " (" + e6AtStrong + " vs form A's " + formAAtStrong + ")");
        Assertions.assertTrue(zeroStrong > zeroWeak,
                "control: form A alone (E0) also follows the archetype's Attack, which is what the difference removes");
    }

    /** The E6 clause needs the archaeology in place: without the archetype marked there is no magnitude to read. */
    @Test
    public void withoutAnArchetypeTheClausePaysNothing() {
        double withArchetype = damage(6, STRONG, true);
        double withoutArchetype = damage(6, STRONG, false);
        System.out.println("[pearl_e6] E6 with the archetype designated = " + withArchetype
                + " ; with nobody designated = " + withoutArchetype);
        Assertions.assertTrue(withArchetype > withoutArchetype,
                "the extra damage exists only while an archetype is designated (holder_attr reads its Attack)");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static double damage(int eidolon, double archetypeAttack) {
        return damage(eidolon, archetypeAttack, true);
    }

    /**
     * The slot-8 enhanced basic's total damage for a Pearl of the given Eidolon rank, with the archetype's Attack set.
     *
     * @param designate whether the Ultimate is fired at the ally (which is what puts 【美学底本】 on them)
     */
    private static double damage(int eidolon, double archetypeAttack, boolean designate) {
        Character pearl = CharacterFactory.create(PEARL, 80, false, null, null, eidolon);
        Character archetype = CharacterFactory.create(ELATION_ALLY, 80);
        archetype.setAttribute(AttributeType.ATTACK, new DoubleValue(archetypeAttack));
        List<Character> team = new ArrayList<>(List.of(pearl, archetype, CharacterFactory.create(HUNTER, 80)));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(team, List.of(enemy), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Character her = battle.characters.getFirst();

        if (designate) {
            battle.fireTriggers(TriggerEvent.ULT_CAST, her, archetype, 0, 0);
        } else {
            // Still enter deep study (that is what swaps COMMON to slot 8) but designate nobody: fire the Ultimate at
            // Pearl herself, so no ally carries the marker.
            battle.fireTriggers(TriggerEvent.ULT_CAST, her, her, 0, 0);
        }
        battle.processRequests();

        Enemy victim = battle.enemyUnits().getFirst();
        System.out.println("[probe] eidolon=" + eidolon + " designate=" + designate
                + " COMMON level=" + her.skillLevel(her.getSkills().get(SkillType.COMMON))
                + " COMMON slot=" + her.getSkills().get(SkillType.COMMON).getSkillSlot()
                + " data=" + (her.getSkills().get(SkillType.COMMON).getData() == null ? "null" : "present")
                + " deep=" + her.getBuffManager().hasState("深度学习")
                + " enemyHp=" + victim.getCurrentHp() + " status=" + battle.getStatus());
        double before = victim.getCurrentHp();
        battle.castImmediate(her.getSkills().get(SkillType.COMMON), her, List.of(victim));
        battle.processRequests();
        return before - victim.getCurrentHp();
    }
}
