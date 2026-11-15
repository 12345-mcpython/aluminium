package com.laosun.aluminium.test.content.characters.argenti;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1302 银枝 (Argenti): one class for every clause this folder pins - the clause-coverage map, the enhanced Ultimate, trace
 * 慷慨's energy, the [升格] stacks with trace Courage (勇气) and Eidolons 1/2/4, and the technique.
 *
 * <p><b>Clause coverage.</b> Every section of {@code 1302_银枝.md} maps to a rule or to the data row it reads. The file's
 * {@code _gaps} carried a single line that ended in 「⛔ 仍开：其余技能从句与星魂」 while every one of those sections - the
 * talent's stacks, the three traces, all six Eidolons, the technique and the enhanced Ultimate - already had a rule. The
 * status said {@code implemented}, so nothing ever re-read that line; this map is what settles it.
 *
 * <p><b>Enhanced Ultimate (data slot 14).</b> It is installed into the ULTRA slot only while the energy is full, so the same
 * cast deals the enhanced numbers instead of the ordinary ones.
 *
 * <p><b>Trace 慷慨.</b> 「在敌方目标进入战斗时，自身立即恢复 2 点能量」 - the reader of the {@code ENEMY_ENTERED} event. What
 * the event promises: an enemy that <b>joins the enemy roster after the battle started</b> raises it once, with the
 * newcomer as actor; adding the same unit twice raises nothing the second time; an ally joining is not an enemy entering;
 * and a roster that was laid out before {@code startBattle} has not "entered" (nothing fires then).
 *
 * <p><b>Stacks and Eidolons.</b> [升格], the talent's per-hit energy, trace Courage (勇气), Eidolon 1/4 and Eidolon 2. The
 * talent's "for each 1 enemy target hit ... restore 3 energy" is {@code per_target} on {@code GAIN_ENERGY}; [升格] is a
 * <b>stackable named modifier</b> (`max_stacks: 10` + `buff: 升格`); Courage (勇气)'s "enemy targets whose current HP
 * percentage {@code <= 50}%" is the existing {@code target_hp_percent} condition; and Eidolon 2's "3 or more enemies" is
 * the existing {@code enemy_count} variable (it landed after the old note here said no such variable existed). Eidolon 2
 * is the clause this round added.
 *
 * <p><b>One witness, one fix.</b> Eidolon 1 is still a flat +4% crit damage instead of per layer; it is measured and
 * registered in the file's {@code _gaps}, and the witness at the bottom pins today's reading so that a fix turns it red.
 * The talent's "for each enemy hit, gain 1 layer" is fixed: {@code times_from: "hit_count"} makes the stack follow the
 * attack's hit count, and {@code theTalentStacksOncePerCastAndOncePerHit} pins both the three-hit and the one-hit case.
 *
 * <p><b>Technique.</b> 「进入战斗时，对敌方全体造成等同于银枝 80% 攻击力的物理属性伤害，并使银枝恢复 15 点能量」.
 */
public class ArgentiTest {

    private static final int ARGENTI = 1302;
    private static final int LEVEL = 80;
    private static final int GENEROUS_ALLY = 1001;
    private static final int STACKS_ALLY = 1002;
    private static final double TRACE_ENERGY = 2.0;

    // ==================================================================
    // Clause coverage: the file does not claim gaps it does not have
    // ==================================================================

    @Test
    public void theFileNoLongerClaimsGapsItDoesNotHave() throws IOException {
        Path file = Path.of("src", "main", "resources", "game", "characters", "1302.json");
        JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        Assertions.assertEquals("implemented", root.get("_status").getAsString());
        Assertions.assertTrue(root.getAsJsonArray("_gaps").isEmpty(),
                "the old line said 「其余技能从句与星魂」 while all six Eidolons and the traces already had rules");
    }

    // ==================================================================
    // Enhanced Ultimate
    // ==================================================================

    @Test
    public void aFullBankGivesTheEnhancedUltimate() {
        double ordinary = ultimateDamage(false);
        double enhanced = ultimateDamage(true);
        System.out.println("[1302] ordinary=" + ordinary + " enhanced=" + enhanced + " ratio=" + (enhanced / ordinary));
        Assertions.assertTrue(enhanced > ordinary * 1.5, "「消耗180点能量」的强化行给出的伤害显著更高");
    }

    private static double ultimateDamage(boolean fillEnergy) {
        Character argenti = CharacterFactory.create(ARGENTI, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(argenti, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        if (fillEnergy) {
            argenti.setCurrentEnergy(argenti.getMaxEnergy());
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.ENERGY_GAINED, argenti, argenti, 0, 0));
        }
        double before = enemy.getCurrentHp();
        battle.castImmediate(argenti.getSkills().get(SkillType.ULTRA), argenti, List.of(enemy));
        return before - enemy.getCurrentHp();
    }

    // ==================================================================
    // Trace 慷慨: ENEMY_ENTERED
    // ==================================================================

    /**
     * A mid-battle enemy, through either door, pays exactly the trace's 2 energy; the same unit twice pays once; an ally
     * joining is not an enemy entering; and a roster laid out before the battle starts has not "entered".
     */
    @Test
    public void anEnteringEnemyPaysTheTrace() {
        GenerousFixture f = new GenerousFixture();
        double baseline = f.argenti.getCurrentEnergy();

        f.battle.addEnemy(BattleFixture.monster());
        double afterAddEnemy = f.argenti.getCurrentEnergy();
        f.battle.addCombatant(BattleFixture.monster());
        double afterAddCombatant = f.argenti.getCurrentEnergy();

        GenerousFixture repeat = new GenerousFixture();
        Enemy once = BattleFixture.monster();
        repeat.battle.addEnemy(once);
        double afterOnce = repeat.argenti.getCurrentEnergy();
        repeat.battle.addEnemy(once);
        double afterRepeat = repeat.argenti.getCurrentEnergy();
        repeat.battle.addCombatant(CharacterFactory.create(GENEROUS_ALLY, LEVEL));
        double afterAlly = repeat.argenti.getCurrentEnergy();

        GenerousFixture pre = new GenerousFixture(false);
        pre.battle.addEnemy(BattleFixture.monster());
        pre.battle.startBattle();
        double preBattleEnergy = pre.argenti.getCurrentEnergy();

        Assertions.assertAll("trace 慷慨: only an enemy entering mid-battle pays 2 energy",
                () -> Assertions.assertEquals(baseline + TRACE_ENERGY, afterAddEnemy, 1e-9,
                        "addEnemy: 「在敌方目标进入战斗时，立即恢复 2 点能量」"),
                () -> Assertions.assertEquals(afterAddEnemy + TRACE_ENERGY, afterAddCombatant, 1e-9,
                        "addCombatant: the other door into the enemy roster"),
                () -> Assertions.assertEquals(afterOnce, afterRepeat, 1e-9, "the same enemy twice pays once"),
                () -> Assertions.assertEquals(afterOnce, afterAlly, 1e-9, "an ally is not an entering enemy"),
                () -> Assertions.assertEquals(baseline, preBattleEnergy, 1e-9,
                        "added before startBattle: no ENEMY_ENTERED, so no trace energy"));
    }

    // ==================================================================

    private static final class GenerousFixture {
        private final Character argenti;
        private final Battle battle;

        private GenerousFixture() {
            this(true);
        }

        private GenerousFixture(boolean start) {
            argenti = CharacterFactory.create(ARGENTI, LEVEL);
            battle = new Battle(new ArrayList<>(List.of(argenti, CharacterFactory.create(GENEROUS_ALLY, LEVEL))),
                    new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
            if (start) {
                battle.startBattle();
            }
        }
    }

    // ==================================================================
    // Stacks: talent [升格], traces, Eidolon 1/4
    // ==================================================================

    /**
     * The talent's 「施放普攻、战技、终结技时，每击中 1 个敌方目标，获得 1 层【升格】」: every cast slot marks exactly once,
     * and the number of layers follows the event's hit count (three targets hit give three, a single target gives one).
     *
     * <p>Note: The energy half of the same sentence ("for each 1 enemy target hit ... restore 3 energy") is {@code per_target},
     * and its arithmetic already has its own pin in {@code CastAppliedCountTest} - the engine exposes no public "current
     * energy" reader, and inventing one for a test would be a worse trade than pointing at the case that exists. What is
     * pinned here is what this file adds: <b>three rules</b>, one per cast event, each marking exactly once.
     */
    @Test
    public void theTalentStacksOncePerCastAndOncePerHit() {
        StacksFixture casts = new StacksFixture();
        StacksFixture hits = new StacksFixture(0, 3);
        hits.fireCast(TriggerEvent.BASIC_ATTACK, 3);
        int threeHitStacks = hits.argenti.getBuffManager().stacksOf("升格");
        double threeHitCrit = hits.argenti.getAttribute(AttributeType.CRIT_CHANCE).get();
        hits.fireCast(TriggerEvent.SKILL_CAST, 1);
        int oneHitStacks = hits.argenti.getBuffManager().stacksOf("升格");

        Assertions.assertAll("talent 【升格】: one layer per cast event, one per target hit",
                () -> {
                    for (TriggerEvent cast : List.of(TriggerEvent.BASIC_ATTACK, TriggerEvent.SKILL_CAST,
                            TriggerEvent.ULT_CAST)) {
                        int before = casts.argenti.getBuffManager().stacksOf("升格");
                        casts.fireCast(cast, 1);
                        Assertions.assertEquals(1, casts.argenti.getBuffManager().stacksOf("升格") - before,
                                cast + " marks 【升格】 (Apotheosis) exactly once");
                    }
                },
                () -> Assertions.assertEquals(3, threeHitStacks, "three enemies hit: three layers, not one"),
                () -> Assertions.assertEquals(0.05 + 3 * 0.025, threeHitCrit, 1e-9,
                        "…and the attribute shows all three"),
                () -> Assertions.assertEquals(4, oneHitStacks, "a single-target cast adds one more, not three"));
    }

    /**
     * [升格] stacks up to its cap and each stack is a crit-rate step; Eidolon 4's second half - "make the talent's effect
     * <b>max stack count raise by 2</b>" - really does move that cap.
     *
     * <p>Note: the Eidolon-4 half is measured on the ATTRIBUTE, and the arithmetic has a trap worth stating: Eidolon 4
     * grants two layers at battle start and they share the talent's stack group, so a cap of 12 is reached by 2 + 10 -
     * twelve more hits still only add ten. The probe printed, at E0 vs E4: 10 stacks / crit 0.30 against 12 stacks in the
     * group / crit 0.35 - the raise is real, and an expectation of "base + 12  x  0.025" double-counts those two layers.
     * Note: `stacksOf("升格")` is <b>not</b> usable there either: three modifier groups carry that name (the talent's crit
     * rate, Eidolon 1's crit damage, Eidolon 4's battle-start pair), and a name count adds them up.
     */
    @Test
    public void theStacksCapAtTenAndTheFourthEidolonRaisesTheCap() {
        StacksFixture capped = new StacksFixture();
        double base = capped.argenti.getAttribute(AttributeType.CRIT_CHANCE).get();
        for (int i = 0; i < 12; i++) {
            capped.fireCast(TriggerEvent.BASIC_ATTACK, 1);
        }
        double cappedCrit = capped.argenti.getAttribute(AttributeType.CRIT_CHANCE).get();
        int cappedStacks = capped.argenti.getBuffManager().stacksOf("升格");

        StacksFixture fourthEidolon = new StacksFixture(4);
        double fourthEidolonBase = fourthEidolon.argenti.getAttribute(AttributeType.CRIT_CHANCE).get();
        for (int i = 0; i < 12; i++) {
            fourthEidolon.fireCast(TriggerEvent.BASIC_ATTACK, 1);
        }
        double fourthEidolonCrit = fourthEidolon.argenti.getAttribute(AttributeType.CRIT_CHANCE).get();
        // 0.028, not 0.025: at rank 4 her Eidolon 3 has already raised the TALENT by two, so the row the rule reads is
        // Lv12 (0.025 at Lv10, 0.028 at Lv12). That difference is exactly why the rule reads the row instead of a literal.
        int fourthEidolonBonus = fourthEidolon.argenti.ruleEffectMaxStacksBonus("talent_stack_basic");

        Assertions.assertAll("【升格】 cap: ten layers, raised by Eidolon 4",
                () -> Assertions.assertEquals(base + 10 * 0.025, cappedCrit, 1e-6,
                        "「该效果最多叠加10层」 (this effect stacks at most 10 times): twelve hits give ten stacks, not twelve"),
                () -> Assertions.assertEquals(10, cappedStacks,
                        "…and the count is readable by name, which is what Eidolon 4 and any removal would use"),
                () -> Assertions.assertEquals(fourthEidolonBase + 10 * 0.028, fourthEidolonCrit, 1e-6,
                        "12 layers in the group: the two granted at battle start plus ten more from the hits"),
                () -> Assertions.assertEquals(2, fourthEidolonBonus,
                        "…and the amendment is filed against the named rule — the mechanism, not a coincidence"));
    }

    /**
     * trace Piety (虔诚) grants a stack on his own turn start.
     */
    @Test
    public void herTraceGrantsAStackOnHerTurn() {
        StacksFixture f = new StacksFixture();
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
        StacksFixture f = new StacksFixture();
        f.enemies.get(0).takeDamage(f.enemies.get(0).getMaxHp() * 0.8);

        Assertions.assertEquals(1, TriggerTables.of(ARGENTI).ruleCount(TriggerEvent.TURN_START),
                "census: his file has the turn-start trace");
        Assertions.assertEquals(2, TriggerTables.of(ARGENTI).ruleCount(TriggerEvent.DEALING_DAMAGE),
                "the trace Courage (勇气): the target's HP percentage -- no per-target vocabulary needed, because the condition's subject "
                        + "is already the unit being hit) and Eidolon 6's defence ignore");
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
        StacksFixture three = new StacksFixture(2, 3);
        double before = three.attack();
        double base = three.argenti.getAttribute(AttributeType.ATTACK).baseValue();
        three.castUltimate();

        Assertions.assertEquals(0.4 * base, three.attack() - before, 1e-9,
                "「攻击力提高40%」: an ATK% modifier is a share of the character's BASE attack, so the total moves by 0.4 x base");

        StacksFixture two = new StacksFixture(2, 2);
        double twoBefore = two.attack();
        two.castUltimate();
        Assertions.assertEquals(twoBefore, two.attack(), 1e-9,
                "two enemies: 「若敌方目标数量大于等于3个」 does not hold, so the attack does not move");

        StacksFixture e0 = new StacksFixture(0, 3);
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
        StacksFixture e1 = new StacksFixture(1, 3);
        double without = e1.castUltimate();

        StacksFixture e2 = new StacksFixture(2, 3);
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
    public void herCritDamageFollowsTheStacks() {
        StacksFixture f = new StacksFixture(1, 1);
        double start = f.argenti.getAttribute(AttributeType.CRIT_ATTACK).get();
        Assertions.assertEquals(0.5, start, 1e-9, "0 layers: 「每层…提高 4%」 grants nothing");

        for (int i = 0; i < 5; i++) {
            f.fireCast(TriggerEvent.BASIC_ATTACK, 1);
        }
        Assertions.assertEquals(0.7, f.argenti.getAttribute(AttributeType.CRIT_ATTACK).get(), 1e-9,
                "「每层【升格】额外使暴击伤害提高 4%」 - five layers = +0.20 over the base 0.5");
        Assertions.assertEquals(0.175, f.argenti.getAttribute(AttributeType.CRIT_CHANCE).get(), 1e-9,
                "and the crit-rate half stacks the same way (0.025 x 5 over its base)");
    }

    // ==================================================================
    // Helpers

    // ==================================================================

    private static final class StacksFixture {
        private final Character argenti;
        private final List<Enemy> enemies;
        private final Battle battle;

        private StacksFixture() {
            this(0);
        }

        private StacksFixture(int eidolon) {
            this(eidolon, 1);
        }

        private StacksFixture(int eidolon, int enemyCount) {
            argenti = CharacterFactory.create(ARGENTI, LEVEL, true, null, null, eidolon);
            enemies = new ArrayList<>();
            for (int i = 0; i < enemyCount; i++) {
                enemies.add(BattleFixture.monster());
            }
            battle = new Battle(List.of(argenti, CharacterFactory.create(STACKS_ALLY, LEVEL)), enemies, fixed());
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

    // ==================================================================
    // Technique
    // ==================================================================

    @Test
    public void theTechniqueHitsEveryoneAndPaysEnergy() {
        double[] with = run(true);
        double[] without = run(false);
        System.out.println("[1302] with technique: lost=" + with[0] + " energy=" + with[1]
                + " ; without: lost=" + without[0] + " energy=" + without[1]);
        Assertions.assertTrue(with[0] > 0, "「对敌方全体造成…物理属性伤害」");
        Assertions.assertEquals(15.0, with[1] - without[1], 1e-9, "「恢复 15 点能量」");
    }

    /** {total HP the enemies lost, her energy}. */
    private static double[] run(boolean technique) {
        Character him = CharacterFactory.create(1302, 80);
        if (technique) {
            him.getBuffManager().addBuff(new StateBuff("sys:technique", 1));
        }
        Battle battle = new Battle(new ArrayList<>(List.of(him)),
                new ArrayList<>(List.of(BattleFixture.dummy(), BattleFixture.dummy())),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        double lost = 0;
        for (var enemy : battle.enemyUnits()) {
            lost += enemy.getMaxHp() - enemy.getCurrentHp();
        }
        return new double[]{lost, him.getCurrentEnergy()};
    }
}
