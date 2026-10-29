package com.laosun.aluminium.test.content.characters.archer;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.skill.DefaultSkill;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1015 Archer (Archer), 战技 伪•螺旋剑 (101502) and its 【回路连接】loop:
 *
 * <p>「进入【回路连接】状态。…【回路连接】状态下施放战技后，本回合不会结束，并使Archer<b>战技造成的伤害提高 100%</b>，该效果可以
 * 叠加 <b>2</b> 层，持续至退出【回路连接】状态。主动施放 <b>5</b> 次战技后或战技点不足以再次施放战技则退出【回路连接】状态。
 * 每个波次中的所有敌方目标被消灭后会退出【回路连接】状态。」
 *
 * <p>⚠ <b>What is read, and why not the damage number.</b> Each cast settles one instance whose {@code BoostArea} rate IS
 * {@code 1 + Σ boost} - so the layer's contribution is visible exactly as "+1.0 on that factor", and the entering cast can
 * be pinned against the boost the character already has from her traces (0.224 of quantum DMG boost). Asserting on the
 * settled damage instead would mix in every other zone and, worse, a wrong "one layer on the entering cast" would keep the
 * per-cast RATIOS perfectly consistent.
 *
 * <p>⚠ Every number in the clause is a place to be off by one: the entering cast must not be boosted, cast 2 must gain one
 * layer, cast 3 must hit the ceiling, cast 4 must stay there, and cast 5 must still carry the ceiling (the exit happens
 * <em>after</em> it settles) while the state, the layers and the counter are gone afterwards.
 */
public class ArcherLoopTest {
    private static final int ARCHER = 1015;
    private static final int ALLY = 1204;
    private static final String LOOP = "回路连接";
    private static final String CYCLES = "回路次数";
    private static final String LAYERS = "回路层数";
    /** The manual exit button: {@code skills.json} 1015 slot 9 ({@code 101509}, effect {@code Enhance}). */
    private static final int EXIT_SLOT = 9;

    /**
     * Five casts: no layer, +1, +2 (ceiling), +2, +2 - then the state, the layers and the counter are cleared.
     */
    @Test
    public void theLoopRaisesSkillDamageOneLayerPerCastAndEndsAfterFive() {
        Fixture f = new Fixture();
        double traceBoost = f.archer.getAttribute(AttributeType.QUANTUM_DAMAGE_BOOST).get();
        List<Double> factors = new ArrayList<>();
        List<Integer> layers = new ArrayList<>();
        for (int cast = 0; cast < 5; cast++) {
            factors.add(f.castSkillAndReadBoostFactor());
            layers.add(f.archer.getBuffManager().stacksOf(LAYERS));
            System.out.println("[1015] 第 " + (cast + 1) + " 发: 增伤区 ×" + factors.get(cast)
                    + " | 层数 " + layers.get(cast)
                    + " | 次数 " + f.archer.getResources().value(CYCLES)
                    + " | 状态 " + f.archer.getBuffManager().hasState(LOOP));
        }
        Assertions.assertEquals(1 + traceBoost, factors.get(0), 1e-9,
                "进状态那一发**不吃**加成：增伤区只有行迹那 " + traceBoost + "（多一层就会是 +1.0 以上）");
        Assertions.assertEquals(factors.get(0) + 1.0, factors.get(1), 1e-9, "第 2 发：+100%（1 层）");
        Assertions.assertEquals(factors.get(1) + 1.0, factors.get(2), 1e-9, "第 3 发：再 +100%（2 层）");
        Assertions.assertEquals(factors.get(2), factors.get(3), 1e-9, "第 4 发：2 层封顶，不再叠");
        Assertions.assertEquals(factors.get(2), factors.get(4), 1e-9,
                "第 5 发**照样**带满加成 —— 退出发生在它结算之后");

        Assertions.assertEquals(List.of(1, 2, 2, 2, 0), layers, "层数逐发 +1、封顶 2、退出时清空");
        Assertions.assertFalse(f.archer.getBuffManager().hasState(LOOP), "「主动施放 5 次战技后…退出」");
        Assertions.assertEquals(0, f.archer.getResources().value(CYCLES), "退出时次数计数器被清空");
    }

    /**
     * 「或战技点不足以再次施放战技则退出」 - an empty pool ends the loop.
     */
    @Test
    public void theLoopEndsWhenTheSkillPointsRunOut() {
        Fixture f = new Fixture();
        f.castSkillAndReadBoostFactor();
        Assertions.assertTrue(f.archer.getBuffManager().hasState(LOOP), "precondition: the first cast entered the loop");

        while (f.battle.getSkillPoints() > 0) {
            f.battle.getSkillPointPolicy().spend();
        }
        f.castSkillAndReadBoostFactor();
        System.out.println("[1015] 手里 0 点之后再施放一次战技: 状态 " + f.archer.getBuffManager().hasState(LOOP));
        Assertions.assertFalse(f.archer.getBuffManager().hasState(LOOP), "手里 0 点 ⇒ 不足以再次施放 ⇒ 退出");
    }

    /**
     * And one remaining point is NOT "insufficient": the loop survives it.
     */
    @Test
    public void oneRemainingSkillPointIsStillEnough() {
        Fixture f = new Fixture();
        f.castSkillAndReadBoostFactor();
        while (f.battle.getSkillPoints() > 1) {
            f.battle.getSkillPointPolicy().spend();
        }
        Assertions.assertEquals(1, f.battle.getSkillPoints(), "precondition: exactly one point left");
        f.castSkillAndReadBoostFactor();
        Assertions.assertTrue(f.archer.getBuffManager().hasState(LOOP),
                "手里的 1 点**还够**再放一次 ⇒ 不退出（写成 `skill_point <= 1` 会在这里红）");
    }

    /**
     * 「每个波次中的所有敌方目标被消灭后会退出」 - read through the next wave's start.
     */
    @Test
    public void theLoopEndsWhenTheWaveClears() {
        Fixture f = new Fixture();
        f.castSkillAndReadBoostFactor();
        Assertions.assertTrue(f.archer.getBuffManager().hasState(LOOP), "precondition: the loop is on");

        f.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.WAVE_START, null, null, 0, 0));
        f.battle.processRequests();
        Assertions.assertFalse(f.archer.getBuffManager().hasState(LOOP), "波次清空 ⇒ 退出");
        Assertions.assertEquals(0, f.archer.getResources().value(CYCLES), "并清空计数器");
        Assertions.assertEquals(0, f.archer.getBuffManager().stacksOf(LAYERS), "并清空层数");
    }

    /**
     * 【结束】 (slot 9) is the manual exit: casting it leaves the loop.
     */
    @Test
    public void theManualExitLeavesTheLoop() {
        Fixture f = new Fixture();
        f.castSkillAndReadBoostFactor();
        Assertions.assertTrue(f.archer.getBuffManager().hasState(LOOP), "precondition: the loop is on");

        f.battle.castImmediate(new DefaultSkill(ARCHER, EXIT_SLOT, 1), f.archer,
                List.of(f.battle.enemyUnits().getFirst()));
        f.battle.processRequests();
        Assertions.assertFalse(f.archer.getBuffManager().hasState(LOOP), "【结束】= 退出【回路连接】");
    }

    // ==================================================================

    private static final class Fixture {
        private final Character archer = CharacterFactory.create(ARCHER, 80);
        private final Character ally = CharacterFactory.create(ALLY, 80);
        private final Battle battle = new Battle(List.of(archer, ally), List.of(BattleFixture.dummy()),
                BattleFixture.alwaysRoll(0.9999));
        private final List<Damage> settled = new ArrayList<>();

        private Fixture() {
            battle.startBattle();
            battle.processRequests();
            battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        }

        /**
         * Cast the Skill once and return the {@code BoostArea} factor of the instance it settled.
         *
         * <p>⚠ The instance is typed {@code NORMAL}: the engine types every non-Elation skill's own damage as NORMAL, which
         * is exactly why the loop's bonus cannot be spelled as a damage type (see the rule's note) - it is spelled as the
         * CONDITION {@code from_skill SKILL}.
         */
        private double castSkillAndReadBoostFactor() {
            int mark = settled.size();
            battle.castImmediate(archer.getSkills().get(SkillType.SKILL), archer,
                    List.of(battle.enemyUnits().getFirst()));
            battle.processRequests();
            return settled.subList(mark, settled.size()).stream()
                    .filter(d -> d.getType() == DamageType.NORMAL)
                    .map(d -> d.breakdown().get("BoostArea"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("the Skill settled no ordinary instance"));
        }
    }
}
