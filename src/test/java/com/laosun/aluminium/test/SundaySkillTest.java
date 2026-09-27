package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.buff.StatModifierBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 星期日（1313）的战技「纸醉金迷」(131302) — the shipped content that the two conditions of M-41 were added for.
 *
 * <p><b>The document, and what each clause needed.</b> 「使指定我方单体角色<b>及其召唤物</b>立即行动，并使其造成的伤害提高
 * #2[i]%，若目标拥有召唤物，则造成的伤害提高效果额外提高 #4[i]%，持续 #3[i]回合。…当星期日对「同谐」命途的角色施放该
 * 技能时，<b>无法触发</b>立即行动效果。」 —
 * <ul>
 *   <li>「及其召唤物」 is the {@code target_and_summon} selector (M-37): the chosen ally <b>and its</b>
 *       memosprite, not the caster's;</li>
 *   <li>「若目标拥有召唤物」 is {@code target_summon_count} — the same question 遗器 127 asks about the wearer,
 *       asked about the ally;</li>
 *   <li>「对同谐…无法触发」 is the {@code !} prefix on {@code target has_path 同谐} — an <b>exception</b>, and the
 *       reason a negation exists at all (the DSL's condition list is an AND).</li>
 * </ul>
 *
 * <p><b>What is pinned here.</b> That the ally AND its memosprite act; that a 同谐 target does <b>not</b> act
 * (but still gets the damage share, because the sentence only blocks 立即行动); that the share is the
 * document's own arithmetic — 15%, or 15% + 25% = 40% when the target has a summon — for 2 turns; and that the
 * file really carries three clauses (a missing one would show up in one of the cases above).
 *
 * <p>⚠ Not authored from this Skill: 「对【蒙福者】施放战技后恢复1个技能点」, which needs his ultimate's state
 * (registered as M-42).
 */
public class SundaySkillTest {
    private static final double EPS = 1e-6;

    private static final int SUNDAY = 1313;
    /** 姬子 — 智识 (Erudition), so the advance happens; and she owns no summon, which is the 15% side. */
    private static final int ERUDITION_ALLY = 1003;
    /** 知更鸟 — 同谐 (Harmony), so the advance must <b>not</b> happen. */
    private static final int HARMONY_ALLY = 1309;
    /** 阿格莱雅 — 记忆 (Remembrance) and a memosprite owner, so she is the "target has a summon" side. */
    private static final int MEMOSPRITE_ALLY = 1402;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    /** His Skill's slot in {@code skills.json}. */
    private static final int SKILL_SLOT = 2;

    // ==================================================================
    // 1. 「指定我方单体及其召唤物立即行动」
    // ==================================================================

    /** The chosen ally and its memosprite both act now — the pair, not just the ally. */
    @Test
    public void theChosenAllyAndItsSummonActImmediately() {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL);
        Character ally = CharacterFactory.create(MEMOSPRITE_ALLY, LEVEL);
        Battle battle = new Battle(List.of(sunday, ally), List.of(dummy()), new Random(0));
        battle.startBattle();
        Summon evey = battle.summonMemosprite(ally);
        battle.processRequests();
        double sundayBefore = timeRemaining(battle, sunday);

        cast(battle, sunday, ally);

        Assertions.assertEquals(0, timeRemaining(battle, ally), EPS, "「指定我方单体角色…立即行动」");
        Assertions.assertEquals(0, timeRemaining(battle, evey), EPS, "「及其召唤物」 -- the pair moves together");
        Assertions.assertEquals(sundayBefore, timeRemaining(battle, sunday), EPS,
                "the caster is not part of the pair");
    }

    /**
     * 同谐 gets no advance — the sentence's exception, and the half a positive spelling could not express.
     *
     * <p>知更鸟 and Sunday himself are both 同谐, so without the gate this rule would push exactly the units the
     * sentence excludes (and self-cast would push him too).
     */
    @Test
    public void aHarmonyTargetIsNotAdvanced() {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL);
        Character harmony = CharacterFactory.create(HARMONY_ALLY, LEVEL);
        Battle battle = new Battle(List.of(sunday, harmony), List.of(dummy()), new Random(0));
        battle.startBattle();
        double harmonyBefore = timeRemaining(battle, harmony);

        cast(battle, sunday, harmony);

        Assertions.assertEquals(harmonyBefore, timeRemaining(battle, harmony), EPS,
                "「当星期日对「同谐」命途的角色施放该技能时，无法触发立即行动效果」");
    }

    /** …but the damage share is a different clause, and it is granted to a 同谐 target all the same. */
    @Test
    public void theShareIsStillGrantedToAHarmonyTarget() {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL);
        Character harmony = CharacterFactory.create(HARMONY_ALLY, LEVEL);
        Battle battle = new Battle(List.of(sunday, harmony), List.of(dummy()), new Random(0));
        battle.startBattle();

        cast(battle, sunday, harmony);

        Assertions.assertEquals(0.15, damageBoostOf(harmony), EPS,
                "only 立即行动 is blocked by the sentence, not 「使其造成的伤害提高」");
    }

    // ==================================================================
    // 2. The share: 15%, or 40% when the target has a summon
    // ==================================================================

    /** The document's own arithmetic: #2 alone, or #2 + #4 when the target has a memosprite, for #3 turns. */
    @Test
    public void theShareIsFifteenPercentWithoutASummonAndFortyWith() {
        Assertions.assertEquals(0.15, boostAfterCastAt(ERUDITION_ALLY, false), EPS,
                "「并使其造成的伤害提高#2[i]%」 -- #2 = 0.15 at Lv1");
        Assertions.assertEquals(0.40, boostAfterCastAt(MEMOSPRITE_ALLY, true), EPS,
                "「若目标拥有召唤物，则…额外提高#4[i]%」 -- 0.15 + 0.25, the sum the target ends up with");
    }

    /** …and it lasts the 2 turns the row says (#3), not a number this file invented. */
    @Test
    public void theShareLastsTheStatedTurns() {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL);
        Character ally = CharacterFactory.create(ERUDITION_ALLY, LEVEL);
        Battle battle = new Battle(List.of(sunday, ally), List.of(dummy()), new Random(0));
        battle.startBattle();

        cast(battle, sunday, ally);

        List<StatModifierBuff> buffs = ally.getBuffManager().allBuffsOf(StatModifierBuff.class);
        Assertions.assertEquals(1, buffs.size(), "one modifier, not two: the two clauses are disjoint rules");
        Assertions.assertEquals(2, buffs.getFirst().duration(), "「持续#3[i]回合」 -- #3 = 2");
    }

    // ==================================================================
    // 3. The file
    // ==================================================================

    /**
     * The three clauses are three rules — and the count is what makes the cases above evidence.
     *
     * <p>They cannot be read back individually the way 遗器 rules are ({@code matching} only returns rules whose
     * conditions hold, and the two share-clauses are deliberately disjoint), so this pins the shape and the
     * behaviour above pins the numbers.
     */
    @Test
    public void theAuthoredSkillIsThreeClauses() {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL);

        Assertions.assertEquals(3, TriggerTables.of(SUNDAY).ruleCount(TriggerEvent.SKILL_CAST),
                "advance (unless 同谐) + share without a summon + share with one");
        Assertions.assertEquals(0, TriggerTables.of(SUNDAY).ruleCount(TriggerEvent.ULT_CAST),
                "his ultimate's 【蒙福者】 kit is not authored yet (M-42)");
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    /** How much damage boost the ally carries after Sunday casts his Skill on it. */
    private static double boostAfterCastAt(int allyCid, boolean withMemosprite) {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL);
        Character ally = CharacterFactory.create(allyCid, LEVEL);
        Battle battle = new Battle(List.of(sunday, ally), List.of(dummy()), new Random(0));
        battle.startBattle();
        if (withMemosprite) {
            battle.summonMemosprite(ally);
            battle.processRequests();
        }

        cast(battle, sunday, ally);

        return damageBoostOf(ally);
    }

    /** A real cast of his Skill, aimed at {@code ally} — the path that carries 「指定」 as the event's target. */
    private static void cast(Battle battle, Character sunday, Character ally) {
        battle.castImmediate(new DefaultSkill(SUNDAY, SKILL_SLOT, 1), sunday, List.of(ally));
    }

    private static double damageBoostOf(CanHit unit) {
        return unit.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
    }

    /** How much action value the unit still has — zero means "acts now". */
    private static double timeRemaining(Battle battle, CanHit target) {
        for (Signal signal : battle.queue.snapshot()) {
            if (signal.getCanHit() == target) {
                return battle.queue.getTimeRemaining(signal);
            }
        }
        return Double.NaN;
    }

    private static Enemy dummy() {
        return EnemyFactory.create(MONSTER, 90, 1);
    }
}
