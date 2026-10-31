package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
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
 * 谎言在风中飘扬 (23043): 「施放攻击后，有 120% 的基础概率使敌方每个单体目标陷入【茫然】（防御 −#3%，2 回合）；若速度 ≥ 170，
 * 另有 120% 的基础概率使敌方每个单体目标陷入【失窃】（防御 −#6%，2 回合）；重复施加只保留最新」.
 *
 * <p><b>What it needed.</b> Nothing new: {@code ATTACK_FINISHED} + {@code base_chance} on {@code MODIFY_ATTR} + the
 * speed gate {@code self_attr:SPEED >= 170}. The two DEFENCE shares cannot live as two separate modifiers (same-attribute
 * modifiers replace, measured even with different {@code buff} names), so the gated branch folds both into one share
 * ({@code #3 + #6}) and the two branches are mutually exclusive - which is also what 「仅最新施加的生效」 asks for when the
 * same state is re-applied (the modifier is replaced and its clock refreshed).
 */
public class ConeTwentyThreeThousandFortyThreeTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int CONE = 23043;

    /**
     * Below 170 only 【茫然】's −16% applies; at 170+ the two shares add to −24%.
     */
    @Test
    public void theSpeedGateAddsTheftAndBothSharesApply() {
        Fixture slow = new Fixture(1, false);
        double base = slow.defenceBase;
        slow.attackFinished();
        Assertions.assertEquals(-0.16 * base, slow.defenceDelta(), 1e-6,
                "【茫然】: defence −16% of base");

        Fixture fast = new Fixture(1, true);
        fast.attackFinished();
        Assertions.assertEquals(-0.24 * base, fast.defenceDelta(), 1e-6,
                "speed >= 170: 【茫然】−16% and 【失窃】−8% add up to −24%");
    }

    /**
     * 「重复施加只保留最新」: a second attack replaces the modifier (same share, no stacking), and without the cone
     * nothing happens.
     */
    @Test
    public void reapplyingKeepsOnlyTheLatestAndTheConeIsRequired() {
        Fixture f = new Fixture(1, true);
        f.attackFinished();
        f.attackFinished();
        Assertions.assertEquals(-0.24 * f.defenceBase, f.defenceDelta(), 1e-6,
                "two attacks do not stack; the latest application is the one that counts");

        Fixture bare = new Fixture(1, true, false);
        bare.attackFinished();
        Assertions.assertEquals(0.0, bare.defenceDelta(), 1e-9, "without the cone, nothing");
    }

    /**
     * Both shares are per rank (#3 / #6 = 16/8, 18/9, 20/10, 22/11, 24/12%).
     */
    @Test
    public void theSharesFollowTheRank() {
        double[] daze = {0.16, 0.18, 0.2, 0.22, 0.24};
        double[] theft = {0.08, 0.09, 0.1, 0.11, 0.12};
        for (int rank = 1; rank <= 5; rank++) {
            Fixture slow = new Fixture(rank, false);
            slow.attackFinished();
            Assertions.assertEquals(-daze[rank - 1] * slow.defenceBase, slow.defenceDelta(), 1e-6,
                    "rank " + rank + " slow");

            Fixture fast = new Fixture(rank, true);
            fast.attackFinished();
            Assertions.assertEquals(-(daze[rank - 1] + theft[rank - 1]) * fast.defenceBase, fast.defenceDelta(), 1e-6,
                    "rank " + rank + " fast");
        }
    }

    // ==================================================================

    private static final class Fixture {
        private final Character wearer;
        private final Enemy enemy;
        private final Battle battle;
        private final double defenceBase;
        private final double defenceBefore;

        private Fixture(int rank, boolean fast) {
            this(rank, fast, true);
        }

        private Fixture(int rank, boolean fast, boolean withCone) {
            wearer = withCone
                    ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank))
                    : CharacterFactory.create(WEARER, LEVEL);
            if (fast) {
                wearer.getAttribute(AttributeType.SPEED).addModifier(DoubleValue.Modifier.pure(200));
            }
            enemy = BattleFixture.monster();
            // The stated base chance (120%) is rolled against effect resistance; a zero-resistance target makes the
            // fixture measure the DEFENCE share rather than the roll.
            DoubleValue resistance = enemy.getAttribute(AttributeType.EFFECT_RESISTANCE);
            if (resistance != null && resistance.get() != 0) {
                resistance.addModifier(DoubleValue.Modifier.pure(-resistance.get()));
            }
            battle = new Battle(new ArrayList<>(List.of(wearer, CharacterFactory.create(1001, LEVEL))),
                    new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
            battle.startBattle();
            defenceBase = enemy.getAttribute(AttributeType.DEFENCE).baseValue();
            defenceBefore = enemy.getAttribute(AttributeType.DEFENCE).get();
        }

        private void attackFinished() {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.ATTACK_FINISHED, wearer, enemy, 1, 0));
        }

        private double defenceDelta() {
            return enemy.getAttribute(AttributeType.DEFENCE).get() - defenceBefore;
        }
    }
}
