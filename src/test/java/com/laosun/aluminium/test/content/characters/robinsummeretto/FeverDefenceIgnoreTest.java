package com.laosun.aluminium.test.content.characters.robinsummeretto;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.buff.StateBuff;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1512 知更鸟•晴歌's 【Fever】aura: while it is on, the team's damage ignores part of the target's DEF -
 * <b>15% + 0.5% per point of 气氛值, capped at 40%</b>.
 *
 * <h2>Where the numbers come from</h2>
 * Her talent (151204, {@code SkillP01}): 「结界内我方目标造成伤害时无视敌方目标（#8% + 气氛值…#9%）的防御力」 with
 * {@code param = [0.7, 1.8, 0, 0, 50, 6, 12, 0.15, 0.005]}. The gauge's ceiling is 50, so the cap is 0.15 + 0.25 = 0.40.
 *
 * <h2>Why it is measured through the settled DefenceArea</h2>
 * DEFENCE_IGNORE on this rule is an <b>instance</b> modifier: it lives on the hit, not on the character, so
 * {@code getAttribute(DEFENCE_IGNORE)} answers 0 no matter what (measured). The observable is the settled
 * {@code breakdown()["DefenceArea"]}, which is also what a whole-battle capture lets us solve back out.
 *
 * <h2>What the capture says</h2>
 * A real fight's events implied effective DEF of {@code 1150 x {0.775, 0.77, 0.76, 0.725, 0.71, 0.705, 0.685, 0.6}},
 * i.e. DEF-ignore of 22.5%, 23%, 24%, 27.5%, 29%, 29.5%, 31.5% and 40% - this formula at 气氛值 15, 16, 18, 25, 28, 29,
 * 33 and 50.
 */
public class FeverDefenceIgnoreTest {

    private static final int ROBIN = 1512;
    private static final int ALLY = 1001;
    private static final String VIBES = "气氛值";
    private static final String FEVER = "Fever";

    /**
     * The aura scales with the gauge.
     */
    @Test
    public void theAuraIgnoresMoreDefenceAsTheGaugeRises() {
        double ignore0 = ignoreAt(0);
        double ignore15 = ignoreAt(15);
        double ignore50 = ignoreAt(50);
        Assertions.assertEquals(0.15, ignore0, 1e-6, "no gauge: the base 15% still applies");
        Assertions.assertEquals(0.225, ignore15, 1e-6, "气氛值 15 -> 15% + 15 x 0.5% = 22.5%");
        Assertions.assertEquals(0.40, ignore50, 1e-6, "气氛值 50 -> 15% + 25% = 40% (the document's cap)");
    }

    /**
     * Without the state the aura does nothing, however full the gauge is - the gate is the state.
     */
    @Test
    public void withoutFeverTheGaugeChangesNothing() {
        Assertions.assertEquals(0.0, ignoreAt(50, false), 1e-9,
                "the aura is gated on the 【Fever】 state (the document's gate is \"all three memosprites on stage\")");
    }

    private static double ignoreAt(int vibes) {
        return ignoreAt(vibes, true);
    }

    /** The DEF-ignore the engine actually applied, solved back out of the settled defence zone. */
    private static double ignoreAt(int vibes, boolean fever) {
        Character robin = CharacterFactory.create(ROBIN, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(robin, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        if (vibes > 0) {
            Assertions.assertNotNull(battle.partyResource(VIBES), "the file declares the party gauge");
            battle.partyResource(VIBES).gain(vibes);
        }
        Assertions.assertEquals(Math.min(vibes, 50), battle.partyResourceValue(VIBES),
                "precondition: the gauge really moved");
        if (fever) {
            robin.getBuffManager().addBuff(new StateBuff(FEVER, 99));
        }
        battle.processRequests();
        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        SkillExecutor.execute(battle, ally.getSkills().get(SkillType.COMMON), ally, List.of(battle.enemies.get(0)));
        battle.processRequests();
        Assertions.assertEquals(1, settled.size(), "precondition: one settled instance");

        double defence = battle.enemies.get(0).getAttribute(AttributeType.DEFENCE).get();
        double levelTerm = 200.0 + 10.0 * 80;
        double zone = settled.get(0).breakdown().get("DefenceArea");
        // zone = levelTerm / (effectiveDef + levelTerm)  ->  effectiveDef = levelTerm/zone - levelTerm
        double effective = levelTerm / zone - levelTerm;
        return 1.0 - effective / defence;
    }
}
