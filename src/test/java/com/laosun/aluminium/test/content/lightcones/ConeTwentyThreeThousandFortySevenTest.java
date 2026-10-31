package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 海洋为何而歌 (23047), the 【魂迷】 clause: 「每有 1 个<b>装备者施加的</b>负面效果，受到的持续伤害提高 <b>5%</b>，最多 <b>6</b> 层」.
 *
 * <p><b>What was missing.</b> The old note called this clause impossible because the engine could not count 「装备者施加的负面效果」.
 * It can: a buff keeps its {@code getSource()}, so "negative AND applied by me" is a fact. The stair reads it through the counter
 * {@code target_debuffs_from_self}, and the zone is re-stated on every debuff so the layer count follows the live count - which is
 * measured here as the DAMAGE a DoT instance actually takes.
 */
public class ConeTwentyThreeThousandFortySevenTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1204;


    private static final class Run {
        private final Character wearer;
        private final Enemy enemy;
        private final Battle battle;

        Run() {
            wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(23047, LEVEL, false, 1));
            enemy = BattleFixture.monster();
            battle = new Battle(new ArrayList<>(List.of(wearer)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.seeded());
            battle.startBattle();
            enemy.getBuffManager().addBuff(new com.laosun.aluminium.buff.StateBuff("魂迷", 3));
        }

        void applyMyDebuffs(int count) {
            for (int i = 0; i < count; i++) {
                // A StateBuff is never a debuff (`StateBuff.isDebuff()` is false), so a real negative EFFECT is used: a
                // DEBUFF-role modifier, which is exactly what 「负面效果」 means to the engine.
                // A DOT per ELEMENT: same-kind buffs REPLACE each other, so several debuffs must differ in kind - and a DOT touches
                // no attribute of the enemy, so the measurement below is not contaminated by a defence change (measured: a
                // DEFENCE-down "debuff" moved the very damage this test reads).
                var element = com.laosun.aluminium.enums.DamageElement.values()[i % 7];
                if (i >= 7) {
                    // More debuffs than elements exist: name them so each is still its own kind.
                    enemy.getBuffManager().addBuff(new com.laosun.aluminium.buff.DotBuff(wearer, element, 1, 99, 1, "我施加的负面" + i));
                    continue;
                }
                enemy.getBuffManager().addBuff(new com.laosun.aluminium.buff.DotBuff(wearer, element, 1, 99));
            }
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                    TriggerEvent.DEBUFF_APPLIED, wearer, enemy, count, 0));
            battle.processRequests();
        }

        /**
         * What one DoT instance of 1000 base settles for on the enemy.
         */
        double dotSettled() {
            Damage dot = new Damage(wearer, enemy, DamageElement.PHYSICAL, DamageType.DOT, 1000);
            return battle.applyDamage(enemy, dot);
        }
    }

    /**
     * 「当有敌方目标陷入装备者施加的负面效果时，有 80% 基础概率使其陷入【魂迷】，持续 3 回合，同类效果无法叠加」.
     *
     * <p>The guard {@code !target has_state 魂迷} is not decoration: an {@code APPLY_BUFF} with a {@code base_chance} goes through
     * {@code DebuffService.tryApply}, which fires {@code DEBUFF_APPLIED} again after adding the buff - so without it the rule meets
     * its own application (measured: 「Trigger recursion exceeded 8 levels」, with the state correctly on the enemy).
     */
    @Test
    public void myNegativeEffectHasAChanceToCharmTheTarget() {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(23047, LEVEL, false, 1));
        Character ally = CharacterFactory.create(1001, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(wearer, ally)), new ArrayList<>(List.of(enemy)),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                TriggerEvent.DEBUFF_APPLIED, ally, enemy, 0, 0));
        battle.processRequests();
        Assertions.assertFalse(enemy.getBuffManager().hasState("魂迷"),
                "「装备者施加的负面效果」- a TEAMMATE's debuff does not charm");

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                TriggerEvent.DEBUFF_APPLIED, wearer, enemy, 0, 0));
        battle.processRequests();
        Assertions.assertTrue(enemy.getBuffManager().hasState("魂迷"),
                "her own debuff has an 80% base chance, and the generator rolled low");
    }

    /**
     * 「当装备者陷入无法战斗状态时，移除所有【魂迷】」 - the engine's own anchor rule: a buff whose CLOCK is another unit is
     * released when that unit dies ({@code DeathService.releaseBuffsAnchoredToTheDead}), which is why 魂迷 is applied
     * {@code ticks_on: self}. The sweep runs after any cast, so an ally's basic is enough to reach it.
     */
    @Test
    public void whenTheWearerFallsEveryCharmIsReleased() {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(23047, LEVEL, false, 1));
        Character ally = CharacterFactory.create(1001, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(wearer, ally)), new ArrayList<>(List.of(enemy)),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                TriggerEvent.DEBUFF_APPLIED, wearer, enemy, 0, 0));
        battle.processRequests();
        Assertions.assertTrue(enemy.getBuffManager().hasState("魂迷"), "precondition: the target is charmed");

        wearer.takeDamage(wearer.getMaxHp() * 2);
        // Any cast sweeps the dead; the ally's basic is the lever the public API offers.
        battle.castImmediate(ally.getSkills().get(com.laosun.aluminium.enums.SkillType.COMMON), ally, List.of(enemy));
        battle.processRequests();
        Assertions.assertFalse(enemy.getBuffManager().hasState("魂迷"),
                "「装备者陷入无法战斗状态时，移除所有【魂迷】」");
    }

    /**
     * 「受到我方目标攻击时，使<b>攻击者</b>速度提高 10%，持续 3 回合」 - the attacker, not the wearer.
     */
    @Test
    public void theAttackerGetsTheSpeedWhileTheTargetCarriesTheState() {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(23047, LEVEL, false, 1));
        Character ally = CharacterFactory.create(1001, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(wearer, ally)), new ArrayList<>(List.of(enemy)),
                BattleFixture.seeded());
        battle.startBattle();
        double before = ally.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get();

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                TriggerEvent.TAKING_HIT, ally, enemy, 0, 0));
        battle.processRequests();
        Assertions.assertEquals(before, ally.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get(), 1e-9,
                "no 魂迷 on the target, no speed");

        enemy.getBuffManager().addBuff(new com.laosun.aluminium.buff.StateBuff("魂迷", 3));
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                TriggerEvent.TAKING_HIT, ally, enemy, 0, 0));
        battle.processRequests();
        Assertions.assertEquals(before * 1.10, ally.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get(),
                1e-6, "「使攻击者速度提高 10%」");
    }

    @Test
    public void theZoneFollowsTheNumberOfNegativeEffectsTheWearerApplied() {
        Run bare = new Run();
        double none = bare.dotSettled();

        Run one = new Run();
        one.applyMyDebuffs(1);
        Assertions.assertEquals(none * 1.05, one.dotSettled(), 1e-6, "one of my debuffs is one 5% layer");

        Run three = new Run();
        three.applyMyDebuffs(3);
        Assertions.assertEquals(none * 1.15, three.dotSettled(), 1e-6, "three of mine is three layers");

        Run eight = new Run();
        eight.applyMyDebuffs(8);
        Assertions.assertEquals(none * 1.30, eight.dotSettled(), 1e-6, "「最多 6 层」 - eight still pays six");
    }
}
