package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 纵然山河万程 (23051): 「获得【卫戍】的目标，造成的伤害提高 #2%，若目标拥有召唤物则造成的伤害额外提高 #3%」.
 *
 * <p><b>What it needed.</b> One filter over our side: {@code allies_with_summons} - the allies that own at least one
 * memosprite. The two shares must ADD for a summon owner (same-attribute modifiers replace, measured), so both carry the
 * same {@code buff} name and {@code max_stacks: 2}: the owner gets two stackable instances (#2 + #3), everybody else one.
 */
public class ConeTwentyThreeThousandFiftyOneTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int SUMMON_OWNER = 1409;   // memory path: can have a memosprite
    private static final int PLAIN_ALLY = 1001;
    private static final int CONE = 23051;

    /**
     * The summon owner gets #2 + #3; a plain ally gets #2 only.
     */
    @Test
    public void theSummonOwnerGetsTheExtraShare() {
        Fixture f = new Fixture(1, true);
        f.castUltimate();
        Assertions.assertEquals(0.24, f.boost(f.wearer), 1e-9, "the wearer, no summon: #2");
        Assertions.assertEquals(0.24, f.boost(f.plain), 1e-9, "a plain ally: #2");
        Assertions.assertEquals(0.24 + 0.12, f.boost(f.summonOwner), 1e-9,
                "「若目标拥有召唤物则造成的伤害额外提高 12%」 - both shares add");
    }

    /**
     * Without a memosprite the same ally gets only #2, and the shares are per rank.
     */
    @Test
    public void withoutASummonThereIsNoExtraShareAndTheSharesFollowTheRank() {
        Fixture bare = new Fixture(1, false);
        bare.castUltimate();
        Assertions.assertEquals(0.24, bare.boost(bare.summonOwner), 1e-9,
                "no memosprite on the field: no extra share");

        double[] base = {0.24, 0.3, 0.36, 0.42, 0.48};
        double[] extra = {0.12, 0.15, 0.18, 0.21, 0.24};
        for (int rank = 1; rank <= 5; rank++) {
            Fixture f = new Fixture(rank, true);
            f.castUltimate();
            Assertions.assertEquals(base[rank - 1], f.boost(f.plain), 1e-9, "rank " + rank + " #2");
            Assertions.assertEquals(base[rank - 1] + extra[rank - 1], f.boost(f.summonOwner), 1e-9,
                    "rank " + rank + " #2 + #3");
        }
    }

    // ==================================================================

    private static final class Fixture {
        private final Character wearer;
        private final Character summonOwner;
        private final Character plain;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private Fixture(int rank, boolean withSummon) {
            wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
            summonOwner = CharacterFactory.create(SUMMON_OWNER, LEVEL);
            plain = CharacterFactory.create(PLAIN_ALLY, LEVEL);
            battle = new Battle(new ArrayList<>(List.of(wearer, summonOwner, plain)),
                    new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
            battle.startBattle();
            if (withSummon && battle.memospriteOf(summonOwner) == null) {
                battle.summonMemosprite(summonOwner);
            }
        }

        private void castUltimate() {
            battle.castImmediate(wearer.getSkills().get(SkillType.ULTRA), wearer, List.of(enemy));
        }

        private double boost(Character unit) {
            DoubleValue value = unit.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST);
            return value == null ? 0.0 : value.get();
        }
    }
}
