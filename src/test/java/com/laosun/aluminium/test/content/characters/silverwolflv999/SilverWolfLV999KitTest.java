package com.laosun.aluminium.test.content.characters.silverwolflv999;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Character 1506 (Silver Wolf LV.999): the batch that took her from 2 rules to 15.
 *
 * <p>From her page: 战技 gains 5 笑点; 欢愉技 gains 15 隐藏分; 终结技 enters 【无敌玩家】 and her trace 1506103 adds 20
 * 隐藏分 as she does; her Ultimate also installs the enhanced basic; three enhanced basics end 【无敌玩家】 and leaving it
 * clears 隐藏分; her true-ending trace pays 20 隐藏分 at 20 counted laughs and another 20 at 40.
 *
 * <h2>The clause that WAS open, and what was actually wrong with it</h2>
 *
 * <p><b>the 40% Basic/Skill rider while she holds 【好活当赏】</b> used to read the same damage with and without the state
 * (147.84 either way), and the file recorded it rather than guessing. The rule was never the problem: the fixture
 * <b>synthesized its own DAMAGE instance</b>, so the rule's {@code from_skill_id == 1.0} could not match anything. Driving
 * her own slots through {@code requestSkill} closed it - and the case now reads BOTH slots, because the two rules state the
 * same 40% and reading only one would let the other be deleted without a red.
 *
 * <p><b>The gate itself was the second defect, found by the audit.</b> Both riders asked {@code self has_state 好活当赏}, and
 * <b>nothing in the corpus puts that state on her</b> - the only {@code APPLY_BUFF 好活当赏} in the whole data set is 1513's,
 * and its target is itself. So the two clauses were dead on any real battlefield, while this fixture kept them alive by
 * building the state by hand. They now read the resource the corpus already uses for 「持有【好活当赏】」
 * ({@code self_resource:好活当赏 >= 1}).
 *
 * <p><b>Eidolon 1's 「保留 20% 的【隐藏分】」 WAS open and is now closed</b>, and the reason is worth keeping: the clear and
 * the retention had been written as TWO rules, and `previousCredited` - what the retention's share reads - is per-RULE and
 * starts at 0, so the share was 20% of nothing. They are one rule now, and it reads a fifth of what she actually held.
 *
 * <h2>Her ZONE (结界)</h2>
 *
 * Character 1506 (Silver Wolf LV.999): her ZONE (结界) - both halves, which the registration had called impossible.
 *
 * <p>The Zone is a NAMED STATE marker in this project, not a container: 1203 opens its own with {@code APPLY_BUFF buff: 结界}
 * and reads it with {@code target has_state 结界}, and 1410/1513 do the same. And {@code SKILL_POINT_SPENT} is a real event.
 * So 「【无敌玩家】状态下展开结界」 and 「结界内我方目标每消耗 1 个战技点…触发 1 次【头号补给盲盒】：造成 90% 的虚数属性欢愉伤害」
 * are both writable, and this case pins them.
 *
 * <p>⚠ Moved: these cases used to live in their own class ({@code SilverWolfLV999ZoneTest}); one file now carries her
 * kit, the state it grants and the zone it deploys. No assertion changed in the move.
 *
 * <p><b>L3:</b> the single-clause cases are grouped into the scenarios below, one {@code assertAll} per fixture so a
 * failing clause still names itself: her two slots' resources, the Ultimate's entry (state, allowance, trace pay AND both
 * enhanced slots), the Zone with the spent point it enables, and the shipped declarations with the overflow they promise.
 * The exit, the E1 retention, the control ban, the E2 extension and the no-zone control keep their own tests - they are
 * different fixtures or a refusal, and an {@code assertAll} is not where either belongs.
 *
 */
public class SilverWolfLV999KitTest {
    private static final int WOLF = 1506;
    private static final int ALLY = 1204;
    private static final int LEVEL = 80;
    private static final String LAUGH = "elation:punchline";
    private static final String HIDDEN = "char:1506/hidden_score";
    private static final String CHARGES = "internal:1506/enhanced_basic_count";
    private static final String GOD = "char:1506/invincible_player";
    private static final String FREEZE = "ctrl:frozen";
    private static final String ZONE = "char:1506/barrier";
    /**
     * 【好活当赏】 - a RESOURCE, because that is what her gates ask about ({@code self_resource:好活当赏 >= 1}): her two
     * riders' and the 【头号补给盲盒】's (「「银狼LV.999」持有【好活当赏】时」).
     *
     * <p>⚠ This constant used to be a {@code StateBuff}: the file's gate said {@code self has_state 好活当赏}, so the
     * fixture synthesized the state. Nothing in the whole corpus grants it to her (1513 applies it to itself; the Aha
     * Moment is what grants it, and this engine has no Aha stage), so the rules could never fire and the case could not
     * tell. The audit moved the gate to the resource spelling the corpus already uses (1503's note, 1505's identical
     * clause) and the fixtures now state that premise instead.
     */
    private static final String GIFT = "elation:certified_banger";

    /**
     * Her Skill gains the 5 笑点 her page states, and her Elation skill gains 15 隐藏分: the two resource clauses her two
     * damage slots pay, read on ONE battle - which is the order the second of them already drove them in.
     */
    @Test
    public void herSkillAndHerElationSkillPayTheirResources() {
        Battle battle = newBattle();
        Character wolf = battle.characters().getFirst();
        int laughsBefore = battle.partyResourceValue(LAUGH);
        cast(battle, wolf, SkillType.SKILL, battle.enemyUnits().getFirst());
        int laughsGained = battle.partyResourceValue(LAUGH) - laughsBefore;
        int afterSkill = wolf.getResources().value(HIDDEN);
        fireElationSkill(battle, wolf);
        int afterElation = wolf.getResources().value(HIDDEN);
        int hiddenGained = afterElation - afterSkill;
        System.out.println("[1506] the Skill gained " + laughsGained + " elation:punchline ; char:1506/hidden_score after the Skill " + afterSkill
                + " ; after the Elation skill " + afterElation);
        Assertions.assertAll(
                () -> Assertions.assertEquals(5, laughsGained, "战技: 「获得 5 个笑点」"),
                () -> Assertions.assertEquals(15, hiddenGained,
                        "欢愉技 殿堂级操作回放: 「获得 15 点【char:1506/hidden_score】」"));
    }

    /**
     * Her Ultimate enters 【无敌玩家】, fills the enhanced-basic allowance, pays the trace's 20 - and installs the enhanced
     * Elation skill (slot 21) as well as the enhanced basic.
     *
     * <p>All of it hangs on the same cast, so it is one scenario: the Elation slot is read before and after the same
     * Ultimate whose state, allowance and trace payment it shares the fixture with.
     */
    @Test
    public void theUltimateEntersGodmodeAndInstallsTheEnhancedSlots() {
        Battle battle = newBattle();
        Character wolf = battle.characters().getFirst();
        int elationSlotBefore = wolf.getSkills().get(SkillType.ELATION_SKILL).getSkillSlot();
        int before = wolf.getResources().value(HIDDEN);
        castUltimate(battle, wolf);
        boolean inGodmode = wolf.getBuffManager().hasState(GOD);
        int charges = wolf.getResources().value(CHARGES);
        int hiddenAfter = wolf.getResources().value(HIDDEN);
        int elationSlotAfter = wolf.getSkills().get(SkillType.ELATION_SKILL).getSkillSlot();
        System.out.println("[1506] after the Ultimate: state=" + inGodmode
                + " charges=" + charges
                + " char:1506/hidden_score " + before + " -> " + hiddenAfter
                + " ; her Elation skill's slot " + elationSlotBefore + " -> " + elationSlotAfter);
        Assertions.assertAll(
                () -> Assertions.assertTrue(inGodmode, "终结技: 「进入【char:1506/invincible_player】状态」"),
                () -> Assertions.assertEquals(3, charges,
                        "天赋: 「完整施放 3 次强化普攻后退出」 - so the allowance starts full"),
                () -> Assertions.assertEquals(20, hiddenAfter - before,
                        "行迹 隐藏关卡全成就: 「进入【char:1506/invincible_player】状态后，获得 20 点【char:1506/hidden_score】」"),
                () -> Assertions.assertEquals(20, elationSlotBefore,
                        "premise: her Elation skill is slot 20 before the Ultimate"),
                () -> Assertions.assertEquals(21, elationSlotAfter,
                        "【char:1506/invincible_player】状态下拥有强化欢愉技 - 崩坏级伤害演示 is slot 21"));
    }

    /**
     * Three enhanced basics spend the allowance and end the state, and leaving it clears 隐藏分.
     */
    @Test
    public void threeEnhancedBasicsEndGodmodeAndClearHidden() {
        Battle battle = newBattle();
        Character wolf = battle.characters().getFirst();
        castUltimate(battle, wolf);
        wolf.getResources().gain(HIDDEN, 30);
        int beforeExit = wolf.getResources().value(HIDDEN);

        for (int use = 1; use <= 3; use++) {
            cast(battle, wolf, SkillType.COMMON, battle.enemyUnits().getFirst());
            System.out.println("[1506] after enhanced basic " + use + ": slot="
                    + wolf.getSkills().get(SkillType.COMMON).getSkillSlot()
                    + " charges=" + wolf.getResources().value(CHARGES)
                    + " state=" + wolf.getBuffManager().hasState(GOD));
        }
        System.out.println("[1506] char:1506/hidden_score " + beforeExit + " -> " + wolf.getResources().value(HIDDEN));
        Assertions.assertFalse(wolf.getBuffManager().hasState(GOD),
                "天赋: 「完整施放 3 次强化普攻后退出【char:1506/invincible_player】状态」");
        Assertions.assertEquals(0, wolf.getResources().value(HIDDEN),
                "天赋: 「退出【char:1506/invincible_player】状态时【char:1506/hidden_score】会被清空」");
    }

    /**
     * Eidolon 1 keeps a fifth of her 隐藏分 when 【无敌玩家】 ends, instead of losing all of it.
     *
     * <p>「退出【无敌玩家】状态时不会清空【隐藏分】，改为保留 20% 的【隐藏分】。」 The clear and the retention live in ONE rule,
     * because `previousCredited` - what the retention's share reads - is per-RULE and starts at 0: as two rules the share
     * was 20% of nothing. That is what this case pins.
     */
    @Test
    public void eidolonOneKeepsAFifthOfTheHidden() {
        int kept = hiddenAfterLeavingGodmode(1);
        int lost = hiddenAfterLeavingGodmode(0);
        System.out.println("[1506] char:1506/hidden_score after leaving 【char:1506/invincible_player】: E1 keeps " + kept + " ; E0 keeps " + lost
                + " (a fifth of the 80 she held is 16)");
        // Note: She holds 80 at the exit, not 60 - the Ultimate and its trace pay 20 before the fixture's own 60 - and the
        // rule keeps a fifth of THAT. The first version of this case expected a fifth of 60 and read the rule as wrong.
        Assertions.assertEquals(16, kept, 1e-9,
                "星魂 1: 「改为保留 20% 的【char:1506/hidden_score】」 - a fifth of the 80 she held at the exit");
        Assertions.assertEquals(0, lost, 1e-9, "and at E0 the base clause clears it as the document says");
    }

    /**
     * Her true-ending trace pays 20 at 20 counted laughs and another 20 at 40.
     */
    @Test
    public void theTrueEndingTracePaysAtTwentyAndAtForty() {
        int below = hiddenFromElationCastAt(19);
        int at20 = hiddenFromElationCastAt(20);
        int at39 = hiddenFromElationCastAt(39);
        int at40 = hiddenFromElationCastAt(40);
        System.out.println("[1506] char:1506/hidden_score from one Elation cast at 19/20/39/40 counted laughs: "
                + below + " / " + at20 + " / " + at39 + " / " + at40);
        Assertions.assertEquals(15, below, "under 20 counted laughs the trace pays nothing");
        Assertions.assertEquals(35, at20, "「大于等于 20 个，额外获得 20 点」 -> 15 + 20");
        Assertions.assertEquals(35, at39, "and 39 is still only the first threshold");
        Assertions.assertEquals(55, at40, "「若大于等于 40 个，再额外获得 20 点」 -> 15 + 20 + 20");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * 「持有【好活当赏】时，施放普攻、战技对受到攻击的敌方目标造成 40% 的虚数属性欢愉伤害」 - read on BOTH slots.
     *
     * <p>⚠ This is the case the file once recorded as OPEN. Every earlier attempt synthesized a {@code DAMAGE} instance and
     * fired {@code DEALING_DAMAGE} by hand, so the rule's {@code from_skill_id == 1.0} never matched and the reading came
     * out identical with and without the state. Driving her own slot through {@code requestSkill} is what the fixture was
     * missing; the rules themselves were never touched.
     *
     * <p>And both slots are read, because the two clauses state the same 40%: a case that reads only one of them cannot
     * tell "the other rule is missing" from "the other rule is there".
     */
    @Test
    public void theBangerRidersPayOnBothSlots() {
        assertRider(SkillType.COMMON, "普攻");
        assertRider(SkillType.SKILL, "战技");
    }

    /**
     * 「【无敌玩家】状态下，「银狼LV.999」免疫控制类负面状态」 - and the immunity leaves with the state.
     *
     * <p>⚠ The three phases are one battle on purpose: a fixture that only showed "the control does not land" would pass just
     * as well if the control could never land at all (the fixture monster is immune to freeze, which is why the injector is a
     * party member with a hand-built table). Phase 1 states the premise - a 100% base chance control lands on her while no
     * state protects her - phase 2 is the clause, and phase 3 is its lifetime.
     */
    @Test
    public void godmodeIsImmuneToControlAndTheImmunityLeavesWithIt() {
        Character wolf = CharacterFactory.create(WOLF, 80);
        Character injector = Character.fromAttributes("injector", 10_000, 100, 100, 100);
        injector.setTriggerTable(new TriggerTable(9999, List.of(TriggerSpecs.rule(
                TriggerEvent.TURN_START.name(), List.of(), control()))));
        Battle battle = new Battle(List.of(wolf, injector), List.of(BattleFixture.monster()),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.processRequests();

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, injector, wolf, 0, 0));
        Assertions.assertTrue(wolf.getBuffManager().hasState(FREEZE),
                "premise: a 100% base chance control lands on her when nothing protects her");
        wolf.getBuffManager().removeState(FREEZE);

        castUltimate(battle, wolf);
        Assertions.assertTrue(wolf.getBuffManager().hasState(GOD), "premise: the Ultimate puts her in 【char:1506/invincible_player】");
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, injector, wolf, 0, 0));
        System.out.println("[1506] in 【char:1506/invincible_player】, after the same control: ctrl:frozen="
                + wolf.getBuffManager().hasState(FREEZE));
        Assertions.assertFalse(wolf.getBuffManager().hasState(FREEZE),
                "「【char:1506/invincible_player】状态下，「银狼LV.999」免疫控制类负面状态」");

        for (int use = 1; use <= 3; use++) {
            cast(battle, wolf, SkillType.COMMON, battle.enemyUnits().getFirst());
        }
        Assertions.assertFalse(wolf.getBuffManager().hasState(GOD), "premise: three enhanced basics end the state");
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, injector, wolf, 0, 0));
        System.out.println("[1506] after leaving 【char:1506/invincible_player】, the same control: ctrl:frozen="
                + wolf.getBuffManager().hasState(FREEZE));
        Assertions.assertTrue(wolf.getBuffManager().hasState(FREEZE),
                "「【char:1506/invincible_player】状态下…免疫」 is scoped by the state, so the control lands again once it is gone");
    }

    /**
     * 「结界中的敌方目标受到的伤害提高 20%」 (星魂 1) - read as a RATIO between two fixtures that differ only in the rank.
     */
    @Test
    public void eidolonOneRaisesTheDamageEnemiesTakeInsideTheZone() {
        double atZero = damageInsideTheZone(0);
        double atOne = damageInsideTheZone(1);
        System.out.println("[1506] damage the same attack deals inside the zone: E0 " + atZero + " -> E1 " + atOne
                + " (x" + (atOne / atZero) + ")");
        Assertions.assertTrue(atZero > 0, "premise: the attack really settles damage");
        Assertions.assertEquals(1.2, atOne / atZero, 1e-9,
                "「结界中的敌方目标受到的伤害提高 20%」 - and only once, though two rules apply it");
    }

    /**
     * 「【无敌玩家】状态下…无法施放终结技」 - asserted both ways, because "the cast failed" alone would pass just as
     * well if the bar had been empty.
     */
    @Test
    public void godmodeBansTheUltimateAndTheBanLeavesWithIt() {
        Battle battle = newBattle();
        Character wolf = battle.characters().getFirst();
        Assertions.assertFalse(wolf.getBuffManager().hasState(GOD), "precondition: not in 【char:1506/invincible_player】 yet");

        castUltimate(battle, wolf);
        Assertions.assertTrue(wolf.getBuffManager().hasState(GOD), "precondition: the Ultimate put her in 【char:1506/invincible_player】");

        wolf.setCurrentEnergy(wolf.getMaxEnergy());
        Assertions.assertFalse(battle.castUltra(wolf, List.of(battle.enemyUnits().getFirst())),
                "「无法施放终结技」 - the bar is full and the gate still refuses");
        battle.processRequests();

        wolf.getBuffManager().removeState(GOD);
        wolf.setCurrentEnergy(wolf.getMaxEnergy());
        Assertions.assertTrue(battle.castUltra(wolf, List.of(battle.enemyUnits().getFirst())),
                "control: with the state removed the same cast is allowed");
    }

    /**
     * One slot's rider - the talent row's share of her ATTACK, against an expectation built from the engine's own factors.
     *
     * <p>⚠ Why the exact arithmetic rather than "it went up": a case that only compares the two runs leaves a mutation of the
     * rider's share GREEN, because both readings move together. The identity used here has no free parameter - the rider is
     * {@code (the talent row's #3) x ATTACK} folded with 欢愉度, 增笑 and the laugh snapshot, then settled through the SAME
     * zones as the attack that carries it, so {@code delta / attackDamage} is {@code (share / the row) x those factors}
     * exactly. ⚠ The share is read from the SAME row the rule reads on purpose: a rule that went back to a literal (0.4, the
     * document's Lv10 display) then disagrees with this expectation by 25%, which is the red this case owes.
     */
    private static void assertRider(SkillType slot, String label) {
        double without = attackDamage(newBattle(), false, slot);
        Battle held = newBattle();
        double with = attackDamage(held, true, slot);
        Character wolf = held.characters().getFirst();
        double boosts = (1 + wolf.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get())
                * (1 + wolf.getAttribute(AttributeType.ELATION_DAMAGE_AMP).get());
        double laughs = held.elationLaughMultiplier();
        var skill = wolf.getSkills().get(slot);
        double row = skill.getData().getSkills().get(wolf.skillLevel(skill) - 1).getFirst();
        // The rider's share is no longer a literal: it is item #3 of HER TALENT row, read at the level `level_convention`
        // raises the talent to. The document's prose prints 40% because the page generator substitutes the Lv10 display row
        // (export_battle.py: `min(10, rows)` for everything that is not a Normal attack) - her talent is a 15-row table.
        var talent = wolf.getSkills().get(SkillType.TALENT);
        int talentLevel = wolf.skillLevel(talent);
        double riderShare = talent.getData().getSkills().get(talentLevel - 1).get(2);
        double expected = (riderShare / row) * without * boosts * laughs;
        Assertions.assertEquals(15, talentLevel, "她的天赋被等级口径抬到 15 行表的最后一行");
        Assertions.assertEquals(0.5, riderShare, 1e-9,
                "天赋满级行的 #3 = 0.5（文档正文印的 40% 是它的 Lv10 显示行）");
        System.out.println("[1506] " + label + ": " + without + " -> " + with + " ; delta " + (with - without)
                + " ; expected the talent row's " + riderShare + " of the same attack (row " + row + ", boosts " + boosts
                + ", laughs x" + laughs + ") = " + expected);
        Assertions.assertTrue(with > without, label + " pays the rider while she holds 【elation:certified_banger】");
        Assertions.assertEquals(expected, with - without, 1e-6,
                label + " pays exactly the talent row's share of her ATTACK folded by elation:elation_degree/增笑/计入的笑点, settled "
                        + "through the same zones");
    }

    /**
     * The settled damage of one 普攻/战技 against the single enemy, with or without 【好活当赏】.
     *
     * <p>The resource is granted the way the clause asks about it ({@code self_resource:好活当赏 >= 1}), and the counted laughs
     * are frozen the way the engine freezes them when the gift arrives - without it the Elation factor would be 1 and the
     * rider would be measured under the wrong multiplier.
     */
    private static double attackDamage(Battle battle, boolean holding, SkillType slot) {
        Character wolf = battle.characters().getFirst();
        if (holding) {
            battle.partyResource(LAUGH).gain(20);
            wolf.getResources().gain(GIFT, 1);
            battle.noteElationGift(wolf, 2);
        }
        Enemy victim = battle.enemyUnits().getFirst();
        double before = victim.getCurrentHp();
        cast(battle, wolf, slot, victim);
        return before - victim.getCurrentHp();
    }

    /**
     * Her 隐藏分 after 【无敌玩家】 ends, at the given Eidolon rank: 60 held going in, so a fifth is 12.
     *
     * <p>The 60 is reached through the Ultimate, the trace's 20 and a plain gain of 40, so the reading is about the exit
     * rather than about how the counter was built.
     */
    private static int hiddenAfterLeavingGodmode(int eidolonRank) {
        Battle battle = newBattle(eidolonRank);
        Character wolf = battle.characters().getFirst();
        castUltimate(battle, wolf);
        wolf.getResources().gain(HIDDEN, 60);
        int atExit = wolf.getResources().value(HIDDEN);
        for (int use = 1; use <= 3; use++) {
            cast(battle, wolf, SkillType.COMMON, battle.enemyUnits().getFirst());
        }
        System.out.println("[1506] E" + eidolonRank + ": char:1506/hidden_score " + atExit + " -> "
                + wolf.getResources().value(HIDDEN));
        return wolf.getResources().value(HIDDEN);
    }

    /**
     * 隐藏分 gained by one Elation skill cast, with the party's counted laughs set to the given figure.
     *
     * <p>The counted figure is the merged snapshot of 【好活当赏】, so it is built by granting the state while the party
     * pool holds that many laughs - the same route the engine takes.
     */
    private static int hiddenFromElationCastAt(int countedLaughs) {
        Battle battle = newBattle();
        Character wolf = battle.characters().getFirst();
        battle.partyResource(LAUGH).gain(countedLaughs);
        battle.noteElationGift(wolf, 3);
        int before = wolf.getResources().value(HIDDEN);
        fireElationSkill(battle, wolf);
        return wolf.getResources().value(HIDDEN) - before;
    }

    /**
     * Fires the Elation skill's cast event, which is what her trace and her slot-20 rule both hang on.
     */
    private static void fireElationSkill(Battle battle, Character wolf) {
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Cast.of(TriggerEvent.CAST_SETUP, wolf, battle.enemyUnits().getFirst(), 0, 0,
                com.laosun.aluminium.enums.SkillCategory.ELATION_DAMAGE, 20, 0, java.util.List.of()));
        battle.processRequests();
    }

    /**
     * Casts a slot through the engine's own request path and settles the action.
     */
    private static void cast(Battle battle, Character unit, SkillType slot, Enemy victim) {
        battle.requestSkill(unit.getSkills().get(slot), unit, List.of(victim));
        battle.processRequests();
        // Note: Driven here because the fixture casts directly rather than through a move, so the engine's own
        // end-of-attack announcement never runs. The rule that ends 【无敌玩家】 hangs on this event.
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ATTACK_FINISHED, unit, victim, 1, 0));
        battle.processRequests();
    }

    private static void castUltimate(Battle battle, Character wolf) {
        wolf.setCurrentEnergy(wolf.getMaxEnergy());
        battle.castUltra(wolf, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
    }

    /**
     * Eidolon 2's first half: 「进入【无敌玩家】状态后，使自身所有增益效果延长 1 回合」.
     *
     * <p>⚠ <b>Read structurally, and the reason is measured.</b> The behavioural version - put a ONE-turn buff on her, cast
     * the Ultimate, then advance a turn and see whether it survived - does not work in this fixture: firing TURN_START (and
     * TURN_END) by hand does NOT drive the buff clock, so the 1-turn buff was still there in the E0 CONTROL as well, i.e.
     * nothing ticked. Advancing a real turn means running the queue, which is a different fixture; what this case can
     * honestly pin instead is the shipped spelling, which is the whole clause: every buff, one turn, on herself, at E2, on
     * the same gate as entering 【无敌玩家】.
     */
    @Test
    public void eidolonTwoExtendsHerBuffsOnEnteringGodmode() {
        var table = CharacterFactory.create(WOLF, 80, false, null, null, 2).getTriggerTable();
        var rule = table.rulesFor(TriggerEvent.CAST_SETUP).stream()
                .filter(r -> r.id().equals("p1506_e2_extends_her_buffs_on_entering_godmode"))
                .findFirst().orElseThrow(() -> new AssertionError("E2's extension rule is not on her table"));
        var conditions = rule.conditions().stream().map(c -> c.source()).toList();
        var effect = rule.effects().getFirst();
        System.out.println("[1506] E2 extension: when=" + conditions + " op=" + effect.getOp() + " kind="
                + effect.getKind() + " turns=" + effect.getTurns() + " target=" + effect.getTarget());
        Assertions.assertEquals("EXTEND_BUFF", effect.getOp(), "「使自身所有增益效果延长 1 回合」 is the shipped op");
        Assertions.assertEquals("all", effect.getKind(), "「所有增益效果」 - not one named state");
        Assertions.assertEquals(1, effect.getTurns(), "延长 1 回合");
        Assertions.assertEquals("self", effect.getTarget(), "自身");
        Assertions.assertTrue(conditions.contains("from_category ULTRA") && conditions.contains("actor == self"),
                "and it hangs on the same entry as 【char:1506/invincible_player】 itself, which is the order 「进入…后」 needs");
        // ⚠ The Eidolon gate is a FIELD on the compiled rule, not a loading filter: the same table serves every rank and the
        // interpreter decides at firing time (measured - the first version looked the rule up at E0 and still found it).
        Assertions.assertEquals(2, rule.minEidolon(), "「星魂 2」 gates the clause itself, not the base kit");
    }

    /**
     * A 100% base chance control aimed at the event's target - the fixture monster is immune to freeze.
     */
    private static EffectSpec control() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "APPLY_CONTROL");
        TriggerSpecs.set(effect, "control", FREEZE);
        TriggerSpecs.set(effect, "turns", 1);
        TriggerSpecs.set(effect, "baseChance", 1.0);
        TriggerSpecs.set(effect, "target", "target");
        return effect;
    }

    /**
     * One ordinary attack's damage on the enemy, in a battle whose Zone is already up at the given Eidolon rank.
     */
    private static double damageInsideTheZone(int eidolonRank) {
        Battle battle = newBattleNoCrit(eidolonRank);
        Character wolf = battle.characters().getFirst();
        castUltimate(battle, wolf);
        Assertions.assertTrue(wolf.getBuffManager().hasState(ZONE), "premise: the Ultimate deployed the Zone");
        Enemy victim = battle.enemyUnits().getFirst();
        double hp = victim.getCurrentHp();
        double before = hp;
        cast(battle, wolf, SkillType.SKILL, victim);
        double dealt = before - victim.getCurrentHp();
        Assertions.assertTrue(dealt < hp, "premise: the attack must not kill it, or the HP cap hides the ratio (dealt "
                + dealt + " of " + hp + ")");
        return dealt;
    }

    private static Battle newBattleNoCrit(int eidolonRank) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(WOLF, 80, false, null, null, eidolonRank),
                        CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster()), BattleFixture.alwaysRoll(0.9999));
        battle.startBattle();
        battle.processRequests();
        return battle;
    }

    private static Battle newBattle() {
        return newBattle(0);
    }

    private static Battle newBattle(int eidolonRank) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(WOLF, 80, false, null, null, eidolonRank),
                        CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return battle;
    }

    // ==================================================================
    // Zone (结界) - the cases that were SilverWolfLV999ZoneTest
    // ==================================================================

    /**
     * Her Ultimate deploys the Zone on HERSELF, and a Skill Point spent inside it fires 【头号补给盲盒】 at the enemies.
     *
     * <p>Both halves are the same battle and the same cast - the Zone the Ultimate deploys, then the spent point that
     * needs it - so they are one scenario, read on the enemy side's HP.
     */
    @Test
    public void theUltimateDeploysTheZoneAndASpentPointFiresTheBox() {
        Battle battle = newPromotedBattle();
        Character wolf = battle.characters().getFirst();
        Character ally = battle.characters().get(1);
        boolean zoneBefore = wolf.getBuffManager().hasState(ZONE);

        castUltimateHoldingGift(battle, wolf);
        boolean zoneOnHer = wolf.getBuffManager().hasState(ZONE);
        boolean zoneOnAlly = ally.getBuffManager().hasState(ZONE);
        System.out.println("[1506] after the Ultimate: 结界=" + zoneOnHer
                + " char:1506/invincible_player=" + wolf.getBuffManager().hasState(GOD)
                + " ; the ally holds 结界=" + zoneOnAlly);

        double before = totalEnemyHp(battle);
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_POINT_SPENT, wolf, wolf, 0, 1));
        battle.processRequests();
        double after = totalEnemyHp(battle);
        System.out.println("[1506] enemy HP before/after one spent Skill Point: " + before + " -> " + after
                + " (dealt " + (before - after) + ")");
        Assertions.assertAll(
                () -> Assertions.assertFalse(zoneBefore, "precondition: no Zone before the Ultimate"),
                () -> Assertions.assertTrue(zoneOnHer, "终结技: 「展开结界」"),
                () -> Assertions.assertFalse(zoneOnAlly, "and it is HERS - the marker is on the caster"),
                () -> Assertions.assertTrue(before - after > 0,
                        "结界内每消耗 1 个战技点触发一次盲盒，造成 90% 虚数欢愉伤害"));
    }

    /**
     * Without the state there is no Zone, so a spent Skill Point is just a spent Skill Point.
     */
    @Test
    public void withoutTheZoneASpentPointDoesNothing() {
        Battle battle = newPromotedBattle();
        Character wolf = battle.characters().getFirst();
        double before = totalEnemyHp(battle);
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_POINT_SPENT, wolf, wolf, 0, 1));
        battle.processRequests();
        double after = totalEnemyHp(battle);
        System.out.println("[1506] with no Ultimate: enemy HP " + before + " -> " + after);
        Assertions.assertEquals(before, after, 1e-9,
                "the Zone and 【char:1506/invincible_player】 are both required, so nothing fires without them");
    }

    /**
     * 「达到 60 点后可激活终结技，达到上限后还可溢出 240 点」: the shipped declarations carry those numbers, a plain gain
     * may sit above the normal cap, a clamped gain stops at it, and her Ultimate's advance is one 100% rule.
     *
     * <p>One fixture (her compiled character in a started battle) and one subject (what the compiled table declares), so
     * the declarations and the behaviour they promise are one scenario. The resource is moved only AFTER the declarations
     * are read, which is why the same battle can serve both halves.
     */
    @Test
    public void theShippedDeclarationsCarryTheirNumbers() {
        Character wolf = CharacterFactory.create(WOLF, LEVEL);
        Battle battle = new Battle(List.of(wolf, CharacterFactory.create(ALLY, LEVEL)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        var spec = wolf.getTriggerTable().resources().stream()
                .filter(resource -> resource.id().equals(HIDDEN)).findFirst().orElseThrow();
        var rules = wolf.getTriggerTable().rulesFor(TriggerEvent.CAST_SETUP).stream()
                .filter(rule -> rule.id().equals("p1506_ult_advance")).toList();
        Assertions.assertEquals(1, rules.size(), "her Ultimate's advance is one rule, named p1506_ult_advance");
        var effect = rules.getFirst().effects().getFirst();
        int overflowGain = wolf.getResources().gain(HIDDEN, 100);
        int afterOverflow = wolf.getResources().value(HIDDEN);
        int clampedGain = wolf.getResources().get(HIDDEN).gainClamped(100);
        int afterClamped = wolf.getResources().value(HIDDEN);
        System.out.println("[1506] declared " + HIDDEN + ": max=" + spec.max() + " overflow=" + spec.overflow()
                + " ; a plain gain of 100 took the resource to " + afterOverflow + " (it added " + overflowGain
                + ") ; a clamped gain of 100 added " + clampedGain + " and left it at " + afterClamped
                + " ; the panel reads " + wolf.getResources().get(HIDDEN));
        Assertions.assertAll(
                () -> Assertions.assertEquals(60, spec.max(), "the activation threshold is the normal cap"),
                () -> Assertions.assertEquals(240, spec.overflow(), "and the text allows 240 more"),
                () -> Assertions.assertEquals(100, afterOverflow, "the normal cap of 60 does not stop a plain gain"),
                () -> Assertions.assertEquals(60, afterClamped, "but a clamped gain stops exactly at it"),
                () -> Assertions.assertEquals(1.0, effect.getPercent(), 1e-9, "action advance 100% (行动提前 100%)"));
    }

    // ==================================================================
    // Zone helpers
    // ==================================================================

    private static double totalEnemyHp(Battle battle) {
        return battle.enemyUnits().stream().mapToDouble(e -> e.getCurrentHp()).sum();
    }

    /**
     * Casts her Ultimate with 【好活当赏】 granted first - the box's own gate, which the zone cases have to satisfy before
     * a spent Skill Point can trigger it.
     *
     * <p>⚠ Deliberately NOT the kit's {@code castUltimate}: that one leaves the resource alone, because its rider cases
     * measure the gate's effect on her two slots and must not also arm the box. The two helpers were one name in the two
     * original classes, which is why they are distinguished here.
     */
    private static void castUltimateHoldingGift(Battle battle, Character wolf) {
        wolf.getResources().gain(GIFT, 1);
        wolf.setCurrentEnergy(wolf.getMaxEnergy());
        battle.castUltra(wolf, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
    }

    /**
     * A battle for the zone cases, built with {@code CharacterFactory.create(cid, level)} for both units.
     *
     * <p>⚠ Deliberately NOT the kit's {@code newBattle()}: {@code create(cid, level)} promotes the character, while the
     * kit's fixture passes {@code promoted = false}. That is a different stat sheet, so the two fixtures are not unified.
     */
    private static Battle newPromotedBattle() {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(WOLF, 80), CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}
