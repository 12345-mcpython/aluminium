package com.laosun.aluminium.test.content.characters.hyacine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1409 风堇's 忆灵技4 「消失时，使风堇行动提前 30%」 and the other half of her Rank 6 aura
 * (「小伊卡在场时…」 -- so it has to be gone when the memosprite is not there).
 *
 * <p>Both hang on {@code SUMMON_LEFT}, the event that says "the memosprite left the field": a memosprite can go away
 * without being killed ({@code CanHit.perish()} is the engine's "it went away, nobody killed it", and by its own
 * javadoc it emits no {@code HpLoss} / {@code Kill}), so {@code DEATH} is not the same fact.
 */
public class HyacineMemospriteLeavesTest {

    private static final int HYACINE = 1409;
    private static final int ALLY = 1001;
    private static final double EPS = 1e-9;

    @Test
    public void theRankSixAuraIsTakenBackWhenIkachiGoesAway() {
        Fixture f = new Fixture(6);
        Assertions.assertEquals(0.2, f.pen(f.hyacine), EPS, "precondition: the aura is up while 小伊卡 is out");

        f.ika.perish();
        f.battle.processRequests();

        Assertions.assertEquals(0.0, f.pen(f.hyacine), EPS,
                "「小伊卡在场时」 -- the aura must not outlive the memosprite");
        Assertions.assertEquals(0.0, f.pen(f.ally), EPS, "and it was a party-wide aura");
    }

    @Test
    public void herActionIsAdvancedByThirtyPercentWhenIkachiGoesAway() {
        Fixture f = new Fixture(6);
        double before = f.remainingWaitOf(f.hyacine);

        f.ika.perish();
        f.battle.processRequests();

        // 「使风堇行动提前 30%」 is a share of her REMAINING wait, so the clock after is 70% of the one before. The
        // readout is the action bar's own snapshot (Queue.snapshot + getTimeRemaining); the battle clock does not move.
        Assertions.assertEquals(before * 0.7, f.remainingWaitOf(f.hyacine), Math.max(1e-9, before * 1e-9),
                "「消失时，使风堇行动提前 30%」 (remaining before=" + before + ", after="
                        + f.remainingWaitOf(f.hyacine) + ")");
    }
    @Test
    public void withoutRankSixNothingIsGivenBack() {
        Fixture f = new Fixture(0);
        double before = f.pen(f.hyacine);
        f.ika.perish();
        f.battle.processRequests();
        Assertions.assertEquals(before, f.pen(f.hyacine), EPS, "the aura is Rank 6's; E0 has nothing to take back");
    }

    private static final class Fixture {
        final Battle battle;
        final Character hyacine;
        final Character ally;
        final Summon ika;

        Fixture(int eidolon) {
            this.hyacine = CharacterFactory.create(HYACINE, 80, true, null, null, eidolon);
            this.ally = CharacterFactory.create(ALLY, 80);
            this.battle = new Battle(List.of(hyacine, ally), List.of(BattleFixture.dummy()),
                    BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
            SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.SKILL), hyacine,
                    List.of(battle.enemies().getFirst()));
            battle.processRequests();
            this.ika = (Summon) battle.memospriteOf(hyacine);
            Assertions.assertNotNull(ika, "precondition: 小伊卡 is out");
        }

        /** 风堇 的剩余等待，从行动条自己的快照读。 */
        double remainingWaitOf(com.laosun.aluminium.models.CanHit unit) {
            for (var signal : battle.queue().snapshot()) {
                if (signal.getCanHit() == unit) {
                    return battle.queue().getTimeRemaining(signal);
                }
            }
            throw new AssertionError(unit.getName() + " is not on the action bar");
        }

        double pen(com.laosun.aluminium.models.CanHit unit) {
            return unit.getAttribute(AttributeType.DAMAGE_PENETRATION).get();
        }
    }
}


