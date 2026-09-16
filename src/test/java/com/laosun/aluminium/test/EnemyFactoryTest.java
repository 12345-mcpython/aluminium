package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.MonsterConfig;
import com.laosun.aluminium.beans.MonsterTemplate;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * P2-4 acceptance: 工厂造出的敌人面板、弱点、抗性都对，并且**抗性真的进入了伤害流水线**。
 *
 * <p>锚点：冰锋 1002011 在组1·Lv90 → HP≈16498.296、防御≈1100、速度 132、弱火/雷、冰抗 0.2、韧性 60。
 */
public class EnemyFactoryTest {
    private static final double EPS = 1e-6;

    @Test
    public void iceEdgePanelIsAssembled() {
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);

        Assertions.assertEquals(16498.296, enemy.getMaxHp(), 1e-3);
        Assertions.assertEquals(16498.296, enemy.getCurrentHp(), 1e-3, "初始 HP = 生命上限");
        Assertions.assertEquals(1099.99995, enemy.getAttribute(AttributeType.DEFENCE).get(), 1e-4);
        Assertions.assertEquals(662.784912, enemy.getAttribute(AttributeType.ATTACK).get(), 1e-6);
        Assertions.assertEquals(132, enemy.getAttribute(AttributeType.SPEED).get(), EPS);
        Assertions.assertEquals(90, enemy.getLevel(), "等级来自关卡，进防御区");
    }

    @Test
    public void weaknessAndToughnessAreCarriedOver() {
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);

        Assertions.assertEquals(Set.of(DamageElement.FIRE, DamageElement.THUNDER), enemy.getStanceWeak());
        Assertions.assertTrue(enemy.isWeakTo(DamageElement.FIRE));
        Assertions.assertFalse(enemy.isWeakTo(DamageElement.ICE));
        Assertions.assertFalse(enemy.isWeakTo(null));
        Assertions.assertEquals(60, enemy.getMaxStance(), EPS);      // 模板 stance 60 × 组1·Lv90 的 1
        Assertions.assertEquals(60, enemy.getStance(), EPS);
        Assertions.assertEquals(1, enemy.getStanceCount());
        Assertions.assertEquals(DamageElement.ICE, enemy.getStanceType());
    }

    @Test
    public void resistanceFlowsIntoTheDamagePipeline() {
        Character attacker = Character.fromAttributes("attacker", 1000, 100, 100, 100);  // ATK 100 / Lv80
        Enemy iceEdge = EnemyFactory.create(1002011, 90, 1);
        Battle battle = new Battle(List.of(attacker), List.of(iceEdge), new Random(0));

        double defence = iceEdge.getAttribute(AttributeType.DEFENCE).get();
        Damage iceHit = new Damage(attacker, iceEdge, DamageElement.ICE, DamageType.NORMAL, 1000);

        double settled = battle.applyDamage(iceEdge, iceHit);

        // 防御区 = 1000 / (def + 1000)（攻击者 Lv80）；抗性区 = 1 - 0.2（冰锋冰抗 0.2）
        Assertions.assertEquals(1000.0 * 1000.0 / (defence + 1000.0) * 0.8, settled, EPS);
    }

    @Test
    public void patchedAttackRatioReachesThePanel() {
        // 100201506 的攻击修正在 tbgd 里是 0.33333302，本项目数据缺这一列 → 由补丁文件合并
        EnemyConfigAndTemplate pair = configOf(100201506);
        Enemy enemy = EnemyFactory.create(100201506, 90, 1);

        Assertions.assertEquals(0.33333302, pair.config().attackRatio(), 1e-8);
        double expectedAttack = pair.template().attack()
                * Constant.HARD_LEVEL_GROUPS.get(1).get(90).attack()
                * pair.config().attackRatio();
        Assertions.assertEquals(expectedAttack, enemy.getAttribute(AttributeType.ATTACK).get(), 1e-6);
    }

    @Test
    public void unknownMonsterOrLevelIsRejected() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> EnemyFactory.create(99999999, 90, 1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> EnemyFactory.create(1002011, 999, 1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> EnemyFactory.create(1002011, 90, 999));
    }

    private record EnemyConfigAndTemplate(MonsterConfig config, MonsterTemplate template) {
    }

    private static EnemyConfigAndTemplate configOf(int monsterId) {
        MonsterConfig config = Constant.MONSTER_CONFIGS.get(monsterId);
        return new EnemyConfigAndTemplate(config, Constant.MONSTER_TEMPLATES.get(config.templateId()));
    }
}
