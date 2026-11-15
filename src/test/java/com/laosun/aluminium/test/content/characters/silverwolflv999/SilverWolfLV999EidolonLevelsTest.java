package com.laosun.aluminium.test.content.characters.silverwolflv999;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Character 1506 (Silver Wolf LV.999), batch 2: her skill LEVELS, her speed trace, and Eidolon 6's 增笑.
 *
 * <h2>What this case found, which matters more than the batch</h2>
 *
 * <p>The project pins a character's skill levels with one {@code level_convention} rule, because a skill STARTS at level 1
 * and nothing else raises it - 63 of the other 94 characters carry one. <b>1506 had none</b>, so every number of hers was
 * being computed at Lv1: 战技 0.8 instead of 2.0, 强化普攻 1.2 instead of 3.36, 欢愉技·强化 0.45 instead of 1.125. Silent,
 * because her judges read states and counters rather than multipliers.
 *
 * <h2>And what the levels mean for the Eidolon clauses</h2>
 *
 * <p>{@code CANNOT BE OBSERVED AT FULL LEVELS}: the effective level is clamped to the skill table's own length, and the
 * convention already puts every slot AT that length (SKILL 15, COMMON 10). So 「战技等级+2」 cannot move the number this
 * engine reads - not because the rule is missing, but because there is no room above the cap. The rules are shipped;
 * this case therefore pins the convention's RESULT and says plainly that the Eidolon increments are invisible here.
 *
 * <h2>欢愉技·强化 (崩坏级伤害演示, slot 21) - a gap witness</h2>
 *
 * Character 1506 (Silver Wolf LV.999), 欢愉技·强化 (崩坏级伤害演示, slot 21): 「造成 <b>6</b> 次伤害，每次对敌方随机单体造成
 * <b>90%</b> 的虚数属性欢愉伤害」 - and her own row puts those two numbers in the opposite order to the rows the engine reads.
 *
 * <p>⚠⚠ <b>This is a GAP WITNESS, not a pass.</b> {@code SkillExecutor}'s Elation-damage branch reads a row as
 * {@code [hits, per-hit share, final share]}, which is 8009/8010's layout ({@code [8, 0.1, 0.3]}). Her row is
 * {@code [1.125, 6.0]} - share first, count second - so the branch takes {@code round(1.125) = 1} for the count and
 * {@code 6.0} for the per-hit share: <b>one instance of 600%</b> instead of six of 112.5%. Measured on this fixture: 878.17,
 * where the text's six hits would be 987.94 (6.75 x ATK against 6.0 x ATK before zones).
 *
 * <p>Why a witness rather than a fix: the count is not identifiable from the row alone - 1513's slot 20 is a THIRD layout
 * ({@code [0.3, 10, 0.09]}, an AoE share then count then per-hit share) and is registered as such in its own file, while
 * 8009/8010's rows are read correctly today. Making the branch guess would break the rows that work. The capability this
 * needs - "which column is the count", or the layout written into {@code skill_effects.json} - is registered in AGENT.md
 * §4.3; when it lands, THIS case is what goes red and points at the file to update.
 *
 * <h2>Eidolon 2's milestone ledger, and her CRIT talent</h2>
 *
 * 1506 Silver Wolf LV.999's Eidolon 2, second half: 「本次【无敌玩家】状态下每使【隐藏分】提高 120 点（含初始隐藏分），
 * 获得 1 个额外回合并恢复 1 次强化普攻的可施放次数」 - the accounting, and the charge it restores.
 *
 * <p><b>What this file pins.</b> The ledger is the shadow of 【隐藏分】 taken when the state is entered (「含初始隐藏分」)
 * and moved by each gain's delta, and every whole 120 points of it pays one milestone: the charge is restored once per
 * milestone and the milestone is consumed once, by {@code SPEND_STEPS}. The three readings that carry it are the
 * arithmetic (250 in, two milestones, 30 left), the multiplicity (a single 280-point gain is TWO milestones, so two
 * charges come back) and the negative control (outside the state, the ledger does not move at all).
 *
 * <p><b>The extra turn is part of it too.</b> That half had to wait: {@code Queue.grantExtraTurn} kept ONE pending
 * extra turn, so a {@code per_steps} repeat would have paid one where the document counts two. The author ruled that
 * the game accumulates them, the queue now counts them for the same unit ({@code ExtraTurnTest} pins the new
 * semantics), and this case takes both of them through the engine's own turn boundaries.
 *
 * <p><b>The fixture moves her hidden score the way the documents do</b>: a second unit grants a lump of SHARED 笑点, and
 * her own talent (「获得笑点时，获得等量【隐藏分】」) forwards it one for one. ⚠ Deliberately NOT a direct
 * {@code GAIN_RESOURCE 隐藏分} pointed at her from another unit: the delta a change ANNOUNCES is measured on the rule
 * owner's own store, so a self-scoped counter granted to somebody else is announced as 0 (measured while writing this
 * case; registered in GAPS). The party-scoped laugh pool has no such blind spot, and the forwarding clause that turns it
 * into hidden points is her own rule - the shipped premise.
 *
 * <p>⚠ Moved: the gap witness and these Eidolon-2/crit cases used to live in their own classes
 * ({@code SilverWolfElationTest}, {@code SilverWolfCritTest}); one file now carries the state's accounting, her talent and
 * the slot-21 witness. No assertion changed in the move.
 *
 * <p><b>L3:</b> the single-clause cases are grouped into the scenarios below, one {@code assertAll} per fixture so a failing
 * clause still names itself: the level convention with the Eidolon increments it clamps away, the speed trace's threshold
 * and its 100-point ceiling, the ramp's four terms with the gate they hang on, the slot-21 row with the number it settles,
 * and the CRIT talent's curve from 0.4% per point through the switch to the 0.8% overflow. The E6 amp, the wave rule, the
 * Eidolon-2 ledger's own cases (three fixtures, one of them the negative control, plus the shipped rule) and the recompute
 * clause keep their own tests - their fixtures differ on the very dimension they measure.
 *
 */
public class SilverWolfLV999EidolonLevelsTest {
    private static final int WOLF = 1506;
    private static final int ALLY = 1204;
    private static final String HIDDEN = "char:1506/hidden_score";
    private static final String RAMP_RULE = "p1506_enhanced_basic_hidden_ramp";
    private static final String LAUGH = "笑点";
    private static final String CHARGES = "internal:1506/enhanced_basic_count";
    private static final String QUOTA = "internal:1506/milestone_quota";
    private static final String RULE = "p1506_e2_milestones_while_in_godmode";
    private static final int STEP = 120;
    private static final String WAVE_RULE = "p1506_technique_box_each_wave";
    /**
     * The reading the engine produces today, measured with a source that never crits (so the number is the row's, not a
     * roll's). The text's six hits would settle 987.9408235224 on the same fixture.
     */
    private static final double TODAY_ONE_INSTANCE_OF_SIX_HUNDRED = 878.1696209088004;

    /**
     * The convention puts each slot at the last row of its own table: 15 for the 15-row skills, 10 for the Basic.
     *
     * <p>And that is why Eidolon 3's and 5's 「等级+2」 cannot be seen at these levels: the effective level is clamped to the
     * skill table's own length, and the convention already puts every slot AT that length (SKILL 15, COMMON 10). The rules
     * ARE shipped and DO fire - what they add lands above the last row and is clamped to it. Stated beside the levels that
     * make it so, not hidden.
     */
    @Test
    public void theLevelConventionPutsEverySlotAtItsTableMaximum() {
        Character atZero = battleAt(0).characters().getFirst();
        int common = levelOf(atZero, SkillType.COMMON);
        int skill = levelOf(atZero, SkillType.SKILL);
        int ultra = levelOf(atZero, SkillType.ULTRA);
        int talent = levelOf(atZero, SkillType.TALENT);
        int elation = levelOf(atZero, SkillType.ELATION_SKILL);
        int extra = levelOf(atZero, SkillType.ELATION_EXTRA);
        Character atThree = battleAt(3).characters().getFirst();
        Character atFive = battleAt(5).characters().getFirst();
        int skillAtThree = levelOf(atThree, SkillType.SKILL);
        int ultraAtFive = levelOf(atFive, SkillType.ULTRA);
        System.out.println("[1506] effective levels: COMMON=" + common + " SKILL=" + skill + " ULTRA=" + ultra
                + " TALENT=" + talent + " ELATION_SKILL=" + elation + " ELATION_EXTRA=" + extra
                + " ; SKILL at E0/E3: " + skill + "/" + skillAtThree
                + " ; ULTRA at E0/E5: " + ultra + "/" + ultraAtFive);

        Assertions.assertAll(
                // 1506 is the one file whose convention is its own (fourteen levels on five slots), so its basic stays at row 10.
                () -> Assertions.assertEquals(10, common, "普攻的表有 10 行 -> 满级 10（这一份文件的口径是自定的）"),
                () -> Assertions.assertEquals(15, skill, "战技的表有 15 行 -> 满级 15"),
                () -> Assertions.assertEquals(15, ultra, "终结技 15 行"),
                () -> Assertions.assertEquals(15, talent, "天赋 15 行"),
                () -> Assertions.assertEquals(15, elation, "欢愉技 15 行"),
                () -> Assertions.assertEquals(15, extra, "欢愉技·强化 15 行"),
                // The rules ARE shipped and DO fire; what they add lands above the table's last row and is clamped to it.
                () -> Assertions.assertEquals(skill + 0, skillAtThree,
                        "星魂 3 的 「战技等级+2」 在满级口径下无处可加（引擎把等级夹在表的行数上）"),
                () -> Assertions.assertEquals(ultra, ultraAtFive,
                        "星魂 5 的 「终结技等级+2」 同理"));
    }

    /**
     * The speed trace, BOTH halves: +50% at 160 Speed, then +2% for every point above it - up to 100 counted points.
     *
     * <p>「速度大于等于 160 时，使自身欢愉度提高 50%，之后每超过 1 点速度使自身欢愉度提高 2%，最多计入 100 点超出的速度。」
     *
     * <p>The ceiling is the half that could not be written before, and the reading that matters is the one that would be
     * wrong without the bound: at a Speed far above 160 the per-point term must stop growing, so the difference from a
     * lower Speed is NOT proportional - read on the same fixture as the threshold it bounds.
     */
    @Test
    public void theSpeedTraceCountsFiftyPercentThenTwoPercentPerPointUpToAHundred() {
        double none = elationWithSpeedBuff(0.0);
        // The fixture's Speed is 119, so +50% is 178.5 -> 174 after the stat pipeline's rounding: 14 points over 160.
        // And the base is NOT zero - she carries 0.1 of her own - so the expectation is stated against the measured base
        // rather than against 0, which is the mistake the first version made.
        double justOver = elationWithSpeedBuff(0.5);
        double farOver = elationWithSpeedBuff(2.0);
        double atCap = none + 0.5 + 100 * 0.02;
        double unbounded = none + 0.5 + 179 * 0.02;
        System.out.println("[1506] 欢愉度 with no Speed buff: " + none + " ; with +50% (Speed 174): " + justOver
                + " ; at Speed 339: " + farOver + " ; the ceiling is " + atCap
                + " and WITHOUT `max_points` 339 Speed would read " + unbounded);
        Assertions.assertAll(
                () -> Assertions.assertEquals(none + 0.5 + 14 * 0.02, justOver, 1e-6,
                        "「+50%」 plus 「每超过 1 点 +2%」 for the 14 points above 160, on top of her own 0.1"),
                () -> Assertions.assertTrue(farOver > justOver, "more Speed still helps below the ceiling"),
                () -> Assertions.assertEquals(atCap, farOver, 1e-6,
                        "and past 100 points over, the per-point term stops - without `max_points` this would read "
                                + unbounded));
    }

    /**
     * E6's 增笑 lands with the Ultimate and not before it.
     */
    @Test
    public void eidolonSixAmpsOnlyAfterTheUltimate() {
        Battle battle = battleAt(6);
        Character wolf = battle.characters().getFirst();
        double before = wolf.getAttribute(AttributeType.ELATION_DAMAGE_AMP).get();
        wolf.setCurrentEnergy(wolf.getMaxEnergy());
        battle.castUltra(wolf, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        double after = wolf.getAttribute(AttributeType.ELATION_DAMAGE_AMP).get();
        System.out.println("[1506] E6 增笑 " + before + " -> " + after);
        Assertions.assertEquals(0.5, after - before, 1e-9,
                "星魂 6: 「强化普攻期间造成的欢愉伤害增笑 50%」 - and 强化普攻期间 is the 【char:1506/invincible_player】 the Ultimate grants");
    }

    /**
     * The enhanced-basic ramp, pinned as the DECOMPOSITION it is: four linear terms whose sum is the plateau - and, on the
     * same compiled rule, the gate that makes them hers alone: her enhanced basic's own slot, her own damage.
     *
     * <p>Both halves read the same {@code p1506_enhanced_basic_hidden_ramp} rule off the same compiled character, so they
     * are one scenario: the gate says where the terms apply, and the terms are what they pay. Her ordinary Basic is left
     * alone by that gate.
     */
    @Test
    public void theRampIsFourLinearTermsGatedOnTheEnhancedBasic() {
        TriggerTable table = CharacterFactory.create(WOLF, 80).getTriggerTable();
        var rule = table.rulesFor(TriggerEvent.DEALING_DAMAGE).stream()
                .filter(r -> r.id().equals(RAMP_RULE)).findFirst().orElseThrow();
        var effects = rule.effects().stream()
                .filter(effect -> "MODIFY_ATTR".equals(effect.getOp())).toList();
        var removals = rule.effects().stream().filter(effect -> "REMOVE_STATE".equals(effect.getOp())).toList();
        List<String> conditions = rule.conditions().stream().map(c -> c.source()).toList();

        StringBuilder shown = new StringBuilder();
        for (var effect : effects) {
            shown.append(effect.getScale()).append(" -> ").append(effect.getPercent()).append(" ; ");
        }
        System.out.println("[1506] the ramp's terms: " + shown);
        System.out.println("[1506] the ramp's gate: " + conditions);

        Assertions.assertAll(
                () -> Assertions.assertEquals(4, effects.size(), "four terms carry the plateau and the cap"),
                () -> assertTerm(effects.get(0), HIDDEN, 60, 0.15, "the first layer starts at 60"),
                () -> assertTerm(effects.get(1), HIDDEN, 120, -0.075,
                        "and at 120 a NEGATIVE term cancels the one still running"),
                () -> assertTerm(effects.get(2), HIDDEN, 120, 0.075, "while a positive one opens the second layer"),
                () -> assertTerm(effects.get(3), HIDDEN, 180, 0.15,
                        "and at 180 the second layer completes - nothing above it"),
                () -> Assertions.assertEquals(4, removals.size(),
                        "one named removal per term, before the terms are re-attached"),
                () -> removals.forEach(removal -> Assertions.assertEquals("own", removal.getKind(),
                        "each removal only takes off this rule's own modifier")),
                () -> Assertions.assertEquals(0.15, sum(0.15, false, false), 1e-9, "60..119: one layer"),
                () -> Assertions.assertEquals(0.15, sum(0.15, true, false), 1e-9,
                        "120..179: the plateau, still one layer"),
                () -> Assertions.assertEquals(0.30, sum(0.15, true, true), 1e-9,
                        ">=180: two layers, and it stops there"),
                () -> Assertions.assertTrue(conditions.contains("from_skill_id == 8.0"),
                        "强化普攻 是槽位 8；伤害路径携带槽位（与 ATTACK_FINISHED 不同）"),
                () -> Assertions.assertTrue(conditions.contains("actor == self"), "and it is her own damage"));
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static double sum(double first, boolean plateau, boolean cap) {
        double total = first;
        if (plateau) {
            total += -0.075 + 0.075;
        }
        if (cap) {
            total += 0.15;
        }
        return total;
    }

    private static void assertTerm(Object effect, String resource, int threshold, double percent, String why) {
        var spec = (com.laosun.aluminium.beans.EffectSpec) effect;
        Assertions.assertEquals("self_attr_above:" + resource + ":" + threshold, spec.getScale(), why);
        Assertions.assertEquals(percent, spec.getPercent(), 1e-9, why);
        Assertions.assertEquals(Boolean.TRUE, spec.getCoexist(),
                "同属性同目标的两个修正器会被判同类而互相驱逐，所以每一项都要 coexist");
    }

    /**
     * The EFFECTIVE level of a slot: base plus every raise, clamped to the table's own length.
     *
     * <p>Note: Not {@code Skill.getLevel()}, which is the BASE level and does not move when a rule raises it - measured,
     * every slot read 1 that way and the rules looked broken. {@code CanHit.skillLevel(skill)} is what the engine reads
     * when it computes damage, so it is what a judge has to read.
     */
    private static int levelOf(Character unit, SkillType slot) {
        var skill = unit.getSkills().get(slot);
        return skill == null ? -1 : unit.skillLevel(skill);
    }

    /*
     * Her Elation-degree boost with Speed forced to the given value, so the threshold is what moves.
     *
     * <p>Note: Speed is written BEFORE {@code startBattle()}, because the trace fires on {@code BATTLE_START} - setting it
     * afterwards would be measuring a rule that has already run.
     */
    /**
     * Her Elation degree with a Speed buff of the given additive share, applied BEFORE the battle starts.
     *
     * <p>Note: Applied before, because the trace fires on {@code BATTLE_START} - afterwards would be measuring a rule that
     * has already run. And a MODIFIER rather than a raw base write: measured, {@code baseNoCompute} is overwritten when the
     * battle starts (the unit read 119 however the base was set), while a buff survives.
     */
    private static double elationWithSpeedBuff(double share) {
        Character wolf = CharacterFactory.create(WOLF, 80);
        Battle battle = new Battle(
                List.of(wolf, CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        if (share != 0) {
            wolf.getBuffManager().addBuff(com.laosun.aluminium.buff.StatModifierBuff
                    .percentBuff(AttributeType.SPEED, share, 1));
        }
        System.out.println("[1506] Speed buff " + share + " -> Speed reads "
                + wolf.getAttribute(AttributeType.SPEED).get());
        battle.startBattle();
        battle.processRequests();
        return battle.characters().getFirst().getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
    }

    private static Battle battleAt(int eidolonRank) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(WOLF, 80, false, null, null, eidolonRank),
                        CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return battle;
    }

    // ==================================================================
    // 欢愉技·强化 (slot 21) - the gap witness that was SilverWolfElationTest
    // ==================================================================

    /**
     * Her row is {@code [share, count]}, the engine reads the first column as a count - and the same battle measures the
     * consequence: one 600% instance rather than six 112.5% ones.
     *
     * <p>The row and the cast are the same fixture (her alone, against one non-critting monster), so the layout that is
     * mis-read and the number it produces are one scenario: the second is only explainable by the first.
     */
    @Test
    public void theSlot21RowIsShareFirstAndSettlesOneBigInstance() {
        // Note: Through a started battle, because her level comes from her own `level_convention` rule (a table read at level
        // 1 would answer [0.45, 6.0] and hide which column is which - measured).
        Battle battle = new Battle(List.of(CharacterFactory.create(WOLF, 80)), List.of(BattleFixture.monster()),
                new NoCrit());
        battle.startBattle();
        battle.processRequests();
        Character wolf = battle.characters().getFirst();
        var skill = wolf.getSkills().get(SkillType.ELATION_EXTRA);
        int level = wolf.skillLevel(skill);
        var row = skill.getData().getSkills().get(level - 1);
        // Read BEFORE the cast, so the two columns are the table's own entries and not anything a settlement could touch.
        double shareColumn = row.getFirst();
        double countColumn = row.get(1);
        System.out.println("[1506] slot 21 at her level " + level + " reads " + row
                + " ; the engine takes round(row[0]) = " + Math.round(shareColumn) + " for the count"
                + " and row[1] = " + countColumn + " for the per-hit share");
        var enemy = battle.enemyUnits().getFirst();
        double before = enemy.getCurrentHp();
        battle.castImmediate(skill, wolf, List.of(enemy));
        battle.processRequests();
        double dealt = before - enemy.getCurrentHp();
        System.out.println("[1506] 崩坏级伤害演示 settled " + dealt + " (today's reading; six hits would be "
                + (dealt * 6.75 / 6.0) + ")");
        Assertions.assertAll(
                () -> Assertions.assertEquals(1.125, shareColumn, 1e-9, "第一列是**份额**（Lv15 的 112.5%）"),
                () -> Assertions.assertEquals(6.0, countColumn, 1e-9, "第二列才是**次数**（正文的 6）"),
                () -> Assertions.assertEquals(6, (int) Math.round(countColumn), "「造成 6 次伤害」说的是第二列"),
                () -> Assertions.assertEquals(1, (int) Math.round(shareColumn),
                        "而欢愉行分支把第一列当次数 ⇒ 今天只结算 1 次（这就是缺口）"),
                () -> Assertions.assertEquals(TODAY_ONE_INSTANCE_OF_SIX_HUNDRED, dealt, 1e-6,
                        "今天的读数 = 1 次 × 600%：这是被登记的缺口，不是期望的行为"));
    }

    /**
     * Her technique's wave clause: a WAVE_START rule that fires the same box and counts 99 laughs for that firing.
     */
    @Test
    public void theTechniqueFiresTheBoxEachWaveCounting99Laughs() {
        var table = CharacterFactory.create(WOLF, 80).getTriggerTable();
        var rule = table.rulesFor(TriggerEvent.WAVE_START).stream()
                .filter(candidate -> candidate.id().equals(WAVE_RULE))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the technique's wave clause is not on her table: " + WAVE_RULE));
        List<String> conditions = rule.conditions().stream().map(condition -> condition.source()).toList();
        var effects = rule.effects();
        System.out.println("[1506-tech] " + WAVE_RULE + " when=" + conditions + " effects=" + effects.size()
                + " countedLaughs=" + effects.stream()
                .filter(e -> "ELATION".equalsIgnoreCase(e.getDamageType() == null ? "" : e.getDamageType().trim()))
                .findFirst().map(e -> e.getCountedLaughs()).orElse(null));

        Assertions.assertTrue(conditions.contains("self has_state sys:technique"),
                "「【怪怪怪味豆】在场时」 is the technique state the engine applies before every BATTLE_START");
        Assertions.assertEquals(4, effects.size(),
                "the same box as the skill-point route: the Elation hit, the sword, the skill points and the laughs");
        var elation = effects.stream()
                .filter(e -> "ELATION".equalsIgnoreCase(e.getDamageType() == null ? "" : e.getDamageType().trim()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the wave clause has no Elation instance"));
        Assertions.assertEquals(99.0, elation.getCountedLaughs(), 1e-9,
                "「本次造成的欢愉伤害固定计入 99 点【好活当赏】」 - the instance states 99 rather than reading the pool");
        Assertions.assertEquals("all_enemies", elation.getTarget(),
                "and it is still the box's own hit on every enemy");
    }

    /**
     * A source that never crits, so the reading is the row's number rather than a roll's.
     */
    private static final class NoCrit extends Random {
        @Override
        public double nextDouble() {
            return 0.9999;
        }
    }

    // ==================================================================
    // Eidolon 2's ledger and her CRIT talent - the cases that were SilverWolfCritTest
    // ==================================================================

    private Character wolf;
    private Character feeder;
    private Enemy enemy;
    private Battle battle;

    /**
     * 「含初始隐藏分」: she enters the state holding 250, the Ultimate's own trace adds 20, and the ledger counts all 270
     * - two milestones paid, 30 left. Without the initial 250 the ledger would read 20 and nothing would have been paid.
     */
    @Test
    public void theInitialHiddenScoreCountsTowardTheMilestones() {
        build(2, 0);
        wolf.getResources().gain(HIDDEN, 250);
        enterGodmode();
        System.out.println("[milestone] entered with an initial 250: hidden " + hidden() + ", ledger " + quota()
                + ", charges " + charges());
        Assertions.assertEquals(270, hidden(), "the Ultimate's own trace granted 20 more");
        Assertions.assertEquals(30, quota(),
                "270 counted as two milestones (2 x 120 paid, 30 left); the initial 250 counted, or nothing would be paid");
    }

    /**
     * ⚠ The case the whole primitive exists for: ONE gain of 280 is two milestones, so BOTH effects are paid twice -
     * two charges come back and two extra turns are owed, taken back to back. A per-event "over 120, pay one" check
     * would pay one of each.
     */
    @Test
    public void oneGainOfTwoHundredAndEightyPaysTwoMilestones() {
        build(2, 280);
        enterGodmode();
        Assertions.assertEquals(20, hidden(), "precondition: the Ultimate's own trace, and no milestone yet");
        Assertions.assertEquals(20, quota(), "precondition: the ledger holds the 20 with nothing paid");
        wolf.getResources().spend(CHARGES, 2);
        Assertions.assertEquals(1, charges(), "precondition: two charges spent, so the restoration is visible");

        feederLaughs();
        System.out.println("[milestone] one 280-point laugh gain: hidden " + hidden() + ", ledger " + quota()
                + ", charges " + charges());
        Assertions.assertEquals(300, hidden(), "forwarded one for one, up to the counter's own ceiling (60 + 240)");
        Assertions.assertEquals(3, charges(), "1 + 2 milestones: a single 'over 120' check would leave 2");
        Assertions.assertEquals(60, quota(), "300 counted as two milestones, 60 left for the next one");

        // ⭐ The same two milestones paid the extra turns too, and the engine takes them one after the other: the second
        // is still owed after the first has been taken (Queue counts them; ExtraTurnTest has the queue-level case).
        Assertions.assertEquals(wolf, battle.getExtraTurnActor(), "two milestones, so an extra turn is pending");
        Assertions.assertEquals(wolf, takeOneTurn(), "the first extra turn is hers");
        Assertions.assertEquals(wolf, battle.getExtraTurnActor(), "and the second is still owed, not overwritten");
        Assertions.assertEquals(wolf, takeOneTurn(), "the second extra turn is hers");
        Assertions.assertNull(battle.getExtraTurnActor(), "no more are owed");
    }

    /**
     * The negative control: outside 【无敌玩家】 the ledger does not move, whatever her hidden score does.
     */
    @Test
    public void nothingIsCountedWhileSheIsNotInGodmode() {
        build(2, 280);
        feederLaughs();
        System.out.println("[milestone] the same gain with no 【char:1506/invincible_player】: hidden " + hidden() + ", ledger " + quota()
                + ", charges " + charges());
        Assertions.assertEquals(280, hidden(), "the hidden score still moves");
        Assertions.assertEquals(0, quota(), "but the ledger only exists inside the state");
        Assertions.assertEquals(0, charges(), "and nothing was restored");
    }

    /**
     * The ledger is per state: leaving 【无敌玩家】 ends the accounting with it.
     */
    @Test
    public void theLedgerIsResetWhenTheStateEnds() {
        build(2, 0);
        enterGodmode();
        Assertions.assertEquals(20, quota(), "precondition: the ledger was seeded on entry");
        battle.fireStateEnded(wolf, "char:1506/invincible_player");
        battle.processRequests();
        System.out.println("[milestone] after the state ended: hidden " + hidden() + ", ledger " + quota());
        Assertions.assertEquals(0, quota(), "the next state starts from its own initial hidden score");
        Assertions.assertEquals(4, hidden(), "and her Eidolon 1 keeps a fifth of what the talent cleared");
    }

    /**
     * The shipped rule, read off the compiled table: what it spends, and that it stays LAST on her resource event.
     *
     * <p>⚠ The order is load-bearing and is why this case exists: its first effect moves the shadow counter, which
     * overwrites the battle's {@code lastChangedResource} - so any further {@code resource_changed:隐藏分} rule placed
     * after it would never fire.
     */
    @Test
    public void theShippedRuleSpendsTheMilestonesAndStaysLast() {
        Character unit = CharacterFactory.create(WOLF, 80, false, null, null, 2);
        var rules = unit.getTriggerTable().rulesFor(TriggerEvent.RESOURCE_CHANGED).stream()
                .filter(rule -> rule.id().startsWith("p1506_")).toList();
        List<String> ids = rules.stream().map(rule -> rule.id()).toList();
        System.out.println("[milestone] her RESOURCE_CHANGED rules, in order: " + ids);
        Assertions.assertEquals(RULE, ids.getLast(),
                "it overwrites lastChangedResource, so anything after it on char:1506/hidden_score would never fire");
        var rule = rules.getLast();
        var effects = rule.effects();
        Assertions.assertEquals(2, rule.minEidolon(), "the clause is her Eidolon 2");
        Assertions.assertEquals(List.of("self has_state char:1506/invincible_player", "resource_changed:char:1506/hidden_score"),
                rule.conditions().stream().map(condition -> condition.source()).toList(),
                "the state is the scope, and the hidden score is what moved");
        Assertions.assertEquals(4, effects.size(), "accumulate, extra turn, restore, spend - in that order");
        Assertions.assertEquals(Boolean.TRUE, effects.get(0).getAmountFromEvent(), "the ledger grows by the event's delta");
        Assertions.assertEquals(QUOTA, effects.get(0).getResource());
        Assertions.assertEquals("EXTRA_TURN", effects.get(1).getOp(), "「获得 1 个额外回合」 comes first in the sentence");
        Assertions.assertEquals(QUOTA + ":" + STEP, effects.get(1).getPerSteps(),
                "one per milestone, counted - not spent");
        Assertions.assertEquals(CHARGES, effects.get(2).getResource());
        Assertions.assertEquals(QUOTA + ":" + STEP, effects.get(2).getPerSteps(),
                "one charge per milestone, off the same number");
        Assertions.assertEquals("SPEND_STEPS", effects.get(3).getOp());
        Assertions.assertEquals(QUOTA + ":" + STEP, effects.get(3).getSteps(),
                "and the milestones are consumed once, after both readers");
    }

    /**
     * Her CRIT talent as one curve: below full, +0.4% CRIT Rate per hidden point with no crit-damage half; the switch is at
     * CRIT RATE 1.0, not at a round number of points; past it every point pays 0.8% CRIT DMG - read as the MARGINAL rate,
     * since the first ~190 points pay none.
     *
     * <p>All three clauses are read on the same fixture (her CRIT_CHANCE / CRIT_ATTACK after a hidden-score gain), so they
     * are one scenario: the rate the first clause raises is what the switch and the overflow both read.
     */
    @Test
    public void theCritTalentRunsFromPointFourPercentToTheOverflow() {
        Reading plain = read(0);
        Reading atHundred = read(100);
        int justBelow = (int) Math.floor((1 - plain.crit()) / 0.004) - 1;
        Reading below = read(justBelow);
        Reading above = read(justBelow + 3);
        double excess = above.crit() - 1.0;
        Reading full = read(300);
        Reading lower = read(250);
        double excessAtCap = full.crit() - 1.0;
        System.out.println("[1506-talent] CRIT_CHANCE " + plain.crit() + " -> " + atHundred.crit()
                + " ; CRIT_ATTACK " + plain.critDamage() + " -> " + atHundred.critDamage()
                + " ; char:1506/hidden_score " + atHundred.hidden());
        System.out.println("[1506-talent] base CRIT_CHANCE " + plain.crit() + " ; at " + below.hidden() + " char:1506/hidden_score crit = "
                + below.crit() + " (crit dmg " + below.critDamage() + ") ; at " + above.hidden() + " crit = "
                + above.crit() + " (crit dmg " + above.critDamage() + ", excess " + excess + ")");
        System.out.println("[1506-talent] at the 300-point ceiling: CRIT_CHANCE " + full.crit() + ", CRIT_ATTACK "
                + full.critDamage() + " (excess " + excessAtCap + " -> 2 x excess = " + (2 * excessAtCap) + ")"
                + " ; marginal per point past the switch = " + ((full.critDamage() - lower.critDamage()) / 50));
        Assertions.assertAll(
                () -> Assertions.assertEquals(100, atHundred.hidden(),
                        "precondition: 100 counted laughs became 100 char:1506/hidden_score"),
                () -> Assertions.assertEquals(plain.crit() + 100 * 0.004, atHundred.crit(), 1e-9,
                        "「每点【char:1506/hidden_score】使暴击率提高 0.4%」"),
                () -> Assertions.assertEquals(plain.critDamage(), atHundred.critDamage(), 1e-9,
                        "and while she is below full, NOTHING switches to crit damage yet"),
                () -> Assertions.assertTrue(below.crit() < 1.0, "precondition: the lower reading is still under full"),
                () -> Assertions.assertTrue(above.crit() > 1.0, "precondition: the upper reading is over it"),
                () -> Assertions.assertEquals(plain.critDamage(), below.critDamage(), 1e-9,
                        "under full there is no crit-damage half at all"),
                () -> Assertions.assertEquals(plain.critDamage() + 2 * excess, above.critDamage(), 1e-9,
                        "and over it the excess pays 2x - 「每溢出 1% 暴击率提高 2% 暴击伤害」"),
                () -> Assertions.assertEquals(300, full.hidden(), "60 plus the 240 overflow is the ceiling"),
                () -> Assertions.assertEquals(plain.crit() + 300 * 0.004, full.crit(), 1e-9,
                        "the crit-rate half keeps its rate - it is not clamped, because its overflow is what the other "
                                + "half reads"),
                () -> Assertions.assertEquals(plain.critDamage() + 2 * excessAtCap, full.critDamage(), 1e-9,
                        "and 2 x excess is exactly 0.008 per hidden point past full"),
                () -> Assertions.assertEquals(0.008, (full.critDamage() - lower.critDamage()) / 50, 1e-9,
                        "past the switch each hidden point pays the page's 0.8% CRIT DMG"));
    }

    /**
     * Gains must RECOMPUTE, not accumulate: two gains of 100 total what one gain of 200 does.
     */
    @Test
    public void twoGainsRecomputeInsteadOfAccumulating() {
        Reading oneGain = read(200);
        Reading twice = read(new int[]{100, 100});
        Reading threeTimes = read(new int[]{60, 80, 60});
        System.out.println("[1506-talent] 200 at once = " + oneGain.crit() + " ; 100 then 100 = " + twice.crit()
                + " ; 60+80+60 = " + threeTimes.crit() + " ; char:1506/hidden_score " + threeTimes.hidden());
        Assertions.assertEquals(200, twice.hidden(), "precondition: two gains of 100 are 200 points");
        Assertions.assertEquals(oneGain.crit(), twice.crit(), 1e-9,
                "each point counts once: base + 200 x 0.004, not base + 100 x 0.004 twice over");
        Assertions.assertEquals(oneGain.crit(), threeTimes.crit(), 1e-9,
                "and the same holds for three gains of different sizes");
        Assertions.assertEquals(oneGain.critDamage(), twice.critDamage(), 1e-9,
                "the crit-damage half recomputes too - it is the one that reads the crit rate the other half just set");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * Her CRIT RATE, CRIT DMG and 隐藏分 after the party gains {@code laughs} 笑点 (which her trace turns into 隐藏分).
     */
    private record Reading(double crit, double critDamage, int hidden) {
    }

    private static Reading read(int hiddenPoints) {
        return read(new int[]{hiddenPoints});
    }

    /**
     * {@link #read(int)} for several gains in a row, which is what tells "recomputes" from "accumulates".
     */
    private static Reading read(int[] gains) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(WOLF, 80), CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character wolf = battle.characters().getFirst();
        TriggerTable withGrant = wolf.getTriggerTable().plus(new TriggerTable(WOLF,
                List.of(TriggerSpecs.rule("TURN_START", List.of("actor == self"),
                        TriggerSpecs.gainResource(HIDDEN, 1)))));
        wolf.setTriggerTable(withGrant);
        for (int gain : gains) {
            for (int step = 0; step < gain; step++) {
                battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, wolf, wolf, 0, 0));
                battle.processRequests();
            }
        }
        return new Reading(wolf.getAttribute(AttributeType.CRIT_CHANCE).get(),
                wolf.getAttribute(AttributeType.CRIT_ATTACK).get(), wolf.getResources().value(HIDDEN));
    }

    /**
     * A party whose second unit can move the SHARED laugh pool, which her talent forwards into hidden points.
     */
    private void build(int eidolon, int laughs) {
        wolf = CharacterFactory.create(WOLF, 80, false, null, null, eidolon);
        feeder = Character.fromAttributes("feeder", 1000, 100, 100, 100);
        EffectSpec grant = new EffectSpec();
        TriggerSpecs.set(grant, "op", "GAIN_RESOURCE");
        TriggerSpecs.set(grant, "resource", LAUGH);
        TriggerSpecs.set(grant, "amount", (double) laughs);
        // ⚠ DAMAGE_SETTLED, not TURN_START: driving the extra turns below goes through the engine's own turn boundaries,
        // and TURN_START would fire this fixture's rule again - a second 280-point grant would move every reading here.
        feeder.setTriggerTable(new TriggerTable(1, List.of(
                TriggerSpecs.rule(TriggerEvent.DAMAGE_SETTLED.name(), List.of(), grant))));
        enemy = BattleFixture.monster();
        battle = new Battle(List.of(wolf, feeder), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
    }

    private void enterGodmode() {
        wolf.setCurrentEnergy(wolf.getMaxEnergy());
        battle.castUltra(wolf, List.of(enemy));
        battle.processRequests();
    }

    /**
     * One party-scoped laugh gain, which 「获得笑点时，获得等量【隐藏分】」 turns into hidden points.
     */
    private void feederLaughs() {
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.DAMAGE_SETTLED, feeder, feeder, 0, 0));
        battle.processRequests();
    }

    /**
     * One complete turn through the engine's own boundaries, answering with whoever acted.
     */
    private CanHit takeOneTurn() {
        battle.stepForward();
        CanHit actor = battle.currentMove().getCanHit();
        battle.beforeMove();
        battle.afterMove();
        return actor;
    }

    private int hidden() {
        return wolf.getResources().value(HIDDEN);
    }

    private int charges() {
        return wolf.getResources().value(CHARGES);
    }

    private int quota() {
        return wolf.getResources().value(QUOTA);
    }
}
