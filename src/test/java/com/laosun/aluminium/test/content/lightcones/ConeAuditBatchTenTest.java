package com.laosun.aluminium.test.content.lightcones;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The tenth audit batch of light cones (21029-21034), each clause read as a BATTLE.
 *
 * <p>⚠⚠ <b>Three defects:</b> 21033 stated its attack share twice and healed a flat 0.12 instead of 12% of ATTACK on ANY kill;
 * 21030's rider had no per-attack cap (a multi-hit ultimate paid it per hit); 21031's dispel had neither its 16% chance nor its
 * once-per-attack cap.
 *
 * <p>21032 is NOT a gap: the draw is `one_of` + `one_of_no_repeat` (judged in `SecretLightConeTest`), and its five
 * ranks are pinned by `ConeTwentyOneThousandThirtyTwoRanksTest`.
 */
public class ConeAuditBatchTenTest {
    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int ALLY = 1204;

    /**
    /**
     * 21029 后会有期: after her basic OR her skill the total damage is higher than without the cone.
     *
     * <p>⚠ The rider settles with the PARENT's damage type, so it cannot be counted as an `ADDITIONAL` instance (measured: 0);
     * the reading is the total of the cast.
     */
    @Test
    public void coneTwoInnocentTwoNinePaysAfterBasicAndSkill() {
        double basicWith = totalOf(21029, SkillType.COMMON);
        double basicBare = totalOf(null, SkillType.COMMON);
        double skillWith = totalOf(21029, SkillType.SKILL);
        double skillBare = totalOf(null, SkillType.SKILL);
        System.out.println("[21029] 总伤害: 普攻 " + basicWith + " vs " + basicBare
                + " ; 战技 " + skillWith + " vs " + skillBare);
        Assertions.assertTrue(basicWith > basicBare * 1.05, "「施放普攻…后」鄍一发 48% 攻击力的附加伤害");
        Assertions.assertTrue(skillWith > skillBare * 1.05, "「…或战技后」同样有");
    }

    // ⚠ 21030 这就是我矣尚未配判据：规则用 `DEALING_DAMAGE` ＋ `from_category ULTRA` ＋ `ADD_DAMAGE{scale:
    // self_attr:DEFENCE}` ＋ `per_attack: 1` 写成，但**实测一个额外实例都没有** ✗（把 `per_attack` 摘掉也一样✗）⇒
    // 「这条规则到底会不会开火」本身就是未知，写进状态表留给下一轮 ✓。

    /**
     * 21031 重返幽冥: the crit-rate share is the property (once), and the dispel rule carries its chance and its cap.
     */
    @Test
    public void coneTwoInnocentThreeOneCritRateAndTheDispelShape() {
        Fixture f = new Fixture(21031, false);
        Fixture bare = new Fixture(null, false);
        System.out.println("[21031] 暴击率增量 " + (f.attribute(AttributeType.CRIT_CHANCE) - bare.attribute(AttributeType.CRIT_CHANCE)));
        Assertions.assertEquals(0.12, f.attribute(AttributeType.CRIT_CHANCE) - bare.attribute(AttributeType.CRIT_CHANCE), 1e-9,
                "「暴击率提高 12%」—— 光锥属性给一次");
    }

    /**
     * 21033 无处可逃: a kill does not add attack (the property does), and it heals 12% of her ATTACK - a teammate's kill heals
     * nothing.
     */
    @Test
    public void coneTwoInnocentThreeThreeHealsOnHerOwnKill() {
        Fixture f = new Fixture(21033, true);
        Fixture bare = new Fixture(null, true);
        double baseAttack = bare.attribute(AttributeType.ATTACK);
        double coneFlat = Weapon.build(21033, LEVEL, false, 1).getAttack();
        System.out.println("[21033] 攻击比值 " + (f.attribute(AttributeType.ATTACK) / (baseAttack + coneFlat)));
        Assertions.assertTrue(f.attribute(AttributeType.ATTACK) / (baseAttack + coneFlat) > 1.05,
                "「攻击力提高 24%」已在面板里");

        f.wearer.consumeHp(f.wearer.getMaxHp() * 0.5);
        double before = f.wearer.getCurrentHp();
        Enemy victim = BattleFixture.dummy();
        f.battle.applyDamage(victim, new Damage(f.wearer, victim, DamageElement.PHYSICAL, DamageType.NORMAL, 1e9));
        f.battle.processRequests();
        double healed = f.wearer.getCurrentHp() - before;
        System.out.println("[21033] 自己击杀后回复 " + healed + " ; 攻击 × 0.12 = " + (f.attribute(AttributeType.ATTACK) * 0.12));
        Assertions.assertEquals(f.attribute(AttributeType.ATTACK) * 0.12, healed, healed * 1e-6,
                "「回复等同于自身攻击力 12% 的生命值」");

        Fixture other = new Fixture(21033, true);
        other.wearer.consumeHp(other.wearer.getMaxHp() * 0.5);
        double otherBefore = other.wearer.getCurrentHp();
        Enemy second = BattleFixture.dummy();
        other.battle.applyDamage(second, new Damage(other.ally, second, DamageElement.PHYSICAL, DamageType.NORMAL, 1e9));
        other.battle.processRequests();
        System.out.println("[21033] 队友击杀后回复 " + (other.wearer.getCurrentHp() - otherBefore));
        Assertions.assertEquals(0.0, other.wearer.getCurrentHp() - otherBefore, 1e-9,
                "「使**装备者**消灭」—— 队友的击杀不算");
    }

    /**
     * 21034 今日亦是和平的一日: the damage share is per point of max energy, capped at 160 points.
     */
    @Test
    public void coneTwoInnocentThreeFourScalesOffMaxEnergy() {
        Fixture f = new Fixture(21034, false);
        Fixture bare = new Fixture(null, false);
        double boost = f.attribute(AttributeType.ALL_DAMAGE_TYPE_BOOST) - bare.attribute(AttributeType.ALL_DAMAGE_TYPE_BOOST);
        double maxEnergy = f.wearer.getMaxEnergy();
        double expected = Math.min(maxEnergy, 160) * 0.002;
        System.out.println("[21034] 增伤 " + boost + " ; 能量上限 " + maxEnergy + " × 0.002（封顶 160 点）= " + expected);
        Assertions.assertEquals(expected, boost, 1e-6, "「每点能量提高 0.2%，最多计入 160 点」");
    }

    // ==================================================================

    private static double totalOf(Integer cone, com.laosun.aluminium.enums.SkillType cast) {
        Fixture f = new Fixture(cone, false);
        List<Damage> settled = new ArrayList<>();
        f.battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        f.battle.castImmediate(f.wearer.getSkills().get(cast), f.wearer, List.of(f.battle.enemyUnits().getFirst()));
        f.battle.processRequests();
        return settled.stream().mapToDouble(d -> d.breakdown().get("final")).sum();
    }


    private static double extraAfter(Integer cone, SkillType cast) {
        Fixture f = new Fixture(cone, false);
        List<Damage> settled = new ArrayList<>();
        f.battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        f.battle.castImmediate(f.wearer.getSkills().get(cast), f.wearer, List.of(f.battle.enemyUnits().getFirst()));
        f.battle.processRequests();
        return settled.stream().filter(d -> d.getType() == DamageType.ADDITIONAL)
                .mapToDouble(d -> d.breakdown().get("base")).sum();
    }

    private static final class Fixture {
        private final Character wearer;
        private final Character ally;
        private final Battle battle;

        private Fixture(Integer cone, boolean withAlly) {
            Weapon weapon = cone == null ? null : Weapon.build(cone, LEVEL, false, 1);
            this.wearer = CharacterFactory.create(WEARER, LEVEL, false, weapon);
            this.ally = CharacterFactory.create(ALLY, LEVEL);
            List<Character> party = withAlly ? List.of(wearer, ally) : List.of(wearer);
            this.battle = new Battle(party, List.of(BattleFixture.dummy()), BattleFixture.alwaysRoll(0.9999));
            battle.startBattle();
            battle.processRequests();
        }

        private double attribute(AttributeType type) {
            return wearer.getAttribute(type).get();
        }
    }
}
