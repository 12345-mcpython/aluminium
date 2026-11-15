package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TestTurns;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 嗤笑 (20023): 「阿哈时刻发动时，使装备者的欢愉度提高 16~32%，持续到阿哈时刻结束。」
 *
 * <p><b>How the two halves of the sentence become one effect.</b> 「发动时」 is a cast of an Elation skill (that is the
 * event the engine's 阿哈时刻 hangs on - 1513's {@code elation_moment_start}), and 「持续到…结束」 is that state's
 * lifetime: `while_state` has the buff asked about at every turn boundary and dropped when the state is gone. Note that
 * the gate is ONE-WAY - a dropped buff never comes back - which is exactly why the effect is installed per cast rather
 * than once at battle start; otherwise only the first moment would ever be covered.
 *
 * <p>Every reading here is a <b>difference</b> between the same board with and without the cone, because 1513's own kit
 * also raises her Elation - the cone's share has to be isolated from his.
 */
public class SneeringTest {

    private static final int LEVEL = 80;
    /** 1513 Aventurine · Waveflair (砂金•戏浪): the one character whose kit raises 阿哈时刻, so the state really appears. */
    private static final int WEARER = 1513;
    private static final String MOMENT = "阿哈时刻";

    /** 「发动时」: casting an Elation skill raises the wearer's Elation by the cone's share. */
    @Test
    public void theElationIsUpInsideTheMoment() {
        Assertions.assertEquals(0.16, shareRaisedByTheCone(1), 1e-9,
                "rank 1: 「阿哈时刻发动时，使装备者的欢愉度提高 16%」");
        Assertions.assertEquals(0.32, shareRaisedByTheCone(5), 1e-9, "rank 5 states 32%");
    }

    /** 「持续到阿哈时刻结束」: once the moment is gone, so is the cone's share. */
    @Test
    public void theElationIsBackToTheCharactersOwnWhenTheMomentEnds() {
        Run withCone = new Run(1);
        withCone.castElationSkill();
        Assertions.assertTrue(withCone.moment(), "precondition: the cast put her inside 阿哈时刻");
        double inside = withCone.elation();

        withCone.endTheMoment();

        Assertions.assertFalse(withCone.moment(), "precondition: the state is gone");
        Assertions.assertEquals(inside - 0.16, withCone.elation(), 1e-9,
                "「持续到阿哈时刻结束」 — the gate drops the cone's share, and only it");
    }

    /** Without the moment the cone contributes nothing - the control for every reading above. */
    @Test
    public void theConeDoesNothingWithoutTheMoment() {
        Run withCone = new Run(1);
        Assertions.assertFalse(withCone.moment(), "precondition: nothing has started a moment yet");
        Assertions.assertEquals(0.0, withCone.elation() - new Run(1, false).elation(), 1e-9,
                "before the first Elation cast the cone has added nothing");
    }

    // ==================================================================

    /** The Elation the cone adds across one Elation-skill cast, measured against the same board without it. */
    private static double shareRaisedByTheCone(int rank) {
        Run withCone = new Run(rank);
        Run without = new Run(rank, false);
        withCone.castElationSkill();
        without.castElationSkill();
        return withCone.elation() - without.elation();
    }

    private static final class Run {
        private final Character wearer;
        private final Enemy enemy;
        private final Battle battle;

        private Run(int rank) {
            this(rank, true);
        }

        private Run(int rank, boolean withCone) {
            wearer = CharacterFactory.create(WEARER, LEVEL, true,
                    withCone ? Weapon.build(20023, LEVEL, false, rank) : null);
            enemy = BattleFixture.monster();
            battle = new Battle(List.of(wearer), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        /** 「阿哈时刻发动」: an Elation skill - the event her own kit hangs the moment on. */
        private void castElationSkill() {
            battle.castImmediate(wearer.getSkills().get(SkillType.ELATION_SKILL), wearer, List.of(enemy));
            battle.processRequests();
        }

        /** 「阿哈时刻结束」: drop the state and cross a turn boundary, where the gate is asked. */
        private void endTheMoment() {
            wearer.getBuffManager().removeState(MOMENT);
            TestTurns.take(battle, wearer);
        }

        private boolean moment() {
            return wearer.getBuffManager().hasState(MOMENT);
        }

        private double elation() {
            return wearer.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
        }
    }
}
