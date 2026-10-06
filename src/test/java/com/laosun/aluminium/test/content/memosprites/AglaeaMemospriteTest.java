package com.laosun.aluminium.test.content.memosprites;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Aglaea (阿格莱雅) 1402's memosprite kit - "衣匠" ("Garmentmaker"), as data.
 *
 * <p><b>Why this character.</b> Her two abilities are the densest test of everything the engine does,
 * and none of it needed a new engine piece: the panel is inherited from her ({@code memosprites/1402.json}), the
 * memosprite is summoned by a rule, healed by a scaled {@code HEAL} aimed with {@code target: "summon"}, and her
 * own place in the turn order is moved with {@code ADVANCE}.
 *
 * <pre>
 * Skill (战技): "为衣匠回复等同于其#1[i]%生命上限的生命值。若衣匠不在场，则召唤忆灵衣匠，并使自身立即行动。"
 * Ultimate (终结技): "召唤忆灵衣匠，若衣匠已在场，则使其生命值回复至上限。阿格莱雅进入[至高之姿]状态并使自身立即行动。"
 * </pre>
 *
 * <p><b>The two halves are two rules, and the split is the interesting part.</b> "并使自身立即行动" ("and make herself act immediately") hangs off the
 * "若衣匠不在场" ("if the Garmentmaker is not on the field") branch (the "并" ("and") continues the summoning clause), so the skill's two sentences become two rules
 * gated by {@code self_summon_count >= 1} and {@code == 0} - mutually exclusive by construction, rather than by
 * asking whether the summon "really happened" (which {@code SUMMON}'s idempotence makes unobservable afterwards).
 * Her ultimate, in contrast, needs no branch at all: the "若已在场则回复至上限" ("if it is already out, restore it to the maximum") clause is covered by summoning
 * first and then healing, because a fresh memosprite is at full HP.
 *
 * <p>Note: What is deliberately <b>not</b> authored, and why it is not an oversight: the Garmentmaker's own attack. Its Memosprite Skill 1 is
 * a Blast for "110% ATK (攻击力)" of Lightning damage, but no document gives the Garmentmaker an ATK - the panel states HP and SPD only - 
 * and scaling it off Aglaea's ATK would be a guess. The loader would refuse it anyway: an attack whose base the
 * panel never states is exactly the "every hit deals zero" case {@code Memosprites.validateAttack} exists for.
 */
public class AglaeaMemospriteTest {
    private static final double EPS = 1e-6;

    private static final int AGLAEA = 1402;
    /** The stance her ultimate enters, and the state whose life is anchored to the memosprite. */
    private static final String STANCE = "至高之姿";
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    // ==================================================================
    // 1. The skill: heal, or summon and advance
    // ==================================================================

    /** "衣匠已在场" ("the Garmentmaker is already on the field"): the skill restores 50% of the memosprite's Max HP. */
    @Test
    public void herSkillHealsTheMemospriteThatIsAlreadyOut() {
        Battle battle = battle();
        Character aglaea = battle.characters.getFirst();
        battle.startBattle();
        Summon tailor = battle.summonMemosprite(aglaea);
        battle.processRequests();
        tailor.takeDamage(tailor.getMaxHp() * 0.8);
        double before = tailor.getCurrentHp();

        battle.fireTriggers(TriggerEvent.SKILL_CAST, aglaea, null, 0, 0);

        Assertions.assertEquals(before + tailor.getMaxHp() * 0.5, tailor.getCurrentHp(), EPS,
                "「回复等同于其 50% 生命上限的生命值」 (restores HP equal to 50% of its Max HP)");
        Assertions.assertEquals(1, battle.summonCountOf(aglaea), "…and no second one appeared");
    }

    /** "衣匠不在场" ("the Garmentmaker is not on the field"): the skill brings it out <b>and</b> makes her act immediately. */
    @Test
    public void herSkillSummonsAndAdvancesHerWhenItIsNotOut() {
        Battle battle = battle();
        Character aglaea = battle.characters.getFirst();
        battle.startBattle();
        Assertions.assertEquals(0, battle.summonCountOf(aglaea), "precondition: nothing is out");

        battle.fireTriggers(TriggerEvent.SKILL_CAST, aglaea, null, 0, 0);
        battle.processRequests();

        Summon tailor = battle.memospriteOf(aglaea);
        Assertions.assertNotNull(tailor, "「若衣匠不在场，则召唤忆灵衣匠」 (if the Garmentmaker is not on the field, summons the memosprite Garmentmaker (忆灵衣匠))");
        Assertions.assertEquals(0, timeRemaining(battle, aglaea), EPS, "「并使自身立即行动」 (and makes herself act immediately)");
        Assertions.assertTrue(timeRemaining(battle, tailor) > 0,
                "the advance is HERS: the memosprite's own turn is untouched (it is not memosprite skill 3 (忆灵技能3))");
    }

    /** The two halves are mutually exclusive, so a skill cast never heals and summons at once. */
    @Test
    public void onlyOneHalfOfTheSkillAppliesAtATime() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        Battle battle = new Battle(List.of(aglaea), List.of(dummy()), new Random(0));
        battle.startBattle();
        Summon tailor = battle.summonMemosprite(aglaea);
        battle.processRequests();
        double out = timeRemaining(battle, aglaea);

        battle.fireTriggers(TriggerEvent.SKILL_CAST, aglaea, null, 0, 0);

        Assertions.assertEquals(out, timeRemaining(battle, aglaea), EPS,
                "with the memosprite out, the summon branch must not run -- so she is not advanced");
        Assertions.assertEquals(1, battle.summonCountOf(aglaea));
        Assertions.assertEquals(tailor.getMaxHp(), tailor.getCurrentHp(), EPS,
                "…and the heal branch did run (a full-HP memosprite is healed to full)");
    }

    // ==================================================================
    // 2. The ultimate: summon, restore to full, act immediately
    // ==================================================================

    /** From empty: it is brought out, and she acts immediately. */
    @Test
    public void herUltimateBringsItOutAndAdvancesHer() {
        Battle battle = battle();
        Character aglaea = battle.characters.getFirst();
        battle.startBattle();

        battle.fireTriggers(TriggerEvent.ULT_CAST, aglaea, null, 0, 0);
        battle.processRequests();

        Assertions.assertEquals(1, battle.summonCountOf(aglaea), "「召唤忆灵衣匠」 (summons the memosprite Garmentmaker (忆灵衣匠))");
        Assertions.assertEquals(0, timeRemaining(battle, aglaea), EPS, "「并使自身立即行动」 (and makes herself act immediately)");
    }

    /**
     * From already-out: it is restored to full without a branch, and there is still only one.
     *
     * <p>The text's "若衣匠已在场，则使其生命值回复至上限" ("if the Garmentmaker is already on the field, restore its HP to the maximum") needs no second rule: {@code SUMMON} is idempotent, and
     * the {@code HEAL} that follows tops up whatever is standing.
     */
    @Test
    public void herUltimateTopsUpTheOneAlreadyOut() {
        Battle battle = battle();
        Character aglaea = battle.characters.getFirst();
        battle.startBattle();
        Summon tailor = battle.summonMemosprite(aglaea);
        battle.processRequests();
        tailor.takeDamage(tailor.getMaxHp() * 0.7);
        Assertions.assertTrue(tailor.getCurrentHp() < tailor.getMaxHp(), "precondition: it is damaged");

        battle.fireTriggers(TriggerEvent.ULT_CAST, aglaea, null, 0, 0);
        battle.processRequests();

        Assertions.assertEquals(1, battle.summonCountOf(aglaea), "no second memosprite");
        Assertions.assertSame(tailor, battle.memospriteOf(aglaea), "the same one");
        Assertions.assertEquals(tailor.getMaxHp(), tailor.getCurrentHp(), EPS, "「回复至上限」 (restores HP to the maximum)");
        Assertions.assertEquals(0, timeRemaining(battle, aglaea), EPS, "…and she still acts immediately");
    }

    // ==================================================================
    // 3. The authored rules state their numbers
    // ==================================================================

    /** Both abilities are filed with the effects their sentences name, in the order they name them. */
    @Test
    public void theAuthoredRulesStateTheirShape() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        Battle battle = new Battle(List.of(aglaea), List.of(dummy()), new Random(0));
        battle.startBattle();
        Summon tailor = battle.summonMemosprite(aglaea);
        Assertions.assertNotNull(tailor);

        // The skill is TWO rules, and `matching` hands back only the one whose condition holds -- which is the
        // split doing its job, so each half is read from the state it belongs to.
        TriggerTable table = TriggerTables.of(AGLAEA);
        Assertions.assertEquals(2, table.ruleCount(TriggerEvent.SKILL_CAST),
                "the skill's two sentences are two rules");

        List<TriggerTable.CompiledRule> present = table.matching(TriggerEvent.SKILL_CAST,
                new TriggerTable.TriggerContext(aglaea, aglaea, null, 0, 0, null, battle));
        Assertions.assertEquals(1, present.size(), "with the memosprite out only the heal half applies");
        EffectSpec heal = present.getFirst().effects().getFirst();
        Assertions.assertEquals("HEAL", heal.getOp());
        Assertions.assertEquals("target_max_hp", heal.getScale());
        Assertions.assertEquals(0.5, heal.getPercent(), EPS, "skill 140202's #1 at Lv10 (the prose's row)");
        Assertions.assertEquals("summon", heal.getTarget());

        Battle empty = battle();
        Character alone = empty.characters.getFirst();
        empty.startBattle();
        List<TriggerTable.CompiledRule> absent = TriggerTables.of(AGLAEA).matching(TriggerEvent.SKILL_CAST,
                new TriggerTable.TriggerContext(alone, alone, null, 0, 0, null, empty));
        Assertions.assertEquals(1, absent.size(), "with nothing out only the summon half applies");
        Assertions.assertEquals(List.of("SUMMON", "ADVANCE"), absent.getFirst().effects().stream()
                .map(EffectSpec::getOp).toList());
        Assertions.assertEquals(1.0, absent.getFirst().effects().get(1).getPercent(), EPS);
        Assertions.assertEquals("self", absent.getFirst().effects().get(1).getTarget(), "\"makes herself\" (「使自身」)");

        List<TriggerTable.CompiledRule> ultimate = TriggerTables.of(AGLAEA).matching(TriggerEvent.ULT_CAST,
                new TriggerTable.TriggerContext(aglaea, aglaea, null, 0, 0, null, battle));
        Assertions.assertEquals(1, ultimate.size());
        Assertions.assertEquals(List.of("SUMMON", "HEAL", "APPLY_BUFF", "ADVANCE"),
                ultimate.getFirst().effects().stream().map(EffectSpec::getOp).toList(),
                "the order the sentence writes them -- SUMMON must come first (the other three aim at the summon, "
                        + "and the stance is ANCHORED to it, which needs it to exist), and the stance \"Aglaea enters that stance\" (「阿格莱雅进入"
                        + "【至高之姿】 (Supreme Stance)状态」) precedes \"and makes herself act immediately\" (「并使自身立即行动」)");
        Assertions.assertEquals(1.0, ultimate.getFirst().effects().get(1).getPercent(), EPS, "restores HP to the maximum (回复至上限)");
    }

    // ==================================================================
    // 4. The stance, and whose existence ends it
    // ==================================================================

    /**
     * "阿格莱雅进入[至高之姿]状态" + "衣匠消失时阿格莱雅解除[至高之姿]状态" ("Aglaea enters the [至高之姿] state" + "when the Garmentmaker disappears, Aglaea removes the [至高之姿] state") - the state's whole
     * <b>lifecycle</b>, through the anchor ({@code ticks_on: "summon"}).
     *
     * <p><b>Why the anchor rather than a turn count.</b> The document gives the stance no duration: it ends when the
     * the Garmentmaker is gone ("行动序列上出现倒计时…回合开始时使衣匠自毁。衣匠消失时阿格莱雅解除[至高之姿]状态" - "a Countdown (倒计时) appears on the action order ... at its turn the Garmentmaker self-destructs. When the Garmentmaker disappears, Aglaea removes the [至高之姿] state"). The
     * engine's anchor <i>is</i> the tick owner - {@code BuffManager.removeBuffsAnchoredTo} asks
     * {@code buff.ticksOn(dead)} - so one field says both things, and this case measures both ends of it:
     * <ul>
     *   <li>the stance is on her right after the ultimate, and a turn of hers does <b>not</b> spend it (it is
     *       permanent, so nothing counts it down - the control for "no turn count was invented");</li>
     *   <li>and when the Garmentmaker dies, it is gone - the half a turn-count spelling could never deliver.</li>
     * </ul>
     */
    @Test
    public void herStanceEndsWhenTheGarmentmakerDoes() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        Battle battle = new Battle(List.of(aglaea), List.of(dummy()), new Random(0));
        battle.startBattle();
        Summon tailor = battle.summonMemosprite(aglaea);
        battle.processRequests();
        Assertions.assertNotNull(tailor, "precondition: the memosprite is out");

        battle.fireTriggers(TriggerEvent.ULT_CAST, aglaea, null, 0, 0);
        battle.processRequests();
        Assertions.assertTrue(aglaea.getBuffManager().hasState(STANCE), "「阿格莱雅进入【至高之姿】状态」 (Aglaea enters the Supreme Stance (【至高之姿】) state)");

        battle.beforeMove();
        battle.afterMove();
        Assertions.assertTrue(aglaea.getBuffManager().hasState(STANCE),
                "nothing counts it down: the document gives the stance NO turn count, and a turn of hers must not "
                        + "spend it (that is what 「permanent」 is for here)");

        tailor.takeDamage(tailor.getMaxHp() * 10);
        battle.processRequests();
        Assertions.assertTrue(tailor.isDeath(), "precondition: the memosprite is gone");
        Assertions.assertFalse(aglaea.getBuffManager().hasState(STANCE),
                "「衣匠消失时阿格莱雅解除【至高之姿】状态」 (Aglaea leaves the Supreme Stance state when the Garmentmaker disappears) -- the anchor's death takes it off");
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    private static Battle battle() {
        return new Battle(List.of(CharacterFactory.create(AGLAEA, LEVEL)), List.of(dummy()), new Random(0));
    }

    /** How much action value the unit still has - zero means "acts now". */
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
