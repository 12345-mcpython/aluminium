package com.laosun.aluminium.test.content.characters.aventurinewaveflair;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1513 砂金•戏浪 (Aventurine - Waveflair): everything this character folder asserts, in one class.
 *
 * <p><b>Why one class.</b> This evidence used to live in six files - the 【热意】 resource, the panel-less 【阿哈】
 * summon, the curated Elation skill row, the three 笑点 grants, the clause-coverage audit (星魂 + 行迹), and the
 * talent milestone. Nothing about the evidence changed when they merged: every case, assertion, expectation and
 * message is preserved, and the one doc pointer that named a now-merged class retargets to its method
 * ({@link #theCrossingCastsSpendsAndInstallsTheSwap}). Only the class names and the duplicated constants were
 * unified: 1513 had been spelled {@code WAVEFLAIR}, {@code AVENTURINE} and {@code OWNER}, and the two different
 * allies are now {@code ALLY} (1002) and {@code ELATION_ALLY} (1505).
 *
 * <p><b>Sections.</b> 1. 【热意】; 2. 【阿哈】; 3. the Elation skill row; 4. 笑点; 5. the 行迹/星魂 clauses;
 * 6. the talent milestone. Each section opens with the narrative its own class carried.
 *
 * <p><b>L3.</b> The single-clause cases are folded into the scenario tests below, one {@code Assertions.assertAll} per
 * fixture and per observed object so a failing clause still names itself: the two Elation rows' instance counts, the
 * three 笑点 grants, the two eidolon panels, and the two readings that are NOT the crossing. A case whose fixture is
 * not the same - the Ultimate's own board, the summon's two boards, the Eidolon 2 cap, the shared counter's own
 * sequence - keeps its own test.
 */
public class AventurineWaveflairTest {
    private static final int AVENTURINE = 1513;
    private static final int LEVEL = 80;

    /** The plain ally of the clause-coverage, 笑点 and 【热意】 cases (1002). */
    private static final int ALLY = 1002;

    /** The Elation ally of the milestone cases (1505): their cast must not touch HER counter. */
    private static final int ELATION_ALLY = 1505;

    private static final String FERVOR = "热意";
    private static final String LAUGH = "笑点";
    private static final int THRESHOLD = 10;
    private static final int FIXED_LAUGHS = 20;

    // The milestone fixture, rebuilt by build(...) before each of its readings and fresh for every test instance.
    private Character wolf;
    private Character ally;
    private Enemy enemy;
    private Battle battle;

    // ==================================================================
    // 1. 【热意】 - the one capped resource (formerly AventurineWaveflairTest)
    // ==================================================================
    /*
     * 1513 Aventurine - Waveflair, from his own file: [热意], the one resource in this kit the document caps.
     */

    /**
     * Note: One Fervor per TEAMMATE's attack, and the document's cap of 30 enforced by exceeding it.
     *
     * <p>⚠ The base 【举杯！敬炽烈一夏】 does NOT spend the counter: the spend sentence belongs to the ENHANCED
     * 【All in！敬炽烈一夏】 (its own row, 151321), which is what the Eidolon/normal flow swaps in for the next Elation
     * cast. So a crossing at ten casts and leaves the counter standing, and forty-one single points reach the CAP - the
     * clamp and the accumulation are both true now. The crossing is pinned in {@link #theCrossingCastsSpendsAndInstallsTheSwap}.
     *
     */
    @Test
    public void teammateAttacksFeedFervorUpToThirty() {
        Character aventurine = CharacterFactory.create(AVENTURINE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(aventurine, ally), List.of(enemy), fixed());
        battle.startBattle();

        Assertions.assertEquals(0, fervorOf(aventurine), "no initial value is stated, so it starts at 0");
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0));
        Assertions.assertEquals(1, fervorOf(aventurine),
                "「队友施放攻击后，砂金•戏浪获得1点【热意】」 (after an ally casts an attack, Aventurine Waveflair gains 1 point of [热意])");

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, aventurine, enemy, 0, 0));
        Assertions.assertEquals(1, fervorOf(aventurine),
                "the gate is `actor is_other_ally`: her OWN attack adds nothing");

        for (int i = 0; i < 40; i++) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0));
        }
        Assertions.assertEquals(30, fervorOf(aventurine),
                "forty-one single points reach the declared cap (the base cast does not spend the counter)");
        aventurine.getResources().gain("热意", 41);
        Assertions.assertEquals(30, fervorOf(aventurine),
                "「【热意】上限为30点」 is the DECLARATION's clamp on one gain");
    }

    /**
     * Note: The Ultimate: 8 Fervor and 30% more SPEED for the document's four turns.
     */
    @Test
    public void theUltimateAddsFervorAndSpeed() {
        Character aventurine = CharacterFactory.create(AVENTURINE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(aventurine, ally), List.of(enemy), fixed());
        battle.startBattle();
        double before = aventurine.getAttribute(AttributeType.SPEED).get();
        double base = aventurine.getAttribute(AttributeType.SPEED).baseValue();
        int fervor = fervorOf(aventurine);

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, aventurine, enemy, 0, 0));

        Assertions.assertEquals(fervor + 8, fervorOf(aventurine), "「获得8点【热意】」 (gains 8 points of [热意])");
        Assertions.assertEquals(base * 0.3, aventurine.getAttribute(AttributeType.SPEED).get() - before, base * 0.02,
                "「使自身速度提高30%，持续4回合」 (raises its own SPD by 30% for 4 turns) of the BASE speed: base " + base);
    }

    /**
     * The declared resource's value, read through the combatant's own manager.
     */
    private static int fervorOf(Character unit) {
        return unit.getResources().get("热意").getValue();
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }

    // ==================================================================
    // 2. 【阿哈】 - the panel-less summon (formerly AventurineWaveflairAhaTest)
    // ==================================================================
    /*
     * 1513's 【阿哈】 is a <b>panel-less</b> summon: `SUMMON { memosprite_name: 阿哈, panel_less: true }` places it with HER OWN attributes.
     *
     * <p>This is the third summon model in the engine (monster id / memosprite spec / inherited panel). The document writes 「使阿哈…」
     * exactly like 「召唤忆灵」, so the content states which one it means.
     */

    /**
     * 行迹 纵享惊涛:「队友施放攻击后…使阿哈速度提高 25」 - an ABSOLUTE 25 points, not 25%.
     */
    @Test
    public void aTeammatesAttackQuickensAha() {
        Character waveflair = CharacterFactory.create(AVENTURINE, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Character ally = CharacterFactory.create(1204, LEVEL);
        Battle battle = new Battle(List.of(waveflair, ally), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.processRequests();
        Summon aha = battle.summonsOf(waveflair).stream()
                .filter(summon -> "阿哈".equals(summon.getName()))
                .findFirst()
                .orElseThrow();
        double before = aha.getAttribute(AttributeType.SPEED).get();

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                com.laosun.aluminium.enums.TriggerEvent.ALLY_ATTACK, ally, enemy, 1, 0));
        battle.processRequests();

        Assertions.assertEquals(before + 25, aha.getAttribute(AttributeType.SPEED).get(), 1e-9,
                "「速度提高25」 — twenty-five POINTS, not twenty-five percent");
    }

    @Test
    public void ahaTakesTheFieldWithHerOwnPanel() {
        Character waveflair = CharacterFactory.create(AVENTURINE, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(waveflair), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.processRequests();

        Summon aha = battle.summonsOf(waveflair).stream()
                .filter(summon -> "阿哈".equals(summon.getName()))
                .findFirst()
                .orElse(null);
        Assertions.assertNotNull(aha, "「阿哈」 reached the field through SUMMON { panel_less: true }");
        Assertions.assertEquals(waveflair.getAttribute(AttributeType.SPEED).get(),
                aha.getAttribute(AttributeType.SPEED).get(), 1e-9,
                "…and it inherits her panel instead of needing a spec it never had");
    }

    // ==================================================================
    // 3. The Elation skill row (formerly AventurineWaveflairElationRowTest)
    // ==================================================================
    /*
     * 1513 砂金•戏浪's Elation skill row: 「对敌方全体造成 <b>60%</b>…并<b>额外造成 10 次</b>伤害，每次对随机敌方单体造成 <b>18.00%</b>…」
     *
     * <p><b>Why a curated row.</b> The engine's {@code ElationDamage} branch reads the layout of 8009/8010's rows
     * ({@code [hits, per-hit share, final share]}), while 1513's row is {@code [全体份额, 次数, 每段份额]} - the same KIND of
     * skill with a different column order. Read automatically, the whole cast settled off 0.6 and the ten instances vanished
     * silently. The curated entry names the two parts, so the row is read as the sentence is written.
     *
     * <p>The criterion counts INSTANCES (one area hit plus ten), which is what the sentence states and what a wrong column order
     * cannot fake: the old reading produced exactly one.
     */

    /**
     * 「全体那一段 + 额外 10 次」 = eleven settled instances - the criterion counts INSTANCES (one area hit plus ten),
     * which is what the sentence states and what a wrong column order cannot fake: the old reading produced exactly one.
     *
     * <p>Both rows are read the same way, one cast each through {@code instancesOf(int)}: the base row (20), and the
     * enhanced row (21) whose own columns are 0.6 area + ten 0.18 instances and NOT the 0.21 per-point share.
     */
    @Test
    public void theElationRowsSettleElevenInstances() {
        int baseRow = instancesOf(20);
        int enhancedRow = instancesOf(21);
        Assertions.assertAll("「额外造成 10 次」: the base row and the enhanced row",
                () -> Assertions.assertEquals(11, baseRow, "1 area hit + 10 extra instances (「额外造成 10 次」)"),
                () -> Assertions.assertEquals(11, enhancedRow,
                        "the enhanced row's extra instances are its #3 / #4 columns"));
    }

    // ==================================================================

    /** Counts the damage instances a cast settles, through a scratch rule on DEALING_DAMAGE. */
    private static int instancesOf(int slot) {
        Character aventurine = CharacterFactory.create(AVENTURINE, LEVEL);
        Enemy enemy = BattleFixture.dummy();
        Battle battle = new Battle(new ArrayList<>(List.of(aventurine)), new ArrayList<>(List.of(enemy)),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        aventurine.setTriggerTable(new TriggerTable(AVENTURINE, List.of(countingRule())));
        battle.processRequests();
        battle.castImmediate(new com.laosun.aluminium.skill.DefaultSkill(AVENTURINE, slot, LEVEL), aventurine,
                List.of(enemy));
        battle.processRequests();
        return aventurine.getBuffManager().stacksOf("计数");
    }

    private static TriggerSpec countingRule() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "ADD_STACK");
        TriggerSpecs.set(effect, "buff", "计数");
        TriggerSpecs.set(effect, "maxStacks", 99);
        TriggerSpecs.set(effect, "permanent", true);
        TriggerSpecs.set(effect, "target", "self");
        return TriggerSpecs.rule("DEALING_DAMAGE", List.of(), effect);
    }

    // ==================================================================
    // 4. 笑点 - the three grants (formerly AventurineWaveflairLaughterTest)
    // ==================================================================
    /*
     * 1513: "gain 1/4/6 笑点" - the half each of the three sentences gives.
     *
     * <p>Punchline (笑点) is a PARTY-scoped, uncapped counter (declared by 1502 as `max: 21448364`); these
     * readings are about HER grants, each the sentence's own number. Note: The counter is shared, so the sum test is the point --
     * that is what "party-level" means, and `partyResourceValue` is the accessor `YaoGuangTest` already uses.
     */

    /**
     * "Skill ... gain 4 笑点", "Ultimate ... gain 6 笑点" and "after a teammate casts an attack ... and 1 笑点" - each
     * grant on its own battle of the same {@code fight()} fixture, all read through {@code partyResourceValue(LAUGH)}.
     * The teammate's grant is a REAL attack, not a hand-fired event.
     */
    @Test
    public void theThreeGrantsGiveFourSixAndOne() {
        Scene skill = fight();
        int atStart = skill.battle.partyResourceValue(LAUGH);
        skill.battle.castImmediate(skill.her.getSkills().get(SkillType.SKILL), skill.her, List.of());
        int afterSkill = skill.battle.partyResourceValue(LAUGH);

        Scene ultimate = fight();
        ultimate.battle.castImmediate(ultimate.her.getSkills().get(SkillType.ULTRA), ultimate.her, List.of());
        int afterUltimate = ultimate.battle.partyResourceValue(LAUGH);

        Scene teammate = fight();
        teammate.battle.castImmediate(teammate.mate.getSkills().get(SkillType.COMMON), teammate.mate,
                List.of(teammate.battle.enemies().getFirst()));
        int afterTeammateAttack = teammate.battle.partyResourceValue(LAUGH);

        Assertions.assertAll("「获得 4 / 6 / 1 点笑点」: her Skill, her Ultimate and a teammate's attack",
                () -> Assertions.assertEquals(0, atStart, "the battle starts with none"),
                () -> Assertions.assertEquals(4, afterSkill, "\"gain 4 笑点\" (gain 4 Punchline)"),
                () -> Assertions.assertEquals(6, afterUltimate, "\"gain 6 笑点\" (gain 6 Punchline)"),
                () -> Assertions.assertEquals(1, afterTeammateAttack,
                        "\"and 1 笑点\" (and 1 more Punchline)"));
    }

    /**
     * THE SHARED COUNTER: all three in one battle sum, because Punchline (笑点) is party-scoped.
     */
    @Test
    public void theCounterIsSharedAcrossTheParty() {
        Scene scene = fight();
        scene.battle.castImmediate(scene.her.getSkills().get(SkillType.SKILL), scene.her, List.of());
        scene.battle.castImmediate(scene.her.getSkills().get(SkillType.ULTRA), scene.her, List.of());
        scene.battle.castImmediate(scene.mate.getSkills().get(SkillType.COMMON), scene.mate,
                List.of(scene.battle.enemies().getFirst()));
        Assertions.assertEquals(11, scene.battle.partyResourceValue(LAUGH), "4 + 6 + 1 on the SHARED counter");
    }

    // ==================================================================

    private static final class Scene {
        final Battle battle;
        final Character her;
        final Character mate;

        Scene(Battle battle, Character her, Character mate) {
            this.battle = battle;
            this.her = her;
            this.mate = mate;
        }
    }

    private static Scene fight() {
        Character her = CharacterFactory.create(AVENTURINE, 80, false, null, null, 0);
        Character mate = CharacterFactory.create(ALLY, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(her, mate),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return new Scene(battle, her, mate);
    }

    // ==================================================================
    // 5. 行迹 / 星魂 clause coverage (formerly AventurineWaveflairClauseCoverageTest)
    // ==================================================================
    /*
     * 1513 砂金•戏浪: the four clauses this round could write, measured off the panel.
     *
     * <p>Its {@code _gaps} was a pointer to the ledger, and the ledger said 「未逐句核」 — so nothing pinned these numbers. What
     * stays uncompiled is registered by the mechanism it needs: threshold crossings (星魂 1/2), a cast counter plus a permanent
     * replacement (星魂 6), the two branches of 行迹 纵享惊涛, and the elation skill's 「10 × 18%」 segment (an engine/data gap).
     */

    /**
     * 星魂 1 (first half): 「全属性抗性穿透提高 24%」; 星魂 6 (first half): 「造成的欢愉伤害增笑 25%」 — the amp
     * attribute, not the elation-boost one.
     *
     * <p>One fixture ({@code attributeAt}) and one observable (the attribute a rank adds): the E0 control and the rank's
     * own value, for each of the two eidolon statements.
     */
    @Test
    public void theEidolonPanelsRaiseTheirAttributes() {
        double penetrationAtE0 = attributeAt(0, AttributeType.DAMAGE_PENETRATION, 0);
        double penetrationAtE1 = attributeAt(1, AttributeType.DAMAGE_PENETRATION, 0);
        double elationAmpAtE0 = attributeAt(0, AttributeType.ELATION_DAMAGE_AMP, 0);
        double elationAmpAtE6 = attributeAt(6, AttributeType.ELATION_DAMAGE_AMP, 0);

        Assertions.assertAll("星魂 1 / 星魂 6: E0 is the control, and the rank adds its own attribute",
                () -> Assertions.assertEquals(0.0, penetrationAtE0, 1e-9, "control: nothing at E0"),
                () -> Assertions.assertEquals(0.24, penetrationAtE1, 1e-9, "「全属性抗性穿透提高 24%」"),
                () -> Assertions.assertEquals(0.0, elationAmpAtE0, 1e-9, "control: nothing at E0"),
                () -> Assertions.assertEquals(0.25, elationAmpAtE6, 1e-9, "「欢愉伤害增笑 25%」"));
    }

    /**
     * 星魂 4: 「施放战技时，使我方全体造成的伤害无视敌方目标 18% 的防御力，持续 3 回合」.
     *
     * <p>Measured on a TEAMMATE, because the sentence says 「我方全体」: the ally's {@code DEFENCE_IGNORE} goes 0 → 0.18 after her
     * Skill, and nothing moves at E3.
     */
    @Test
    public void eidolon4GivesTheWholeSideDefenceIgnoreAfterHerSkill() {
        Fixture e4 = new Fixture(4);
        Assertions.assertEquals(0.0, e4.ally.getAttribute(AttributeType.DEFENCE_IGNORE).get(), 1e-9,
                "precondition: nothing before the Skill");
        e4.castSkill();
        Assertions.assertEquals(0.18, e4.ally.getAttribute(AttributeType.DEFENCE_IGNORE).get(), 1e-9,
                "「我方全体…无视敌方目标 18% 的防御力」 — the ally carries it too");

        Fixture e3 = new Fixture(3);
        e3.castSkill();
        Assertions.assertEquals(0.0, e3.ally.getAttribute(AttributeType.DEFENCE_IGNORE).get(), 1e-9,
                "control: it is Eidolon 4");
    }

    /**
     * 行迹 极乐派对: 「速度 ≥140 时，欢愉度提高 30%，之后每超过 1 点速度额外提高 1%，最多计入 200 点」.
     *
     * <p>Measured at three speeds: 130 ⇒ <b>0</b> (the condition is false), 150 ⇒ <b>0.4</b> (0.3 + 10 × 0.01), and 400 ⇒
     * <b>2.3</b> (0.3 + the 200-point cap of 2.0).
     */
    @Test
    public void theJoyfulPartyTraceScalesElationWithExtraSpeed() {
        double below = elationAt(130);
        Assertions.assertEquals(below, elationAt(139), 1e-9, "below 140 the clause does not apply");
        Assertions.assertEquals(below + 0.4, elationAt(150), 1e-9,
                "「之后每超过 1 点速度…额外提高 1%」 — 10 points over");
        Assertions.assertEquals(below + 2.3, elationAt(400), 1e-9,
                "「最多计入 200 点超出的速度」 — the cap is 2.0");
    }

    // ==================================================================

    private static double attributeAt(int eidolon, AttributeType type, int speedBonus) {
        Character owner = CharacterFactory.create(AVENTURINE, 80, false, null, null, eidolon);
        if (speedBonus != 0) {
            owner.getAttribute(AttributeType.SPEED).addBase(speedBonus);
        }
        Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        Battle battle = new Battle(List.of(owner), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return owner.getAttribute(type).get();
    }

    /** Elation boost at a target speed: the base panel is raised to it before the battle starts. */
    private static double elationAt(int speed) {
        Character owner = CharacterFactory.create(AVENTURINE, 80);
        owner.getAttribute(AttributeType.SPEED).addBase(speed - owner.getAttribute(AttributeType.SPEED).get());
        Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        Battle battle = new Battle(List.of(owner), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return owner.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
    }

    private static final class Fixture {
        private final Character owner;
        private final Character ally = CharacterFactory.create(ALLY, 80);
        private final Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        private final Battle battle;

        private Fixture(int eidolon) {
            owner = CharacterFactory.create(AVENTURINE, 80, false, null, null, eidolon);
            battle = new Battle(List.of(owner, ally), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        private void castSkill() {
            battle.castImmediate(owner.getSkills().get(SkillType.SKILL), owner, List.of(enemy));
            battle.processRequests();
        }
    }

    // ==================================================================
    // 6. The talent milestone (formerly AventurineWaveflairMilestoneTest)
    // ==================================================================
    /*
     * 1513 (砂金•戏浪)'s talent milestone: 「【热意】达到10点时，砂金•戏浪会立即施放1次<b>固定计入20笑点</b>的【举杯！敬炽烈一夏】，
     * 并在施放后使自身下一次在阿哈时刻中施放的欢愉技强化为【All in！敬炽烈一夏】」.
     *
     * <p><b>What this replaced.</b> The file shipped the clause as an approximation, with an empty {@code source}: the cast fired
     * on <b>TURN_START</b> with a level test, so 「达到 10 点」 was read as 「回合开始时够 10 点」 - a wrong trigger, which is the
     * legal-but-wrong trap this project refuses. Two neighbours were wrong in the same area: 「每<b>消耗</b>1点【热意】…造成 1 次
     * 21%」 sat on {@code RESOURCE_CHANGED} with {@code times_from: event_amount}, so a <b>gain</b> of four points dealt four
     * hits; and the spend that the same sentence states had no {@code actor == self}, so <b>anybody's</b> Elation cast drained
     * her counter. All three are pinned below, each with the reading that the old shape would have produced.
     *
     * <p><b>The new lifetime.</b> 「下一次在阿哈时刻中施放的欢愉技」 is a slot swap that must end on the <b>Elation</b> cast and
     * on nothing else: {@code next_skill} would end it on a 战技 and {@code cast_end} inside the cast that granted it. So the
     * swap rides {@code until: next_elation_skill} (the engine judge for the lifetime itself is {@code BuffLifetimeTest}).
     */

    /**
     * The crossing casts, spends the whole counter and installs the All-in swap - and the NEXT Elation cast consumes it.
     *
     * <p>The crossing is reached by the talent's own +1 (the ally-attack path), so the damage read here is the auto-cast's
     * alone: a 战技 would deal its own 240% in the same breath.
     */
    @Test
    public void theCrossingCastsSpendsAndInstallsTheSwap() {
        build();
        wolf.getResources().gain(FERVOR, THRESHOLD - 1);
        Assertions.assertEquals(9, fervor(), "precondition: one point short of the threshold");

        int frozen = battle.countedLaughPoints();
        double before = enemy.getCurrentHp();
        alleyAttacks();
        double cast = before - enemy.getCurrentHp();
        System.out.println("[1513] the crossing: counter " + fervor() + ", the commanded cast dealt " + cast
                + " (fixed " + FIXED_LAUGHS + "), the Elation slot now holds " + slotOf(wolf));
        Assertions.assertTrue(cast > 0, "「立即施放1次…欢愉技」: the crossing performs a cast");
        Assertions.assertEquals(THRESHOLD, fervor(),
                "the BASE cast does not spend: 「施放时会消耗所有【热意】」 is the ENHANCED row sentence (151321)");
        Assertions.assertEquals(21, slotOf(wolf),
                "「下一次…强化为【All in！敬炽烈一夏】」 is installed in the Elation slot (data row 21)");
        // ⚠ The fixed 20 is pinned STRUCTURALLY here, and numerically by the ENGINE judge. Measured while writing this:
        // a cross-cast damage ratio cannot pin it in this fixture, because the content's own Aha Moment reward moves the
        // laugh snapshot between the two casts (the crossing's cast read 554.77, her own next one 1829.52) - the ratio
        // would measure that difference, not the field. What this layer can honestly assert is that the rule STATES 20.
        Assertions.assertEquals(FIXED_LAUGHS, fixedLaughsOf(wolf),
                "「固定计入 20 笑点」 rides the commanded cast (its number is pinned by CastCountedLaughsTest)");
        Assertions.assertTrue(frozen >= 0, "the snapshot the other cast folds is a different count: " + frozen);

        double beforeOwn = enemy.getCurrentHp();
        battle.castImmediate(wolf.getSkills().get(SkillType.ELATION_SKILL), wolf, List.of(enemy));
        battle.processRequests();
        System.out.println("[1513] after her own Elation cast the slot holds " + slotOf(wolf) + " and it dealt "
                + (beforeOwn - enemy.getCurrentHp()) + " against the crossing's " + cast);
        Assertions.assertEquals(0, fervor(),
                "the ENHANCED row spends ALL of it (「施放时会消耗所有【热意】」 lives there)");
        Assertions.assertEquals(20, slotOf(wolf),
                "next_elation_skill: the swap ends on the Elation cast it was waiting for");
    }

    /**
     * ⚠ The trigger is the CROSSING, not a turn start and not a bare gain.
     *
     * <p>The shape this replaced would cast at the beginning of her turn whenever the counter happened to be high
     * enough - and would leave the counter standing. And 「每<b>消耗</b>1点…造成 1 次 21%」 is about a CONSUMED point: the
     * shape this replaced hung on RESOURCE_CHANGED with {@code times_from: event_amount}, so the two points gained here
     * would have dealt two hits.
     *
     * <p>Both readings are the same E0 {@code build()} fixture and the same observable (the enemy's HP and the
     * counter): they are the negative half of 「达到 10 点」.
     */
    @Test
    public void onlyTheCrossingTriggersTheAutoCast() {
        build();
        wolf.getResources().gain(FERVOR, 12);
        double beforeTurnStart = enemy.getCurrentHp();

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, wolf, wolf, 0, 0));
        battle.processRequests();

        double turnStartDamage = beforeTurnStart - enemy.getCurrentHp();
        int turnStartFervor = fervor();
        System.out.println("[1513] a turn start with 12 points: damage " + turnStartDamage
                + ", counter " + turnStartFervor);

        build();
        double beforeGain = enemy.getCurrentHp();
        alleyAttacks();
        alleyAttacks();

        double gainDamage = beforeGain - enemy.getCurrentHp();
        int gainFervor = fervor();
        System.out.println("[1513] two gained points below the threshold: damage " + gainDamage
                + ", counter " + gainFervor);

        Assertions.assertAll("「达到 10 点」 is a crossing: neither a turn start nor a gain below it triggers the cast",
                () -> Assertions.assertEquals(0, turnStartDamage, 1e-9,
                        "「达到 10 点」 is a crossing: a turn beginning is not one"),
                () -> Assertions.assertEquals(12, turnStartFervor, "and nothing spent the counter"),
                () -> Assertions.assertEquals(2, gainFervor, "precondition: two points were gained"),
                () -> Assertions.assertEquals(0, gainDamage, 1e-9,
                        "nothing was CONSUMED, so the per-point extra damage must not happen"));
    }

    /**
     * ⚠ The spend belongs to HER cast: somebody else's Elation cast leaves her counter alone.
     *
     * <p>⚠ The counter starts BELOW the threshold on purpose. Measured while writing this: an ally's Elation cast also
     * fires {@code ALLY_ATTACK}, which her talent answers with +1 热意 - so starting at 12 crosses the threshold and her
     * own auto-cast fires and spends it, correctly. Starting at 5 isolates the gate: the ally's cast may add its one
     * point (5 to 6) and must do nothing else. Had the merged rule fired on somebody else's cast, it would have spent
     * all six and dealt six extra hits.
     */
    @Test
    public void someoneElsesElationCastDoesNotSpendHerFervor() {
        build();
        wolf.getResources().gain(FERVOR, 5);
        double beforeEnemy = enemy.getCurrentHp();

        battle.castImmediate(ally.getSkills().get(SkillType.ELATION_SKILL), ally, List.of(enemy));
        battle.processRequests();

        System.out.println("[1513] after the ALLY's Elation cast: her counter " + fervor() + ", the ally's damage "
                + (beforeEnemy - enemy.getCurrentHp()));
        Assertions.assertTrue(beforeEnemy - enemy.getCurrentHp() > 0, "precondition: the ally's cast really settled");
        Assertions.assertEquals(6, fervor(),
                "one point from the ally's attack, and nothing spent: 「施放时会消耗所有【热意】」 is about HER cast");
    }

    /**
     * Eidolon 1: 「【热意】达到10点/20点/30点时，都会立即施放」 - three CROSSINGS of one counter, not three levels.
     *
     * <p>⚠ The negative cases are the point: 21 is not a threshold, and at E0 there is no 20-point tier at all. A shape
     * that read "at or above" would fire on every one of these gains.
     */
    @Test
    public void theFirstEidolonAddsTheTwentyAndThirtyCrossings() {
        build();
        wolf.setEidolonRank(1);
        wolf.getResources().gain(FERVOR, 19);

        double beforeTwenty = enemy.getCurrentHp();
        alleyAttacks();                                  // 19 -> 20: a crossing at E1
        Assertions.assertTrue(beforeTwenty - enemy.getCurrentHp() > 0,
                "「达到 20 点时…立即施放」");
        Assertions.assertEquals(20, fervor(), "the BASE cast does not spend the counter");

        double afterTwenty = enemy.getCurrentHp();
        alleyAttacks();                                  // 20 -> 21: not a threshold
        Assertions.assertEquals(0.0, afterTwenty - enemy.getCurrentHp(), 1e-9,
                "only the crossing pays, not every gain above 20");

        wolf.getResources().gain(FERVOR, 8);             // 21 -> 29
        double beforeThirty = enemy.getCurrentHp();
        alleyAttacks();                                  // 29 -> 30
        Assertions.assertTrue(beforeThirty - enemy.getCurrentHp() > 0, "「达到 30 点时…」");
    }

    /**
     * Eidolon 1's tiers do not exist at E0: 19 -> 20 pays nothing.
     */
    @Test
    public void withoutTheFirstEidolonTwentyIsNotAThreshold() {
        build();
        wolf.getResources().gain(FERVOR, 19);
        double before = enemy.getCurrentHp();
        alleyAttacks();                                  // 19 -> 20 at E0
        Assertions.assertEquals(0.0, before - enemy.getCurrentHp(), 1e-9,
                "E0 states only the 10-point tier");
        Assertions.assertEquals(20, fervor(), "and nothing spent the counter");
    }

    /**
     * Eidolon 2: 「【热意】上限提高至 50 点，达到 40 点/50 点时，也会触发…」.
     *
     * <p>The cap has to move FIRST, or the two thresholds are unreachable: the declaration says 30, so a gain of 41
     * clamps at 30 and the 40-point crossing never happens. That is why this case reads {@code getMax()}, not only the
     * damage.
     *
     * <p>⚠ The counter after a crossing is 44, not 40: Eidolon 2's THIRD sentence pays 「施放欢愉技后额外获得 4 点」 on
     * the auto-cast itself, which is a real Elation cast.
     */
    @Test
    public void theSecondEidolonRaisesTheCapAndAddsTheFortyAndFiftyCrossings() {
        build(2);
        Assertions.assertEquals(50, wolf.getResources().get(FERVOR).getMax(),
                "「【热意】上限提高至 50 点」 - the DECLARED cap, raised at battle start");

        wolf.getResources().gain(FERVOR, 39);
        double beforeForty = enemy.getCurrentHp();
        alleyAttacks();                                  // 39 -> 40: a crossing at E2
        Assertions.assertTrue(beforeForty - enemy.getCurrentHp() > 0, "「达到 40 点时…」");
        Assertions.assertEquals(44, fervor(), "the base cast does not spend, and E2 pays 4 more after it");

        double afterForty = enemy.getCurrentHp();
        alleyAttacks();                                  // 44 -> 45: not a threshold
        Assertions.assertEquals(0.0, afterForty - enemy.getCurrentHp(), 1e-9,
                "only the crossing pays");

        wolf.getResources().gain(FERVOR, 4);              // 45 -> 49
        double beforeFifty = enemy.getCurrentHp();
        alleyAttacks();                                  // 49 -> 50: the raised cap's own threshold
        Assertions.assertTrue(beforeFifty - enemy.getCurrentHp() > 0, "「达到 50 点时…」");
    }

    /**
     * The cap is an EIDOLON's: at E1 the same 41-point gain still clamps at 30, so the 40-point tier is unreachable.
     */
    @Test
    public void withoutTheSecondEidolonTheCapStaysAtThirty() {
        build(1);
        Assertions.assertEquals(30, wolf.getResources().get(FERVOR).getMax(), "the declaration's cap");
        wolf.getResources().gain(FERVOR, 41);
        Assertions.assertEquals(30, fervor(), "「上限为30点」 clamps the gain");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private void build() {
        build(0);
    }

    /**
     * The same fixture with an Eidolon rank set BEFORE the battle starts.
     *
     * <p>⚠ Load-bearing: a BATTLE_START rule reads the rank when the battle starts, so setting it afterwards leaves the
     * cap at the declaration (measured: the E2 cap stayed 30 when the rank was set after startBattle()).
     */
    private void build(int eidolon) {
        wolf = CharacterFactory.create(AVENTURINE, 80);
        wolf.setEidolonRank(eidolon);
        // A crit roll would make every damage ratio below a coin toss, so it is pinned off.
        wolf.setAttribute(com.laosun.aluminium.enums.AttributeType.CRIT_CHANCE,
                new com.laosun.aluminium.DoubleValue(0));
        ally = CharacterFactory.create(ELATION_ALLY, 80);
        enemy = BattleFixture.monster();
        battle = new Battle(List.of(wolf, ally), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
    }

    /**
     * One ally attack, as the event her talent answers: 「队友施放攻击后，砂金•戏浪获得1点【热意】」.
     *
     * <p>⚠ The event is fired directly rather than by an attack, so the reading is the talent's own point and not the
     * ally's swing: what is under test here is the counter and what the crossing does with it.
     */
    private void alleyAttacks() {
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, enemy, 1, 0));
        battle.processRequests();
    }

    private int fervor() {
        return wolf.getResources().value(FERVOR);
    }

    /**
     * The laugh count the shipped rule states on its commanded cast, read off the compiled table.
     */
    private static Integer fixedLaughsOf(Character unit) {
        return unit.getTriggerTable().rulesFor(TriggerEvent.RESOURCE_CHANGED).stream()
                .filter(rule -> rule.id().equals("talent_autocasts_elation"))
                .flatMap(rule -> rule.effects().stream())
                .filter(effect -> "CAST_SKILL".equals(effect.getOp()))
                .map(effect -> effect.getCastCountedLaughs())
                .findFirst().orElse(null);
    }

    /**
     * The data row her Elation slot currently holds: 20 = 【举杯！敬炽烈一夏】, 21 = 【All in！敬炽烈一夏】.
     */
    private static int slotOf(Character unit) {
        return unit.getSkills().get(SkillType.ELATION_SKILL).getSkillSlot();
    }
}
