package com.laosun.aluminium.test.content.characters.feixiao;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
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
import com.laosun.aluminium.DoubleValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1220 Feixiao (飞霄), consolidated: five earlier per-area classes (follow-up, fallback, Ultimate branches, 行迹 解形, 【飞黄】)
 * now live here, each with the helpers its own file carried.
 *
 * <p><b>Follow-up.</b> 1220 Feixiao, from her own file: the teammate-triggered follow-up with its per-turn limit, and the boost
 * it carries. ⚠ 「当飞霄的队友对敌方目标施放攻击后」 needs a TEAMMATE, and 「该效果每回合最多触发1次」 caps it, so two firings in
 * one turn must deal ONE instance -- and her OWN attack must draw nothing.
 *
 * <p><b>Fallback.</b> 1220 Feixiao: "若不存在可攻击的主目标，则攻击敌方随机单体" -- her follow-up fallback. Two enemies, because
 * with one the fallback and the preference pick the same unit. A is defeated first with {@code CanHit.perish()}, which fires no
 * events and leaves it in {@code Battle.enemies}.
 *
 * <p><b>Ultimate branches.</b> 1220 Feixiao (飞霄)'s ultimate branches: "if the target is in the weakness-broken state, the
 * damage multiplier is increased by 30%" and "if the target is not in the weakness-broken state, the damage multiplier is
 * increased by 30%". {@code BOOST_DAMAGE} boosts the DAMAGE INSTANCE, so the judgement is a damage number: measured on a 900k
 * victim with a no-crit Random. Three arms -- the character with a synthetic level-only table (D0), the full content with the
 * enemy NOT broken (D1, the negated branch) and with the enemy broken (D2, the mirror branch, broken through the engine with the
 * enemy's own declared weakness).
 *
 * <p>Note: One caveat stated rather than hidden: the control arm replaces her table, so it also drops any other rule of hers that
 * might touch the ultimate's damage. If the ratio comes out above 1.3, that is why -- and the assertion's message carries the
 * measured numbers.
 *
 * <p><b>行迹 解形 (1220102).</b> "the follow-up attack's CRIT DMG is increased by 36%", judged on one wearer with both readings
 * fixed-crit, so the only difference between them is the damage type the rule is scoped to.
 *
 * <p><b>【飞黄】.</b> 1220 飞霄's 【飞黄】: 「我方目标每施放 2 次攻击，飞霄获得 1 点【飞黄】，飞霄的终结技攻击不计入次数。最多累计 12
 * 点」. Why a counter rather than one rule: "Every two attacks" has no arithmetic in the DSL, so it is a counter that resets when
 * it fires (the same shape as 1223's "every three charges"). And the exclusion is <b>hers alone</b>: 「飞霄的终结技攻击不计入」 says
 * HER Ultimate, so this file gates with {@code from_skill_id != 3} rather than {@code from_category ULTRA} - the latter would
 * also drop a TEAMMATE's Ultimate, which the sentence counts.
 *
 * <p><b>L3.</b> The single-clause cases are folded into the scenario tests below, one {@code Assertions.assertAll} per
 * fixture, so a failing clause still names itself: the teammate attack's follow-up with its per-turn cap, her own attack's
 * silence and the 60% the same trigger grants (one feixiao + ally + monster board); the 解形 clause read both ways on one
 * wearer and one monster (the compiled rule's scope / share / instance flag, and the measured 1.24 crit ratio); and 【飞黄】's
 * every-two-attacks counter together with her own Ultimate's exclusion (one {@code Scene}). The fallback and the Ultimate's
 * two weakness-broken branches keep their own tests, because each already is one scenario on one board. Nothing was dropped:
 * every expected value, tolerance, message and precondition assertion is still asserted.
 */
public class FeixiaoTest {
    private static final int FEIXIAO = 1220;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int VICTIM = 1002011;
    private static final String AUREUS = "char:1220/feihuang";

    // ==================================================================
    // 追击: the teammate-triggered follow-up, its per-turn cap and the boost it carries
    // ==================================================================

    /**
     * Note: One board (feixiao, a teammate, the standard monster) read four ways: a teammate's attack draws the follow-up,
     * the second firing in the same turn does nothing, her OWN attack draws nothing, and the attack that draws it also raises
     * her own DMG dealt by 60%.
     */
    @Test
    public void aTeammateAttackDrawsOneFollowUpPerTurnAndBoostsHer() {
        double first = followUpLoss(true, 1);
        double twice = followUpLoss(true, 2);
        double hers = followUpLoss(false, 1);

        Character feixiao = CharacterFactory.create(FEIXIAO, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(feixiao, ally), List.of(enemy), fixed());
        battle.startBattle();
        double before = feixiao.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();

        battle.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0));
        double boost = feixiao.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - before;

        Assertions.assertAll("「当飞霄的队友对敌方目标施放攻击后」: the follow-up, its per-turn cap and the 60% it carries",
                () -> Assertions.assertTrue(first > 0,
                        "a teammate's attack must draw it"),
                () -> Assertions.assertEquals(first, twice, 1e-6,
                        "「该效果每回合最多触发1次」 (this effect triggers at most once per turn) -- two firings in the same turn must deal ONE instance"),
                () -> Assertions.assertEquals(0.0, hers, 1e-9,
                        "「当飞霄的队友对敌方目标施放攻击后」 (after Feixiao's ally casts an attack on an enemy target) -- her OWN attack must not draw it"),
                () -> Assertions.assertEquals(0.6, boost, 1e-9,
                        "「发动此攻击时使自身造成的伤害提高60%」 (this attack raises her own DMG dealt by 60%)"));
    }

    /**
     * Fires `times` teammate attacks and returns the total damage they drew; `byTeammate` false means Feixiao herself attacks.
     */
    private static double followUpLoss(boolean byTeammate, int times) {
        Character feixiao = CharacterFactory.create(FEIXIAO, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(feixiao, ally), List.of(enemy), fixed());
        battle.startBattle();
        double before = enemy.getCurrentHp();
        for (int i = 0; i < times; i++) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, byTeammate ? ally : feixiao, enemy, 0, 0));
        }
        return before - enemy.getCurrentHp();
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }

    // ==================================================================
    // 追击 fallback: no attackable main target
    // ==================================================================

    private static double[] lossesWithDeadTarget() {
        Character feixiao = CharacterFactory.create(FEIXIAO, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy a = EnemyFactory.create(1002011, 90, 1);
        Enemy b = EnemyFactory.create(1002011, 90, 2);
        Battle battle = new Battle(List.of(feixiao, ally), List.of(a, b), BattleFixture.seeded());
        battle.startBattle();
        a.perish();
        Assertions.assertTrue(a.isDeath() && battle.enemies().contains(a),
                "the fixture must leave A defeated but still in the roster");
        TriggerTable.TriggerContext ctx = new TriggerTable.TriggerContext(feixiao, ally, a, 1, 0, null, battle,
                SkillCategory.UNSPECIFIED);
        var rules = TriggerTables.of(FEIXIAO).rulesFor(TriggerEvent.ALLY_ATTACK).stream()
                .filter(r -> "talent_followup_on_teammate_attack".equals(r.id())).toList();
        Assertions.assertEquals(1, rules.size(), "the follow-up rule must exist");
        double aBefore = a.getCurrentHp();
        double bBefore = b.getCurrentHp();
        TriggerInterpreter.apply(battle, rules.getFirst(), ctx);
        return new double[]{aBefore - a.getCurrentHp(), bBefore - b.getCurrentHp()};
    }

    @Test
    public void aDeadMainTargetFallsBackToARandomEnemy() {
        double[] loss = lossesWithDeadTarget();
        Assertions.assertAll("「若不存在可攻击的主目标，则攻击敌方随机单体」: with the main target defeated",
                () -> Assertions.assertEquals(0.0, loss[0], 1e-9,
                        "「若不存在可攻击的主目标」: the defeated main target must not be hit"),
                () -> Assertions.assertTrue(loss[1] > 0,
                        "「则攻击敌方随机单体」: the fallback must reach another enemy, got " + loss[1]));
        System.out.println("[1220] fallback ok: deadTargetLoss=" + loss[0] + " otherLoss=" + loss[1]);
    }

    // ==================================================================
    // Ultimate: the two weakness-broken branches
    // ==================================================================

    @Test
    public void bothBranchesRaiseTheDamageAndNeitherAppliesTwice() {
        double control = damage(false, false);
        double unbroken = damage(true, false);
        double broken = damage(true, true);
        // ⚠ The two runs differ in one more way than the branch: the broken target has toughness 0, so it does NOT take
        // the 10% toughness damage reduction and the unbroken one does. Normalizing by it is what leaves "the same +30%".
        double normalized = unbroken / (1 - com.laosun.aluminium.Constant.TOUGHNESS_DAMAGE_REDUCTION);

        Assertions.assertAll("「伤害倍率提高30%」: both weakness-broken branches against the level-only control",
                () -> Assertions.assertTrue(control > 0, "precondition (control arm): the ultimate landed"),
                () -> Assertions.assertTrue(unbroken > control,
                        "「若目标未处于弱点击破状态」: the unbroken branch fired: " + control + " -> " + unbroken),
                () -> Assertions.assertEquals(normalized, broken, Math.max(1.0, 0.02 * normalized),
                        "「若目标处于弱点击破状态」: either branch gives the same +30% once the toughness reduction is normalized away: unbroken "
                                + unbroken + " (÷0.9 = " + normalized + ") vs broken " + broken),
                () -> Assertions.assertTrue(unbroken / control > 1.15 && unbroken / control < 1.6,
                        "the boost is in the +30% band (control " + control + ", unbroken " + unbroken + ", ratio "
                                + (unbroken / control) + ")"));
    }

    private static double damage(boolean fullContent, boolean broken) {
        Character feixiao = CharacterFactory.create(FEIXIAO, LEVEL);
        if (!fullContent) {
            EffectSpec raise = new EffectSpec();
            TriggerSpecs.set(raise, "op", "RAISE_SKILL_LEVEL");
            TriggerSpecs.set(raise, "skill", "ULTRA");
            TriggerSpecs.set(raise, "amount", 9.0);
            feixiao.setTriggerTable(new TriggerTable(9970, List.of(TriggerSpecs.rule(
                    TriggerEvent.BATTLE_START.name(), List.of(), raise))));
        }
        Enemy victim = EnemyFactory.create(VICTIM, 90, 1);
        victim.setAttribute(AttributeType.HEALTH, new DoubleValue(900000));
        victim.heal(900000);
        Random noCrit = new Random(0) {
            @Override
            public double nextDouble() {
                return 0.99;
            }
        };
        Battle battle = new Battle(List.of(feixiao), List.of(victim), noCrit);
        battle.startBattle();
        if (broken) {
            battle.reduceToughness(feixiao, victim, victim.getStanceWeak().iterator().next(), 999);
            Assertions.assertTrue(victim.getBuffManager().hasState("break:weakness"), "precondition: the target is broken");
        }
        double before = victim.getCurrentHp();
        battle.castImmediate(feixiao.getSkills().get(SkillType.ULTRA), feixiao, List.of(victim));
        double dealt = before - victim.getCurrentHp();
        Assertions.assertFalse(victim.isDeath(), "the judged hit must not kill the victim");
        return dealt;
    }

    // ==================================================================
    // 行迹 解形 (1220102): the follow-up's CRIT DMG
    // ==================================================================

    /**
     * Note: 行迹 解形 (1220102) on one wearer and one monster, read twice: the compiled rule's own scope / share / instance
     * flag, and the measured ratio, which is exactly (1.5 + 0.36) / 1.5 when the modifier really is 36 PERCENTAGE POINTS of
     * crit damage rather than a share of something.
     */
    @Test
    public void theTraceAdds36PointsOfCritDamageToFollowUpInstances() {
        Character unit = CharacterFactory.create(FEIXIAO, LEVEL);
        Enemy enemy = BattleFixture.monster();
        List<EffectSpec> critMods = new ArrayList<>();
        for (var rule : unit.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE,
                new TriggerTable.TriggerContext(unit, unit, enemy, 0, 0))) {
            for (var effect : rule.effects()) {
                if ("MODIFY_ATTR".equals(effect.getOp()) && "CRIT_ATTACK".equals(effect.getAttribute())) {
                    critMods.add(effect);
                    System.out.println("[1220] id=" + rule.id() + " scope=" + effect.getDamageType()
                            + " percent=" + effect.getPercent() + " instance=" + effect.getInstance());
                }
            }
        }

        Battle battle = new Battle(List.of(unit), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        // NOT fixedCrit: that path bypasses the crit zone, so an instance's extra crit damage stays invisible
        // (measured: the ratio stayed exactly 1.0). A guaranteed crit instead -- the zone runs and the roll cannot fail.
        unit.getAttribute(com.laosun.aluminium.enums.AttributeType.CRIT_CHANCE)
                .addModifier(com.laosun.aluminium.DoubleValue.Modifier.pure(
                        2.0, com.laosun.aluminium.DoubleValue.Modifier.ModifierSource.BUFF, 122001));
        boolean alwaysCrits = unit.getAttribute(com.laosun.aluminium.enums.AttributeType.CRIT_CHANCE).get() >= 1.0;
        double normal = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.WIND, DamageType.NORMAL, 1000));
        double additional = battle.applyAdditionalDamage(unit, enemy, DamageElement.WIND, 1000, null, null);
        System.out.println("[1220] normal=" + normal + " additional=" + additional + " ratio=" + (additional / normal));
        // MEASURED first, then pinned: with the guaranteed crit the factor is exactly (1.5 + 0.36) / 1.5, i.e. the
        // modifier really is 36 PERCENTAGE POINTS of crit damage rather than a share of anything.

        Assertions.assertAll("行迹 解形 (1220102): 「追加攻击的暴击伤害提高36%」 - the compiled rule and the measured ratio",
                () -> critMods.forEach(effect -> Assertions.assertEquals("ADDITIONAL", effect.getDamageType(),
                        "「追加攻击」: scoped to follow-up damage")),
                () -> critMods.forEach(effect -> Assertions.assertEquals(0.36, effect.getPercent(), 1e-9,
                        "「提高36%」: the data row states 0.36")),
                () -> critMods.forEach(effect -> Assertions.assertTrue(Boolean.TRUE.equals(effect.getInstance()),
                        "the instance flag: it belongs to the instance")),
                () -> Assertions.assertEquals(1, critMods.size(), "exactly one such modifier"),
                () -> Assertions.assertTrue(alwaysCrits, "precondition: every hit crits"),
                () -> Assertions.assertEquals(1.24, additional / normal, 1e-6,
                        "「追加攻击的暴击伤害提高36%」: the follow-up instance crits for exactly 36 points more"));
    }

    // ==================================================================
    // 【飞黄】: the every-two-attacks counter
    // ==================================================================

    /**
     * Note: 【飞黄】's counter on the same {@link Scene} shape, read three times: two attacks pay one point and the count keeps
     * running from zero; her own Ultimate never counts while her non-ultimate attacks do; and a TEAMMATE's Ultimate still
     * counts, because 「飞霄的终结技攻击不计入次数」 names hers alone.
     */
    @Test
    public void twoTeammateAttacksPayOnePointAndHerOwnUltimateIsExcluded() {
        Scene scene = new Scene();
        int startsEmpty = scene.aureus();
        scene.teammateAttacks(SkillCategory.NORMAL);
        int afterOneAttack = scene.aureus();
        scene.teammateAttacks(SkillCategory.NORMAL);
        int afterTwoAttacks = scene.aureus();
        scene.teammateAttacks(SkillCategory.NORMAL, 2);
        int afterFourAttacks = scene.aureus();

        Scene hers = new Scene();
        hers.herAttack(SkillCategory.ULTRA, 3, 3);
        int afterHerUltimates = hers.aureus();
        hers.herAttack(SkillCategory.BPSKILL, 2, 2);
        int afterHerNonUltimates = hers.aureus();

        Scene teammate = new Scene();
        teammate.teammateAttacks(SkillCategory.ULTRA, 2);
        int afterTeammateUltimates = teammate.aureus();

        Assertions.assertAll("「我方目标每施放 2 次攻击，飞霄获得 1 点【char:1220/feihuang】；飞霄的终结技攻击不计入次数」",
                () -> Assertions.assertEquals(0, startsEmpty, "it starts empty"),
                () -> Assertions.assertEquals(0, afterOneAttack, "one attack is not yet two"),
                () -> Assertions.assertEquals(1, afterTwoAttacks, "「每施放 2 次攻击，飞霄获得 1 点【char:1220/feihuang】」"),
                () -> Assertions.assertEquals(2, afterFourAttacks, "and it keeps counting from zero, not from two"),
                () -> Assertions.assertEquals(0, afterHerUltimates,
                        "「飞霄的终结技攻击不计入次数」: a single Ultimate attack is not two, and must not count either"),
                () -> Assertions.assertEquals(1, afterHerNonUltimates,
                        "two of HER non-ultimate attacks do count"),
                () -> Assertions.assertEquals(1, afterTeammateUltimates,
                        "a TEAMMATE's Ultimate counts - only 飞霄's own is excluded"));
    }

    // ==================================================================

    private static final class Scene {
        private final Character feixiao;
        private final Character ally;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private Scene() {
            feixiao = CharacterFactory.create(FEIXIAO, LEVEL);
            ally = CharacterFactory.create(ALLY, LEVEL);
            enemy.setMaxStance(100_000);
            enemy.setStance(100_000);
            battle = new Battle(new ArrayList<>(List.of(feixiao, ally)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
            battle.processRequests();
        }

        private int aureus() {
            return feixiao.getResources().has(AUREUS) ? feixiao.getResources().value(AUREUS) : 0;
        }

        private void teammateAttacks(SkillCategory category, int times) {
            for (int i = 0; i < times; i++) {
                battle.fire(TriggerPayload.Cast.of(TriggerEvent.ALLY_ATTACK, ally, enemy, 1, 0,
                        category, 1, 0, List.of(enemy)));
                battle.processRequests();
            }
        }

        private void teammateAttacks(SkillCategory category) {
            teammateAttacks(category, 1);
        }

        private void herAttack(SkillCategory category, int times, int skillSlot) {
            for (int i = 0; i < times; i++) {
                battle.fire(TriggerPayload.Cast.of(TriggerEvent.ALLY_ATTACK, feixiao, enemy, 1, 0,
                        category, skillSlot, 0, List.of(enemy)));
                battle.processRequests();
            }
        }
    }
}
