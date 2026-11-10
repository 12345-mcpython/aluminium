package com.laosun.aluminium.test.content.characters.hysilens1410;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1410 海瑟音's Eidolons 1 (the party DoT multiplier) and 4 (the zone's resistance cut).
 *
 * <p><b>Why a criterion.</b> The two numbers land on two different sides of the damage pipeline, and both have a
 * same-looking neighbour that would be wrong: the DoT multiplier is {@code DOT_DAMAGE_BOOST} on the PARTY (an additive
 * zone, so 0.16 is 「116% of the original」), while the resistance cut is {@code RESISTANCE_REDUCTION} on the ENEMIES -
 * positive on that attribute means "resists less", and it is not the attacker-side penetration.
 *
 * <p>The Eidolon 4 modifier must also share the zone's clock: it is granted on the same Ultimate, with the same
 * three-turn / own-turn ticks the zone's two existing modifiers use.
 */
public class HysilensEidolonsTest {
    private static final int HYSILENS = 1410;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;

    /**
     * Eidolon 1: 「我方目标造成的持续伤害为原伤害的 116%」 - 0.16 into the DoT boost zone, for the whole party.
     */
    @Test
    public void theFirstEidolonRaisesThePartysDotDamage() {
        Run e0 = new Run(0);
        Run e1 = new Run(1);
        double plain = e0.allyDotBoost();
        double raised = e1.allyDotBoost();
        System.out.println("[1410-E1] ally DoT boost " + plain + " -> " + raised);
        Assertions.assertEquals(0.0, plain, 1e-9, "E0 states no such multiplier");
        Assertions.assertEquals(0.16, raised - plain, 1e-9,
                "「116% of the original」 is +16% into the additive DoT zone");
    }

    /**
     * Eidolon 4: 「结界持续期间，使敌方全体全属性抗性降低 20%」.
     */
    @Test
    public void theFourthEidolonCutsEveryEnemysResistance() {
        Run e3 = new Run(3);
        Run e4 = new Run(4);
        e3.battle.castImmediate(e3.hysilens.getSkills().get(SkillType.ULTRA), e3.hysilens,
                List.of(e3.battle.enemies().getFirst()));
        e4.battle.castImmediate(e4.hysilens.getSkills().get(SkillType.ULTRA), e4.hysilens,
                List.of(e4.battle.enemies().getFirst()));
        e3.battle.processRequests();
        e4.battle.processRequests();

        double plain = e3.battle.enemies().getFirst().getAttribute(AttributeType.RESISTANCE_REDUCTION).get();
        double cut = e4.battle.enemies().getFirst().getAttribute(AttributeType.RESISTANCE_REDUCTION).get();
        System.out.println("[1410-E4] enemy resistance reduction " + plain + " -> " + cut);
        Assertions.assertEquals(0.2, cut - plain, 1e-9,
                "「全属性抗性降低 20%」 - positive on RESISTANCE_REDUCTION lowers the target's resistance");
        // The control: the attacker-side penetration is untouched (the two attributes are different readers).
        Assertions.assertEquals(
                e3.hysilens.getAttribute(AttributeType.DAMAGE_PENETRATION).get(),
                e4.hysilens.getAttribute(AttributeType.DAMAGE_PENETRATION).get(), 1e-9,
                "this is the victim's resistance, not the attacker's penetration");
    }

    // ==================================================================

    private static final class Run {
        private final Character hysilens;
        private final Character ally;
        private final Battle battle;

        Run(int eidolon) {
            hysilens = CharacterFactory.create(HYSILENS, LEVEL);
            hysilens.setEidolonRank(eidolon);
            ally = CharacterFactory.create(ALLY, LEVEL);
            Enemy enemy = BattleFixture.monster();
            battle = new Battle(List.of(hysilens, ally), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        double allyDotBoost() {
            return ally.getAttribute(AttributeType.DOT_DAMAGE_BOOST).get();
        }
    }
}
