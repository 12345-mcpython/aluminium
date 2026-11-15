package com.laosun.aluminium.test.content.characters.luocha;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1203 罗刹's Eidolons: 1 (「结界生效时我方全体攻击力提高 20%」), 2 (the heal bonus below half HP and the shield at or
 * above it), 3 (the level bumps) and 4 (「结界生效时使敌方目标陷入虚弱状态，造成的伤害降低 12%」).
 *
 * <p>E1's aura lives exactly while the zone does: the zone is opened by his Talent, so the case puts it up directly and
 * then closes it, which leaves the clause under test as the only variable. E2's two halves are mutually exclusive, and
 * the shield half carries the {@code >= 50%} gate -- the sentence's own "if / otherwise", spelled in this engine as a
 * per-target filter on the shield; the heal half is a {@code REGISTER_HEAL_BONUS} with
 * {@code target_when: target_hp_percent < 0.5}.
 *
 * <p>⚠ 1203's Eidolon 6 (「敌方全体全属性抗性降低 20%，持续 2 回合」) has no case in this folder.
 */
public class LuochaEidolonTest {

    private static final int LUOCHA = 1203;
    // Eidolon 1 and 4 use ally 1001; Eidolon 2's heal/shield target is 1002, so the two ids stay separate constants.
    private static final int ALLY = 1001;
    private static final int E2_ALLY = 1002;
    private static final int LEVEL = 80;
    private static final String ZONE = "char:1203/barrier";

    // ==================================================================
    // Eidolon 1: the aura lives exactly while the zone does.
    // ==================================================================

    /**
     * 1203 罗刹's Eidolon 1: 「结界生效时我方全体攻击力提高 20%」.
     */
    @Test
    public void theAuraFollowsTheZone() {
        Character luocha = CharacterFactory.create(LUOCHA, LEVEL, true, null, null, 1);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(luocha, ally)),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();

        // The zone is opened by his Talent; the judge puts it up directly so the clause under test is the only variable.
        luocha.getBuffManager().addBuff(new StateBuff(ZONE, 2, false));
        battle.castImmediate(luocha.getSkills().get(SkillType.SKILL), luocha, List.of(ally));
        double whileUp = ally.getAttribute(AttributeType.ATTACK).get() / ally.getAttribute(AttributeType.ATTACK).baseValue();
        System.out.println("[1203] ally atk/base while the zone is up = " + whileUp);
        Assertions.assertTrue(whileUp > 1.19, "「结界生效时我方全体攻击力提高 20%」");

        // Close the zone and take a turn boundary: the gate drops the aura.
        luocha.getBuffManager().removeState(ZONE);
        luocha.getBuffManager().beforeMove();
        ally.getBuffManager().beforeMove();
        Assertions.assertEquals(1.0, ally.getAttribute(AttributeType.ATTACK).get()
                / ally.getAttribute(AttributeType.ATTACK).baseValue(), 1e-9, "结界结束，加成随之消失");
    }

    // ==================================================================
    // Eidolon 2: two mutually exclusive halves.
    // ==================================================================

    /**
     * The shield half of Eidolon 2 covers an ally at or above half HP, does NOT cover an ally below half (that side is
     * the heal bonus), and an E0 罗刹 shields nobody at all.
     */
    @Test
    public void theShieldOnlyCoversAnAllyAtOrAboveHalfHp() {
        EidolonFixture high = new EidolonFixture(2, 1.0);
        high.cast();
        EidolonFixture low = new EidolonFixture(2, 0.3);
        low.cast();
        EidolonFixture bare = new EidolonFixture(0, 1.0);
        bare.cast();

        Assertions.assertAll(
                () -> Assertions.assertTrue(high.ally.getShield() > 0, "「大于等于 50%，则为其提供…护盾」"),
                () -> Assertions.assertEquals(0.0, low.ally.getShield(), 1e-9,
                        "below half HP the shield must not apply - the other half of the sentence is the heal bonus"),
                () -> Assertions.assertEquals(0.0, bare.ally.getShield(), 1e-9, "E0 has no shield clause"));
    }

    /**
     * Below half HP the other half pays: the heal of an E2 Luocha is 30% larger than an E0 Luocha on the same target.
     */
    @Test
    public void theHealBonusIsTheOtherHalf() {
        // The heal itself can cap at full HP, so the BONUS is measured on the healing amount, not on HP gained.
        double e2 = new EidolonFixture(2, 0.3).calculatedHeal();
        double e0 = new EidolonFixture(0, 0.3).calculatedHeal();
        System.out.println("[1203] E2 heal=" + e2 + " E0 heal=" + e0);
        Assertions.assertEquals(1.3, e2 / e0, 1e-6,
                "「小于 50% 时治疗量提高 30%」 - same cast, same target");

        EidolonFixture full = new EidolonFixture(2, 1.0);
        EidolonFixture fullBare = new EidolonFixture(0, 1.0);
        Assertions.assertEquals(1.0, full.calculatedHeal() / fullBare.calculatedHeal(), 1e-6,
                "a full-HP ally gets no bonus (the condition is the whole point)");
    }

    // ==================================================================

    private static final class EidolonFixture {
        private final Character luocha;
        private final Character ally;
        private final Battle battle;

        /**
         * @param eidolon the Eidolon rank
         * @param hpShare the ally's HP share the fixture starts with
         */
        private EidolonFixture(int eidolon, double hpShare) {
            luocha = CharacterFactory.create(LUOCHA, LEVEL, true, null, null, eidolon);
            ally = CharacterFactory.create(E2_ALLY, LEVEL);
            Enemy enemy = BattleFixture.monster();
            battle = new Battle(new ArrayList<>(List.of(luocha, ally)),
                    new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
            battle.startBattle();
            if (hpShare < 1.0) {
                ally.consumeHp(ally.getMaxHp() * (1.0 - hpShare));
            }
        }

        private void cast() {
            battle.castImmediate(luocha.getSkills().get(SkillType.SKILL), luocha, List.of(ally));
        }

        private double calculatedHeal() {
            return battle.calculateHeal(luocha, ally, 100.0);
        }
    }

    // ==================================================================
    // Eidolon 3: the level bumps.
    // ==================================================================

    @Test
    public void eidolonThreeRaisesSkillAndBasic() {
        Battle e0 = battle(0);
        Battle e3 = battle(3);
        Assertions.assertEquals(level(e0, SkillType.SKILL) + 2, level(e3, SkillType.SKILL), "战技 +2");
        Assertions.assertEquals(level(e0, SkillType.COMMON) + 1, level(e3, SkillType.COMMON), "普攻 +1");
    }

    private static Battle battle(int eidolon) {
        Character luocha = CharacterFactory.create(LUOCHA, LEVEL, true, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(luocha, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return battle;
    }

    private static int level(Battle battle, SkillType slot) {
        return battle.characters().getFirst().skillLevelBonus(slot);
    }

    // ==================================================================
    // Eidolon 4: the damage the ENEMY deals falls while the zone is up.
    // ==================================================================

    /**
     * 1203 罗刹's Eidolon 4: 「结界生效时使敌方目标陷入虚弱状态，造成的伤害降低 12%」.
     */
    @Test
    public void theEnemyDealsLessWhileTheZoneIsUp() {
        double bare = dealtByEnemy(false);
        double weakened = dealtByEnemy(true);
        System.out.println("[1203] enemy damage " + bare + " -> " + weakened + " ratio=" + (weakened / bare));
        Assertions.assertEquals(0.88, weakened / bare, 1e-6, "「造成的伤害降低 12%」");
    }

    private static double dealtByEnemy(boolean withZone) {
        Character luocha = CharacterFactory.create(LUOCHA, LEVEL, true, null, null, 4);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(luocha, ally)),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        if (withZone) {
            luocha.getBuffManager().addBuff(new StateBuff(ZONE, 2, false));
            battle.castImmediate(luocha.getSkills().get(SkillType.SKILL), luocha, List.of(ally));
            Assertions.assertTrue(enemy.getBuffManager().activeBuffs().size() > 0, "precondition: the debuff landed");
        }
        CanHit target = ally;
        return battle.applyDamage(target, new Damage(enemy, target, DamageElement.PHYSICAL, DamageType.NORMAL, 100));
    }
}
