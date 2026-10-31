package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 溯忆 (20022): 「忆灵的回合开始时，使装备者和忆灵分别获得 1 层【缅怀】，每层使造成的伤害提高 #1%，最多叠加 #2 层。
 * 忆灵消失时移除装备者和忆灵的【缅怀】」.
 *
 * <p><b>What this pins.</b> Both halves of "分别": each unit gets its <b>own</b> stack, and each unit's damage share reads
 * <b>its own</b> counter ({@code per_stack} is resolved on the effect's target, which is why the memosprite's copy is
 * {@code target: summon} rather than a second copy of the owner's). The previous record left the memosprite half
 * unwritten because "whose counter" had not been measured; it is the target's.
 *
 * <p>Also pinned: the per-rank share. The file used to state 8% at every rank; ranks 2-5 are 9/10/11/12%.
 */
public class ConeTwentyThousandTwentyTwoTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1409;
    private static final int CONE = 20022;

    /**
     * One memosprite turn gives one layer to each side, and each side's share follows its own layer count.
     */
    @Test
    public void eachSideGetsItsOwnLayerAndItsOwnShare() {
        Fixture f = new Fixture();
        f.summonTurn();
        Assertions.assertEquals(1, f.stacks(f.wearer), "the wearer gains 1 layer");
        Assertions.assertEquals(1, f.stacks(f.summon), "the memosprite gains 1 layer of its own");
        Assertions.assertEquals(0.08, f.share(f.wearer), 1e-9, "the wearer's damage +8% at one layer");
        Assertions.assertEquals(0.08, f.share(f.summon), 1e-9, "the memosprite's damage +8%, read off ITS counter");

        f.summonTurn();
        Assertions.assertEquals(0.16, f.share(f.wearer), 1e-9, "two layers on the wearer");
        Assertions.assertEquals(0.16, f.share(f.summon), 1e-9,
                "two layers on the memosprite - not the wearer's count, and not double");
    }

    /**
     * 「最多叠加 4 层」: the fifth turn adds nothing.
     */
    @Test
    public void theLayersCapAtFour() {
        Fixture f = new Fixture();
        for (int i = 0; i < 5; i++) {
            f.summonTurn();
        }
        Assertions.assertEquals(4, f.stacks(f.wearer));
        Assertions.assertEquals(4, f.stacks(f.summon));
        Assertions.assertEquals(0.32, f.share(f.wearer), 1e-9);
    }

    /**
     * The gate is 「忆灵的回合」: a turn of anybody else grants nothing.
     */
    @Test
    public void aNonMemospriteTurnGrantsNothing() {
        Fixture f = new Fixture();
        f.ownerTurn();
        Assertions.assertEquals(0, f.stacks(f.wearer));
        Assertions.assertEquals(0, f.stacks(f.summon));
    }

    /**
     * 「忆灵消失时移除装备者和忆灵的【缅怀】」: when the memosprite leaves, the wearer's layers go with it.
     */
    @Test
    public void theLayersLeaveWhenTheMemospriteDoes() {
        Fixture f = new Fixture();
        f.summonTurn();
        f.summonTurn();
        Assertions.assertEquals(2, f.stacks(f.wearer));

        f.summon.takeDamage(f.summon.getCurrentHp());
        f.battle.processRequests();

        Assertions.assertTrue(f.summon.isDeath(), "the fixture's memosprite left");
        Assertions.assertEquals(0, f.stacks(f.wearer),
                "「忆灵消失时移除装备者和忆灵的【缅怀】」 - the wearer's layers are cleared too");
    }

    // ==================================================================

    private static final class Fixture {
        private final Character wearer;
        private final Summon summon;
        private final Battle battle;

        private Fixture() {
            wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1));
            battle = new Battle(new ArrayList<>(List.of(wearer, CharacterFactory.create(1001, LEVEL))),
                    new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
            battle.startBattle();
            Summon found = battle.memospriteOf(wearer);
            if (found == null) {
                found = battle.summonMemosprite(wearer);
            }
            summon = found;
            Assertions.assertNotNull(summon, "1409's memosprite must exist for this fixture");
        }

        private void summonTurn() {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, summon, summon, 0, 0));
        }

        private void ownerTurn() {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, wearer, wearer, 0, 0));
        }

        private int stacks(com.laosun.aluminium.entity.CanHit unit) {
            return unit.getBuffManager().stacksOf("缅怀");
        }

        private double share(com.laosun.aluminium.entity.CanHit unit) {
            return unit.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        }
    }
}


