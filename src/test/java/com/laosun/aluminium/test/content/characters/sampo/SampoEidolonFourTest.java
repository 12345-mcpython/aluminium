package com.laosun.aluminium.test.content.characters.sampo;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.DotBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1108 桑博's Eidolon 4: 「战技击中风化状态大于等于 5 层的敌方目标时，使其当前承受的风化状态立即产生相当于原伤害 8% 的伤害」.
 *
 * <p>The threshold reads the DOT's own layers ({@code target_stacks:风化}). The expected extra is derived from a measured
 * full tick rather than from the DOT's raw base: the settled tick passes the target's DEF and resistance like any damage.
 */
public class SampoEidolonFourTest {

    private static final int LEVEL = 80;
    private static final int SAMPO = 1108;
    private static final double TICK = 100.0;

    @Test
    public void fiveLayersTickOnceAndFourDoNot() {
        double fullTick = tickOnly(5);
        // Compare WITHIN one Eidolon rank: rank 4 also raises the Skill by two levels, so E4-vs-E0 would include that.
        double extra = skillLoss(4, 5) - skillLoss(4, 4);
        System.out.println("[1108] fullTick=" + fullTick + " extra=" + extra);
        Assertions.assertEquals(0.08 * fullTick, extra, 1e-6,
                "「相当于原伤害 8%」 - one extra tick at 8% of the whole windshear stack");

        Assertions.assertEquals(0.0, skillLoss(0, 5) - skillLoss(0, 4), 1e-6,
                "「5 层」 is a threshold, and without the Eidolon nothing ticks");
    }

    /**
     * One full windshear tick on five layers (the settled number, DEF and resistance included).
     */
    private static double tickOnly(int layers) {
        Fixture f = new Fixture(0, layers);
        return f.battle.tickDotStateNow(f.enemy, DamageElement.WIND, 1.0);
    }

    private static double skillLoss(int eidolon, int layers) {
        Fixture f = new Fixture(eidolon, layers);
        double before = f.enemy.getCurrentHp();
        f.battle.castImmediate(f.sampo.getSkills().get(SkillType.SKILL), f.sampo, List.of(f.enemy));
        return before - f.enemy.getCurrentHp();
    }

    private static final class Fixture {
        private final Character sampo;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private Fixture(int eidolon, int layers) {
            sampo = CharacterFactory.create(SAMPO, LEVEL, true, null, null, eidolon);
            battle = new Battle(new ArrayList<>(List.of(sampo, CharacterFactory.create(1001, LEVEL))),
                    // 0.99 fails the talent's 65% roll: otherwise the Skill itself adds a windshear layer and the
                    // threshold (and the tick it triggers) would move between the two runs.
                    new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.99));
            battle.startBattle();
            for (int i = 0; i < layers; i++) {
                enemy.getBuffManager().addBuff(new DotBuff(sampo, DamageElement.WIND, TICK, 3, 0, "风化"));
            }
            Assertions.assertEquals(layers, enemy.getBuffManager().stacksOf("风化"),
                    "precondition: the windshear layers are readable by name");
        }
    }
}


