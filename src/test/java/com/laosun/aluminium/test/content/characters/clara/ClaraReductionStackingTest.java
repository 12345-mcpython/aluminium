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
 * Two independent reduction sentences must both land: Clara's talent 「受到的伤害降低 10%」 and her Eidolon 4 「降低 30%」.
 *
 * <p>The canon's reduction zone is a <b>product</b> (HSR.md: 减伤区 = Π(1 - 减伤)), so the total is 0.9 x 0.7 = 0.63 - not
 * 0.6 (that would be additive) and not 0.7 (that is what a same-kind <b>replacement</b> gives, which silently loses the
 * talent's sentence). {@code ReductionBuff.isSameKind} now compares the creating rule, so two rules coexist while a
 * re-application of the same rule still refreshes.
 */
public class ClaraReductionStackingTest {

    private static final int LEVEL = 80;
    private static final int CLARA = 1107;

    @Test
    public void theTwoSentencesMultiply() {
        Fixture f = new Fixture();
        double dummy = f.probeDummy();
        Assertions.assertEquals(0.9, f.probeClara() / dummy, 1e-6, "the talent alone");

        f.fireAttackFinishedOnClara();
        double after = f.probeClara();
        System.out.println("[1107] stacking: after=" + after / dummy);
        Assertions.assertEquals(0.63, after / dummy, 1e-6,
                "both sentences are in the zone now (a same-kind replacement would give 0.7)");
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

