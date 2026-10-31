package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 两个人的演唱会 (21043): 「场上每有一名持有护盾的角色，装备者造成的伤害提高 #2%」.
 *
 * <p><b>What it needed.</b> A count of OUR shielded units - not an enemy state count, not a named counter. The engine's
 * {@code shielded_count} (read on the instance, because the count changes between hits) is that variable, and the
 * shipped file already uses {@code MODIFY_ATTR ALL_DAMAGE_TYPE_BOOST instance: true per_stack: shielded_count}. The
 * status row calling it "no spelling exists" was a stale record - this test pins the behaviour so it cannot quietly go
 * back to a flat unconditional share.
 *
 * <p>The fixture compares the <b>delta</b> of the boost zone between casts: the wearer's own kit contributes a constant
 * boost, which the delta cancels, so the number read is exactly the cone's share.
 */
public class ConeTwoOneZeroFourThreeTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int ALLY_A = 1001;
    private static final int ALLY_B = 1204;
    private static final int CONE = 21043;

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
            wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1));
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

