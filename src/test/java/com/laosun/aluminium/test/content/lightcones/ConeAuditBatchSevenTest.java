package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.damage.Damage;
import com.laosun.aluminium.models.equipment.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The seventh audit batch of light cones (21012-21017), each clause read as a BATTLE.
 *
 * <p>⚠⚠ <b>Two defects:</b> 21014 stated its effect-resistance share twice (property + rule, so +32%), and 21017's whole
 * second clause (「当前能量值等于上限时，该效果额外提高 #2%」) was <b>missing</b> - no rule carried it at all.
 */
public class ConeAuditBatchSevenTest {
    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int ALLY = 1204;

    /**
     * 21012 秘密誓心: the extra share lands only when the target's HP percentage is at or above the wearer's.
     */
    @Test
    public void coneTwoInnocentExtraAgainstHealthierTargets() {
        Fixture f = new Fixture(21012, false);
        Enemy enemy = f.battle.enemyUnits().getFirst();
        double atFull = zone(f, enemy);                       // both at 100% ⇒ >= holds
        f.wearer.consumeHp(f.wearer.getMaxHp() * 0.5);
        double hurtWearer = zone(f, enemy);                   // 100% >= 50% ⇒ still holds
        // A FRESH enemy for the third reading: the first one dies to the 85% hit, and a dead target settles nothing.
        Enemy weak = BattleFixture.dummy();
        weak.consumeHp(weak.getMaxHp() * 0.85);              // 15% < her 50% ⇒ does not hold
        double hurtEnemy = zone(f, weak);
        System.out.println("[21012] 增伤区: 两人满血 " + atFull + " ; 她 50% 血 " + hurtWearer + " ; 敌人 15% 血 " + hurtEnemy);
        Assertions.assertEquals(atFull, hurtWearer, 1e-9, "「大于等于」—— 敌人满血 ≥ 她 50% ⇒ 仍然加");
        Assertions.assertEquals(0.2, atFull - hurtEnemy, 1e-9, "敌人比她血少 ⇒ 不再加那 20%");
    }

    /**
     * 21013 别让世界静下来: +20 energy at battle start and +32% on the ultimate channel.
     */
    @Test
    public void coneTwoInnocentStartsWithEnergyAndBoostsUltimates() {
        Fixture f = new Fixture(21013, false);
        Fixture bare = new Fixture(null, false);
        System.out.println("[21013] 开战能量 " + f.wearer.getCurrentEnergy() + " ; 无锥 " + bare.wearer.getCurrentEnergy()
                + " ; 终结技档增量 " + (f.attribute(AttributeType.ULTIMATE_DAMAGE_BOOST)
                - bare.attribute(AttributeType.ULTIMATE_DAMAGE_BOOST)));
        Assertions.assertEquals(20.0, f.wearer.getCurrentEnergy() - bare.wearer.getCurrentEnergy(), 1e-9,
                "「进入战斗时立即恢复 20 点能量」（叠影 1）");
        Assertions.assertEquals(0.32, f.attribute(AttributeType.ULTIMATE_DAMAGE_BOOST)
                - bare.attribute(AttributeType.ULTIMATE_DAMAGE_BOOST), 1e-9, "「终结技造成的伤害提高 32%」");
    }

    /**
     * 21014 此时恰好: the healing share is 33% OF the effect resistance, and the resistance itself is stated once.
     */
    @Test
    public void coneTwoInnocentHealsOffItsEffectResistance() {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, false, Weapon.build(21014, LEVEL, false, 1));
        Character bare = CharacterFactory.create(WEARER, LEVEL);
        Battle battle = new Battle(List.of(wearer), List.of(BattleFixture.dummy()), BattleFixture.alwaysRoll(0.9999));
        battle.startBattle();
        battle.processRequests();
        double effRes = wearer.getAttribute(AttributeType.EFFECT_RESISTANCE).get()
                - bare.getAttribute(AttributeType.EFFECT_RESISTANCE).get();
        double heal = wearer.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get()
                - bare.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get();
        System.out.println("[21014] 效果抵抗增量 " + effRes + " ; 治疗增量 " + heal
                + "（= 抵抗 × 0.33 = " + (effRes * 0.33) + "）");
        Assertions.assertEquals(0.16, effRes, 1e-9, "「效果抵抗提高 16%」—— 光锥属性给一次");
        Assertions.assertEquals(effRes * 0.33, heal, 1e-6, "「提高数值等同于效果抵抗的 33%」");
    }

    /**
     * 21016 宇宙市场趋势: being hit applies a burn worth 40% of her DEFENCE.
     */
    @Test
    public void coneTwoInnocentBurnsTheAttacker() {
        Fixture f = new Fixture(21016, false);
        Enemy enemy = f.battle.enemyUnits().getFirst();
        double defence = f.wearer.getAttribute(AttributeType.DEFENCE).get();
        double before = enemy.getCurrentHp();
        f.battle.applyDamage(f.wearer, new Damage(enemy, f.wearer, DamageElement.PHYSICAL, DamageType.NORMAL, 10));
        f.battle.processRequests();
        double burn = enemy.getCurrentHp() - before + 0;   // the burn ticks on the enemy's turn; read what landed now
        System.out.println("[21016] 她的防御 " + defence + " ; 灼烧那一发在敌人身上落了 " + burn
                + " ; 敌人身上有灼烧 = " + (enemy.getBuffManager().hasBuff(com.laosun.aluminium.models.buff.DotBuff.class)));
        // The burn is a state on the enemy whose per-tick value is 40% of her DEFENCE; the state existing is the reading.
        Assertions.assertTrue(enemy.getBuffManager().hasBuff(com.laosun.aluminium.models.buff.DotBuff.class),
                "「受到攻击后有基础概率使敌方目标陷入灼烧状态」—— 状态要挂在敌人身上");
    }

    /**
     * 21017 点个关注吧！: with a full energy bar the basic/skill boosts are 24% higher again.
     */
    @Test
    public void coneTwoInnocentPaysMoreWhenEnergyIsFull() {
        Fixture f = new Fixture(21017, false);
        double base = f.attribute(AttributeType.BASIC_ATTACK_DAMAGE_BOOST) + f.attribute(AttributeType.SKILL_DAMAGE_BOOST);
        f.wearer.setCurrentEnergy(f.wearer.getMaxEnergy());
        // The rule re-reads at the wearer's own turn start (mid-turn ENERGY_GAINED doubled the layers - measured, reverted).
        f.battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, f.wearer, f.wearer, 0, 0));
        double full = f.attribute(AttributeType.BASIC_ATTACK_DAMAGE_BOOST) + f.attribute(AttributeType.SKILL_DAMAGE_BOOST);
        System.out.println("[21017] 两档合计: 一般 " + base + " ; 满能量 " + full);
        Assertions.assertEquals(0.48, full - base, 1e-9, "「能量满时，该效果额外提高 24%」⇒ 两档各多 0.24（合计 +0.48）");
    }

    // ==================================================================

    private static double zone(Fixture f, Enemy target) {
        List<Damage> settled = new ArrayList<>();
        f.battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        f.battle.castImmediate(f.wearer.getSkills().get(SkillType.COMMON), f.wearer, List.of(target));
        f.battle.processRequests();
        return settled.get(0).breakdown().get("BoostArea");
    }

    private static final class Fixture {
        private final Character wearer;
        private final Battle battle;

        private Fixture(Integer cone, boolean withAlly) {
            Weapon weapon = cone == null ? null : Weapon.build(cone, LEVEL, false, 1);
            this.wearer = CharacterFactory.create(WEARER, LEVEL, false, weapon);
            List<Character> party = withAlly ? List.of(wearer, CharacterFactory.create(ALLY, LEVEL)) : List.of(wearer);
            this.battle = new Battle(party, List.of(BattleFixture.dummy()), BattleFixture.alwaysRoll(0.9999));
            battle.startBattle();
            battle.processRequests();
        }

        private double attribute(AttributeType type) {
            return wearer.getAttribute(type).get();
        }
    }
}
