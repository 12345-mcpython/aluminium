package com.laosun.aluminium.test.content.characters;

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
 * 1013 Herta, from her own file: a per-target threshold bonus, a control-class resistance, and the frozen bonus.
 *
 * <p>The skill case uses two enemies on ONE battlefield - one at full HP and one already below half - so the claim "对该目标" is measured
 * rather than assumed: the same cast must hurt the healthy one proportionally more.
 */
public class HertaTest {
    private static final int HERTA = 1013;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Note: "抵抗控制类负面状态的概率提高35%": the engine keeps a per-class resistance, and it must be up. */
    @Test
    public void herPuppetTraceRaisesControlResistance() {
        Fixture f = new Fixture();
        Assertions.assertTrue(f.herta.getBuffManager().debuffResistOf(DebuffClass.CONTROL) > 0,
                "「抵抗控制类负面状态的概率提高35%」 (raises RES to Crowd Control debuffs by 35%)");
    }

    /** Note: "若敌方目标当前生命值百分比大于等于50%": the bonus is per TARGET, so a low-HP enemy must not get it. */
    @Test
    public void theSkillBonusAppliesOnlyToHealthyTargets() {
        Character herta = CharacterFactory.create(HERTA, LEVEL);
        Enemy healthy = Enemy.fromAttributes("Healthy Dummy", 40000, 500, 100, 90);
        Enemy hurt = Enemy.fromAttributes("Hurt Dummy", 40000, 500, 100, 90);
        Battle battle = new Battle(java.util.List.of(herta), java.util.List.of(healthy, hurt), new java.util.Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        });
        battle.startBattle();
        // Below the threshold, settled through the engine (its category is UNSPECIFIED, so her rule cannot apply to it).
        battle.applyDamage(hurt, new com.laosun.aluminium.models.Damage(herta, hurt,
                com.laosun.aluminium.enums.DamageElement.ICE,
                com.laosun.aluminium.enums.DamageType.NORMAL, hurt.getMaxHp() * 0.6));
        double healthyBefore = healthy.getCurrentHp();
        double hurtBefore = hurt.getCurrentHp();

        battle.castImmediate(herta.getSkills().get(SkillType.SKILL), herta, java.util.List.of(healthy, hurt));

        double healthyLoss = healthyBefore - healthy.getCurrentHp();
        double hurtLoss = hurtBefore - hurt.getCurrentHp();
        // Same defence on both sides, so an equal instance would cost each the same ABSOLUTE HP, and the document's 20% has to show up
        // as at least a 10% larger loss. (Shares would differ by construction; a strict `>` is satisfied by float noise.)
        Assertions.assertTrue(healthyLoss > hurtLoss * 1.1,
                "「若敌方目标当前生命值百分比大于等于50%，则对该目标造成的伤害提高20%」 (if the enemy target's current HP percentage is at least 50%, DMG dealt to that target is raised by 20%) -- absolute losses: "
                        + healthyLoss + " (healthy) vs " + hurtLoss + " (hurt)");
    }

    /** Census: the threshold bonus, the frozen bonus, the resistance and the level convention. */
    @Test
    public void herFileCarriesTheClauses() {
        var table = TriggerTables.of(HERTA);
        Assertions.assertEquals(2, table.ruleCount(TriggerEvent.DEALING_DAMAGE),
                "the threshold bonus and the frozen bonus");
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
