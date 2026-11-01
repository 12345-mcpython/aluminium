package com.laosun.aluminium.test.content.characters.fuxuan;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1208 符玄's technique, battle half: 「屏障持续期间进入战斗时符玄自动开启【穷观阵】，持续 2 回合」 - the same three effects the
 * Skill opens, on the technique's 2-turn clock.
 */
public class FuXuanTechniqueTest {

    private static final int LEVEL = 80;
    private static final int FUXUAN = 1208;
    private static final int ALLY = 1001;
    private static final String WARD = "鉴知";

    @Test
    public void theTechniqueOpensTheZoneOnEntry() {
        Fixture with = new Fixture(true);
        Fixture without = new Fixture(false);

        Assertions.assertTrue(with.fuxuan.getBuffManager().hasState(WARD), "「自动开启【穷观阵】」 - 【鉴知】 is up");
        Assertions.assertTrue(with.ally.getBuffManager().hasState(WARD), "on every ally");

        // The share is an ABSOLUTE amount: 6% of HER Max HP, added to each ally (not 6% of the ally own HP).
        double added = with.ally.getAttribute(AttributeType.HEALTH).get()
                - without.ally.getAttribute(AttributeType.HEALTH).get();
        Assertions.assertEquals(0.06 * without.fuxuan.getAttribute(AttributeType.HEALTH).get(), added, 1e-6,
                "「生命上限提高…等同于符玄生命上限的 6%」");
        Assertions.assertEquals(without.ally.getAttribute(AttributeType.CRIT_CHANCE).get() + 0.12,
                with.ally.getAttribute(AttributeType.CRIT_CHANCE).get(), 1e-6, "「暴击率提高 12.00%」");

        Assertions.assertFalse(without.fuxuan.getBuffManager().hasState(WARD), "no technique, no zone");
    }

    /**
     * 「当符玄陷入无法战斗状态时，【穷观阵】也会被解除」: the zone is anchored to HER clock, so her death takes it off the ally.
     */
    @Test
    public void herDeathTakesTheZoneOffEveryone() {
        Fixture f = new Fixture(true);
        f.battle.castImmediate(f.fuxuan.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), f.fuxuan,
                java.util.List.of(f.ally));
        Assertions.assertTrue(f.ally.getBuffManager().hasState(WARD), "precondition: the zone is up");

        f.battle.applyTrueDamage(f.ally, f.fuxuan, com.laosun.aluminium.enums.DamageElement.PHYSICAL,
                f.fuxuan.getCurrentHp() * 10);
        f.battle.processRequests();

        Assertions.assertTrue(f.fuxuan.isDeath(), "precondition: she is down");
        Assertions.assertFalse(f.ally.getBuffManager().hasState(WARD),
                "「当符玄陷入无法战斗状态时，【穷观阵】也会被解除」");
    }
    private static final class Fixture {
        private final Character fuxuan;
        private final Character ally;
        private final Battle battle;

        private Fixture(boolean technique) {
            fuxuan = CharacterFactory.create(FUXUAN, LEVEL);
            ally = CharacterFactory.create(ALLY, LEVEL);
            Enemy enemy = BattleFixture.monster();
            battle = new Battle(new ArrayList<>(List.of(fuxuan, ally)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.seeded());
            if (technique) {
                battle.markTechniqueUsed(fuxuan);
            }
            battle.startBattle();
        }
    }
}







