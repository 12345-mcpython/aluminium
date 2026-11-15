package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Light cone 23053: every skill point the wearer SPENDS makes its elation damage ignore 5% more defence, up to 4 layers.
 *
 * <p>`SKILL_POINT_SPENT` is already a trigger event (fired by the policy's own listener, so a spend at the cap -- which
 * credits nothing -- does not fire it), and the instance route already supports DEFENCE_IGNORE with per_stack. The judge
 * therefore spends for real and re-reads the settled damage, and it also checks that a NON-elation hit is untouched.
 *
 * <p>Also covers 花花世界迷人眼 「装备者在场时，队伍中每有 1 名「欢愉」命途角色，战技点上限提高 1 点，最多提高 3 点」. Nothing new was
 * needed: {@code allies_with_path:<PATH>} is an existing numeric variable over our living units, and
 * {@code RAISE_SKILL_POINT_CAP} is the op that raises the BATTLE's cap (readers 1015 / 1306). The sentence's ceiling is
 * expressed by having exactly three rules (≥ 1, ≥ 2, ≥ 3), so four or more Elation allies still add three.
 */
public class DazzledByAFloweryWorldTest {
    private static final int CONE = 23053;
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int CAP = 4;
    private static final double PER_POINT = 0.05;
    // From the merged DazzledByAFloweryWorldConeTest: its wearer is a DIFFERENT character id (the old class's WEARER, renamed).
    private static final int CONE_WEARER = 1002;   // a non-Elation path, so the wearer is not counted himself
    /** Elation-path characters, used as the counted allies. */
    private static final int[] ELATION = {1501, 1502, 1503, 1505};

    private Character wearer;
    private Enemy enemy;

    private Battle battle(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(wearer), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        wearer.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(DoubleValue.Modifier.pure(-1.0, DoubleValue.Modifier.ModifierSource.BUFF, 230531));
        return battle;
    }

    private double hit(Battle battle, DamageType type) {
        return battle.applyDamage(enemy, new Damage(wearer, enemy, DamageElement.FIRE, type, 1000));
    }

    /**
     * Spend through a REAL Skill cast: the camp check lives in the policy ({@code Battle.applySkillPointCost}'s doc says
     * so), and a bare {@code spendSkillPoint()} therefore refuses when no actor is set up -- measured.
     */
    private void spend(Battle battle, int times) {
        var skill = wearer.getSkills().values().stream()
                .filter(candidate -> candidate.getData() != null
                        && candidate.getData().getCategory() == com.laosun.aluminium.enums.SkillCategory.BPSKILL)
                .findFirst().orElseThrow(() -> new AssertionError("the wearer has no Skill to cast"));
        for (int i = 0; i < times; i++) {
            battle.gainSkillPoint(1);   // keep the pool from capping out: a spend at the cap credits nothing
            Assertions.assertTrue(battle.applySkillPointCost(skill, wearer),
                    "the pool must have room to spend (point " + (i + 1) + ")");
        }
    }

    @Test
    public void eachSpentPointLowersTheEnemysDefenceForElationDamage() {
        Battle battle = battle(true);
        double none = hit(battle, DamageType.ELATION);
        spend(battle, 1);
        double one = hit(battle, DamageType.ELATION);
        spend(battle, 3);
        double four = hit(battle, DamageType.ELATION);
        spend(battle, 2);
        double capped = hit(battle, DamageType.ELATION);
        System.out.println("[23053] none=" + none + " one=" + one + " four=" + four + " after six=" + capped
                + " layers=" + wearer.getBuffManager().stacksOf("internal:23053/spent_layers"));
        Assertions.assertTrue(one > none, "one spent point must raise the settled elation damage");
        // The VALUE, not just the direction (discipline 189): the settled damage is `base / (effDef + 200 + 10L)`, and
        // the ignore multiplies effDef. Everything here comes from the engine's own numbers, so a wrong `percent` has to
        // move this line -- a monotonicity-only judge let `5% -> 2.5%` pass (measured, 0 red).
        double defence = enemy.getAttribute(AttributeType.DEFENCE).get();
        double level = wearer.getLevel();
        double zoneNone = 1.0 / (defence + 200 + 10 * level);
        double zoneFour = 1.0 / (defence * (1 - 4 * PER_POINT) + 200 + 10 * level);
        double expectedFour = none * zoneFour / zoneNone;
        System.out.println("[23053] defence=" + defence + " level=" + level + " expectedFour=" + expectedFour
                + " measured=" + four);
        if (Math.abs(expectedFour - none) > 1e-9) {
            Assertions.assertEquals(expectedFour, four, Math.abs(expectedFour) * 1e-6,
                    "four layers must ignore exactly " + (4 * PER_POINT) + " of the defence");
        }
        Assertions.assertTrue(four > one, "and four must raise it further (" + PER_POINT + " per layer)");
        Assertions.assertEquals(four, capped, 1e-9, "but the clause caps at " + CAP + " layers");
        Assertions.assertEquals(CAP, wearer.getBuffManager().stacksOf("internal:23053/spent_layers"), "counter at the cap");
    }

    @Test
    public void aNonElationHitIsUntouched() {
        Battle battle = battle(true);
        double before = hit(battle, DamageType.NORMAL);
        spend(battle, 4);
        double after = hit(battle, DamageType.NORMAL);
        System.out.println("[23053] ordinary damage before=" + before + " after four spends=" + after);
        Assertions.assertEquals(before, after, 1e-9, "the clause names ELATION damage only (false case)");
    }

    @Test
    public void withoutTheConeSpendingChangesNothing() {
        Battle battle = battle(false);
        double none = hit(battle, DamageType.ELATION);
        spend(battle, 4);
        System.out.println("[23053] without the cone: " + none + " -> " + hit(battle, DamageType.ELATION));
        Assertions.assertEquals(none, hit(battle, DamageType.ELATION), 1e-9, "no cone, no ignore (false case)");
    }

    /**
     * The cap follows the number of Elation allies, and stops at +3.
     */
    @Test
    public void theCapFollowsTheElationAlliesAndStopsAtThree() {
        Assertions.assertEquals(5, capWith(0), "no Elation ally: the ordinary cap");
        Assertions.assertEquals(6, capWith(1), "one Elation ally: +1");
        Assertions.assertEquals(7, capWith(2), "+2");
        Assertions.assertEquals(8, capWith(3), "+3");
        Assertions.assertEquals(8, capWith(4), "「最多提高 3 点」 - a fourth ally adds nothing");
    }

    /**
     * The wearer's own path counts too: 「队伍中每有 1 名」 is the party, not "the other allies".
     */
    @Test
    public void theWearerCountsWhenSheIsAnElationCharacter() {
        Character wearer = CharacterFactory.create(ELATION[0], LEVEL, true, Weapon.build(CONE, LEVEL, false, 1));
        Battle battle = new Battle(new ArrayList<>(List.of(wearer, CharacterFactory.create(CONE_WEARER, LEVEL))),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
        battle.startBattle();
        Assertions.assertEquals(6, battle.getSkillPointMax(),
                "the wearer is an Elation character herself, so the party has one");
    }

    // ==================================================================

    private static int capWith(int elationAllies) {
        List<Character> party = new ArrayList<>();
        party.add(CharacterFactory.create(CONE_WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1)));
        for (int i = 0; i < elationAllies; i++) {
            party.add(CharacterFactory.create(ELATION[i], LEVEL));
        }
        Battle battle = new Battle(party, new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
        battle.startBattle();
        return battle.getSkillPointMax();
    }
}
