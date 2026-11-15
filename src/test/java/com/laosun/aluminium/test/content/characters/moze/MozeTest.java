package com.laosun.aluminium.test.content.characters.moze;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerInterpreter;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1223 貊泽 (Moze), the whole kit in one class: the 【充能】 charge counter and the 【猎物】 prey mark it lives on, the Prey
 * marker and the additional damage it draws, the Ultimate and the talent's follow-up (including the random-enemy
 * fallback), the two follow-up traces, and the Eidolons that touch each of them.
 *
 * <p>This file merges the former {@code MozeTest}, {@code MozeChargeTest}, {@code MozeFallbackTest},
 * {@code MozeFollowUpTest} and {@code MozeClauseCoverageTest}: every case, every assertion and every helper moved
 * unchanged, and each former class note now sits on the section it explains.
 *
 * <p><b>【充能】</b> 「…并获得 9 点充能」 / 「我方目标攻击【猎物】后…并消耗 1 点充能。每消耗 3 点充能…发动 1 次追加攻击…当充能为 0 时，解除目标【猎物】状态」.
 * The file once said 「充能没有上限 ⇒ 不能声明为资源 ⇒ 与他相关的每一句都无从表达」. That conclusion was wrong:
 * {@code ResourceSpec.max} is an {@code Integer}, and this library already spells "no ceiling" as
 * {@code 2147483647} (1408's 【毁伤】 does exactly that). The whole mechanic is expressible, and this judge is what makes
 * the claim checkable: nine charges, one per hit, a follow-up on every third, and the mark leaving at zero.
 *
 * <p><b>Prey.</b> The additional damage needs the Prey mark, and its 30% is compared with a hand-built 60% reference in
 * the same pipeline; the Ultimate fires the talent's follow-up at the talent's own 160%.
 *
 * <p><b>Ultimate.</b> 「并对该目标发动天赋的追加攻击。**若本次追加攻击施放前目标被消灭则对敌方随机单体发动追加攻击**」.
 * Two enemies, and the preferred one is dead first: with a single enemy {@code target_else_random_enemy} and the plain
 * {@code target} resolve to the same unit, so the fixture must show the fallback.
 *
 * <p><b>Traces.</b> "[猎物]受到的追加攻击伤害提高 25%" (trace 1223103) and
 * "施放天赋的追加攻击后，恢复 1 个战技点，该效果在 1 回合后可再次触发" (trace 1223101).
 *
 * <p><b>Eidolons.</b> The Eidolon 2 sentence says 「<b>我方全体目标</b>对成为【猎物】的敌方目标造成伤害时」, so the
 * critical-damage bonus belongs to whoever <b>attacks</b>, not to 貊泽; the rule therefore targets {@code actor}, not
 * {@code self} — the trap that makes a cross-unit sentence look like a self-buff.
 */
public class MozeTest {
    private static final int MOZE = 1223;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final String CHARGE = "char:1223/charge";
    private static final String PREY = "char:1223/prey";

    // ==================================================================
    // Charge and the prey mark: nine charges, one per hit, a follow-up on every third
    // ==================================================================

    /**
     * The Skill grants nine, and its OWN attack already spends one: 「我方目标攻击【猎物】后…并消耗 1 点充能」 fires on the same
     * cast (the mark is up by the time the hit lands), so the counter starts at 8 with 1 already consumed.
     */
    @Test
    public void theSkillGrantsNineChargesAndEveryHitSpendsOne() {
        ChargeFixture f = new ChargeFixture();
        Assertions.assertEquals(8, f.charge(),
                "「并获得 9 点充能」 minus the one the Skill's own hit spends");
        f.allyHits();
        Assertions.assertEquals(7, f.charge(), "「我方目标攻击【char:1223/prey】后…并消耗 1 点充能」");
        f.allyHits();
        Assertions.assertEquals(6, f.charge(), "one per hit, not one per turn");
    }

    /** 「每消耗 3 点充能…发动 1 次追加攻击」 - the third, sixth and ninth spends carry an extra 160% instance. */
    @Test
    public void everyThirdChargeLaunchesTheFollowUp() {
        ChargeFixture f = new ChargeFixture();
        List<Double> hits = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            hits.add(f.allyHits());
        }
        System.out.println("[1223] per hit: " + hits + " charge=" + f.charge());
        double plain = hits.get(0);
        for (int i : new int[]{0, 2, 3, 5, 6}) {
            Assertions.assertEquals(plain, hits.get(i), plain * 1e-9,
                    "hit " + (i + 1) + " is a plain one (the follow-up rides every THIRD spend, and the Skill's own hit was the first)");
        }
        for (int i : new int[]{1, 4}) {
            Assertions.assertTrue(hits.get(i) > plain * 1.5,
                    "hit " + (i + 1) + " also carries the 160% follow-up (" + hits.get(i) + " vs " + plain + ")");
        }
    }

    /** 「当充能为 0 时，解除目标【猎物】状态」 - the ninth spend empties the counter and the mark goes with it. */
    @Test
    public void theMarkLeavesWhenTheChargeReachesZero() {
        ChargeFixture f = new ChargeFixture();
        for (int i = 0; i < 7; i++) {
            f.allyHits();
        }
        Assertions.assertEquals(1, f.charge(), "precondition: one charge left");
        Assertions.assertTrue(f.enemy.getBuffManager().hasState(PREY), "and the mark is still there");

        f.allyHits();
        Assertions.assertEquals(0, f.charge(), "the eighth hit spends the last charge");
        Assertions.assertFalse(f.enemy.getBuffManager().hasState(PREY),
                "「当充能为 0 时，解除目标【char:1223/prey】状态」");
    }

    /**
     * 「…并**重置计入发动追加攻击所需的充能点数**」 - the 3-point tally restarts for the NEXT prey.
     *
     * <p>If the reset did nothing, the counter would still hold its old layers and the very first hit on the new prey
     * would already launch the follow-up; this case pins the third-hit timing after a reset.
     */
    @Test
    public void theFollowUpCounterRestartsForTheNextPrey() {
        ChargeFixture f = new ChargeFixture();
        for (int i = 0; i < 8; i++) {
            f.allyHits();
        }
        Assertions.assertEquals(0, f.charge(), "precondition: the first prey was emptied");

        f.remarkThePrey();
        double plain = f.allyHits();
        double second = f.allyHits();
        double third = f.allyHits();
        System.out.println("[1223] after reset: first=" + plain + " second=" + second + " third=" + third);
        // The re-cast is itself the new prey FIRST spend (the Skill own hit), so the follow-up is due on the 2nd ally
        // hit: 1 + the cast's = 3. Measured 213 / 1349 / 213 - the tally really did restart.
        Assertions.assertTrue(second > plain * 1.5, "the follow-up lands on the 2nd ally hit (" + second + ")");
        Assertions.assertEquals(plain, third, 1e-6, "and the 3rd is plain again");
    }

    /**
     * 「场上存在【猎物】时，貊泽会进入【离场】状态」 - untargetable and off the action order - and he comes back when the
     * prey leaves.
     */
    @Test
    public void departsWhileThePreyLivesAndReturnsWhenItLeaves() {
        ChargeFixture f = new ChargeFixture();
        Assertions.assertTrue(f.moze.isUntargetable(), "「无法成为技能目标」");
        Assertions.assertFalse(inTheOrder(f.battle, f.moze), "「不会出现在行动序列上」");

        for (int i = 0; i < 8; i++) {
            f.allyHits();
        }
        Assertions.assertFalse(f.moze.isUntargetable(), "the prey is gone, so he is back on the field");
        Assertions.assertTrue(inTheOrder(f.battle, f.moze), "and back in the action order");
    }

    // ==================================================================

    /** Whether the unit currently owns a signal in the action order. */
    private static boolean inTheOrder(Battle battle, Character unit) {
        for (com.laosun.aluminium.Signal signal : battle.queue().snapshot()) {
            if (signal.getCanHit() == unit) {
                return true;
            }
        }
        return false;
    }

    private static final class ChargeFixture {
        private final Character moze;
        private final Character ally;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private ChargeFixture() {
            moze = CharacterFactory.create(MOZE, LEVEL);
            ally = CharacterFactory.create(ALLY, LEVEL);
            enemy.setMaxStance(100_000);
            enemy.setStance(100_000);
            battle = new Battle(new ArrayList<>(List.of(moze, ally)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
            battle.processRequests();
            battle.castImmediate(moze.getSkills().get(SkillType.SKILL), moze, List.of(enemy));
            battle.processRequests();
            Assertions.assertTrue(enemy.getBuffManager().hasState(PREY), "precondition: the Skill marks the prey");
        }

        private int charge() {
            // 【充能】 is Moze's OWN counter (his unit store), not a party pool - the declaration sits on the unit, so reading
            // the party pool answers 0 and would have made this test look like "the gain did nothing".
            return moze.getResources().has(CHARGE) ? moze.getResources().value(CHARGE) : 0;
        }

        /** Casts the Skill again: a fresh prey and nine charges (the 「重置」 case). */
        private void remarkThePrey() {
            battle.castImmediate(moze.getSkills().get(SkillType.SKILL), moze, List.of(enemy));
            battle.processRequests();
        }

        /** The ALLY hits the prey; returns the HP it took. */
        private double allyHits() {
            double before = enemy.getCurrentHp();
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, enemy, 1, 0));
            battle.processRequests();
            return before - enemy.getCurrentHp();
        }
    }

    // ==================================================================
    // The Prey mark and the additional damage it draws
    // ==================================================================

    /**
     * Note: The additional damage needs the Prey mark, and its 30% is compared with a hand-built 60% reference in the same pipeline.
     */
    @Test
    public void theAdditionalDamageNeedsPrey() {
        double unmarked = additionalLoss(true, false);
        double marked = additionalLoss(true, true);
        double reference = additionalLoss(false, true);

        Assertions.assertEquals(0.0, unmarked, 1e-9,
                "「我方目标攻击【char:1223/prey】后」 (after one of our targets attacks [char:1223/prey]) -- without the mark, nothing");
        Assertions.assertTrue(marked > 0, "with the mark, the additional damage lands");
        Assertions.assertEquals(0.5, marked / reference, 0.05,
                "30% against a hand-built 60% reference: " + marked + " vs " + reference);
    }

    /**
     * Note: The Ultimate fires the talent's follow-up, and the number is the talent's own 160%.
     */
    @Test
    public void theUltimateFiresTheTalentsFollowUp() {
        double fired = ultFollowUp();
        Character moze = CharacterFactory.create(MOZE, LEVEL);
        // ⚠ Times the toughness damage reduction: the target is unbroken, so 10% of the instance is removed on the way in.
        double expected = moze.getAttribute(AttributeType.ATTACK).get() * 1.6
                * (1 - com.laosun.aluminium.Constant.TOUGHNESS_DAMAGE_REDUCTION);

        Assertions.assertTrue(fired > 0, "the follow-up must land when the event is fired");
        Assertions.assertEquals(expected, fired, expected * 0.15,
                "the talent's own 160% of his ATTACK: expected about " + expected + ", measured " + fired);
    }

    // ==================================================================

    /**
     * Fires a teammate's attack at the enemy; optionally marks it first, optionally uses a hand-built 60% reference rule.
     */
    private static double additionalLoss(boolean shipped, boolean mark) {
        Character moze = CharacterFactory.create(MOZE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        if (!shipped) {
            EffectSpec effect = new EffectSpec();
            TriggerSpecs.set(effect, "op", "DAMAGE");
            TriggerSpecs.set(effect, "scale", "self_attr:ATTACK");
            TriggerSpecs.set(effect, "percent", 0.6);
            TriggerSpecs.set(effect, "element", "Thunder");
            TriggerSpecs.set(effect, "target", "target");
            moze.setTriggerTable(new TriggerTable(MOZE, List.of(TriggerSpecs.rule(
                    TriggerEvent.ALLY_ATTACK.name(), List.of("target has_state char:1223/prey"), effect))));
        }
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(moze, ally), List.of(enemy), fixed());
        battle.startBattle();
        if (mark) {
            // Applied directly in BOTH paths, so the only difference between them is the percentage (round 20's lesson).
            enemy.getBuffManager().addBuff(new com.laosun.aluminium.buff.StateBuff("char:1223/prey", 1, true));
        }
        double before = enemy.getCurrentHp();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0));
        return before - enemy.getCurrentHp();
    }

    /**
     * Fires the Ultimate event WITHOUT casting the skill, so only the rule's follow-up damage is measured.
     */
    private static double ultFollowUp() {
        Character moze = CharacterFactory.create(MOZE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(moze, ally), List.of(enemy), fixed());
        battle.startBattle();
        double before = enemy.getCurrentHp();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, moze, enemy, 0, 0));
        return before - enemy.getCurrentHp();
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }

    // ==================================================================
    // The Ultimate's random-enemy fallback
    // ==================================================================

    @Test
    public void aDeadTargetFallsBackToAnotherEnemy() {
        Character moze = CharacterFactory.create(MOZE, LEVEL);
        Enemy a = EnemyFactory.create(1002011, 90, 1);
        Enemy b = EnemyFactory.create(1002011, 90, 2);
        Battle battle = new Battle(List.of(moze), List.of(a, b), BattleFixture.seeded());
        battle.startBattle();
        a.perish();
        Assertions.assertTrue(a.isDeath(), "the fixture must leave A defeated");
        Assertions.assertTrue(battle.enemies().contains(a), "the defeated unit stays on the roster");

        var rules = TriggerTables.of(MOZE).rulesFor(TriggerEvent.ULT_CAST).stream()
                .filter(r -> "ult_triggers_talent_followup".equals(r.id())).toList();
        Assertions.assertEquals(1, rules.size(), "the ultimate follow-up rule must exist");

        TriggerTable.TriggerContext ctx = new TriggerTable.TriggerContext(moze, moze, a, 1, 0, null, battle,
                SkillCategory.ULTRA);
        double aBefore = a.getCurrentHp();
        double bBefore = b.getCurrentHp();
        TriggerInterpreter.apply(battle, rules.getFirst(), ctx);

        Assertions.assertEquals(0.0, aBefore - a.getCurrentHp(), 1e-9,
                "the defeated preferred target is not hit");
        Assertions.assertTrue(bBefore - b.getCurrentHp() > 0,
                "the fallback reaches another enemy, got " + (bBefore - b.getCurrentHp()));
    }

    // ==================================================================
    // The two follow-up traces
    // ==================================================================

    @Test
    public void theZoneIsScopedToAdditionalDamage() {
        Character unit = CharacterFactory.create(MOZE, LEVEL);
        Enemy enemy = BattleFixture.monster();
        String scope = null;
        double share = -1;
        for (var rule : unit.getTriggerTable().matching(TriggerEvent.SKILL_CAST,
                new TriggerTable.TriggerContext(unit, unit, enemy, 0, 0))) {
            for (var effect : rule.effects()) {
                if ("MODIFY_DAMAGE_TAKEN".equals(effect.getOp())) {
                    scope = effect.getDamageType();
                    share = effect.getPercent();
                    System.out.println("[1223] zone id=" + rule.id() + " damageType=" + scope + " percent=" + share);
                }
            }
        }
        Assertions.assertEquals("ADDITIONAL", scope, "the engine spells a follow-up attack ADDITIONAL");
        Assertions.assertEquals(0.25, share, 1e-9, "the data row states 0.25");
    }

    @Test
    public void followUpDamageRisesByAQuarter() {
        Character unit = CharacterFactory.create(MOZE, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(unit), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, unit, enemy, 0, 0));
        double plain = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.THUNDER, DamageType.NORMAL, 1000));
        double followUp = battle.applyAdditionalDamage(unit, enemy, DamageElement.THUNDER, 1000, null, null);
        System.out.println("[1223] plain=" + plain + " followUp=" + followUp + " ratio=" + (followUp / plain));
        Assertions.assertEquals(1.25, followUp / plain, 1e-6, "a follow-up instance rises by the authored 25%");
    }

    @Test
    public void aFollowUpRestoresOneSkillPointOncePerTurn() {
        Character unit = CharacterFactory.create(MOZE, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(unit), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        int before = battle.getSkillPoints();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.FOLLOW_UP, unit, enemy, 500, 0));
        int after = battle.getSkillPoints();
        System.out.println("[1223] skillPoints " + before + " -> " + after);
        Assertions.assertEquals(1, after - before, "the trace grants exactly one skill point");
    }

    // ==================================================================
    // Eidolons 2 and 4
    // ==================================================================

    /**
     * 星魂 2: a teammate attacking the marked prey gets +40% crit damage for that instance.
     */
    @Test
    public void eidolon2GivesTheAttackerCritDamageAgainstThePrey() {
        EidolonFixture e2 = new EidolonFixture(2);
        e2.markPrey();
        Assertions.assertEquals(0.4, e2.extraCritDamageOnTheAllysHit(), 1e-9,
                "「我方全体目标对成为【char:1223/prey】的…造成伤害时，暴击伤害提高 40%」 — the bonus rides the ATTACKER's instance");

        EidolonFixture e1 = new EidolonFixture(1);
        e1.markPrey();
        Assertions.assertEquals(0.0, e1.extraCritDamageOnTheAllysHit(), 1e-9, "control: it is Eidolon 2");
    }

    /**
     * 星魂 4: 「施放终结技时，貊泽造成的伤害提高 30%，持续 2 回合」.
     */
    @Test
    public void eidolon4BoostsHerDamageAfterTheUltimate() {
        EidolonFixture e4 = new EidolonFixture(4);
        double before = e4.moze.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        e4.castUltimate();
        Assertions.assertEquals(before + 0.3, e4.moze.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), 1e-9,
                "「貊泽造成的伤害提高 30%」");
    }

    // ==================================================================

    private static final class EidolonFixture {
        private final Character moze;
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        private final Battle battle;

        private EidolonFixture(int eidolon) {
            moze = CharacterFactory.create(MOZE, LEVEL, false, null, null, eidolon);
            battle = new Battle(List.of(moze, ally), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        /** The Skill puts 【猎物】 on its target; casting it is how the state gets there. */
        private void markPrey() {
            battle.castImmediate(moze.getSkills().get(SkillType.SKILL), moze, List.of(enemy));
            battle.processRequests();
        }

        private void allyAttacks() {
            battle.castImmediate(ally.getSkills().get(SkillType.COMMON), ally, List.of(enemy));
            battle.processRequests();
        }

        /**
         * The ally attacks the marked prey, and this reads the instance's own crit-damage bonus.
         *
         * <p>It has to be read off {@code Damage.getExtraCritDamage()}: an {@code instance: true} modifier never lands on the sheet,
         * so a panel reading would show 0.5 before and after and "prove" nothing.
         */
        private double extraCritDamageOnTheAllysHit() {
            double[] seen = {-1};
            battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> {
                if (seen[0] < 0) {
                    seen[0] = damage.getExtraCritDamage();
                }
            });
            battle.castImmediate(ally.getSkills().get(SkillType.COMMON), ally, List.of(enemy));
            battle.processRequests();
            return seen[0];
        }

        private void castUltimate() {
            battle.castImmediate(moze.getSkills().get(SkillType.ULTRA), moze, List.of(enemy));
            battle.processRequests();
        }
    }
}
