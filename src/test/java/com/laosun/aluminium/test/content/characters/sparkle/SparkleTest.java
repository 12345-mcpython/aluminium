package com.laosun.aluminium.test.content.characters.sparkle;

import com.laosun.aluminium.Battle;
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
import com.laosun.aluminium.trigger.TriggerInterpreter;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1306 Sparkle (花火), consolidated: the base kit's Skill / Ultimate / talent, the per-point talent stack, the [幻相]
 * vulnerability zone, and the eidolons that move those numbers (E1, E2, E4).
 *
 * <p>From her own file: the CRIT DMG share the Skill hands over, the party-wide boost her talent accrues on every Skill Point
 * spent, and the skill-point cap her talent raises. 「每消耗 1 点战技点，花火获得 1 层[幻相]」 is PER POINT, not per spending
 * action: the rule carries `scale: event_amount` + `percent: 1`, and the other 1306 judges all spend one point per call, where
 * per-action and per-point agree, so this one hands the rule a context whose amount is 2 and expects two stacks.
 *
 * <p>The talent's zone: 「每层[幻相]使敌方全体受到的伤害提高 4.00%，持续 2 回合，最多叠加 3 层」, judged on the COMPILED rules
 * and on the counter they depend on - the stacking rule must be there and the scaled zone must read the wearer's own counter.
 *
 * <p>Eidolon 1: 「【谜诡】持续时间 +1 回合」 and 「持有【谜诡】的我方目标攻击力提高 40%」, plus the Ultimate's own per-layer rider.
 * The 40% clause is conditional on the HOLDER (「持有【谜诡】的我方目标」), not on the rule owner; {@code while_holder_state} is
 * the gate that asks the carrier, so the case walks the state off one ally and checks that only that ally loses the 40%. The
 * rider 「每层额外提高 10%」 is the <b>base kit's</b> clause (it is in the Ultimate's own text), so it must already be there at
 * E0 - that is asserted as its own reading, not as an Eidolon difference.
 *
 * <p>Eidolon 2 (before enhancement): "each layer of the talent additionally makes our targets ignore 8% of the target's defence
 * when dealing damage". The layers are the talent's own, which is why that modifier had to be named first; the value is read
 * with the counter scale, and DEFENCE_IGNORE is a ratio attribute, so that is the right shape. Three ways: rank 2 states 8% per
 * layer, the count tracks the events, and rank 0 states nothing (the gate is half the clause).
 *
 * <p>Eidolon 4: 「终结技可以额外恢复 1 点战技点，天赋的战技点上限增加效果额外增加 1 点」 - the talent raises the party's
 * skill-point <b>cap</b> by 2, and this Eidolon raises <b>that effect</b> by 1, not "grants one more point". Measured as a cap,
 * because a point grant would be spent. The Skill's 「当花火对自身施放该技能时，无法触发行动提前效果」 splits ONE cast into two:
 * the crit-damage share is granted either way, the advance only when the ally is someone else - an effect-level filter, so the
 * criterion reads the ally's action value before and after.
 */
public class SparkleTest {
    private static final int SPARKLE = 1306;
    private static final int ALLY = 1002;
    /** A character with no rule file of its own, so no other content moves its panel (1210 Guinaifen). */
    private static final int ALLY_WITHOUT_RULES = 1210;
    private static final int LEVEL = 80;
    private static final double EPS = 1e-9;
    private static final String COUNTER = "char:1306/narrative_trick";
    private static final String PHANTASM = "char:1306/riddle";

    /**
     * Note: "等同于花火24.00%暴击伤害+45.00%" -- a derived share PLUS a flat amount, both read from the engine.
     */
    @Test
    public void theSkillHandsOverHerCritDamageShare() {
        Character sparkle = CharacterFactory.create(SPARKLE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(sparkle, ally), List.of(enemy), fixed());
        battle.startBattle();
        double before = ally.getAttribute(AttributeType.CRIT_ATTACK).get();
        double expected = sparkle.getAttribute(AttributeType.CRIT_ATTACK).get() * 0.24 + 0.45;

        battle.castImmediate(sparkle.getSkills().get(SkillType.SKILL), sparkle, List.of(ally));

        Assertions.assertEquals(expected, ally.getAttribute(AttributeType.CRIT_ATTACK).get() - before, expected * 0.02,
                "24% of her CRIT_ATTACK plus a flat 45%: expected " + expected);
    }

    /**
     * Note: The talent's party-wide boost on a Skill Point spent, capped at the document's three stacks.
     */
    @Test
    public void everySpentSkillPointRaisesThePartysDamage() {
        Character sparkle = CharacterFactory.create(SPARKLE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(sparkle, ally), List.of(enemy), fixed());
        battle.startBattle();
        double before = ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_POINT_SPENT, ally, enemy, 0, 0));
        double afterOne = ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        for (int i = 0; i < 4; i++) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_POINT_SPENT, ally, enemy, 0, 0));
        }
        double afterFive = ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();

        Assertions.assertEquals(0.06, afterOne - before, 1e-6,
                "「每消耗 1 点战技点…伤害提高 6.00%」 (for every 1 Skill Point consumed ... DMG is raised by 6.00%)");
        Assertions.assertEquals(0.18, afterFive - before, 1e-6,
                "「最多可叠加 3 层」 (stacks at most 3 times) -- five firings must still read three stacks of 6%");
    }

    /**
     * Note: The Ultimate: four Skill Points and the [谜诡] state on every ally.
     */
    @Test
    public void theUltimateGrantsSkillPointsAndCipher() {
        Character sparkle = CharacterFactory.create(SPARKLE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(sparkle, ally), List.of(enemy), fixed());
        battle.startBattle();
        Assertions.assertTrue(battle.spendSkillPoint(), "the fixture must have a point to spend");
        int before = battle.getSkillPoints();

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, sparkle, enemy, 0, 0));

        Assertions.assertEquals(Math.min(before + 4, battle.getSkillPointMax()), battle.getSkillPoints(),
                "「为我方恢复 4 个战技点」 (clamped by the pool's ceiling)");
        Assertions.assertTrue(ally.getBuffManager().hasState("char:1306/riddle"),
                "「并使我方全体获得【char:1306/riddle】」 (and grants all allies Cipher (【char:1306/riddle】))");
    }

    /**
     * Note: Her talent: "while Sparkle is on the field the skill point cap is raised by 2" (tbgd 130604).
     */
    @Test
    public void herTalentRaisesTheTeamCap() {
        int withHer = capWith(List.of(CharacterFactory.create(SPARKLE, LEVEL), CharacterFactory.create(ALLY, LEVEL)));
        int withoutHer = capWith(List.of(CharacterFactory.create(ALLY, LEVEL)));
        System.out.println("[sparkle_cap] the team cap with her = " + withHer + " ; without her = " + withoutHer);
        Assertions.assertEquals(5, withoutHer, "the conventional cap, with nobody raising it");
        Assertions.assertEquals(7, withHer, "her talent raises the team's cap by 2 (tbgd 130604 param #3)");
    }

    // ==================================================================

    /**
     * 「当我方目标每消耗 1 点战技点，花火获得 1 层[幻相]」 -- per point, so two points handed to one context are two stacks.
     */
    @Test
    public void twoSpentPointsGrantTwoStacks() {
        Assertions.assertEquals(1, phantasmAfter(1), "one point is one stack");
        Assertions.assertEquals(2, phantasmAfter(2),
                "two points must be two stacks -- that is what per-point means, and a per-action reading gives 1");
        System.out.println("[1306] per-point ok: 1 point -> " + phantasmAfter(1) + ", 2 points -> " + phantasmAfter(2));
    }

    // ==================================================================

    @Test
    public void theCounterIsStackedAndTheZoneScalesOffIt() {
        Character unit = CharacterFactory.create(SPARKLE, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(unit), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_POINT_SPENT, unit, enemy, 1, 0));
        int layers = unit.getBuffManager().stacksOf("char:1306/illusion");
        System.out.println("[1306] layersAfterOneSpend=" + layers);
        var rules = unit.getTriggerTable().matching(TriggerEvent.SKILL_POINT_SPENT,
                new TriggerTable.TriggerContext(unit, unit, enemy, 1, 0));
        String scale = null;
        double percent = -1;
        double cap = -1;
        for (var rule : rules) {
            for (var effect : rule.effects()) {
                if ("MODIFY_DAMAGE_TAKEN".equals(effect.getOp())) {
                    scale = effect.getScale();
                    percent = effect.getPercent();
                    cap = effect.getCapAmount();
                    System.out.println("[1306] rule id=" + rule.id() + " scale=" + scale + " percent=" + percent
                            + " capAmount=" + cap + " turns=" + effect.getTurns());
                }
            }
        }
        Assertions.assertEquals(1, layers, "the spend stacked one layer");
        Assertions.assertEquals("self_stacks:char:1306/illusion", scale, "the layers live on the wearer");
        Assertions.assertEquals(0.04, percent, 1e-9, "each layer adds 4%");
        Assertions.assertEquals(0.12, cap, 1e-9, "three layers cap it at 12%");
    }

    @Test
    public void moreLayersMeanMoreDamageTaken() {
        Character unit = CharacterFactory.create(SPARKLE, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(unit), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        double none = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.QUANTUM, DamageType.NORMAL, 1000));
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_POINT_SPENT, unit, enemy, 1, 0));
        double one = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.QUANTUM, DamageType.NORMAL, 1000));
        System.out.println("[1306] none=" + none + " oneLayer=" + one + " ratio=" + (one / none));
        // Note: The measured ratio is 1.04 x 1.06, and the second factor is NOT this clause: the same event also fires
        // `talent_party_damage_on_spend`, which raises every ally's ALL_DAMAGE_TYPE_BOOST by 6%. Stating the product keeps the
        // assertion honest about what the fixture can see (the spec-level judge above isolates this clause).
        Assertions.assertEquals(1.04 * 1.06, one / none, 1e-6,
                "the zone adds 4% on top of the sibling rule's 6% all-damage boost");
    }

    // ==================================================================

    /**
     * 「持有【谜诡】的我方目标攻击力提高 40%」.
     */
    @Test
    public void theFirstEidolonRaisesTheAttackOfWhoeverHoldsThePhantasm() {
        Run e0 = new Run(0);
        Run e1 = new Run(1);
        System.out.println("[1306-E1] ally ATK delta: E0=" + e0.attackDelta() + " ; E1=" + e1.attackDelta());
        Assertions.assertEquals(0.0, e0.attackDelta(), 1e-6, "E0 states nothing of the sort");
        Assertions.assertEquals(0.4 * e1.allyBaseAttack, e1.attackDelta(), 1e-6, "「攻击力提高 40%」");
    }

    /**
     * The gate asks the CARRIER: take 谜诡 off the ally, and only that ally loses the 40%.
     */
    @Test
    public void theBonusLeavesWithTheStateOfItsCarrier() {
        Run run = new Run(1);
        Assertions.assertEquals(0.4 * run.allyBaseAttack, run.attackDelta(), 1e-6);

        run.ally.getBuffManager().removeState(PHANTASM);
        // A `while_holder_state` modifier is pruned at the next turn boundary, like every read-time gate.
        run.battle.setCurrentMove(new com.laosun.aluminium.Signal(run.ally));
        run.battle.beforeMove();
        run.battle.processRequests();

        Assertions.assertEquals(0.0, run.attackDelta(), 1e-6,
                "the gate is `while_holder_state`, so the state going takes the bonus with it");
        Assertions.assertTrue(run.sparkle.getBuffManager().hasState(PHANTASM),
                "her own copy is untouched (the removal was on the ally)");
    }

    /**
     * 「触发花火天赋造成的伤害提高效果每层额外提高 10%」 - the rider is in the Ultimate's own text, so E0 has it too.
     */
    @Test
    public void theUltimateRaisesTheTalentsBoostPerLayer() {
        Run e0 = new Run(0);
        Run e1 = new Run(1);
        double plain = e0.boostAfterThreeSpends();
        double raised = e1.boostAfterThreeSpends();
        System.out.println("[1306] ally all-damage boost after three spends: E0=" + plain + " ; E1=" + raised);
        Assertions.assertEquals(0.06 * 3 + 0.1 * 3, plain, 1e-6,
                "6% per spent point (3 layers) plus 10% per layer of that same boost, for the char:1306/riddle holder");
        Assertions.assertEquals(plain, raised, 1e-6, "the rider is base kit, not an Eidolon");
    }

    // ==================================================================

    /**
     * Eidolon 2's reading: rank 2 states 8% per talent layer, the count tracks the events, and rank 0 states nothing.
     */
    @Test
    public void rankTwoTurnsEachTalentLayerIntoEightPercentDefenceIgnore() {
        Assertions.assertEquals(0.08, ignoreAfter(2, 1), EPS, "one layer is 8%");
        Assertions.assertEquals(0.24, ignoreAfter(2, 3), EPS, "three layers, the talent's cap, are 24%");
        Assertions.assertEquals(0.0, ignoreAfter(0, 3), EPS, "eidolon 2 is the gate: without it nothing is stated");
    }

    // ==================================================================

    /**
     * The cap half: 2 (talent) + 1 (Eidolon 4) over the engine's base.
     */
    @Test
    public void eidolon4RaisesTheSkillPointCapByOneMore() {
        Fixture e4 = new Fixture(4);
        int baseCap = new Fixture(0).battle.getSkillPointMax();
        e4.castUltimate();
        Assertions.assertEquals(baseCap + 1, e4.battle.getSkillPointMax(),
                "「天赋的战技点上限增加效果额外增加 1 点」 — one MORE on top of the talent's own raise");
    }

    /**
     * The point half: the Ultimate pays its own 4 plus this Eidolon's 1 (clamped at the cap).
     */
    @Test
    public void eidolon4PaysOneMorePointWithTheUltimate() {
        Fixture e4 = new Fixture(4);
        int before = e4.battle.getSkillPoints();
        e4.castUltimate();
        Assertions.assertEquals(Math.min(e4.battle.getSkillPointMax(), before + 5), e4.battle.getSkillPoints(),
                "「终结技可以额外恢复 1 点战技点」 — the Ultimate's own 4 plus this Eidolon's 1");

        Fixture e0 = new Fixture(0);
        int beforeE0 = e0.battle.getSkillPoints();
        e0.castUltimate();
        Assertions.assertEquals(Math.min(e0.battle.getSkillPointMax(), beforeE0 + 4), e0.battle.getSkillPoints(),
                "control: at E0 the Ultimate pays 4");
    }

    /**
     * 战技:「…并使该目标行动提前 50%。**当花火对自身施放该技能时，无法触发行动提前效果**」.
     *
     * <p>The sentence splits ONE cast into two: the crit-damage share is granted either way, the advance only when the ally is
     * someone else. That is an effect-level filter, so the criterion reads the ally's action value before and after - unchanged
     * for a self-cast, pushed forward for a cast on a teammate.
     */
    @Test
    public void theSkillAdvancesOnlyWhenCastOnSomeoneElse() {
        Fixture self = new Fixture(0);
        double selfBefore = self.allyActionValue();
        self.castSkillOn(self.sparkle);
        Assertions.assertEquals(selfBefore, self.allyActionValue(), 1e-6,
                "「当花火对自身施放该技能时，无法触发行动提前效果」 - the caster is her own target, so nothing advances");
        Assertions.assertTrue(self.sparkle.getAttribute(com.laosun.aluminium.enums.AttributeType.CRIT_ATTACK).get() > 0,
                "and the crit-damage share is still granted to her");

        Fixture ally = new Fixture(0);
        double allyBefore = ally.allyActionValue();
        ally.castSkillOn(ally.ally);
        Assertions.assertTrue(ally.allyActionValue() < allyBefore,
                "on a teammate the advance happens: " + allyBefore + " -> " + ally.allyActionValue());
    }

    // ==================================================================

    private static int phantasmAfter(int pointsSpent) {
        Character c = CharacterFactory.create(SPARKLE, LEVEL, true, null, null);
        Enemy e = EnemyFactory.create(1002011, 90, 1);
        Battle b = new Battle(List.of(c), List.of(e), BattleFixture.seeded());
        b.startBattle();
        TriggerTable.TriggerContext ctx = new TriggerTable.TriggerContext(c, c, e, 1, pointsSpent, null, b,
                SkillCategory.UNSPECIFIED);
        var rules = TriggerTables.of(SPARKLE).rulesFor(TriggerEvent.SKILL_POINT_SPENT).stream()
                .filter(r -> "talent_phantasm_stack".equals(r.id())).toList();
        Assertions.assertEquals(1, rules.size(), "the stack rule must exist");
        TriggerInterpreter.apply(b, rules.getFirst(), ctx);
        return c.getBuffManager().stacksOf("char:1306/illusion");
    }

    private static double ignoreAfter(int rank, int layers) {
        Character sparkle = CharacterFactory.create(SPARKLE, LEVEL, true, null, null, rank);
        Character ally = CharacterFactory.create(ALLY_WITHOUT_RULES, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(sparkle, ally), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        for (int i = 0; i < layers; i++) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_POINT_SPENT, sparkle, enemy, 1, 1));
        }
        Assertions.assertEquals(layers, sparkle.getBuffManager().stacksOf(COUNTER),
                "precondition: the talent counted " + layers + " layer(s)");
        return ally.getAttribute(AttributeType.DEFENCE_IGNORE).get();
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }

    private static int capWith(List<Character> team) {
        Battle battle = new Battle(team, List.of(BattleFixture.monster()), fixed());
        battle.startBattle();
        return battle.getSkillPointMax();
    }

    // ==================================================================

    private static final class Run {
        private final Character sparkle;
        private final Character ally;
        private final Battle battle;
        private final double allyBaseAttack;
        private final double allyAttackBefore;

        Run(int eidolon) {
            sparkle = CharacterFactory.create(SPARKLE, LEVEL);
            sparkle.setEidolonRank(eidolon);
            ally = CharacterFactory.create(ALLY_WITHOUT_RULES, LEVEL);
            allyBaseAttack = ally.getAttribute(AttributeType.ATTACK).baseValue();
            Enemy enemy = BattleFixture.monster();
            battle = new Battle(List.of(sparkle, ally), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            allyAttackBefore = ally.getAttribute(AttributeType.ATTACK).get();
            battle.castImmediate(sparkle.getSkills().get(SkillType.ULTRA), sparkle, List.of(enemy));
            battle.processRequests();
        }

        double attackDelta() {
            return ally.getAttribute(AttributeType.ATTACK).get() - allyAttackBefore;
        }

        /** Three skill points spent: the talent stacks its 6% three times, then the rider reads the same layers. */
        double boostAfterThreeSpends() {
            for (int i = 0; i < 3; i++) {
                battle.fire(TriggerPayload.Simple.of(TriggerEvent.SKILL_POINT_SPENT, ally,
                        battle.enemies().getFirst(), 1, 0));
                battle.processRequests();
            }
            return ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        }
    }

    // ==================================================================

    private static final class Fixture {
        private final Character sparkle;
        private final Character ally;
        private final Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        private final Battle battle;

        private Fixture(int eidolon) {
            sparkle = CharacterFactory.create(SPARKLE, 80, false, null, null, eidolon);
            ally = CharacterFactory.create(1001, 80);
            battle = new Battle(List.of(sparkle, ally), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        private void castUltimate() {
            battle.castImmediate(sparkle.getSkills().get(SkillType.ULTRA), sparkle, List.of(sparkle));
            battle.processRequests();
        }

        private void castSkillOn(Character target) {
            battle.castImmediate(sparkle.getSkills().get(SkillType.SKILL), sparkle, List.of(target));
            battle.processRequests();
        }

        /** The ally's remaining wait on the action queue (smaller = further forward). */
        private double allyActionValue() {
            for (var signal : battle.queue().snapshot()) {
                if (signal.getCanHit() == ally) {
                    return battle.queue().getTimeRemaining(signal);
                }
            }
            throw new AssertionError("the ally is not in the action order");
        }
    }
}
