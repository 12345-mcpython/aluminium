package com.laosun.aluminium.test.content.characters.jade;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1314 翡翠's Skill, first clause: 「指定我方单体成为【收债人】，使其速度提高 30 点，持续 3 回合…若翡翠成为【收债人】时，
 * 无法获得速度提高效果…翡翠每回合开始时【收债人】状态持续回合数减 1」.
 *
 * <p>Three things are pinned: the ally becomes the collector and is faster by 30 POINTS (not 30%), her own case gets the state
 * but not the speed, and the duration is spent on HER turn boundaries ({@code ticks_on: "self"}), not the ally's.
 */
public class JadeDebtCollectorTest {

    private static final int LEVEL = 80;
    private static final int JADE = 1314;
    private static final int ALLY = 1001;
    private static final String COLLECTOR = "收债人";

    @Test
    public void anAllyBecomesTheCollectorAndGetsThirtySpeed() {
        Fixture f = new Fixture();
        double before = f.ally.getAttribute(AttributeType.SPEED).get();
        f.skill(f.ally);

        Assertions.assertTrue(f.ally.getBuffManager().hasState(COLLECTOR), "「指定我方单体成为【收债人】」");
        Assertions.assertEquals(before + 30, f.ally.getAttribute(AttributeType.SPEED).get(), 1e-6,
                "「速度提高 30 点」 - points, not 30%");
    }

    @Test
    public void sheCanBeTheCollectorButGetsNoSpeed() {
        Fixture f = new Fixture();
        double before = f.jade.getAttribute(AttributeType.SPEED).get();
        f.skill(f.jade);

        Assertions.assertTrue(f.jade.getBuffManager().hasState(COLLECTOR), "the state is applied to her too");
        Assertions.assertEquals(before, f.jade.getAttribute(AttributeType.SPEED).get(), 1e-6,
                "「若翡翠成为【收债人】时，无法获得速度提高效果」");
    }

    @Test
    public void herTurnSpendsTheCollectorsDuration() {
        Fixture f = new Fixture();
        f.skill(f.ally);
        int before = f.ally.getBuffManager().allBuffsOf(StateBuff.class).getFirst().remainingTurns();
        Assertions.assertEquals(3, before, "precondition: 3 turns");

        // Three of HER turn boundaries (early + after). The state sits on the ally, its clock on Jade.
        for (int i = 0; i < 3; i++) {
            f.ally.getBuffManager().tickForeign(f.jade, true);
            f.ally.getBuffManager().tickForeign(f.jade, false);
        }
        Assertions.assertFalse(f.ally.getBuffManager().hasState(COLLECTOR),
                "「翡翠每回合开始时【收债人】状态持续回合数减 1」 - 3 turns spent on HER boundaries");
    }

    // ==================================================================

    private static final class Fixture {
        private final Character jade;
        private final Character ally;
        private final Battle battle;

        private Fixture() {
            jade = CharacterFactory.create(JADE, LEVEL);
            ally = CharacterFactory.create(ALLY, LEVEL);
            battle = new Battle(new ArrayList<>(List.of(jade, ally)),
                    new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
            battle.startBattle();
        }

        private void skill(Character target) {
            battle.castImmediate(jade.getSkills().get(SkillType.SKILL), jade, List.of(target));
        }
    }
}
