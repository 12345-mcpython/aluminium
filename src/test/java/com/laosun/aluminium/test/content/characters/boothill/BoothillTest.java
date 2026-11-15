package com.laosun.aluminium.test.content.characters.boothill;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Signal;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1315 Boothill, from his own file: the Standoff state on BOTH units, the stack that both of its end conditions feed,
 * Eidolon 1's defence ignore, the enhanced basic's skill-point rule, and the per-stack toughness of 【优势口袋】.
 *
 * <p>These were four small files in the same package; one class keeps the character together without changing any
 * assertion. The helpers stay private to this class.
 */
public class BoothillTest {
    private static final int BOOTHILL = 1315;
    private static final int LEVEL = 80;
    private static final double EPS = 1e-9;
    private static final String STANDOFF = "char:1315/deadly_standoff";
    private static final String POCKET = "char:1315/edge_pocket";

    /**
     * Note: The state lands on the enemy AND on him, and both stack rules read their own event.
     */
    @Test
    public void theStandoffLandsOnBothAndBothEndsFeedTheStack() {
        Character boothill = CharacterFactory.create(BOOTHILL, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(boothill), List.of(enemy), fixed());
        battle.startBattle();

        battle.fire(TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, boothill, enemy, 0, 0));
        Assertions.assertTrue(enemy.getBuffManager().hasState(STANDOFF),
                "「使指定敌方单体及自身进入【char:1315/deadly_standoff】状态」 (puts a designated single enemy and herself into the Standoff state) -- the ENEMY");
        Assertions.assertTrue(boothill.getBuffManager().hasState(STANDOFF),
                "and HIMSELF");

        Assertions.assertEquals(0, boothill.getBuffManager().stacksOf(POCKET), "nothing before either end condition");
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.BREAK, boothill, enemy, 0, 0));
        Assertions.assertEquals(1, boothill.getBuffManager().stacksOf(POCKET),
                "「弱点被击破后…获得1层【char:1315/edge_pocket】」 (after the Weakness is Broken ... gains 1 stack of Pocket Trickshot (【char:1315/edge_pocket】))");
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.KILL, boothill, enemy, 0, 0));
        Assertions.assertEquals(2, boothill.getBuffManager().stacksOf(POCKET),
                "「该目标被消灭…后…获得1层」 (after that target is killed ... gains 1 stack) -- the OTHER event feeds it too");

        for (int i = 0; i < 4; i++) {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.BREAK, boothill, enemy, 0, 0));
        }
        Assertions.assertEquals(3, boothill.getBuffManager().stacksOf(POCKET),
                "「最多叠加3层」 (stacks at most 3 times) -- seven firings must still read three");
    }

    /**
     * 1315 Boothill (波提欧)'s Eidolon 1: "when 波提欧 deals damage, ignore 16% of the enemy target's DEF".
     *
     * <p>An unconditional passive, so it is stated at the start of the battle rather than when a damage instance is handed
     * over (a modifier added there would be one step too late for the hit it describes). Two ways: rank 1 reads 16%, rank 0 nothing.
     */
    @Test
    public void hisFirstEidolonIgnoresSixteenPercentDefence() {
        Assertions.assertEquals(0.16, defenceIgnore(1), EPS,
                "when Boothill (波提欧) deals damage, ignore the enemy target's 16% DEF");
        Assertions.assertEquals(0.0, defenceIgnore(0), EPS, "rank 0 states nothing");
    }

    /**
     * F-3: 「强化普攻无法恢复战技点」, from the data's bp_add column (2026-10-02).
     */
    @Test
    public void theEnhancedBasicRestoresNothingWhileTheOrdinaryOneRestoresAPoint() {
        int ordinary = skillPointsAfter(false);
        int enhanced = skillPointsAfter(true);
        System.out.println("[boothill_sp] skill points after an ordinary basic = " + ordinary
                + " ; after the enhanced basic inside Standoff = " + enhanced);
        Assertions.assertEquals(4, ordinary, "an ordinary basic attack restores one point (start 3)");
        Assertions.assertEquals(2, enhanced, "his Skill spent one and the enhanced basic restored none");
    }

    /**
     * 1315 波提欧's talent: 「每层【优势口袋】使强化普攻的削韧值提高 50%，最多叠加 3 层」.
     *
     * <p><b>Why per-stack, read live.</b> The sentence says 「每层」 - the CURRENT count - and the count can move between
     * the cast and the settlement. {@code BOOST_TOUGHNESS} therefore names the counter ({@code per_stack} +
     * {@code per_stack_cap}) and {@code ToughnessBoostBuff.effectivePercent} multiplies at settlement time. This judge
     * measures the enemy's toughness loss with 0, 1 and 3 stacks: 1x, 1.5x and 2.5x.
     */
    @Test
    public void eachPocketStackRaisesTheEnhancedBasicsToughness() {
        double none = toughnessLoss(0);
        double one = toughnessLoss(1);
        double three = toughnessLoss(3);
        System.out.println("[1315] toughness loss: 0=" + none + " 1=" + one + " 3=" + three);
        Assertions.assertTrue(none > 0, "precondition: the enhanced Basic reduces toughness at all");
        Assertions.assertEquals(1.5, one / none, 1e-6, "one stack is 「提高 50%」: " + one + " vs " + none);
        Assertions.assertEquals(2.5, three / none, 1e-6, "three stacks are +150%: " + three + " vs " + none);
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }

    private static double defenceIgnore(int rank) {
        Character boothill = CharacterFactory.create(BOOTHILL, LEVEL, true, null, null, rank);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(boothill), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        return boothill.getAttribute(AttributeType.DEFENCE_IGNORE).get()
                - boothill.getAttribute(AttributeType.DEFENCE_IGNORE).baseValue();
    }

    /**
     * Casts his basic once, either straight away or after his Skill has put him in Standoff.
     */
    private static int skillPointsAfter(boolean enhanced) {
        Character boothill = CharacterFactory.create(BOOTHILL, LEVEL);
        Battle battle = new Battle(List.of(boothill), List.of(EnemyFactory.create(1002011, 100, 1)), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        boothill = battle.characters().getFirst();
        if (enhanced) {
            battle.setCurrentMove(new Signal(boothill));          // useSkill takes the actor from the current move
            Assertions.assertTrue(battle.useSkill(boothill.getSkills().get(SkillType.SKILL),
                    List.of(battle.enemies().getFirst())), "precondition: his Skill is affordable");
            battle.processRequests();
            boothill = battle.characters().getFirst();
            Assertions.assertTrue(boothill.getBuffManager().hasState(STANDOFF), "precondition: Standoff is up");
        }
        battle.setCurrentMove(new Signal(boothill));
        battle.useSkill(boothill.getSkills().get(SkillType.COMMON), List.of(battle.enemies().getFirst()));
        battle.processRequests();
        return battle.getSkillPoints();
    }

    /** Toughness the enhanced Basic took off, with {@code stacks} of 【优势口袋】 on him. */
    private static double toughnessLoss(int stacks) {
        Character boothill = CharacterFactory.create(BOOTHILL, LEVEL);
        Enemy enemy = BattleFixture.monster();
        enemy.setMaxStance(1_000_000);
        enemy.setStance(1_000_000);
        // A weakness the basic's own element can break, or the reduction is refused (the engine only chips at weaknesses).
        enemy.addWeakness(com.laosun.aluminium.enums.DamageElement.PHYSICAL);
        Battle battle = new Battle(new ArrayList<>(List.of(boothill)), new ArrayList<>(List.of(enemy)),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.processRequests();
        for (int i = 0; i < stacks; i++) {
            battle.fire(new TriggerPayload.Cast(TriggerEvent.KILL, boothill, enemy, 1, 0.0,
                    null, 0, 0, List.of(enemy)));
            battle.processRequests();
        }
        Assertions.assertEquals(stacks, boothill.getBuffManager().stacksOf(POCKET),
                "precondition: " + stacks + " stacks of 【char:1315/edge_pocket】");

        double before = enemy.getStance();
        // The enhanced Basic is a data row of its own (slot 8); casting it directly keeps the criterion about the talent.
        battle.castImmediate(new com.laosun.aluminium.skill.DefaultSkill(BOOTHILL, 8, LEVEL), boothill, List.of(enemy));
        battle.processRequests();
        return before - enemy.getStance();
    }
}
