package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import com.laosun.aluminium.models.EnemySkill;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * P5-3 验收：敌人技能（数据驱动 + 兜底）。
 *
 * <p>⚠ 倍率来源见 {@code enemy_skills.json} 的说明：数据源里**没有**敌人技能表，这些倍率是猜的。
 * 所以断言的是"引擎按倍率正确结算"，不是"这个倍率是游戏真值"。
 *
 * <p>期望值一律**从实际面板推导**（攻击力 × 倍率 × 防御区），不写死数字 ——
 * 写死过一次，结果把敌方防御和我方防御算反了。
 */
public class EnemySkillTest {
    private static final double EPS = 1e-9;
    /** 受害者防御 1000、Lv80 → 防御区 = (200 + 10×80) / (1000 + 1000) = 0.5。 */
    private static final double VICTIM_DEFENCE = 1000;

    @Test
    public void everyEnemyGetsAnAttackFromDataOrFallback() {
        Enemy iceEdge = EnemyFactory.create(1002011, 90, 1);

        EnemySkill attack = (EnemySkill) iceEdge.getSkills().get(SkillType.COMMON);
        Assertions.assertNotNull(attack, "敌人身上必须有普攻");

        var data = Constant.ENEMY_SKILLS.get(1002011);
        Assertions.assertNotNull(data, "冰锋在 enemy_skills.json 里有条目（键是怪物实例 id）");
        Assertions.assertTrue(data.guessed(), "倍率是猜的，数据里如实标了");
        Assertions.assertEquals(100201101, data.id());
        Assertions.assertEquals(data.multiplier(), attack.getMultiplier(), EPS);
        Assertions.assertEquals(data.hits(), attack.getHits());
        Assertions.assertEquals(DamageElement.ICE, attack.getElement(), "表里写的是 Ice");
        Assertions.assertNull(attack.getData(), "EnemySkill 不走角色的倍率表");
    }

    /**
     * 兜底：表里没有条目的怪物，也要能打人（倍率 1.0、单段、元素取自身 stance_type）。
     */
    @Test
    public void unknownMonsterFallsBackToNeutralAttack() {
        // 鸣雷造物 8001040：真实存在，但不在 enemy_skills.json 里
        Enemy grunt = EnemyFactory.create(8001040, 80, 1);

        EnemySkill attack = (EnemySkill) grunt.getSkills().get(SkillType.COMMON);
        Assertions.assertNotNull(attack, "没数据也要有兜底普攻，不能站着不动");
        Assertions.assertEquals(1.0, attack.getMultiplier(), EPS);
        Assertions.assertEquals(1, attack.getHits());
        Assertions.assertNull(Constant.ENEMY_SKILLS.get(8001040), "确认它真的不在表里");
    }

    @Test
    public void attackSettlesAttackPowerTimesMultiplierThroughTheDefenceZone() {
        Enemy iceEdge = EnemyFactory.create(1002011, 90, 1);
        Character victim = victim();
        Battle battle = new Battle(List.of(victim), List.of(iceEdge), new Random(0));

        double attack = iceEdge.getAttribute(AttributeType.ATTACK).get();
        double settled = battle.applyDamage(victim,
                new Damage(iceEdge, victim, DamageElement.ICE, DamageType.NORMAL, attack * 1.0));

        double defenceZone = defenceZone(iceEdge.getLevel(), VICTIM_DEFENCE);
        Assertions.assertEquals(attack * 1.0 * defenceZone, settled, 0.1);
    }

    /**
     * 多段：{@link EnemySkill} 把 N 段实现成 N 次独立结算，所以伤害累加、
     * 受击回能也按段数各发一次（每一段都是一次独立的攻击行为）。
     */
    @Test
    public void multiHitAttackSettlesEverySegment() {
        Enemy trampler = EnemyFactory.create(8013010, 80, 1);
        EnemySkill attack = (EnemySkill) trampler.getSkills().get(SkillType.COMMON);
        Assertions.assertEquals(2, attack.getHits(), "表里写了 2 段");

        Character victim = victim();
        victim.setMaxEnergy(120);
        Battle battle = new Battle(List.of(victim), List.of(trampler), new Random(0));

        double attackPower = trampler.getAttribute(AttributeType.ATTACK).get();
        double hpBefore = victim.getCurrentHp();
        attack.execute(battle, trampler, List.of(victim));

        double perHit = attackPower * 1.0 * defenceZone(trampler.getLevel(), VICTIM_DEFENCE);
        Assertions.assertEquals(perHit * 2, hpBefore - victim.getCurrentHp(), 0.2, "两段各结算一次");
        Assertions.assertEquals(20, victim.getCurrentEnergy(), 1e-6, "两段各给一次受击回能（标准 10）");
    }

    @Test
    public void enemyAttackUsesTheSharedDamagePipeline() {
        // 敌人暴击率为 0 → 不暴击；所以结算值 = 攻击力 × 倍率 × 防御区 × 抗性区（角色没有抗性表 = 1）
        Enemy iceEdge = EnemyFactory.create(1002011, 90, 1);
        Character victim = victim();
        Battle battle = new Battle(List.of(victim), List.of(iceEdge), new Random(0));

        Assertions.assertEquals(0, iceEdge.getAttribute(AttributeType.CRIT_CHANCE).get(), EPS);
        double settled = battle.applyDamage(victim,
                new Damage(iceEdge, victim, DamageElement.ICE, DamageType.NORMAL, 1000));

        Assertions.assertEquals(1000 * defenceZone(iceEdge.getLevel(), VICTIM_DEFENCE), settled, 0.1);
    }

    // ==================================================================

    private static Character victim() {
        return Character.fromAttributes("victim", 100_000, VICTIM_DEFENCE, 100, 100);
    }

    /** 防御区 = (200 + 10 × 攻击者等级) / (受击者防御 + 200 + 10 × 攻击者等级)。 */
    private static double defenceZone(int attackerLevel, double defenderDefence) {
        double levelTerm = Constant.DEFENCE_CONST + Constant.DEFENCE_PER_LEVEL * attackerLevel;
        return levelTerm / (defenderDefence + levelTerm);
    }
}
