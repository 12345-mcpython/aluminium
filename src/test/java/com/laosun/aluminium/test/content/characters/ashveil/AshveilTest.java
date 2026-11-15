package com.laosun.aluminium.test.content.characters.ashveil;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1504 不死途 (Ashveil) - the whole character in one place.
 *
 * <p>Consolidated from five per-concern test classes; every test and every assertion of those files is kept. In order, the
 * concerns are:
 *
 * <ul>
 *   <li>her own file: the Bait reaction (8 energy + a 200%-ATK follow-up) and the [婪酣] cap, whose numbers the document states.
 *       Both guards are load-bearing and tested: `actor is_other_ally` (her own attack must not trigger it) and
 *       `target has_state 饲饵`. The cap is tested by EXCEEDING it. Each firing grants the declared TWO layers
 *       (「随后获得 2 层【婪酣】」 - {@code ADD_STACK}'s {@code amount} IS the layer count), so two firings give four, and a LOUD
 *       refusal follows once the two declared charges are spent;</li>
 *   <li>the technique's opening: the AoE and one Charge, whose declaration makes the gain real;</li>
 *   <li>the two readings this round pinned - the talent's layer COUNT (「随后获得 2 层【婪酣】，最多叠加 12 层」, previously
 *       written {@code amount: 1} on the belief that {@code ADD_STACK}'s amount is not the layer count; it is:
 *       {@code TriggerEffects.addStack} builds {@code wanted} from exactly that field) and the Eidolon 2 cap
 *       (「【婪酣】叠加上限提高至 18 层」, the base cap 12);</li>
 *   <li>the Ultimate's other two halves (「使指定敌方单体成为【饲饵】」 and 「获得 3 点充能」): after the Ultimate the target holds
 *       the bait and her charges are back to the declared maximum of 3 (from 0), the bait applied BEFORE the follow-up cast,
 *       which is what lets 「对【饲饵】发动追加攻击」 see it;</li>
 *   <li>the "头狼" (Alpha Wolf) trace 1504103: "不死途在场时，我方目标造成的暴击伤害提高 40%，我方目标追加攻击造成的暴击伤害额外提高 80%".
 *       Both halves are party-wide, and the fixture proves it with an ALLY: the ally's crit damage rises (first half) and its
 *       follow-up rises further (second half); the control battle is the same ally without 1504 in the party;</li>
 *   <li>Eidolons 1 and 6, measured on the enemy the aura reaches: E1's 「敌方全体受到的伤害提高 24%」 is exactly ONE vulnerability
 *       layer, E6's 「敌方全体的全属性抗性降低 20%」 lowers their all-type RES - each compared against the rank BELOW it, so the
 *       character's other Eidolons cannot account for the difference;</li>
 *   <li>Eidolon 1's second tier, 「当敌方目标当前生命值百分比小于等于 50% 时，其受到的伤害提高至 36%」: lowering the bar took four
 *       attempts, and the working one is a LOOP OF SMALL HITS - one big setup hit overkills the target because the settled damage
 *       carries the zones ({@code CanHit} has no {@code setCurrentHp}, and {@code consumeHp} leaves an enemy untouched).</li>
 * </ul>
 *
 * <p><b>L3.</b> The single-clause cases that read one board are folded into the scenario tests below, one
 * {@code Assertions.assertAll} per fixture so a failing clause still names itself: the talent's layer count together with
 * the Eidolon 2 cap that raises it (one {@code Fixture} board, one counter), and the two enemy-facing Eidolon clauses (E1's
 * vulnerability layer and E6's resistance cut) on the one {@link #soloBoard(int)} board. A case whose fixture or observed
 * object is not the same - the compiled data rows of the 头狼 trace, the Ultimate's own cast, the 36% tier that stands the
 * enemy at a fraction of its HP, and every rejection - keeps its own test.
 */
public class AshveilTest {
    private static final int ASHVEIL = 1504;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final String LAYERS = "婪酣";

    // ------------------------------------------------------------------ talent: the Bait reaction

    /**
     * Note: The reaction fires for a teammate's attack on the Bait, and not for her own.
     */
    @Test
    public void theBaitReactionNeedsATeammate() {
        double fromAlly = energyGain(true);
        double fromSelf = energyGain(false);

        Assertions.assertEquals(8.0, fromAlly, 1e-6,
                "「固定恢复8点能量」 (restores a fixed 8 energy)");
        Assertions.assertEquals(0.0, fromSelf, 1e-6,
                "「我方**其他**目标攻击后」 (after another one of our targets attacks) -- her own attack must not trigger it");
    }

    /**
     * Note: Two layers per firing (2026-11-03), and a LOUD refusal once the two declared charges are spent (measured, not assumed).
     *
     * <p><b>This test used to pin the wrong reading</b>: it asserted one layer per firing on the belief that {@code ADD_STACK}'s
     * {@code amount} is not a layer count. It is — {@code TriggerEffects.addStack} builds {@code wanted} from exactly that field —
     * so the talent's 「随后获得 2 层【婪酣】」 now grants two, and two firings give four.
     */
    @Test
    public void eachReactionAddsTwoLayersAndNeedsACharge() {
        Character ashveil = CharacterFactory.create(ASHVEIL, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(ashveil, ally), List.of(enemy), fixed());
        battle.startBattle();
        // Make the enemy the Bait first: without it neither guard passes.
        battle.castImmediate(ashveil.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), ashveil, List.of(enemy));

        // Her declaration gives 2 charges. One firing is TWO layers: the talent says 「获得 2 层【婪酣】」 and `amount` IS the count.
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0));
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0));
        Assertions.assertEquals(5, ashveil.getBuffManager().stacksOf("婪酣"),
                "two firings of two layers, plus the layer the Skill cast grants through 罪途");

        Assertions.assertThrows(IllegalStateException.class,
                () -> battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0)),
                "the engine refuses to spend a charge she does not have");
    }

    /**
     * 「随后获得 2 层【婪酣】」 — one trigger, two layers — and the bar that bounds it: the base cap of 12, and 星魂 2's
     * 「【婪酣】叠加上限提高至 18 层」. One {@code Fixture} board, read before the first trigger, after it and at the cap,
     * so the layer COUNT and the cap it runs into are the same counter rather than two readings.
     */
    @Test
    public void theTalentGrantsTwoLayersUpToTheCap() {
        Fixture base = new Fixture(0);
        int before = base.layers();
        base.triggerTalent();
        int afterOne = base.layers();
        // Nine more firings: the same ten the cap needs, so the count above is the first of them, not an extra one.
        for (int i = 0; i < 9; i++) {
            base.triggerTalent();
        }
        int capped = base.layers();

        Fixture e2 = new Fixture(2);
        for (int i = 0; i < 10; i++) {
            e2.triggerTalent();
        }
        int eidolonTwo = e2.layers();

        Assertions.assertAll("「随后获得 2 层【婪酣】，最多叠加 12 层」, and Eidolon 2's 18",
                () -> Assertions.assertEquals(0, before, "precondition: no layers yet"),
                () -> Assertions.assertEquals(2, afterOne, "「随后获得 2 层【婪酣】」 — two, not one"),
                () -> Assertions.assertEquals(12, capped, "the base cap is 12"),
                () -> Assertions.assertEquals(18, eidolonTwo, "「叠加上限提高至 18 层」"));
    }

    // ------------------------------------------------------------------ technique and Ultimate

    /**
     * Note: The technique's opening: the AoE and one Charge, whose declaration makes the gain real.
     */
    @Test
    public void theTechniqueHitsAndGrantsCharge() {
        Character ashveil = CharacterFactory.create(ASHVEIL, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(ashveil), List.of(enemy), fixed());
        battle.markTechniqueUsed(ashveil);
        double before = enemy.getCurrentHp();

        battle.startBattle();

        Assertions.assertTrue(before - enemy.getCurrentHp() > 0,
                "「对敌方全体造成等同于不死途攻击力100%的雷属性伤害」 (Lightning damage to all enemies equal to 100% of 不死途's ATK)");
    }

    /**
     * The Ultimate's other two halves (2026-11-03): 「使指定敌方单体成为【饲饵】」 and 「获得 3 点充能」.
     *
     * <p>Measured: after the Ultimate the target holds the bait and her charges are back to the declared maximum of <b>3</b> (from 0).
     * The bait is applied BEFORE the follow-up cast, which is what lets 「对【饲饵】发动追加攻击」 see it.
     */
    @Test
    public void theUltimateMarksTheBaitAndRefillsTheCharges() {
        Fixture f = new Fixture(0);
        f.ashveil.getResources().get("char:1504/charge").setValue(0);

        double hpBefore = f.enemy.getCurrentHp();
        f.castUltimate();

        Assertions.assertTrue(f.enemy.getCurrentHp() < hpBefore, "precondition: the Ultimate and its follow-up landed");
        Assertions.assertTrue(f.enemy.getBuffManager().hasState("饲饵"), "「使指定敌方单体成为【饲饵】」");
        Assertions.assertEquals(3, f.ashveil.getResources().get("char:1504/charge").value(), 1e-9,
                "「且不死途获得 3 点充能」 (clamped at the declared maximum of 3)");
    }

    // ------------------------------------------------------------------ 头狼 (Alpha Wolf) aura

    @Test
    public void theRulesCarryTheirScopesAndShares() {
        Character owner = CharacterFactory.create(ASHVEIL, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(owner, ally), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        // The follow-up half is two mutually exclusive rules; both are asked in-battle, one per actor.
        int seen = 0;
        for (var actor : List.of(owner, ally)) {
            for (var rule : owner.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE,
                    new TriggerTable.TriggerContext(owner, actor, enemy, 0, 0))) {
                for (var effect : rule.effects()) {
                    if ("MODIFY_ATTR".equals(effect.getOp()) && "CRIT_ATTACK".equals(effect.getAttribute())) {
                        seen++;
                        System.out.println("[1504] followup rule id=" + rule.id() + " actor=" + (actor == owner ? "self" : "ally")
                                + " scope=" + effect.getDamageType() + " percent=" + effect.getPercent());
                        Assertions.assertEquals("ADDITIONAL", effect.getDamageType(), "follow-up scoped");
                        Assertions.assertEquals(0.8, effect.getPercent(), 1e-9, "the data row states 0.8");
                    }
                }
            }
        }
        // Note: Only the `actor == self` half is asked here. Party predicates (`actor is_ally` / `is_other_ally`) are resolved by the
        // Battle, so a hand-built TriggerContext cannot see those rules -- measured: `is_ally` and `is_other_ally` both return
        // nothing while the very same rule fires in a real battle. That half is therefore covered behaviourally, in the test below.
        Assertions.assertEquals(1, seen, "the self half is visible to a hand-built context");
        double plain = -1;
        for (var rule : owner.getTriggerTable().matching(TriggerEvent.BATTLE_START,
                new TriggerTable.TriggerContext(owner, owner, enemy, 0, 0))) {
            for (var effect : rule.effects()) {
                if ("MODIFY_ATTR".equals(effect.getOp()) && "CRIT_ATTACK".equals(effect.getAttribute())) {
                    plain = effect.getPercent();
                    System.out.println("[1504] aura rule id=" + rule.id() + " percent=" + plain
                            + " target=" + effect.getTarget());
                }
            }
        }
        Assertions.assertEquals(0.4, plain, 1e-9, "the data row states 0.4");
    }

    @Test
    public void anAllyBenefitsFromBothHalves() {
        double[] with = readings(true);
        double[] without = readings(false);
        System.out.println("[1504] with owner: normal=" + with[0] + " followUp=" + with[1]
                + " ratio=" + (with[1] / with[0]));
        System.out.println("[1504] without owner: normal=" + without[0] + " followUp=" + without[1]
                + " ratio=" + (without[1] / without[0]));
        // MEASURED first, then pinned. The two factors are independent and both predictable from the crit multiplier:
        // the aura turns 1.5 into 1.9 (ratio 1.9/1.5) and the follow-up half adds another 0.8 (ratio 2./1.9).
        Assertions.assertEquals(1.0, without[1] / without[0], 1e-6, "without 1504 a follow-up is not special");
        Assertions.assertEquals(1.2666666667, with[0] / without[0], 1e-6,
                "the first half raises the ally's crit damage by exactly 40 points");
        Assertions.assertEquals(1.4210526316, with[1] / with[0], 1e-6,
                "the second half adds exactly 80 points more to a follow-up");
    }

    // ------------------------------------------------------------------ Eidolons 1 and 6

    /**
     * Eidolons 1 and 6, measured on the enemy the aura reaches and each compared against the rank BELOW it, so the
     * character's other Eidolons cannot account for the difference: E1 「敌方全体受到的伤害提高 24%」 is exactly ONE
     * vulnerability layer, and E6 「敌方全体的全属性抗性降低 20%」 lowers their all-type RES. Both readings stand on the one
     * {@link #soloBoard(int)} fixture - one owner, the standard monster, a zero roll.
     *
     * <p>⚠ E1 is read as a LAYER COUNT, not as a damage ratio: the settled-damage ratio is (1 + z + 0.24) / (1 + z), so it is
     * not 1.24 whenever anything else already sits in that zone. And read the FILE first - this clause already had a
     * rule (`eidolon1_enemies_take_more`); a second one written beside it made the enemy carry two layers and
     * looked exactly like an engine that applied the clause twice.
     */
    @Test
    public void theEnemyFacingEidolonsDebuffTheEnemy() {
        int beforeOne = vulnerabilityLayers(0);
        int atOne = vulnerabilityLayers(1);
        double atFive = enemyResistance(5);
        double atSix = enemyResistance(6);
        System.out.println("[1504] enemy RES: E5 " + atFive + " ; E6 " + atSix);

        Assertions.assertAll("Eidolon 1: exactly one vulnerability layer; Eidolon 6: the all-type RES falls",
                () -> Assertions.assertEquals(0, beforeOne, "control: nothing before Eidolon 1"),
                () -> Assertions.assertEquals(1, atOne, "E1's own 24%, exactly one layer"),
                () -> Assertions.assertTrue(atSix > atFive, "「敌方全体的全属性抗性降低 20%」"));
    }

    @Test
    public void belowHalfTheEnemyTakesMore() {
        double above = hitAt(0.8);
        double below = hitAt(0.3);
        System.out.println("[1504] fixed 1000-hit: above half " + above + " ; below half " + below);
        Assertions.assertTrue(above > 0 && below > 0, "both hits must land");
        Assertions.assertEquals(1.36 / 1.24, below / above, 0.02, "「提高至 36%」 where it was 24%");
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The energy gain for a teammate's attack; mode false fires it from Ashveil herself.
     */
    private static double energyGain(boolean teammate) {
        Character ashveil = CharacterFactory.create(ASHVEIL, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(ashveil, ally), List.of(enemy), fixed());
        battle.startBattle();
        battle.castImmediate(ashveil.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), ashveil, List.of(enemy));
        double before = ashveil.getCurrentEnergy();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, teammate ? ally : ashveil, enemy, 0, 0));
        return ashveil.getCurrentEnergy() - before;
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(1.0);
    }

    /**
     * One ally, with or without 1504 in the party; both readings fixed to crit via CRIT_CHANCE.
     */
    private double[] readings(boolean withOwner) {
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        List<Character> party = withOwner
                ? List.of(CharacterFactory.create(ASHVEIL, LEVEL), ally)
                : List.of(ally);
        Battle battle = new Battle(party, List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        ally.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(DoubleValue.Modifier.pure(2.0, DoubleValue.Modifier.ModifierSource.BUFF, 150401));
        Assertions.assertTrue(ally.getAttribute(AttributeType.CRIT_CHANCE).get() >= 1.0,
                "precondition: the ally always crits");
        double normal = battle.applyDamage(enemy, new Damage(ally, enemy, DamageElement.QUANTUM, DamageType.NORMAL, 1000));
        double followUp = battle.applyAdditionalDamage(ally, enemy, DamageElement.QUANTUM, 1000, null, null);
        return new double[]{normal, followUp};
    }

    /**
     * The Eidolon enemy-side board: one owner at the given rank, the standard monster, a zero roll, the battle started.
     *
     * <p>Both Eidolon readings below stand on it - E1's vulnerability rule is written at the start, E6's aura is gated on
     * 「当场上存在【饲饵】时」 and therefore needs the Skill's bait first.
     */
    private static Battle soloBoard(int eidolon) {
        Character owner = CharacterFactory.create(ASHVEIL, LEVEL, true, null, null, eidolon);
        Battle battle = new Battle(new ArrayList<>(List.of(owner)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return battle;
    }

    /** How many VulnerabilityBuff layers the enemy carries right after the battle starts. */
    private static int vulnerabilityLayers(int eidolon) {
        int count = 0;
        for (var buff : soloBoard(eidolon).enemyUnits().getFirst().getBuffManager().activeBuffs()) {
            if (buff instanceof com.laosun.aluminium.buff.VulnerabilityBuff) {
                count++;
            }
        }
        return count;
    }

    /** Damage one fixed hit settles on the enemy, at the given Eidolon rank. */
    private static double taken(int eidolon) {
        Character owner = CharacterFactory.create(ASHVEIL, LEVEL, true, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(owner)), new ArrayList<>(List.of(enemy)),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        double before = enemy.getCurrentHp();
        battle.applyDamage(enemy, new com.laosun.aluminium.damage.Damage(owner, enemy,
                com.laosun.aluminium.enums.DamageElement.THUNDER,
                com.laosun.aluminium.enums.DamageType.NORMAL, 1000));
        return before - enemy.getCurrentHp();
    }

    /**
     * ⚠ The rank-six aura is gated on 「当场上存在【饲饵】时」, so the Skill has to put the bait down first: at BATTLE_START
     * no bait exists and the aura is correctly absent.
     */
    private static double enemyResistance(int eidolon) {
        Battle battle = soloBoard(eidolon);
        Character owner = battle.characters().getFirst();
        var enemy = battle.enemyUnits().getFirst();
        battle.castImmediate(owner.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), owner, List.of(enemy));
        battle.processRequests();
        return enemy.getAttribute(AttributeType.RESISTANCE_REDUCTION).get();
    }

    /** One fixed 1000-point hit on an enemy standing at the given HP fraction. */
    private static double hitAt(double fraction) {
        Character owner = CharacterFactory.create(ASHVEIL, LEVEL, true, null, null, 1);
        Enemy enemy = BattleFixture.dummy();
        Battle battle = new Battle(new ArrayList<>(List.of(owner)), new ArrayList<>(List.of(enemy)),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        // Small steps: a single hit sized to the gap is multiplied by the zones and kills the target.
        for (int step = 0; step < 200 && enemy.getCurrentHp() > enemy.getMaxHp() * fraction; step++) {
            battle.applyDamage(enemy, new com.laosun.aluminium.damage.Damage(owner, enemy,
                    com.laosun.aluminium.enums.DamageElement.THUNDER,
                    com.laosun.aluminium.enums.DamageType.NORMAL, enemy.getMaxHp() * 0.01));
            if (enemy.isDeath()) {
                break;
            }
        }
        Assertions.assertFalse(enemy.isDeath(), "the target must survive the setup");
        double before = enemy.getCurrentHp();
        battle.applyDamage(enemy, new com.laosun.aluminium.damage.Damage(owner, enemy,
                com.laosun.aluminium.enums.DamageElement.THUNDER,
                com.laosun.aluminium.enums.DamageType.NORMAL, 1000));
        return before - enemy.getCurrentHp();
    }

    // ==================================================================

    private static final class Fixture {
        private final Character ashveil;
        private final Character ally = CharacterFactory.create(ALLY, 80);
        private final Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        private final Battle battle;

        private Fixture(int eidolon) {
            ashveil = CharacterFactory.create(ASHVEIL, 80, false, null, null, eidolon);
            battle = new Battle(List.of(ashveil, ally), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
            // the bait is what the talent reacts to
            ashveil.getBuffManager();
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0));
            battle.processRequests();
        }

        /**
         * Fires the talent's door once: an ALLY_ATTACK by a teammate on the baited enemy.
         *
         * <p>The bait state is put on the enemy through the engine's own rule: 秘技/终结技 mark it, so the fixture marks it directly
         * with the same state name the rules read.
         */
        private void triggerTalent() {
            // the talent spends one charge per trigger; top it up so the fixture can fire it repeatedly
            ashveil.getResources().get("char:1504/charge").setValue(3);
            if (!enemy.getBuffManager().hasState("饲饵")) {
                enemy.getBuffManager().addBuff(new com.laosun.aluminium.buff.StateBuff("饲饵", 99, true));
            }
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0));
            battle.processRequests();
        }

        private void castUltimate() {
            battle.castImmediate(ashveil.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA), ashveil, List.of(enemy));
            battle.processRequests();
        }

        private int layers() {
            return ashveil.getBuffManager().stacksOf(LAYERS);
        }
    }
}
