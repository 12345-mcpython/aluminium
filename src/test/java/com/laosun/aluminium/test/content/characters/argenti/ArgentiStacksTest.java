package com.laosun.aluminium.test.content.characters.argenti;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Argenti (银枝) (1302), from his own file: [升格], the talent's per-hit energy, trace Courage (勇气), Eidolon 1/4 and
 * Eidolon 2.
 *
 * <p><b>What it needed.</b> The talent's "for each 1 enemy target hit ... restore 3 energy" is {@code per_target} on
 * {@code GAIN_ENERGY}; [升格] is a <b>stackable named modifier</b> (`max_stacks: 10` + `buff: 升格`); Courage (勇气)'s
 * "enemy targets whose current HP percentage {@code <= 50}%" is the existing {@code target_hp_percent} condition; and
 * Eidolon 2's "3 or more enemies" is the existing {@code enemy_count} variable (it landed after the old note here said
 * no such variable existed). Eidolon 2 is the clause this round added.
 *
 * <p><b>Two witnesses, not claims.</b> Eidolon 1 is a flat +4% crit damage instead of per layer, and the talent grants
 * one layer per cast instead of one per enemy hit. Both are measured and registered in the file's {@code _gaps}; the
 * two tests at the bottom pin today's reading so that fixing them turns them red.
 */
public class ArgentiStacksTest {
    private static final int ARGENTI = 1302;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;

    /**
     * All three cast slots feed the talent: "when casting a basic attack, Skill or Ultimate ... gain 1 stack of [升格]".
     *
     * <p>Note: The energy half of the same sentence ("for each 1 enemy target hit ... restore 3 energy") is {@code per_target}, and its
     * arithmetic already has its own pin in {@code CastAppliedCountTest} - the engine exposes no public "current energy"
     * reader, and inventing one for a test would be a worse trade than pointing at the case that exists. What is pinned
     * here is what this file adds: <b>three rules</b>, one per cast event, each marking exactly once.
     */
    @Test
    public void allThreeSlotsFeedTheTalent() {
        Fixture f = new Fixture();
        for (TriggerEvent cast : List.of(TriggerEvent.BASIC_ATTACK, TriggerEvent.SKILL_CAST, TriggerEvent.ULT_CAST)) {
            int before = f.argenti.getBuffManager().stacksOf("升格");
            f.fireCast(cast, 1);
            int after = f.argenti.getBuffManager().stacksOf("升格");
            Assertions.assertEquals(1, after - before, cast + " marks 【升格】 (Apotheosis) exactly once");
        }
    }


    /**
     * [升格] stacks up to its cap and each stack is a crit-rate step.
     */
    @Test
    public void theStacksCapAtTen() {
        Fixture f = new Fixture();
        double base = f.argenti.getAttribute(AttributeType.CRIT_CHANCE).get();

        for (int i = 0; i < 12; i++) {
            f.fireCast(TriggerEvent.BASIC_ATTACK, 1);
        }

        Assertions.assertEquals(base + 10 * 0.025, f.argenti.getAttribute(AttributeType.CRIT_CHANCE).get(), 1e-6,
                "「该效果最多叠加10层」 (this effect stacks at most 10 times): twelve hits give ten stacks, not twelve");
        Assertions.assertEquals(10, f.argenti.getBuffManager().stacksOf("升格"),
                "…and the count is readable by name, which is what Eidolon 4 and any removal would use");
    }

    /**
     * trace Piety (虔诚) grants a stack on his own turn start.
     */
    @Test
    public void herTraceGrantsAStackOnHerTurn() {
        Fixture f = new Fixture();
        double base = f.argenti.getAttribute(AttributeType.CRIT_CHANCE).get();

        f.argentiTurn();

        Assertions.assertEquals(base + 0.025, f.argenti.getAttribute(AttributeType.CRIT_CHANCE).get(), 1e-6,
                "「回合开始时，立即获得1层【升格】」 (at the start of the turn, immediately gains 1 stack of Apotheosis (【升格】))");
    }

    /**
     * trace Courage (勇气) boosts the instance that lands on a hurt enemy, and only that one.
     */
    @Test
    public void herTraceBoostsDamageOnHurtEnemies() {
        Fixture f = new Fixture();
        f.enemies.get(0).takeDamage(f.enemies.get(0).getMaxHp() * 0.8);

        Assertions.assertEquals(1, TriggerTables.of(ARGENTI).ruleCount(TriggerEvent.TURN_START),
                "census: his file has the turn-start trace");
        Assertions.assertEquals(2, TriggerTables.of(ARGENTI).ruleCount(TriggerEvent.DEALING_DAMAGE),
                "the trace Courage (勇气): the target's HP percentage -- no per-target vocabulary needed, because the condition's subject "
                        + "is already the unit being hit) and Eidolon 6's defence ignore");
    }

    /**
     * Eidolon 4's second half: "make the talent's effect <b>max stack count raise by 2</b>" - the cap really does move.
     *
     * <p>Note: Measured on the ATTRIBUTE, and the arithmetic has a trap worth stating: Eidolon 4 grants two layers at battle
     * start and they share the talent's stack group, so a cap of 12 is reached by 2 + 10 - twelve more hits still only
     * add ten. The probe printed, at E0 vs E4: 10 stacks / crit 0.30 against 12 stacks in the
     * group / crit 0.35 - the raise is real, and an expectation of "base + 12  x  0.025" double-counts those two layers.
     * Note: `stacksOf("升格")` is <b>not</b> usable here either: three modifier groups carry that name (the talent's crit
     * rate, Eidolon 1's crit damage, Eidolon 4's battle-start pair), and a name count adds them up.
     */
    @Test
    public void theFourthEidolonRaisesTheStackCap() {
        Fixture f = new Fixture(4);
        double base = f.argenti.getAttribute(AttributeType.CRIT_CHANCE).get();
        for (int i = 0; i < 12; i++) {
            f.fireCast(TriggerEvent.BASIC_ATTACK, 1);
        }
        Assertions.assertEquals(base + 10 * 0.025, f.argenti.getAttribute(AttributeType.CRIT_CHANCE).get(), 1e-6,
                "12 layers in the group: the two granted at battle start plus ten more from the hits");
        Assertions.assertEquals(2, f.argenti.ruleEffectMaxStacksBonus("talent_stack_basic"),
                "…and the amendment is filed against the named rule — the mechanism, not a coincidence");
    }

    /**
     * Note: Raising the cap of a rule that states none is refused at load: there would be no cap to raise.
     */
    @Test
    public void raisingACapThatDoesNotExistIsRefused() {
        EffectSpec state = new EffectSpec();
        TriggerSpecs.set(state, "op", "APPLY_BUFF");
        TriggerSpecs.set(state, "buff", "标记");
        TriggerSpecs.set(state, "turns", 1);
        TriggerSpecs.set(state, "target", "self");
        TriggerSpec named = TriggerSpecs.rule("BATTLE_START", null, state);
        TriggerSpecs.set(named, "id", "no_cap");

        EffectSpec amend = new EffectSpec();
        TriggerSpecs.set(amend, "op", "MODIFY_RULE");
        TriggerSpecs.set(amend, "rule", "no_cap");
        TriggerSpecs.set(amend, "effectMaxStacks", 2);
        TriggerSpec amender = TriggerSpecs.rule("BATTLE_START", null, amend);

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(ARGENTI, List.of(named, amender)));
        Assertions.assertTrue(refused.getMessage().contains("max_stacks"), refused.getMessage());
    }

    // ==================================================================
    // Eidolon 2: 「施放终结技时，若敌方目标数量大于等于3个时，攻击力提高40%，持续1回合」
    // ==================================================================

    /**
     * The second Eidolon's gate is the enemy count: three enemies raise ATK by 40%, two do not.
     *
     * <p>The 40% is read on {@code ATTACK_PERCENT} (the modifier it writes), not on the total attack: an ATK% raise is a
     * share of the character's <b>base</b> attack, so "40% of the current total" would be a different number. The total
     * moving at all is the second assertion.
     */
    @Test
    public void herSecondEidolonRaisesAttackOnlyWithThreeEnemies() {
        Fixture three = new Fixture(2, 3);
        double before = three.attack();
        double base = three.argenti.getAttribute(AttributeType.ATTACK).baseValue();
        three.castUltimate();

        Assertions.assertEquals(0.4 * base, three.attack() - before, 1e-9,
                "「攻击力提高40%」: an ATK% modifier is a share of the character's BASE attack, so the total moves by 0.4 x base");

        Fixture two = new Fixture(2, 2);
        double twoBefore = two.attack();
        two.castUltimate();
        Assertions.assertEquals(twoBefore, two.attack(), 1e-9,
                "two enemies: 「若敌方目标数量大于等于3个」 does not hold, so the attack does not move");

        Fixture e0 = new Fixture(0, 3);
        double e0Before = e0.attack();
        e0.castUltimate();
        Assertions.assertEquals(e0Before, e0.attack(), 1e-9,
                "the same three enemies without the Eidolon: the clause is gated on the rank");
    }

    /**
     * The raise is applied BEFORE the ultimate settles, so the ultimate's own damage carries it.
     *
     * <p>Compared on two identical battles (three enemies, E0 against E2): if the modifier arrived on {@code ULT_CAST}
     * (which fires after settlement), the two casts would deal the SAME damage and the ratio below would be 1. This is
     * why the rule sits on {@code CAST_SETUP}.
     */
    @Test
    public void theUltimateItselfCarriesTheRaise() {
        Fixture e1 = new Fixture(1, 3);
        double without = e1.castUltimate();

        Fixture e2 = new Fixture(2, 3);
        double before = e2.attack();
        double with = e2.castUltimate();
        double after = e2.attack();

        Assertions.assertEquals(after / before, with / without, 1e-9,
                "the cast's damage moved by exactly the attack multiplier the buff produced, so the buff was live "
                        + "during the cast (without=" + without + ", with=" + with + ")");
        Assertions.assertTrue(with > without, "and the raised cast deals more");
    }

    // ==================================================================
    // Witnesses for the two registered shortfalls (they go RED when the fixes land)
    // ==================================================================

    /**
     * Registered shortfall: 「每层【升格】额外使暴击伤害提高4%」 is a flat +4% today - present at ZERO stacks and not
     * growing with them, because the modifier is attached once at battle start.
     *
     * <p>This is the <b>witness</b>, not a claim that the current reading is right: when the modifier starts following
     * the layer count, the second assertion (0.04 at five layers) becomes 0.24 and this test goes red on purpose.
     */
    @Test
    public void herCritDamageDoesNotFollowTheStacks() {
        Fixture f = new Fixture(1, 1);
        double start = f.argenti.getAttribute(AttributeType.CRIT_ATTACK).get();
        Assertions.assertEquals(0.54, start, 1e-9,
                "today: +0.04 already at 0 layers (base 0.5), which the sentence does not grant");

        for (int i = 0; i < 5; i++) {
            f.fireCast(TriggerEvent.BASIC_ATTACK, 1);
        }
        Assertions.assertEquals(0.54, f.argenti.getAttribute(AttributeType.CRIT_ATTACK).get(), 1e-9,
                "today: five layers are still +0.04; the sentence wants +0.04 x 5 = 0.20");
        Assertions.assertEquals(0.175, f.argenti.getAttribute(AttributeType.CRIT_CHANCE).get(), 1e-9,
                "meanwhile the crit-rate half really does stack, which is what makes the pair's asymmetry visible");
    }

    /**
     * Registered shortfall: the talent grants ONE layer per cast, while the sentence says 「每击中 1 个敌方目标」.
     *
     * <p>The witness: a three-target cast adds one layer today. When the per-hit form lands (the engine's
     * {@code times_from: "hit_count"} already repeats a non-{@code DAMAGE} effect - {@code EffectTimesTest}), this
     * becomes three and the test goes red on purpose.
     */
    @Test
    public void aThreeTargetCastStillAddsOneStack() {
        Fixture f = new Fixture(0, 3);
        f.fireCast(TriggerEvent.BASIC_ATTACK, 3);

        Assertions.assertEquals(1, f.argenti.getBuffManager().stacksOf("升格"),
                "today: one layer for three enemies hit; the sentence wants three");
        Assertions.assertEquals(0.075, f.argenti.getAttribute(AttributeType.CRIT_CHANCE).get(), 1e-9,
                "…and the attribute shows the same single layer");
    }

    // ==================================================================
    // Helpers

    // ==================================================================

    private static final class Fixture {
        private final Character argenti;
        private final List<Enemy> enemies;
        private final Battle battle;

        private Fixture() {
            this(0);
        }

        private Fixture(int eidolon) {
            this(eidolon, 1);
        }

        private Fixture(int eidolon, int enemyCount) {
            argenti = CharacterFactory.create(ARGENTI, LEVEL, true, null, null, eidolon);
            enemies = new ArrayList<>();
            for (int i = 0; i < enemyCount; i++) {
                enemies.add(BattleFixture.monster());
            }
            battle = new Battle(List.of(argenti, CharacterFactory.create(ALLY, LEVEL)), enemies, fixed());
            battle.startBattle();
        }

        private void fireCast(TriggerEvent event, int hits) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(event, argenti, enemies.get(0), hits, 0));
        }

        /**
         * Casts his ultimate through the engine's own path, returning the settled damage of the cast.
         */
        private double castUltimate() {
            List<Double> settled = new ArrayList<>();
            battle.addDamageObserver((Damage damage, double applied, double hpLost, double shield, boolean died) ->
                    settled.add(applied));
            battle.castImmediate(argenti.getSkills().get(SkillType.ULTRA), argenti, new ArrayList<>(enemies));
            battle.processRequests();
            return settled.stream().mapToDouble(Double::doubleValue).sum();
        }

        private double attack() {
            return argenti.getAttribute(AttributeType.ATTACK).get();
        }

        private void argentiTurn() {
            battle.setCurrentMove(battle.queue().snapshot().stream()
                    .filter(signal -> signal.getCanHit() == argenti)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no signal for him")));
            battle.beforeMove();
            battle.afterMove();
        }
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }
}

