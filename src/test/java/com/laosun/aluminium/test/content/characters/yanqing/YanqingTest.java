package com.laosun.aluminium.test.content.characters.yanqing;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Signal;
import com.laosun.aluminium.buff.AbstractBuff;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1209 彦卿 (Yanqing) - the whole character in one place.
 *
 * <p>Consolidated from six per-concern test classes; every test and every assertion of those files is kept. In order, the
 * concerns are:
 *
 * <ul>
 *   <li>the state that rides on himself and the two fixtures that pin a 60% chance: the Skill's 【智剑连心】 with +20%
 *       CRIT Rate and +30% CRIT DMG, the Ultimate's extra +50% CRIT DMG only while synced, and the follow-up's fixed 60%
 *       chance;</li>
 *   <li>his skill's +20% and his ult's +60% CRIT CHANCE must BOTH count. FILE-DRIVEN, and the numbers are the claim: 0.2
 *       after the skill, 0.8 after the ult as well - a replaced modifier gives 0.6 at the second reading, which is what the
 *       mutation has to produce;</li>
 *   <li>Eidolon 6, second branch: 「消灭敌方目标时…终结技的增益效果…持续时间全部延长 1 回合」. It only works because a
 *       modifier can now be found BY NAME - before, the rule fired and lengthened nothing;</li>
 *   <li>the technique: 「下一次战斗开始时…对当前生命值百分比 ≥50% 的敌方目标造成的伤害提高 30%，持续 2 回合」. The clause
 *       had been registered as unwritable because 「条件与修饰单位不同」 - the condition is the TARGET's health while the
 *       bonus lands on HIS damage. That is what {@code target_hp_percent} is for, and the two cases below are the contrast
 *       that proves it is the target's health, not his;</li>
 *   <li>trace Frost Favors the Brave (凌霜): "while in the [智剑连心] effect, effect resistance is increased by 20%";</li>
 *   <li>the talent's 「当彦卿受到伤害后，【智剑连心】将会消失」 and its lowered aggro, 星魂 1's 60%-ATK ice rider on a
 *       frozen target, 星魂 2's 10% energy regeneration while synced, 星魂 4's live HP gate, and the freeze the follow-up
 *       can land.</li>
 * </ul>
 */
public class YanqingTest {
    private static final int YANQING = 1209;
    private static final int LEVEL = 80;
    private static final double EPS = 1e-9;

    /**
     * Note: The Skill applies the state AND its two modifiers, all on himself, all for the document's one turn.
     */
    @Test
    public void theSkillSyncsAndRaisesBothCritStats() {
        Character yanqing = CharacterFactory.create(YANQING, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(yanqing), List.of(enemy), fixed(0.0));
        battle.startBattle();
        double critBefore = yanqing.getAttribute(AttributeType.CRIT_CHANCE).get();
        double dmgBefore = yanqing.getAttribute(AttributeType.CRIT_ATTACK).get();

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, yanqing, enemy, 0, 0));

        Assertions.assertTrue(yanqing.getBuffManager().hasState("char:1209/blade_and_mind"),
                "「并为彦卿附加【char:1209/blade_and_mind】」 (and applies Soulsteel Sync (【char:1209/blade_and_mind】) to Yanqing)");
        Assertions.assertEquals(0.2, yanqing.getAttribute(AttributeType.CRIT_CHANCE).get() - critBefore, 1e-9,
                "「为自身提高20.00%暴击率」 (raises his own CRIT Rate by 20.00%)");
        Assertions.assertEquals(0.3, yanqing.getAttribute(AttributeType.CRIT_ATTACK).get() - dmgBefore, 1e-9,
                "「和30%暴击伤害」 (and 30% CRIT DMG)");
    }

    /**
     * Note: The Ultimate's conditional half: +50% CRIT DMG ONLY while the state is up.
     */
    @Test
    public void theUltimateAddsCritDamageOnlyWhileSynced() {
        double synced = ultCritDamageGain(true);
        double unsynced = ultCritDamageGain(false);

        Assertions.assertEquals(0.5, synced, 1e-9,
                "「若彦卿处于【char:1209/blade_and_mind】效果，则使其暴击伤害额外提高50%」 (if Yanqing has the Soulsteel Sync (【char:1209/blade_and_mind】) effect, his CRIT DMG is raised by an extra 50%)");
        Assertions.assertEquals(0.0, unsynced, 1e-9,
                "without the state the extra 50% must not be granted");
    }

    /**
     * Note: A 60% chance, pinned from both sides by the fixture: 0.0 always fires, 1.0 never does.
     */
    @Test
    public void theFollowUpChanceIsPinnedByTwoFixtures() {
        double always = followUpLoss(0.0);
        double never = followUpLoss(1.0);

        Assertions.assertTrue(always > 0,
                "「有60%的固定概率发动追加攻击」 (a 60% fixed chance to launch a follow-up attack): a 0.0 roll is below 0.6, so it MUST fire");
        Assertions.assertEquals(0.0, never, 1e-9,
                "a 1.0 roll is not below 0.6, so it must not fire -- which is what makes this a test of the CHANCE and not of the damage");
    }

    /**
     * The Ultimate's CRIT DMG gain, with the state applied first when asked.
     */
    private static double ultCritDamageGain(boolean synced) {
        Character yanqing = CharacterFactory.create(YANQING, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(yanqing), List.of(enemy), fixed(0.0));
        battle.startBattle();
        if (synced) {
            // The state is applied DIRECTLY, so the Skill's own CRIT modifiers cannot interfere: this clause is about the Ultimate alone.
            yanqing.getBuffManager().addBuff(new com.laosun.aluminium.buff.StateBuff("char:1209/blade_and_mind", 1, false));
        }
        double before = yanqing.getAttribute(AttributeType.CRIT_ATTACK).get();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, yanqing, enemy, 0, 0));
        return yanqing.getAttribute(AttributeType.CRIT_ATTACK).get() - before;
    }

    /**
     * The damage the follow-up deals on one attack, under a chosen roll.
     */
    private static double followUpLoss(double roll) {
        Character yanqing = CharacterFactory.create(YANQING, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(yanqing), List.of(enemy), fixed(roll));
        battle.startBattle();
        double before = enemy.getCurrentHp();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, yanqing, enemy, 0, 0));
        return before - enemy.getCurrentHp();
    }

    private static Random fixed(double value) {
        return BattleFixture.alwaysRoll(value);
    }

    /**
     * Two sources, two shares, and the total.
     */
    @Test
    public void bothCritChanceSourcesAreCounted() {
        Character owner = CharacterFactory.create(YANQING, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        double base = owner.getAttribute(AttributeType.CRIT_CHANCE).get();

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, owner, owner, 0, 0));
        battle.processRequests();
        double afterSkill = owner.getAttribute(AttributeType.CRIT_CHANCE).get();

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, owner, owner, 0, 0));
        battle.processRequests();
        double afterUlt = owner.getAttribute(AttributeType.CRIT_CHANCE).get();

        Assertions.assertEquals(base + 0.2, afterSkill, 1e-6, "the skill's 0.2 on top of her own value");
        Assertions.assertEquals(base + 0.8, afterUlt, 1e-6, "and the ult's own 0.6 must be counted, not replace it");
    }

    @Test
    public void aKillExtendsTheUltimatesBuffs() {
        Character yanqing = CharacterFactory.create(YANQING, LEVEL, true, null, null, 6);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(yanqing, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.castImmediate(yanqing.getSkills().get(SkillType.ULTRA), yanqing, List.of(enemy));

        int before = namedTurns(yanqing);
        Assertions.assertTrue(before > 0, "precondition: the ultimate's named buff is on him");
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.KILL, yanqing, enemy, 0, 0));
        int after = namedTurns(yanqing);
        System.out.println("[1209] char:1209/ult_buff turns " + before + " -> " + after);
        Assertions.assertEquals(before + 1, after, "「持续时间全部延长 1 回合」");
    }

    private static int namedTurns(Character unit) {
        int total = 0;
        for (AbstractBuff buff : unit.getBuffManager().activeBuffs()) {
            if ("char:1209/ult_buff".equals(buff.getBuffName())) {
                total += buff.remainingTurns();
            }
        }
        return total;
    }

    @Test
    public void theTechniqueBonusOnlyLandsOnHealthyTargets() {
        double onHealthy = damageWithTechnique(1.0);
        double onWounded = damageWithTechnique(0.3);
        Assertions.assertTrue(onHealthy > onWounded,
                "「对当前生命值百分比 ≥50% 的敌方目标」 — a wounded target does not get it ("
                        + onWounded + " -> " + onHealthy + ")");
    }

    private static double shareUsed(double hpShare) {
        Character yanqing = CharacterFactory.create(YANQING, 80);
        Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        Battle battle = new Battle(List.of(yanqing), List.of(enemy), BattleFixture.alwaysRoll(0.9999));
        battle.startBattle();
        battle.processRequests();
        battle.applyDamage(enemy, new com.laosun.aluminium.damage.Damage(yanqing, enemy,
                com.laosun.aluminium.enums.DamageElement.QUANTUM, com.laosun.aluminium.enums.DamageType.TRUE,
                enemy.getMaxHp() * (1 - hpShare)));
        battle.processRequests();
        return enemy.getCurrentHp() / enemy.getMaxHp();
    }

    /** One Basic ATK against a dummy whose current HP is {@code hpShare} of its maximum. */
    private static double damageWithTechnique(double hpShare) {
        Character yanqing = CharacterFactory.create(YANQING, 80);
        Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        Battle battle = new Battle(List.of(yanqing), List.of(enemy), BattleFixture.alwaysRoll(0.9999));
        battle.markTechniqueUsed(yanqing);
        battle.startBattle();
        battle.processRequests();
        // Wound the dummy in small steps, as the handover says: a single large applyDamage instance is scaled by the damage zones
        // and removes a different share than the number asked for (measured: 70% of Max HP became 24%).
        while (enemy.getCurrentHp() / enemy.getMaxHp() > hpShare && enemy.getCurrentHp() > 1) {
            battle.applyDamage(enemy, new com.laosun.aluminium.damage.Damage(yanqing, enemy,
                    com.laosun.aluminium.enums.DamageElement.QUANTUM, com.laosun.aluminium.enums.DamageType.TRUE,
                    enemy.getMaxHp() * 0.01));
            battle.processRequests();
        }
        double[] settled = {0};
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled[0] += applied);
        battle.castImmediate(yanqing.getSkills().get(SkillType.COMMON), yanqing, List.of(enemy));
        battle.processRequests();
        return settled[0];
    }

    @Test
    public void theTraceRaisesEffectResistanceForTheTurn() {
        Character unit = CharacterFactory.create(YANQING, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(unit), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        battle.setCurrentMove(new Signal(unit));
        battle.beforeMove();
        double before = unit.getAttribute(AttributeType.EFFECT_RESISTANCE).get();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, unit, enemy, 0, 0));
        double gain = unit.getAttribute(AttributeType.EFFECT_RESISTANCE).get() - before;
        System.out.println("[1209] effectResGain=" + gain + " hasState="
                + unit.getBuffManager().hasState("char:1209/blade_and_mind"));
        Assertions.assertEquals(0.2, gain, 1e-6, "the trace row's own param");
        Assertions.assertTrue(unit.getBuffManager().hasState("char:1209/blade_and_mind"),
                "the skill's own rule put the state on her");
    }

    @Test
    public void hisSyncEndsWhenHeIsHit() {
        Battle battle = scene(0);
        Character yanqing = battle.characters().getFirst();
        battle.castImmediate(yanqing.getSkills().get(SkillType.SKILL), yanqing, List.of(battle.enemies().getFirst()));
        battle.processRequests();
        Assertions.assertTrue(yanqing.getBuffManager().hasState("char:1209/blade_and_mind"), "precondition: the Skill applies it");
        double critWith = yanqing.getAttribute(AttributeType.CRIT_CHANCE).get();

        battle.applyDamage(yanqing, new Damage(battle.enemies().getFirst(), yanqing, DamageElement.ICE,
                DamageType.NORMAL, 1));
        battle.processRequests();

        Assertions.assertFalse(yanqing.getBuffManager().hasState("char:1209/blade_and_mind"),
                "「当彦卿受到伤害后，【char:1209/blade_and_mind】将会消失」");
        // The CRIT modifiers are gated on the state (`while_state`), so they leave at the next gate evaluation - the point of
        // this assertion is only that the state itself is gone; the numbers are pinned by the Skill test above.
        Assertions.assertTrue(critWith > 0, "precondition: the sync raises his CRIT Rate while it lasts");
    }

    @Test
    public void theSecondEidolonRaisesEnergyRecoveryWhileSynced() {
        Assertions.assertEquals(0.1, energyRegen(2), EPS, "「处于【char:1209/blade_and_mind】时，额外提高 10% 的能量恢复效率」");
        Assertions.assertEquals(0.0, energyRegen(1), EPS, "before 星魂 2 there is none");
    }

    @Test
    public void theFirstEidolonHitsAFrozenTargetAgain() {
        Battle battle = scene(1);
        Character yanqing = battle.characters().getFirst();
        Enemy enemy = (Enemy) battle.enemies().getFirst();
        enemy.getBuffManager().addBuff(new StateBuff("ctrl:frozen", 2));

        double before = enemy.getCurrentHp();
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, yanqing, enemy, 1, 0));
        battle.processRequests();
        double withFreeze = before - enemy.getCurrentHp();

        Battle plain = scene(1);
        Enemy target = (Enemy) plain.enemies().getFirst();
        double hp = target.getCurrentHp();
        plain.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, plain.characters().getFirst(), target, 1, 0));
        plain.processRequests();
        double withoutFreeze = hp - target.getCurrentHp();
        // Same seed in both runs, so the talent's 60%-chance follow-up fires in both: the DIFFERENCE is the Eidolon's rider,
        // and 60% / 50% of the same base means the rider is 1.2x the follow-up it rides on.
        Assertions.assertTrue(withFreeze > withoutFreeze,
                "「…造成等同于彦卿 60% 攻击力的冰属性附加伤害」 — the frozen target takes an extra hit");
        Assertions.assertEquals(1.2, (withFreeze - withoutFreeze) / withoutFreeze, 1e-6,
                "the rider is 60% of ATK against the follow-up's 50%, so exactly 1.2x of it");
    }

    private static double assertPositive(double value, String message) {
        Assertions.assertTrue(value > 0, message);
        return value;
    }

    private static double energyRegen(int eidolon) {
        Battle battle = scene(eidolon);
        Character yanqing = battle.characters().getFirst();
        double before = yanqing.getAttribute(AttributeType.ENERGY_REGENERATION_RATE).get();
        battle.castImmediate(yanqing.getSkills().get(SkillType.SKILL), yanqing, List.of(battle.enemies().getFirst()));
        battle.processRequests();
        return yanqing.getAttribute(AttributeType.ENERGY_REGENERATION_RATE).get() - before;
    }

    private static Battle scene(int eidolon) {
        Character yanqing = CharacterFactory.create(YANQING, LEVEL, false, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        enemy.setMaxStance(10_000);
        enemy.setStance(10_000);
        Battle battle = new Battle(List.of(yanqing), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return battle;
    }

    /**
     * 星魂 4:「当前生命值百分比 ≥80% 时，提高自身 12% 的冰属性抗性穿透」 - a LIVE gate on the HP bar.
     *
     * <p>Applied once at battle start (full HP, so it holds), then the owner is wounded below 80% and the gate is asked again
     * at the next turn boundary - the modifier must be gone. That "asked again" is the whole point of `while_hp_percent`: a
     * state or counter gate could not express a bar that moves on every event.
     */
    @Test
    public void theFourthEidolonPiercesOnlyWhileHeIsHealthy() {
        Battle battle = scene(4);
        Character yanqing = battle.characters().getFirst();
        Assertions.assertEquals(0.12, yanqing.getAttribute(AttributeType.DAMAGE_PENETRATION_ICE).get(), EPS,
                "at full HP the gate holds");

        battle.applyDamage(yanqing, new Damage(battle.enemies().getFirst(), yanqing, DamageElement.ICE,
                DamageType.NORMAL, yanqing.getMaxHp() * 0.5));
        battle.processRequests();
        Assertions.assertTrue(yanqing.getCurrentHp() / yanqing.getMaxHp() < 0.8, "precondition: he is wounded");

        // The gate is asked at the turn boundary, so one has to pass.
        yanqing.getBuffManager().beforeMove();
        battle.processRequests();
        Assertions.assertEquals(0.0, yanqing.getAttribute(AttributeType.DAMAGE_PENETRATION_ICE).get(), EPS,
                "below 80% HP the penetration is gone");

        Assertions.assertEquals(0.0, penetrationAt(1), EPS, "before 星魂 4 there is none at all");
    }

    /**
     * 天赋「有 65% 的基础概率使其陷入冻结状态…不能行动…每回合开始时受到等同于彦卿 50% 攻击力的冰属性附加伤害」.
     *
     * <p>⚠ The stock fixture monster is IMMUNE to freeze (its debuff resistance for that key is 1.0, so `Battle.hitChance`
     * clamps to 0) - the content is right and the enemy refuses. A bare target with no resistances is the only way to judge
     * the clause, exactly as `GepardFreezeTest` documents for the same control.
     */
    @Test
    public void theFollowUpCanFreeze() {
        Character yanqing = CharacterFactory.create(YANQING, LEVEL);
        Enemy enemy = Enemy.fromAttributes("Test Dummy", 20000, 100, 100, 90);
        Battle battle = new Battle(List.of(yanqing), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();

        battle.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, yanqing, enemy, 1, 0));
        battle.processRequests();

        Assertions.assertTrue(enemy.getBuffManager().hasState("ctrl:frozen"),
                "「有 65% 的基础概率使其陷入冻结状态」 — a zero roll on an unresisting target lands it");
        Assertions.assertFalse(enemy.getBuffManager().canAct(), "「冻结状态下，敌方目标不能行动」");

        double before = enemy.getCurrentHp();
        battle.tickDots(enemy);
        Assertions.assertTrue(enemy.getCurrentHp() < before,
                "「每回合开始时受到等同于彦卿 50% 攻击力的冰属性附加伤害」");
    }

    /** 天赋「处于【智剑连心】时，受到攻击的概率降低」: the DIRECTION only - the document states no number. */
    @Test
    public void theSyncLowersHisAggro() {
        Battle battle = scene(0);
        Character yanqing = battle.characters().getFirst();
        double before = battle.aggroOf(yanqing);
        battle.castImmediate(yanqing.getSkills().get(SkillType.SKILL), yanqing, List.of(battle.enemies().getFirst()));
        battle.processRequests();
        Assertions.assertTrue(battle.aggroOf(yanqing) < before,
                "「受到攻击的概率降低」 — the weight drops (the amount is a placeholder: the sentence states none)");
    }

    private static double penetrationAt(int eidolon) {
        return scene(eidolon).characters().getFirst().getAttribute(AttributeType.DAMAGE_PENETRATION_ICE).get();
    }
}
