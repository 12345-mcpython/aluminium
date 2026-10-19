package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The second audit batch of light cones (20006-20011), each clause read as a BATTLE.
 *
 * <p>⚠⚠ <b>Two defects found, both silent:</b>
 * <ul>
 *   <li><b>20007 paid for a TEAMMATE's kill.</b> The file had no condition at all, while {@code KILL} is fired with the
 *       killer as {@code actor} and every ally's table sees it - so 「使装备者消灭敌方目标后」 was true for anyone's kill.
 *       Both sides are read here: the wearer's kill pays, the teammate's does not.</li>
 *   <li><b>20011 was a no-op.</b> It asked for {@code target has_state 减速}, and no buff by that name exists (slow is a
 *       negative SPEED modifier, and the DSL's word for it is {@code is_slowed}). Both sides are read here too.</li>
 * </ul>
 */
public class ConeAuditBatchTwoTest {
    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int ALLY = 1204;

    /**
     * 20006 智库: 「终结技造成的伤害提高 28%」 - the ULTIMATE-scoped channel, and not the basic/skill ones.
     */
    @Test
    public void coneTwoThousandSixBoostsTheUltimateChannelOnly() {
        Fixture f = new Fixture(20006, false);
        Fixture bare = new Fixture(null, false);
        double ultimate = f.attribute(AttributeType.ULTIMATE_DAMAGE_BOOST)
                - bare.attribute(AttributeType.ULTIMATE_DAMAGE_BOOST);
        double basic = f.attribute(AttributeType.BASIC_ATTACK_DAMAGE_BOOST)
                - bare.attribute(AttributeType.BASIC_ATTACK_DAMAGE_BOOST);
        System.out.println("[20006] 终结技增伤 +" + ultimate + " ; 普攻 +" + basic);
        Assertions.assertEquals(0.28, ultimate, 1e-9, "叠影 1 的 #1 = 0.28");
        Assertions.assertEquals(0.0, basic, 1e-9, "这一句只点名终结技");
    }

    /**
     * 20007 离弦: 「使装备者消灭敌方目标后，攻击力提高 24%，持续 3 回合」 - the WEARER's kill, not a teammate's.
     */
    @Test
    public void coneTwoThousandSevenPaysOnlyForTheWearersKill() {
        double ownKill = attackAfterKill(true);
        double teammateKill = attackAfterKill(false);
        // ⚠ The share multiplies the panel's base attack, so a raw difference is ~220 points, not 0.24.
        double base = new Fixture(20007, true).wearer.getAttribute(AttributeType.ATTACK).baseValue();
        System.out.println("[20007] 自己击杀后攻击增量 " + ownKill + "（/ 基础攻击 " + base + " = " + (ownKill / base)
                + "） ; 队友击杀后 " + teammateKill);
        Assertions.assertEquals(0.24, ownKill / base, 1e-9, "「使装备者消灭敌方目标后，攻击力提高 24%」（叠影 1）");
        Assertions.assertEquals(0.0, teammateKill, 1e-9,
                "「使**装备者**消灭」—— 队友的击杀不该给装备者，`actor == self` 这一半");
    }

    /**
     * 20008 嘉果: 「战斗开始时，立即为我方全体恢复 6 点能量」.
     */
    @Test
    public void coneTwoThousandEightPoursEnergyIntoTheParty() {
        Fixture f = new Fixture(20008, true);
        Fixture bare = new Fixture(null, true);
        System.out.println("[20008] 有锥时的能量: " + f.wearer.getCurrentEnergy() + " ; 无锥: " + bare.wearer.getCurrentEnergy());
        for (int i = 0; i < f.battle.characters.size(); i++) {
            double gained = f.battle.characters.get(i).getCurrentEnergy()
                    - bare.battle.characters.get(i).getCurrentEnergy();
            System.out.println("[20008] " + f.battle.characters.get(i).getName() + " 能量增量 " + gained);
            Assertions.assertEquals(6.0, gained, 1e-9, "「我方全体」—— 每人 6 点（叠影 1）");
        }
    }

    /**
     * 20009 乐圮: 「对当前生命值百分比大于 50% 的敌方目标造成的伤害提高 20%」 - and nothing against a hurt one.
     */
    @Test
    public void coneTwoThousandNineBoostsOnlyAgainstHealthyTargets() {
        double healthy = boostAgainstEnemyAt(20009, 1.0);
        double hurt = boostAgainstEnemyAt(20009, 0.3);
        double bareZone = boostAgainstEnemyAt(null, 1.0);
        System.out.println("[20009] 满血目标增伤区 ×" + healthy + " ; 血量 30% 目标 ×" + hurt
                + " ; 无锥的同一发 ×" + bareZone);
        // The wearer's own content already boosts in this bucket, so the cone's share is the difference of the two states.
        Assertions.assertEquals(0.20, healthy - hurt, 1e-9, "叠影 1 的 #2 = 0.20，只在「大于 50%」时加");
        Assertions.assertEquals(bareZone, hurt, 1e-9, "血量 30% 的敌人：锥一分都不加");
    }

    /**
     * 20010 戍御: 「施放终结技时，回复等同于自身生命上限 18% 的生命值」.
     */
    @Test
    public void coneTwoThousandTenHealsTheWearerOnUltimate() {
        Fixture f = new Fixture(20010, false);
        f.wearer.setCurrentEnergy(f.wearer.getMaxEnergy());  // an ultimate needs a full bar
        f.wearer.consumeHp(f.wearer.getMaxHp() * 0.5);
        double before = f.wearer.getCurrentHp();
        f.battle.castUltra(f.wearer, List.of(f.battle.enemyUnits().getFirst()));
        f.battle.processRequests();
        double healed = f.wearer.getCurrentHp() - before;
        System.out.println("[20010] 终结技后的治疗 " + healed + " ; 生命上限 × 0.18 = " + (f.wearer.getMaxHp() * 0.18));
        Assertions.assertEquals(f.wearer.getMaxHp() * 0.18, healed, f.wearer.getMaxHp() * 1e-9,
                "叠影 1 的 #1 = 18% 生命上限");
    }

    /**
     * 20011 渊环: 「对减速状态下的敌方目标造成的伤害提高 24%」 - and nothing against a unit that is not slowed.
     */
    @Test
    public void coneTwoThousandElevenBoostsOnlyAgainstSlowedTargets() {
        double slowed = boostAgainstEnemyAt(20011, 1.0, true);
        double normal = boostAgainstEnemyAt(20011, 1.0, false);
        double bareSlowed = boostAgainstEnemyAt(null, 1.0, true);
        double bareNormal = boostAgainstEnemyAt(null, 1.0, false);
        System.out.println("[20011] 减速目标增伤区 ×" + slowed + " ; 未减速 ×" + normal
                + " ; 无锥但减速 ×" + bareSlowed);
        // 1002's own content also boosts against a slowed target (measured 0.40), so only the DIFFERENCE is this cone's.
        Assertions.assertEquals(0.24, slowed - bareSlowed, 1e-9, "叠影 1 的 #1 = 0.24");
        Assertions.assertEquals(0.0, normal - bareNormal, 1e-9,
                "没被减速就不触发（`is_slowed` 这一半）；⚠ 未减速时的 1.224 是 1002 自己的增伤");
    }

    // ==================================================================

    /**
     * The wearer's (or the teammate's) kill, then the wearer's attack gain.
     */
    private static double attackAfterKill(boolean wearerKills) {
        Fixture f = new Fixture(20007, true);
        double before = attack(f.wearer);
        Enemy victim = f.battle.enemyUnits().getFirst();
        Character killer = wearerKills ? f.wearer : f.battle.characters.get(1);
        f.battle.applyDamage(victim, new Damage(killer, victim, DamageElement.PHYSICAL, DamageType.NORMAL, 1e9));
        f.battle.processRequests();
        return attack(f.wearer) - before;
    }

    /**
     * The boost zone of one basic attack against an enemy at {@code hpFraction} of its health (optionally slowed).
     */
    private static double boostAgainstEnemyAt(Integer cone, double hpFraction) {
        return boostAgainstEnemyAt(cone, hpFraction, false);
    }

    private static double boostAgainstEnemyAt(Integer cone, double hpFraction, boolean slowed) {
        Fixture f = new Fixture(cone, false);
        Enemy target = f.battle.enemyUnits().getFirst();
        if (hpFraction < 1.0) {
            f.battle.applyDamage(target, new Damage(f.wearer, target, DamageElement.PHYSICAL, DamageType.NORMAL,
                    target.getMaxHp() * (1 - hpFraction)));
        }
        if (slowed) {
            // 「减速」 is a negative SPEED modifier, which is exactly what `is_slowed` answers for.
            target.getAttribute(AttributeType.SPEED)
                    .addModifier(DoubleValue.Modifier.addPercent(-0.2, DoubleValue.Modifier.ModifierSource.EXTRA));
        }
        List<Damage> settled = new ArrayList<>();
        f.battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        f.battle.castImmediate(f.wearer.getSkills().get(SkillType.COMMON), f.wearer, List.of(target));
        f.battle.processRequests();
        return settled.getFirst().breakdown().get("BoostArea");
    }

    private static double attack(Character unit) {
        return unit.getAttribute(AttributeType.ATTACK).get();
    }

    private static final class Fixture {
        private final Character wearer;
        private final Battle battle;

        private Fixture(Integer cone, boolean withAlly) {
            Weapon weapon = cone == null ? null : Weapon.build(cone, LEVEL, false, 1);
            this.wearer = CharacterFactory.create(WEARER, LEVEL, false, weapon);
            List<Character> party = withAlly
                    ? List.of(this.wearer, CharacterFactory.create(ALLY, LEVEL))
                    : List.of(this.wearer);
            this.battle = new Battle(party, List.of(BattleFixture.dummy()), BattleFixture.alwaysRoll(0.9999));
            battle.startBattle();
            battle.processRequests();
        }

        private double attribute(AttributeType type) {
            return wearer.getAttribute(type).get();
        }
    }
}
