package com.laosun.aluminium.test.content.characters.clara;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Gap witness: the engine REPLACES two same-kind reductions instead of adding them.
 *
 * <p>Clara's talent says 「受到的伤害降低 10%」 and her Eidolon 4 says 「受到的伤害降低 30%」 - two independent sentences, so the
 * game's total is 40%. Measured: after the Eidolon's zone lands, the probe is reduced by 30% total (the talent's 10% is
 * gone), i.e. 0.7 of a bare dummy instead of 0.6. {@link com.laosun.aluminium.buff.ReductionBuff} inherits the
 * class-level {@code isSameKind}, so {@code BuffManager.addBuff} evicts the first one.
 *
 * <p>This test pins today's reading on purpose: the day reductions become additive across sources, the second assertion
 * turns red and the four numbers in the content file become correct without any further edit.
 */
public class ClaraReductionStackingGapTest {

    private static final int LEVEL = 80;
    private static final int CLARA = 1107;

    /**
     * Today: E2's 10% is replaced by E4's 30%, so the total is 30% and not 40%.
     */
    @Test
    public void todaysTotalIsThirtyPercentNotForty() {
        Fixture f = new Fixture();
        double dummy = f.probeDummy();
        Assertions.assertEquals(0.9, f.probeClara() / dummy, 1e-6, "the talent alone");

        f.fireAttackFinishedOnClara();
        double after = f.probeClara();
        System.out.println("[1107] stacking: after=" + after / dummy);
        Assertions.assertEquals(0.7, after / dummy, 1e-6,
                "TODAY 30% replaces 10%; the sentences add up to 40% (0.6), so this assertion is the defect witness");
    }

    private static final class Fixture {
        private final Character clara;
        private final Character dummy;
        private final Battle battle;
        private final Battle dummyBattle;

        private Fixture() {
            clara = CharacterFactory.create(CLARA, LEVEL, true, null, null, 4);
            battle = new Battle(new ArrayList<>(List.of(clara, CharacterFactory.create(1001, LEVEL))),
                    new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.alwaysRoll(0.99));
            battle.startBattle();
            dummy = Character.fromAttributes("Bare Dummy", clara.getMaxHp(),
                    clara.getAttribute(AttributeType.DEFENCE).get(), 100, 100);
            dummyBattle = new Battle(new ArrayList<>(List.of(dummy)),
                    new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.alwaysRoll(0.99));
            dummyBattle.startBattle();
        }

        private double probeClara() {
            double before = clara.getCurrentHp();
            battle.applyDamage(clara, new Damage(battle.enemies().getFirst(), clara,
                    DamageElement.PHYSICAL, DamageType.NORMAL, 200.0));
            return before - clara.getCurrentHp();
        }

        private double probeDummy() {
            double before = dummy.getCurrentHp();
            dummyBattle.applyDamage(dummy, new Damage(dummyBattle.enemies().getFirst(), dummy,
                    DamageElement.PHYSICAL, DamageType.NORMAL, 200.0));
            return before - dummy.getCurrentHp();
        }

        private void fireAttackFinishedOnClara() {
            battle.fire(TriggerPayload.Cast.of(TriggerEvent.ATTACK_FINISHED, battle.enemies().getFirst(), clara,
                    1, 200.0, com.laosun.aluminium.enums.SkillCategory.UNSPECIFIED, 0, 0, List.of(clara)));
        }
    }
}
