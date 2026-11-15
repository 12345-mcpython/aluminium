package com.laosun.aluminium.test.content.characters.mydei1404;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Signal;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.skill.DefaultSkill;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

/**
 * 1404 Mydei (万敌) -- the whole character in one class: the technique's 80%-Max-HP opening, [血仇] entering and the
 * lethal blow that ends it, the state's +50% Max HP, the automatic casts of [弑王成王] (Kingslayer Made King) and
 * [弑神登神] (Godslayer Be God), and Eidolons 2 / 4 / 6.
 *
 * <p><b>The technique.</b> 1404 Mydei, from his own file: the technique's 80%-Max-HP opening, gated on the technique
 * state.
 *
 * <p>His charge uses the resource vocabulary 1003 established, so the file declares it; the census reads that
 * declaration back.
 *
 * <p><b>[血仇], entering and ending.</b> 1404: [血仇] - - entering it, and the lethal blow that ends it.
 *
 * <p>ONE VARIABLE: whether [血仇] is on when the lethal blow lands. His ultimate grants 20 charge, so five of them reach
 * the hundred the entry clause needs -- no test-only shortcut into his resource.
 *
 * <p><b>[血仇]'s Max HP.</b> 1404："[血仇]状态下生命上限提高，数值等同于当前生命上限的 50%".
 *
 * <p>ONE VARIABLE, in one scene: the state goes ON (a hundred charge, five ultimates) and then OFF (the lethal blow the
 * paragraph names as its only exit). Max HP must follow it both ways.
 *
 * <p><b>The skills.</b> 1404 Mydei (万敌): the automatic cast of [弑王成王] (Kingslayer Made King), and its cost.
 *
 * <p>Two readings, kept apart because the earlier attempt failed with two independent causes. One reads the COST, the
 * other reads the SWAP by the slot the skill reports. Note: And the cost reading wounds him first: at full HP "35% of
 * CURRENT" and "35% of MAXIMUM" are the same number, so a reading taken there would pass for either share -- the first
 * version did exactly that.
 *
 * <p><b>The Eidolons.</b> 1404 万敌's Eidolons 2 / 4 / 6: the per-instance defence ignore and crit damage while in
 * 【血仇】, the heal when he is hit, and entering 【血仇】 at battle start.
 *
 * <p><b>Why per-instance modifiers.</b> 【血仇】 is a real state that ENDS (his lethal-blow rule removes it), so a
 * long-lived attribute would have to be taken back at exactly that moment. Written on {@code DEALING_DAMAGE} with
 * {@code instance: true}, the bonus belongs to the hit that is being settled and nothing outlives the state.
 *
 * <p>The two damage cases compare TOTAL damage at the same target with the same seed: a defence ignore and a crit
 * damage raise are both invisible in a panel reading, so they have to show up in the numbers.
 */
public class Mydei1404Test {
    private static final int MYDEI = 1404;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    // 【血仇】 (Vendetta): one id for both forcing the state on and reading it back -- the old files spelled this single
    // string as STATE ("bloodfeud" tests) and BLOODFEUD ("skills" tests); merged into one constant.
    private static final String STATE = "血仇";
    private static final String CHARGE = "天赋充能";

    // ==================================================================
    // The technique: 80% of his Max HP, gated on the technique state.

    /**
     * The technique's opening read both ways: with it declared the enemies are hit, and the number is 80% of his Max HP
     * (1.6 of a hand-built 50% in the same pipeline); undeclared, nothing happens at all.
     */
    @Test
    public void theTechniqueOpensWithEightyPercentOfHisMaxHp() {
        double declared = openingLoss(true);
        double undeclared = openingLoss(false);
        double reference = referenceLoss();

        Assertions.assertAll("the technique's opening: 80% of his Max HP, and only when declared",
                () -> Assertions.assertTrue(declared > 0, "\"after using the Technique ... deals Imaginary damage to all enemies equal to 80% of Mydei (万敌)'s Max HP\" (「使用秘技后…对敌方全体造成等同于万敌80%生命上限的虚数属性伤害」): " + declared),
                () -> Assertions.assertEquals(0.0, undeclared, 1e-9, "「使用秘技后」 (after using the Technique) -- undeclared, so nothing"),
                () -> Assertions.assertTrue(reference > 0, "the reference must deal damage"),
                () -> Assertions.assertEquals(0.8 / 0.5, declared / reference, 0.05,
                        "content " + declared + " vs reference " + reference + " (expected " + (0.8 / 0.5) + ")"));
    }

    /**
     * Note: The file declares the charge resource with the document's cap.
     */
    @Test
    public void theFileDeclaresTheChargeResource() {
        try (InputStream stream = Mydei1404Test.class.getResourceAsStream("/game/characters/1404.json")) {
            Assertions.assertNotNull(stream, "1404 must have a file");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray resources = root.getAsJsonArray("resources");
            Assertions.assertNotNull(resources, "the file must declare its resource");
            Assertions.assertEquals("天赋充能", resources.get(0).getAsJsonObject().get("id").getAsString());
            Assertions.assertEquals(200, resources.get(0).getAsJsonObject().get("max").getAsInt(),
                    "\"accumulates at most 200 points\" (「最多积攒200点」)");
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    // ==================================================================
    // [血仇]: entering it, and the lethal blow that ends it.

    /**
     * A hundred charge enters [血仇], and a lethal blow then leaves it -- at half his Max HP.
     */
    @Test
    public void aLethalBlowEndsItAndHeSurvives() {
        Character him = CharacterFactory.create(MYDEI, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(him),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();

        chargeToAHundred(battle, him);
        Assertions.assertTrue(him.getBuffManager().hasState(STATE),
                "\"when charge reaches 100, consume 100 points of charge to enter the [血仇] (Vendetta) state\"");

        battle.applyTrueDamage(battle.enemies().getFirst(), him, DamageElement.ICE, him.getCurrentHp() * 2.0);
        battle.processRequests();

        Assertions.assertFalse(him.isDeath(), "\"will not enter the unable-to-fight state\"");
        Assertions.assertEquals(him.getMaxHp() * 0.50, him.getCurrentHp(), him.getMaxHp() * 0.01,
                "\"restore HP equal to 50% of one's own Max HP\"");
        Assertions.assertFalse(him.getBuffManager().hasState(STATE), "\"leave the [血仇] (Vendetta) state\"");
        Assertions.assertEquals(0.0, him.getResources().value(CHARGE), 1e-9, "\"clear the charge\"");
    }

    /**
     * Note: Without [血仇] the same blow kills him -- the clause is the state's, not his.
     */
    @Test
    public void withoutBloodfeudTheBlowKills() {
        Character him = CharacterFactory.create(MYDEI, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(him),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Assertions.assertFalse(him.getBuffManager().hasState(STATE), "precondition: no [血仇] (Vendetta) yet");

        battle.applyTrueDamage(battle.enemies().getFirst(), him, DamageElement.ICE, him.getCurrentHp() * 2.0);
        battle.processRequests();
        Assertions.assertTrue(him.isDeath(), "only while in the [血仇] (Vendetta) state does this clause exist");
    }

    // ==================================================================
    // [血仇]'s +50% Max HP: it goes with the state, both ways.

    /**
     * +50% Max HP while [血仇] is on, and back to the base when it leaves.
     */
    @Test
    public void theRaiseFollowsTheState() {
        Character him = CharacterFactory.create(MYDEI, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(him),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Skill ult = him.getSkills().get(SkillType.ULTRA);
        Assertions.assertNotNull(ult, "precondition: he has an ultimate");
        // Note: FOUR ultimates, then the reading, then the fifth: the entry fires at a hundred, so this compares "immediately before
        // the state" with "immediately after" it. Measuring the baseline earlier (right after startBattle) let an unrelated modifier
        // expire in between -- that is exactly the ~5% gap the first version of this judge reported (24.6 expected, 260.9 seen).
        for (int i = 0; i < 4; i++) {
            SkillExecutor.execute(battle, ult, him, List.of(battle.enemies().getFirst()));
            battle.processRequests();
        }
        Assertions.assertFalse(him.getBuffManager().hasState(STATE), "precondition: not a hundred yet");
        double base = him.getMaxHp();
        SkillExecutor.execute(battle, ult, him, List.of(battle.enemies().getFirst()));
        battle.processRequests();
        Assertions.assertTrue(him.getBuffManager().hasState(STATE), "precondition: 【血仇】 (Vendetta) is on");
        Assertions.assertEquals(base * 1.5, him.getMaxHp(), base * 0.01,
                "「【血仇】状态下生命上限提高，数值等同于当前生命上限的 50%」 (while in the Vendetta (【血仇】) state, Max HP is raised by an amount equal to 50% of the current Max HP)");

        // The paragraph's ONLY exit: the lethal blow. The raise must go with the state.
        battle.applyTrueDamage(battle.enemies().getFirst(), him, DamageElement.ICE, him.getCurrentHp() * 2.0);
        battle.processRequests();
        Assertions.assertFalse(him.getBuffManager().hasState(STATE), "precondition: the lethal blow ended it");
        Assertions.assertEquals(base, him.getMaxHp(), base * 0.01,
                "「退出【血仇】状态」 (leaves the Vendetta state)-- the extra Max HP leaves with it");
    }

    // ==================================================================
    // The automatic casts of [弑王成王] and [弑神登神].

    /**
     * "消耗等同于万敌当前生命值 35% 的生命值"-- the cost is paid, on the CURRENT value, at the start of his turn.
     */
    @Test
    public void theTurnStartPaysTheSkillsCost() {
        Character him = CharacterFactory.create(MYDEI, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(him),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        him.getBuffManager().addBuff(new StateBuff(STATE, 9, true));
        battle.processRequests();

        battle.applyTrueDamage(battle.enemies().getFirst(), him, DamageElement.ICE, him.getCurrentHp() * 0.5);
        battle.processRequests();
        Assertions.assertTrue(him.getCurrentHp() < him.getMaxHp(), "precondition: he is wounded");

        double hpBefore = him.getCurrentHp();
        double maxHp = him.getMaxHp();
        double enemyBefore = battle.enemies().getFirst().getCurrentHp();
        spendTurnOf(battle, him);
        double hpAfter = him.getCurrentHp();
        double enemyAfter = battle.enemies().getFirst().getCurrentHp();
        System.out.println("[mydei-skills] cost: own hp " + hpBefore + " -> " + hpAfter + " (max " + maxHp
                + ") ; enemy " + enemyBefore + " -> " + enemyAfter);

        Assertions.assertEquals(hpBefore * 0.65, hpAfter, hpBefore * 1e-6,
                "「消耗等同于万敌当前生命值 35% 的生命值」 (consumes HP equal to 35% of Mydei's current HP)-- the CURRENT value");
        Assertions.assertTrue(hpAfter > hpBefore - 0.35 * maxHp,
                "and NOT a share of the maximum: that would leave " + (hpBefore - 0.35 * maxHp) + ", and he has " + hpAfter);
        Assertions.assertTrue(enemyAfter < enemyBefore, "and the attack lands");

        // Note: This reading covers the COST half. The other half -- swapping slot 9 in so the cast runs [弑王成王] -- does NOT
        // work from inside a rule yet, measured: the swap lands in the map while a `CAST_SKILL` later in the same rule still runs the
        // old row (6.585 against 383.93 for a direct cast of slot 9). Registered in EXPRESSION §3, so the note does not claim it.
    }

    /**
     * Note: The other half on its own: `REPLACE_SKILL` really installs the row the cast then uses.
     */
    @Test
    public void theSwapInstallsTheEnhancedRow() {
        Character him = CharacterFactory.create(MYDEI, 80, false, null, null, 0);
        EffectSpec swap = new EffectSpec();
        TriggerSpecs.set(swap, "op", "REPLACE_SKILL");
        TriggerSpecs.set(swap, "skill", "SKILL");
        TriggerSpecs.set(swap, "skillId", 9);
        TriggerSpecs.set(swap, "permanent", Boolean.TRUE);
        TriggerSpecs.set(swap, "target", "self");
        him.setTriggerTable(new TriggerTable(MYDEI,
                List.of(TriggerSpecs.rule("BATTLE_START", List.of(), swap)), List.of()));
        Battle battle = new Battle(List.of(him),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();

        int slot = him.getSkills().get(SkillType.SKILL).getSkillSlot();
        System.out.println("[mydei-skills] SKILL slot after the swap = " + slot
                + " ; category = " + him.getSkills().get(SkillType.SKILL).getData().getCategory());
        Assertions.assertEquals(9, slot, "the row swapped in is **slot 9** (Kingslayer Be King (【弑王成王】) toughness 60/30 ✓), where it used to be slot 2");
    }

    /**
     * And the swap must REACH the cast -- read as a SAME-LEVEL comparison, which is what the first attempt got wrong.
     *
     * <p>The earlier "proof" compared against a scene whose trigger table had been REPLACED, so `level_convention` never ran there
     * and the two numbers came from different levels. This one installs the same row by hand AFTER the battle has started (so the
     * levels convention has already run), casts it directly, and requires the content's commanded cast to deal the same.
     */
    @Test
    public void theSwapReachesTheCast() {
        Character him = CharacterFactory.create(MYDEI, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(him),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        him.getBuffManager().addBuff(new StateBuff(STATE, 9, true));
        battle.processRequests();
        double before = battle.enemies().getFirst().getCurrentHp();
        spendTurnOf(battle, him);
        double commanded = before - battle.enemies().getFirst().getCurrentHp();

        Character byHand = CharacterFactory.create(MYDEI, 80, false, null, null, 0);
        Battle other = new Battle(List.of(byHand),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        other.startBattle();
        other.processRequests();
        // Note: AFTER startBattle, so the slot carries the level the convention gives it -- the trap the earlier attempt fell into.
        byHand.getSkills().put(SkillType.SKILL, new com.laosun.aluminium.skill.DefaultSkill(
                MYDEI, 9, byHand.getSkills().get(SkillType.SKILL).getLevel()));
        double otherBefore = other.enemies().getFirst().getCurrentHp();
        com.laosun.aluminium.skill.SkillExecutor.execute(other,
                byHand.getSkills().get(SkillType.SKILL), byHand, List.of(other.enemies().getFirst()));
        other.processRequests();
        double manual = otherBefore - other.enemies().getFirst().getCurrentHp();
        System.out.println("[mydei-skills] commanded=" + commanded + " ; slot-9 by hand=" + manual);

        Assertions.assertEquals(manual, commanded, manual * 1e-9,
                "「自动施放【弑王成王】」 (automatically casts Kingslayer Be King)-- the commanded cast runs the row the swap installed, not the slot's original one");
    }

    /**
     * The charge gates while 【血仇】 is already on: a hundred arriving there is NOT drained again, and a hundred and
     * fifty fires [弑神登神] (Godslayer Be God) in slot 11.
     */
    @Test
    public void theChargeThresholdsWhileInBloodfeud() {
        ChargeRead atHundred = fireResourceChange(100);
        ChargeRead atHundredFifty = fireResourceChange(150);

        Assertions.assertAll("the charge gates while in 【血仇】",
                () -> Assertions.assertEquals(100, atHundred.charge(),
                        "「充能达到 100 时消耗 100 点充能进入【血仇】状态」 (when Charge reaches 100, consumes 100 points of Charge to enter the Vendetta state)-- already in it, so nothing is drained again; "
                                + "without this the charge can never reach 150 and the next sentence is unreachable"),
                () -> Assertions.assertEquals(0, atHundredFifty.charge(), "\"consumes 150 points of Charge\" (「消耗 150 点充能」)"),
                () -> Assertions.assertTrue(atHundredFifty.enemyLoss() > 0, "【弑神登神】 (Godslayer Be God) was cast"),
                () -> Assertions.assertEquals(11, atHundredFifty.skillSlot(),
                        "the cast ran slot 11 (槽 11), which is the row the sentence names"));
    }

    // ==================================================================
    // Eidolons 2 / 4 / 6.

    /**
     * Eidolons 2 and 4, the damage halves: 「【血仇】期间，万敌造成的伤害无视敌方目标 15% 的防御力」 and
     * 「【血仇】期间暴击伤害提高 30%」 - both must land as MORE TOTAL DAMAGE on the same seed, in the same 【血仇】 scene.
     */
    @Test
    public void theEidolonDamageBonusesInBloodfeud() {
        double e1 = damageInBloodfeud(1);
        double e2 = damageInBloodfeud(2);
        double e3 = damageInBloodfeud(3);
        double e4 = damageInBloodfeud(4);
        System.out.println("[1404-E2] damage in 血仇: E1=" + e1 + " ; E2=" + e2);
        System.out.println("[1404-E4] critical damage in 血仇: E3=" + e3 + " ; E4=" + e4);

        Assertions.assertAll("Eidolon damage bonuses while in 【血仇】",
                () -> Assertions.assertTrue(e2 > e1,
                        "「无视 15% 的防御力」 must land as more damage: E1=" + e1 + " E2=" + e2),
                () -> Assertions.assertTrue(e4 > e3,
                        "「暴击伤害提高 30%」 must land as more damage: E3=" + e3 + " E4=" + e4));
    }

    /**
     * Eidolon 4, second half: a hit from an enemy heals him for 10% of his maximum HP.
     */
    @Test
    public void theFourthEidolonHealsWhenHeIsHit() {
        Run run = new Run(4);
        run.him.takeDamage(run.him.getAttribute(AttributeType.HEALTH).get() * 0.5);
        run.battle.processRequests();
        double maxHp = run.him.getAttribute(AttributeType.HEALTH).get();
        double before = run.him.getCurrentHp();

        run.battle.fire(TriggerPayload.Simple.of(TriggerEvent.TAKING_HIT, run.enemy, run.him, 100, 0));
        run.battle.processRequests();

        Assertions.assertEquals(0.1 * maxHp, run.him.getCurrentHp() - before, 1e-6,
                "「受到来自敌方目标的攻击后回复等同于自身 10% 生命上限的生命值」");
    }

    /**
     * Eidolon 6: he starts the battle inside 【血仇】, with that state's own +50% maximum HP.
     */
    @Test
    public void theSixthEidolonEntersBloodfeudAtBattleStart() {
        Run e5 = new Run(5);
        Run e6 = new Run(6);
        Assertions.assertFalse(e5.him.getBuffManager().hasState(STATE), "E5 enters it the normal way: at 100 charge");
        Assertions.assertTrue(e6.him.getBuffManager().hasState(STATE), "「进入战斗时立刻进入【血仇】状态」");
        Assertions.assertEquals(1.5 * e5.him.getAttribute(AttributeType.HEALTH).get(),
                e6.him.getAttribute(AttributeType.HEALTH).get(), 1e-6,
                "the state carries its own 「生命上限 +50%」, so entering it at battle start includes it");
    }

    // ==================================================================
    // Helpers.

    /**
     * his ultimate grants 20 charge, so five casts reach a hundred.
     */
    private static void chargeToAHundred(Battle battle, Character him) {
        Skill ult = him.getSkills().get(SkillType.ULTRA);
        Assertions.assertNotNull(ult, "precondition: he has an ultimate");
        for (int i = 0; i < 5; i++) {
            SkillExecutor.execute(battle, ult, him, List.of(battle.enemies().getFirst()));
            battle.processRequests();
        }
    }

    /**
     * Note: Half a turn is `beforeMove()` alone; a full one is both halves (see `ArlanEidolonFourTest`).
     */
    private static void spendTurnOf(Battle battle, Character unit) {
        Signal signal = battle.queue().snapshot().stream()
                .filter(candidate -> candidate.getCanHit() == unit).findFirst()
                .orElseThrow(() -> new AssertionError("precondition: the unit is in the queue"));
        battle.setCurrentMove(signal);
        battle.beforeMove();
        battle.afterMove();
        battle.processRequests();
    }

    /**
     * The 【血仇】 charge scene, with {@code amount} points of charge arriving: the charge that remains, the damage the
     * automatic cast did, and the slot the SKILL reports afterwards.
     */
    private static ChargeRead fireResourceChange(int amount) {
        Character him = CharacterFactory.create(MYDEI, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(him),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        him.getBuffManager().addBuff(new StateBuff(STATE, 9, true));
        battle.processRequests();

        him.getResources().gain(CHARGE, amount);
        battle.noteChangedResource(CHARGE);
        double enemyBefore = battle.enemies().getFirst().getCurrentHp();
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.RESOURCE_CHANGED, him, battle.enemies().getFirst(), 0, amount));
        battle.processRequests();
        int charge = him.getResources().value(CHARGE);
        double enemyAfter = battle.enemies().getFirst().getCurrentHp();
        int slot = him.getSkills().get(SkillType.SKILL).getSkillSlot();
        System.out.println("[mydei-skills] " + amount + " charge while in 【血仇】: charge " + charge
                + " ; enemy " + enemyBefore + " -> " + enemyAfter + " ; SKILL slot now=" + slot);
        return new ChargeRead(charge, enemyBefore - enemyAfter, slot);
    }

    /** What {@link #fireResourceChange(int)} observed. */
    private record ChargeRead(int charge, double enemyLoss, int skillSlot) {
    }

    private static final class Run {
        private final Character him;
        private final Character ally;
        private final Enemy enemy;
        private final Battle battle;

        Run(int eidolon) {
            him = CharacterFactory.create(MYDEI, LEVEL);
            him.setEidolonRank(eidolon);
            // A fixed crit roll would make the numbers comparable; the seed alone is not enough because the two ranks
            // take different paths through the rules.
            ally = CharacterFactory.create(ALLY, LEVEL);
            enemy = BattleFixture.monster();
            battle = new Battle(List.of(him, ally), List.of(enemy), BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
            battle.processRequests();
        }

        /** Puts him in 【血仇】 the way the talent does: 100 points of charge and the change announced. */
        void enterBloodfeud() {
            him.getResources().gain(CHARGE, 100);
            battle.fire(TriggerPayload.ResourceChanged.of(TriggerEvent.RESOURCE_CHANGED, him, CHARGE, 100));
            battle.processRequests();
        }
    }

    /** The Skill's damage against a fresh dummy, inside 【血仇】. */
    private static double damageInBloodfeud(int eidolon) {
        Run run = new Run(eidolon);
        run.enterBloodfeud();
        double before = run.enemy.getCurrentHp();
        run.battle.castImmediate(new DefaultSkill(MYDEI, 2, LEVEL), run.him, List.of(run.enemy));
        run.battle.processRequests();
        return before - run.enemy.getCurrentHp();
    }

    /**
     * Runs the opening with or without the technique marker.
     */
    private static double openingLoss(boolean declared) {
        Character mydei = CharacterFactory.create(MYDEI, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(mydei, ally), List.of(enemy), fixed());
        if (declared) {
            battle.markTechniqueUsed(mydei);
        }
        double before = enemy.getCurrentHp();
        battle.startBattle();
        return before - enemy.getCurrentHp();
    }

    /**
     * A hand-built 50%-Max-HP instance in the same pipeline, as the ratio's denominator.
     */
    private static double referenceLoss() {
        Character mydei = CharacterFactory.create(MYDEI, LEVEL);
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "DAMAGE");
        TriggerSpecs.set(effect, "scale", "owner_max_hp");
        TriggerSpecs.set(effect, "percent", 0.5);
        TriggerSpecs.set(effect, "element", "Imaginary");
        TriggerSpecs.set(effect, "target", "all_enemies");
        mydei.setTriggerTable(new TriggerTable(MYDEI, List.of(TriggerSpecs.rule(
                TriggerEvent.BATTLE_START.name(), List.of("self has_state sys:technique"), effect))));
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(mydei), List.of(enemy), fixed());
        battle.markTechniqueUsed(mydei);
        double before = enemy.getCurrentHp();
        battle.startBattle();
        return before - enemy.getCurrentHp();
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(1.0);
    }
}
