package com.laosun.aluminium.test.content.characters.jade1314;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1314 翡翠's talent (the charge that pays a follow-up at 8) and her Eidolons 1 / 6 built on it.
 *
 * <p><b>Why the talent comes first.</b> Both Eidolon clauses in this case are ABOUT that follow-up (「天赋的追加攻击伤害提高
 * 32%」) and about the charge it spends (「额外获得 1/2 点充能」), so writing them on an unwritten talent would have been a
 * rule with nothing to amend.
 *
 * <p>The charge is read through her own resource, the follow-up through the enemy's HP: 「每击中 1 名」 is a PER-HIT gain, so
 * one attack of two hits must pay twice.
 */
public class JadeTalentAndEidolonsTest {
    private static final int JADE = 1314;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final String CHARGE = "充能";
    private static final String COLLECTOR = "收债人";
    private static final String GOODS = "当品";

    /**
     * 「每击中 1 名敌方目标均可获得 1 点充能」 - and it is per hit, not per attack.
     */
    @Test
    public void theTalentPaysOneChargePerHit() {
        Run run = new Run(0);
        run.attack(run.jade, 2);
        Assertions.assertEquals(2, run.charge(), "two targets hit, two points of charge");
        run.attack(run.jade, 1);
        Assertions.assertEquals(3, run.charge(), "a third hit is a third point");
    }

    /**
     * 「翡翠**或**成为【收债人】的目标」 - the collector's attacks pay her charge too.
     */
    @Test
    public void theCollectorsAttacksPayHerCharge() {
        Run run = new Run(0);
        run.attack(run.ally, 2);
        Assertions.assertEquals(0, run.charge(), "no state, no charge");

        run.makeAllyTheCollector();
        run.attack(run.ally, 2);
        Assertions.assertEquals(2, run.charge(), "now the collector's two hits pay");
    }

    /**
     * 「充能到达 8 点后，消耗 8 点充能，发动 1 次追加攻击…立即获得 5 层【当品】」.
     */
    @Test
    public void eightChargePaysTheFollowUpAndFiveGoods() {
        Run run = new Run(0);
        for (int i = 0; i < 3; i++) {
            run.attack(run.jade, 2);                      // 6 charge, below the threshold
        }
        Assertions.assertEquals(6, run.charge());
        double before = run.enemy.getCurrentHp();

        run.attack(run.jade, 2);                          // 8 charge: the follow-up fires

        Assertions.assertEquals(0, run.charge(), "「消耗 8 点充能」");
        Assertions.assertTrue(before - run.enemy.getCurrentHp() > 0, "the follow-up deals damage");
        // 5 from the follow-up plus the 1 her trace (逆回购) grants at battle start.
        Assertions.assertEquals(6, run.stacks(GOODS), "「发动…追加攻击时立即获得 5 层【当品】」");
    }
    /**
     * Eidolon 1, the charge half: 「击中数量等于 2/1 名时，翡翠额外获得 1/2 点充能」.
     *
     * <p>⚠ The extra is ADDITIVE: the talent still pays one point per hit, so a two-hit collector attack is 2 + 1 at E1
     * and 2 at E0.
     */
    @Test
    public void theFirstEidolonPaysExtraChargeForSmallHits() {
        Run twoHitsE0 = new Run(0);
        twoHitsE0.makeAllyTheCollector();
        twoHitsE0.attack(twoHitsE0.ally, 2);
        Assertions.assertEquals(2, twoHitsE0.charge(), "E0 pays only the talent own point per hit");

        Run twoHitsE1 = new Run(1);
        twoHitsE1.makeAllyTheCollector();
        twoHitsE1.attack(twoHitsE1.ally, 2);
        Assertions.assertEquals(3, twoHitsE1.charge(), "「等于 2 名…额外获得 1 点」");

        Run oneHitE1 = new Run(1);
        oneHitE1.makeAllyTheCollector();
        oneHitE1.attack(oneHitE1.ally, 1);
        Assertions.assertEquals(3, oneHitE1.charge(), "one hit: 1 from the talent + 2 extra");

        Run threeHitsE1 = new Run(1);
        threeHitsE1.makeAllyTheCollector();
        threeHitsE1.attack(threeHitsE1.ally, 3);
        Assertions.assertEquals(3, threeHitsE1.charge(),
                "three hits are not one of the sentence tiers (exactly 2 or exactly 1)");
    }

    /**
     * 「每层【当品】使暴击伤害提高 2.40%」 - a LIVE reading: the stacks the follow-up just granted are already in the panel.
     */
    @Test
    public void everyGoodRaisesHerCritDamage() {
        Run run = new Run(0);
        double bare = run.jade.getAttribute(AttributeType.CRIT_ATTACK).get();
        for (int i = 0; i < 3; i++) {
            run.attack(run.jade, 2);                      // 6 charge
        }
        run.attack(run.jade, 2);                          // the follow-up grants 5 层
        Assertions.assertEquals(6, run.stacks(GOODS), "5 from the follow-up + 1 from the trace at battle start");
        // `bare` already carries the trace layer (1 当品 at battle start), so the DELTA is the five the follow-up added.
        Assertions.assertEquals(5 * 0.024, run.jade.getAttribute(AttributeType.CRIT_ATTACK).get() - bare, 1e-6,
                "每层 +2.40% read through `per_stack_live`");
    }

    public void theFirstEidolonRaisesTheFollowUpsDamageByThirtyTwoPercent() {
        double plain = followUpDamage(0);
        double raised = followUpDamage(1);
        System.out.println("[1314-E1] follow-up damage: E0=" + plain + " ; E1=" + raised);
        Assertions.assertEquals(1.32, raised / plain, 1e-6, "「天赋的追加攻击伤害提高 32%」 - multiplicative");
    }

    /**
     * Eidolon 6: she becomes a collector, with 20% QUANTUM penetration.
     */
    @Test
    public void theSixthEidolonMakesHerACollectorWithQuantumPenetration() {
        Run e5 = new Run(5);
        Run e6 = new Run(6);
        Assertions.assertFalse(e5.jade.getBuffManager().hasState(COLLECTOR), "E5 is not one");
        Assertions.assertTrue(e6.jade.getBuffManager().hasState(COLLECTOR), "「同时翡翠获得【收债人】状态」");
        Assertions.assertEquals(0.2, e6.jade.getAttribute(AttributeType.DAMAGE_PENETRATION_QUANTUM).get()
                - e5.jade.getAttribute(AttributeType.DAMAGE_PENETRATION_QUANTUM).get(), 1e-9,
                "「量子属性抗性穿透提高 20%」");
    }

    // ==================================================================

    private static final class Run {
        private final Character jade;
        private final Character ally;
        private final Enemy enemy;
        private final Battle battle;

        Run(int eidolon) {
            jade = CharacterFactory.create(JADE, LEVEL);
            jade.setEidolonRank(eidolon);
            // A crit roll would make every damage ratio a coin toss, so it is pinned off.
            jade.setAttribute(AttributeType.CRIT_CHANCE, new DoubleValue(0));
            ally = CharacterFactory.create(ALLY, LEVEL);
            ally.setAttribute(AttributeType.CRIT_CHANCE, new DoubleValue(0));
            enemy = BattleFixture.monster();
            battle = new Battle(List.of(jade, ally), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        void attack(Character actor, int hitCount) {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, actor, enemy, hitCount, 0));
            battle.processRequests();
        }

        /** Her Skill puts 【收债人】 on a target; cast it at the ally. */
        void makeAllyTheCollector() {
            battle.castImmediate(new com.laosun.aluminium.skill.DefaultSkill(JADE, 2, LEVEL), jade, List.of(ally));
            battle.processRequests();
        }

        int charge() {
            return jade.getResources().get(CHARGE).getValue();
        }

        int stacks(String buff) {
            return jade.getBuffManager().stacksOf(buff);
        }
    }

    /** The follow-up's damage at a rank: charge to 8 and measure the enemy's loss. */
    private static double followUpDamage(int eidolon) {
        Run run = new Run(eidolon);
        for (int i = 0; i < 3; i++) {
            run.attack(run.jade, 2);                      // 6
        }
        double before = run.enemy.getCurrentHp();
        run.attack(run.jade, 2);                          // 8: the follow-up
        return before - run.enemy.getCurrentHp();
    }
}
