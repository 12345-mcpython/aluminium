package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 飞向粉色的明天 (22006): 「开拓者•记忆装备时，使我方全体目标造成的伤害提高 #2%，强化普攻【明天，一同写下！】造成的
 * 伤害提高 #3%」.
 *
 * <p><b>What it needed.</b> Two things: {@code self_cid} (a light cone file is shared by every wearer, so "when worn by
 * the Memory Trailblazer" is a number - 8007 / 8008), and the enhanced basic's key. {@code skills.json} carries
 * 【明天，一同写下！】 as <b>8007/8008 slot 8</b>, so on {@code DEALING_DAMAGE} the key is {@code cid*100+8} =
 * 800708 / 800808 - which {@code from_skill_id} already reports there.
 *
 * <p><b>And a real defect.</b> The file still had its own crit-damage rule while the cone's property already grants the
 * same share: the wearer read DOUBLE. The rule is gone.
 */
public class ConeTwentyTwoThousandSixTest {

    private static final int LEVEL = 80;
    private static final int MEMORY_TRAILBLAZER = 8007;
    private static final int MEMORY_TRAILBLAZER_ALT = 8008;
    private static final int OTHER = 1002;
    private static final int ALLY = 1001;
    private static final int CONE = 22006;

    /**
     * The crit-damage share is the cone's property, once - not twice.
     */
    @Test
    public void theCritShareIsNotCountedTwice() {
        Character withCone = CharacterFactory.create(MEMORY_TRAILBLAZER, LEVEL, true,
                Weapon.build(CONE, LEVEL, false, 1));
        Character bare = CharacterFactory.create(MEMORY_TRAILBLAZER, LEVEL);
        battle(List.of(withCone, CharacterFactory.create(ALLY, LEVEL)));
        battle(List.of(bare, CharacterFactory.create(ALLY, LEVEL)));
        Assertions.assertEquals(0.12, withCone.getAttribute(AttributeType.CRIT_ATTACK).get()
                - bare.getAttribute(AttributeType.CRIT_ATTACK).get(), 1e-9,
                "the property's 12% once (a duplicate rule used to make it 24%)");
    }

    /**
     * Only the Memory Trailblazer's wear grants the party damage share; an ordinary wearer grants nothing.
     */
    @Test
    public void thePartyShareOnlyExistsForTheMemoryTrailblazer() {
        Assertions.assertEquals(0.08, partyShare(MEMORY_TRAILBLAZER, 1), 1e-9, "8007 wears it: +8%");
        Assertions.assertEquals(0.08, partyShare(MEMORY_TRAILBLAZER_ALT, 1), 1e-9, "8008 wears it: +8%");
        Assertions.assertEquals(0.0, partyShare(OTHER, 1), 1e-9, "any other wearer: nothing");
    }

    /**
     * The enhanced basic's own damage is raised; another skill or another attacker is not.
     */
    @Test
    public void theEnhancedBasicIsRaised() {
        Fixture f = new Fixture(MEMORY_TRAILBLAZER, 1);
        // The party share (#2) is also on the wearer, so the basic's own share is the DIFFERENCE between two keys.
        double withoutTheBasic = f.boostWithKey(800701);   // her ordinary basic, slot 1
        Assertions.assertEquals(0.08, withoutTheBasic, 1e-9, "the party share applies to everything she deals");
        Assertions.assertEquals(0.6, f.boostWithKey(800708) - withoutTheBasic, 1e-9,
                "【明天，一同写下！】 adds +60% on top");
        Assertions.assertEquals(withoutTheBasic, f.boostWithKey(100208), 1e-9,
                "another character's slot-8 skill is not this basic");
    }

    /**
     * Both shares are per rank (#2 = 8/10/12/14/16%, #3 = 60/70/80/90/100%).
     */
    @Test
    public void theSharesFollowTheRank() {
        double[] party = {0.08, 0.1, 0.12, 0.14, 0.16};
        double[] basic = {0.6, 0.7, 0.8, 0.9, 1.0};
        for (int rank = 1; rank <= 5; rank++) {
            Assertions.assertEquals(party[rank - 1], partyShare(MEMORY_TRAILBLAZER, rank), 1e-9,
                    "rank " + rank + " party share");
            Fixture f = new Fixture(MEMORY_TRAILBLAZER, rank);
            Assertions.assertEquals(basic[rank - 1], f.boostWithKey(800708) - f.boostWithKey(800701), 1e-9,
                    "rank " + rank + " enhanced basic");
        }
    }

    // ==================================================================

    private static double partyShare(int cid, int rank) {
        Character wearer = CharacterFactory.create(cid, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        battle(List.of(wearer, ally));
        return ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
    }

    private static void battle(List<Character> party) {
        new Battle(new ArrayList<>(party), new ArrayList<>(List.of(BattleFixture.monster())),
                BattleFixture.seeded()).startBattle();
    }

    private static final class Fixture {
        private final Character wearer;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private Fixture(int cid, int rank) {
            wearer = CharacterFactory.create(cid, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
            battle = new Battle(new ArrayList<>(List.of(wearer, CharacterFactory.create(ALLY, LEVEL))),
                    new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
            battle.startBattle();
        }

        /** Settles one instance carrying {@code skillKey} and reports the boost it picked up. */
        private double boostWithKey(int skillKey) {
            Damage damage = new Damage(wearer, enemy, DamageElement.FIRE, DamageType.NORMAL, 1000);
            damage.setSkillKey(skillKey);
            battle.applyDamage(enemy, damage);
            return damage.boostArea().getRate() - 1.0;
        }
    }
}
