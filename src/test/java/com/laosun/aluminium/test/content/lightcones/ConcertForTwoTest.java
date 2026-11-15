package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillType;
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
 * Light cone 21043: the wearer deals 4% more damage for EVERY character holding a shield.
 *
 * <p>This is the "场上计数谓词" row's real shape. It needed an INSTANCE slot for the damage-boost attribute plus a live
 * {@code per_stack} count: a written modifier could only ever be a snapshot, and {@code BOOST_DAMAGE} ignores {@code per_stack}.
 * The judge changes the field BETWEEN hits, so a snapshot would show up as a wrong second reading.
 *
 * <p>Also covers the reading of 两个人的演唱会 「场上每有一名持有护盾的角色，装备者造成的伤害提高 #2%」 as a count of OUR shielded
 * units - not an enemy state count, not a named counter. The engine's {@code shielded_count} (read on the instance, because the
 * count changes between hits) is that variable, and the shipped file already uses
 * {@code MODIFY_ATTR ALL_DAMAGE_TYPE_BOOST instance: true per_stack: shielded_count}. The fixture compares the <b>delta</b> of
 * the boost zone between casts: the wearer's own kit contributes a constant boost, which the delta cancels, so the number read
 * is exactly the cone's share.
 */
public class ConcertForTwoTest {
    private static final int CONE = 21043;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final double PER_SHIELDED = 0.04;
    // From the merged ConcertForTwoConeTest: its wearer is a DIFFERENT character id (the old class's WEARER, renamed).
    private static final int CONE_WEARER = 1002;
    private static final int ALLY_A = 1001;
    private static final int ALLY_B = 1204;

    private Character wearer;
    private Character ally;
    private Enemy enemy;

    private Battle battle(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        ally = CharacterFactory.create(ALLY, LEVEL);
        enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        wearer.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(DoubleValue.Modifier.pure(-1.0, DoubleValue.Modifier.ModifierSource.BUFF, 210431));
        return battle;
    }

    private double hit(Battle battle) {
        return battle.applyDamage(enemy, new Damage(wearer, enemy, DamageElement.FIRE, DamageType.NORMAL, 1000));
    }

    @Test
    public void eachShieldedCharacterAddsItsShare() {
        Battle battle = battle(true);
        double none = hit(battle);
        battle.grantShield(wearer, 500);
        double one = hit(battle);
        battle.grantShield(ally, 500);
        double two = hit(battle);
        System.out.println("[21043] none=" + none + " one=" + one + " two=" + two
                + " share=" + (one / none - 1) + " / " + (two / none - 1));
        Assertions.assertEquals(1 + PER_SHIELDED, one / none, 1e-6, "one shielded character is +4%");
        Assertions.assertEquals(1 + 2 * PER_SHIELDED, two / none, 1e-6,
                "two are +8% -- a LIVE count read per hit, not a snapshot");
    }

    @Test
    public void aShieldThatIsGoneStopsCounting() {
        Battle battle = battle(true);
        double none = hit(battle);
        battle.grantShield(ally, 500);
        double with = hit(battle);
        ally.setShield(0);
        double gone = hit(battle);
        System.out.println("[21043] with=" + with + " after the shield is gone=" + gone + " none=" + none);
        Assertions.assertEquals(1 + PER_SHIELDED, with / none, 1e-6, "counted while it is up");
        Assertions.assertEquals(1.0, gone / none, 1e-6, "and not counted once it is gone (false case)");
    }

    @Test
    public void withoutTheConeNothingMoves() {
        Battle battle = battle(false);
        double none = hit(battle);
        battle.grantShield(wearer, 500);
        battle.grantShield(ally, 500);
        System.out.println("[21043] without the cone: " + none + " -> " + hit(battle));
        Assertions.assertEquals(none, hit(battle), 1e-9, "no cone, no scaling (false case)");
    }

    /**
     * Zero shields add nothing; one shielded ally adds 4%; two add 8% (the S1 share).
     */
    @Test
    public void theShareFollowsTheNumberOfShieldedAllies() {
        Fixture f = new Fixture();
        double none = f.boost();

        f.battle.grantShield(f.allyA, 100);
        double one = f.boost();

        f.battle.grantShield(f.allyB, 100);
        double two = f.boost();

        Assertions.assertEquals(0.04, one - none, 1e-9,
                "one shielded ally: +4% (S1 #2)");
        Assertions.assertEquals(0.08, two - none, 1e-9,
                "two shielded allies: +8%, i.e. the share is per shielded unit, not per hit");
    }

    /**
     * The wearer's own shield counts too - 「场上每有一名持有护盾的角色」 is about the field, not about allies only.
     */
    @Test
    public void theWearersOwnShieldCounts() {
        Fixture f = new Fixture();
        double none = f.boost();
        f.battle.grantShield(f.wearer, 100);
        Assertions.assertEquals(0.04, f.boost() - none, 1e-9,
                "a shield on the wearer is a shielded character on the field");
    }

    // ==================================================================

    private static final class Fixture {
        private final Character wearer;
        private final Character allyA;
        private final Character allyB;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;
        private final List<Damage> seen = new ArrayList<>();

        private Fixture() {
            wearer = CharacterFactory.create(CONE_WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1));
            allyA = CharacterFactory.create(ALLY_A, LEVEL);
            allyB = CharacterFactory.create(ALLY_B, LEVEL);
            battle = new Battle(new ArrayList<>(List.of(wearer, allyA, allyB)),
                    new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
            battle.addDamageObserver((Damage damage, double applied, double hpLost, double shield, boolean died) ->
                    seen.add(damage));
            battle.startBattle();
        }

        /**
         * The boost zone of the wearer's own basic-attack instance, as a share.
         */
        private double boost() {
            seen.clear();
            battle.castImmediate(wearer.getSkills().get(SkillType.COMMON), wearer, List.of(enemy));
            battle.processRequests();
            return seen.stream()
                    .filter(d -> d.getType() == DamageType.NORMAL)
                    .filter(d -> d.getCastCategory() == SkillCategory.NORMAL)
                    .mapToDouble(d -> d.boostArea().getRate() - 1.0)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("the basic attack settled no instance"));
        }
    }
}
