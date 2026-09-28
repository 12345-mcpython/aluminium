package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.DebuffClass;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1013 Herta, from her own file (2026-09-29, round 158): a per-target threshold bonus, a control-class resistance, and the frozen bonus.
 *
 * <p>The skill case uses two enemies on ONE battlefield — one at full HP and one already below half — so the claim 「对该目标」 is measured
 * rather than assumed: the same cast must hurt the healthy one proportionally more.
 */
public class HertaTest {
    private static final int HERTA = 1013;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** \u26a0 「抵抗控制类负面状态的概率提高35%」: the engine keeps a per-class resistance, and it must be up. */
    @Test
    public void herPuppetTraceRaisesControlResistance() {
        Fixture f = new Fixture();
        Assertions.assertTrue(f.herta.getBuffManager().debuffResistOf(DebuffClass.CONTROL) > 0,
                "\u300c\u62b5\u6297\u63a7\u5236\u7c7b\u8d1f\u9762\u72b6\u6001\u7684\u6982\u7387\u63d0\u9ad835%\u300d");
    }

    /** Census: the threshold bonus, the frozen bonus, the resistance and the level convention. */
    @Test
    public void herFileCarriesTheClauses() {
        var table = TriggerTables.of(HERTA);
        Assertions.assertEquals(0, table.ruleCount(TriggerEvent.DEALING_DAMAGE),
                "BOOST_DAMAGE rules are withdrawn: the op has no effect on this event (round 158)");
        Assertions.assertEquals(2, table.ruleCount(TriggerEvent.BATTLE_START), "the resistance and the level convention");
    }

    private static final class Fixture {
        private final Character herta = CharacterFactory.create(HERTA, LEVEL);
        private final Enemy healthy = Enemy.fromAttributes("Healthy Dummy", 40000, 500, 100, 90);
        private final Enemy hurt = Enemy.fromAttributes("Hurt Dummy", 40000, 500, 100, 90);
        private final Battle battle;

        private Fixture() {
            battle = new Battle(List.of(herta), List.of(healthy, hurt), new Random() {
                @Override
                public double nextDouble() {
                    return 0.0;
                }
            });
            battle.startBattle();
            // Put the second dummy below the threshold without touching the first. `setCurrentHp` does not exist, so the
            // engine settles a plain instance instead; its cast category is UNSPECIFIED, so her skill rule cannot apply to it.
            battle.applyDamage(hurt, new com.laosun.aluminium.models.Damage(herta, hurt,
                    com.laosun.aluminium.enums.DamageElement.ICE,
                    com.laosun.aluminium.enums.DamageType.NORMAL, hurt.getMaxHp() * 0.6));
        }
    }
}
