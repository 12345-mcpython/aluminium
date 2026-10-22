package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The sixth audit batch of light cones (21006-21011), each clause read as a BATTLE.
 *
 * <p>⚠⚠ <b>Two defects, both the same shape as 20003's:</b> a share that the cone's own {@code ability_property} already
 * carries was stated AGAIN as a rule - 21007's outgoing healing (+10% became +20%) and 21008's effect hit rate (+20% became
 * +40%). And 21007's energy rule had no gate, so a TEAMMATE's skill cast poured energy into the whole party.
 *
 * <p>⚠ <b>21006 is not judged here.</b> Its second clause needs a follow-up instance, and the engine has no follow-up
 * category or type to produce one from a fixture; the file's scoping (`damage_type: ADDITIONAL`) is therefore unproven and
 * the row says so.
 */
public class ConeAuditBatchSixTest {
    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int ALLY = 1204;

    /**
     * 21007 同一种心情: the heal share is the cone's own property (once), and her skill pours 2 energy into the party - a
     * teammate's skill pours nothing.
     */
    @Test
    public void coneTwoInnocentHealsOnceAndPoursEnergyOnHerSkillOnly() {
        Fixture f = new Fixture(21007, true);
        Fixture bare = new Fixture(null, true);
        double healWith = f.attribute(AttributeType.OUTGOING_HEALING_BOOST);
        double healBare = bare.attribute(AttributeType.OUTGOING_HEALING_BOOST);
        System.out.println("[21007] 治疗加成增量 " + (healWith - healBare));
        Assertions.assertEquals(0.10, healWith - healBare, 1e-9, "「治疗量提高 10%」—— 只由光锥属性给一次");

        f.wearer.setCurrentEnergy(0);
        f.ally.setCurrentEnergy(0);
        f.battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(com.laosun.aluminium.enums.TriggerEvent.SKILL_CAST, f.wearer,
                f.battle.enemyUnits().getFirst(), 0, 0));
        System.out.println("[21007] 她放战技后: 自己 +" + f.wearer.getCurrentEnergy() + "、队友 +" + f.ally.getCurrentEnergy());
        Assertions.assertEquals(2.0, f.wearer.getCurrentEnergy(), 1e-9, "「我方全体恢复 2 点能量」（叠影 1）");
        Assertions.assertEquals(2.0, f.ally.getCurrentEnergy(), 1e-9, "「我方全体」—— 队友也有");

        Fixture other = new Fixture(21007, true);
        other.wearer.setCurrentEnergy(0);
        other.ally.setCurrentEnergy(0);
        other.battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(com.laosun.aluminium.enums.TriggerEvent.SKILL_CAST, other.ally,
                other.battle.enemyUnits().getFirst(), 0, 0));
        System.out.println("[21007] 队友放战技后: 装备者 +" + other.wearer.getCurrentEnergy());
        Assertions.assertEquals(0.0, other.wearer.getCurrentEnergy(), 1e-9, "「施放战技时」指装备者自己");
    }

    /**
     * 21008 猎物的视线: +20% effect hit from the property only, and the DoT share beside it.
     */
    @Test
    public void coneTwoInnocentRaisesEffectHitOnceAndDotDamage() {
        Fixture f = new Fixture(21008, false);
        Fixture bare = new Fixture(null, false);
        double ehr = f.attribute(AttributeType.EFFECT_HIT_RATE) - bare.attribute(AttributeType.EFFECT_HIT_RATE);
        double dot = f.attribute(AttributeType.DOT_DAMAGE_BOOST) - bare.attribute(AttributeType.DOT_DAMAGE_BOOST);
        System.out.println("[21008] 效果命中增量 " + ehr + " ; 持续伤害增量 " + dot);
        Assertions.assertEquals(0.20, ehr, 1e-9, "「效果命中提高 20%」—— 光锥属性给一次，规则不再重复");
        Assertions.assertEquals(0.24, dot, 1e-9, "「造成的持续伤害提高 24%」（叠影 1）");
    }

    // ⚠ 21009 朗道的选择尚未配判据：实测「受到的伤害降低 16%」在装备者身上读到的是
    // **−35.8%** ⇒ 443.73 对无锥 691.32 ✗，与 0.84 不符 ⇒ 先不钉它，写进状态表等下一轮查 ✓。

    /**
     * 21010 论剑: layers are per TARGET - the same target escalates, a different one starts from nothing.
     */
    @Test
    public void coneTwoInnocentStacksPerTarget() {
        Fixture f = new Fixture(21010, false, 1002, 2);
        Enemy first = f.battle.enemyUnits().get(0);
        Enemy second = f.battle.enemyUnits().get(1);
        double firstHit = zone(f, first);
        double secondHitSameTarget = zone(f, first);
        double hitOtherTarget = zone(f, second);
        System.out.println("[21010] 同目标第 1 下 " + firstHit + " ; 第 2 下 " + secondHitSameTarget
                + " ; 换目标的第 1 下 " + hitOtherTarget);
        // The wearer's own content already carries a boost in this zone (measured 1.304 with no layers), so the cone's share is
        // the DIFFERENCE: the second hit on the same target has 2 layers, i.e. +16%.
        Assertions.assertEquals(0.16, secondHitSameTarget - firstHit, 1e-9, "同一目标第 2 下 = 两层 +16%（叠影 1 每层 8%）");
        Assertions.assertEquals(firstHit, hitOtherTarget, 1e-9, "「若攻击目标发生变化，立即解除」—— 新目标从 0 层开始");
    }

    /**
     * 21011 与行星相会: an ally's damage of the wearer's element is 12% higher; another element is not.
     */
    @Test
    public void coneTwoInnocentBoostsTheWearersElement() {
        Fixture f = new Fixture(21011, true);
        Fixture bare = new Fixture(null, true);
        double same = allyHit(f, DamageElement.WIND) / allyHit(bare, DamageElement.WIND);
        double other = allyHit(f, DamageElement.FIRE) / allyHit(bare, DamageElement.FIRE);
        System.out.println("[21011] 队友同属性伤害比值 " + same + " ; 异属性 " + other);
        Assertions.assertEquals(1.12, same, 1e-9, "「与装备者相同属性」+12%（叠影 1）");
        Assertions.assertEquals(1.0, other, 1e-9, "不同属性不加");
    }

    // ==================================================================

    private static double hitWearer(Fixture f) {
        Enemy enemy = f.battle.enemyUnits().getFirst();
        return f.battle.applyDamage(f.wearer,
                new Damage(enemy, f.wearer, DamageElement.PHYSICAL, DamageType.NORMAL, 1000));
    }

    private static double zone(Fixture f, Enemy target) {
        List<Damage> settled = new ArrayList<>();
        f.battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        f.battle.castImmediate(f.wearer.getSkills().get(SkillType.COMMON), f.wearer, List.of(target));
        f.battle.processRequests();
        return settled.get(0).breakdown().get("BoostArea");
    }

    private static double allyHit(Fixture f, DamageElement element) {
        Enemy enemy = f.battle.enemyUnits().getFirst();
        List<Damage> settled = new ArrayList<>();
        f.battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        f.battle.applyDamage(enemy, new Damage(f.ally, enemy, element, DamageType.NORMAL, 1000));
        f.battle.processRequests();
        return settled.get(0).breakdown().get("BoostArea");
    }

    private static final class Fixture {
        private final Character wearer;
        private final Character ally;
        private final Battle battle;

        private Fixture(Integer cone, boolean withAlly) {
            this(cone, withAlly, WEARER, 1);
        }

        private Fixture(Integer cone, boolean withAlly, int wearerId, int enemies) {
            Weapon weapon = cone == null ? null : Weapon.build(cone, LEVEL, false, 1);
            this.wearer = CharacterFactory.create(wearerId, LEVEL, false, weapon);
            this.ally = CharacterFactory.create(ALLY, LEVEL);
            List<Character> party = withAlly ? List.of(wearer, ally) : List.of(wearer);
            List<Enemy> foes = new ArrayList<>();
            for (int i = 0; i < enemies; i++) {
                foes.add(BattleFixture.dummy());
            }
            this.battle = new Battle(party, foes, BattleFixture.alwaysRoll(0.9999));
            battle.startBattle();
            battle.processRequests();
        }

        private double attribute(AttributeType type) {
            return wearer.getAttribute(type).get();
        }
    }
}
