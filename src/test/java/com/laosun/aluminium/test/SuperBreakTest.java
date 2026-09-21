package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.AbstractBuff;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DefaultSkill;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.event.AttackEvent;
import com.laosun.aluminium.models.buffs.SuperBreakBuff;
import com.laosun.aluminium.utils.AttributeBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * P4-6 验收：超击破。
 *
 * <p><b>削韧值的分配口径</b>（技能标称削韧 {@code S}、敌人剩余韧性 {@code T}）：
 * <pre>
 *   T &gt; S（没打空）        击破伤害无、超击破无
 *   S = T（刚好打空）      击破伤害用 T、超击破无（没有超出部分）
 *   S &gt; T（打空且超出）    击破伤害用 T、超击破用 S−T   ← 同一发里两条链都产生
 *   敌人已 broken（T = 0）  击破伤害无、超击破用 S（整发都算超出）
 * </pre>
 * 两条链分的是同一个 {@code S}，相加恒等于 {@code S}，不重不漏。
 *
 * <p>全部用例用一个**高血量靶子**：ATK 100、HP 1,000,000、DEF 100、速度 100、**弱火**、韧性 30。
 * 敌人可以随便换韧性而不用改数据，且不会被击破伤害打死。
 * 锚点：姬子 1003 战技（Blast/Fire）= 中心 {@code single = 60}、倍率 1.0、Lv80（击破基数 376.75535）。
 */
public class SuperBreakTest {
    private static final double EPS = 1e-6;
    /** 姬子战技：Fire Blast，中心削韧 60、倍率 1.0。 */
    private static final int HIMEKO_CID = 1003;
    private static final int HIMEKO_SKILL_SLOT = 2;
    private static final double BASE_BREAK_80 = 376.75535;

    /**
     * **核心用例**：这一发把敌人打破且削韧有超出（T = 30 &lt; S = 60）
     * → 同一发里同时产生击破伤害（按 30）与超击破伤害（按超出的 30）。
     */
    @Test
    public void breakingHitProducesBothBreakAndSuperBreakDamage() {
        Character himeko = fireAttacker();
        himeko.getBuffManager().addBuff(new SuperBreakBuff(3));
        Enemy dummy = dummy(30);
        Battle battle = newBattle(himeko, dummy);

        double hpBefore = dummy.getCurrentHp();
        battle.castImmediate(new DefaultSkill(HIMEKO_CID, HIMEKO_SKILL_SLOT, 1), himeko, List.of(dummy));

        Assertions.assertTrue(dummy.isBroken(), "60 点削韧打空 30 点韧性 → 打破");
        // 技能 100×1.0 + 击破(实际 30) + 超击破(超出 30，多乘 1.4)，三者共用防御区与抗性区
        double expected = base(100 * 1.0) + breakBase(BASE_BREAK_80 * 30) + breakBase(BASE_BREAK_80 * 30 * (1 + Constant.SUPER_BREAK_BOOST));
        Assertions.assertEquals(expected, hpBefore - dummy.getCurrentHp(), EPS,
                "技能伤害 + 30 击破伤害 + 30 超击破伤害");
    }

    /**
     * 敌人**已经处于击破状态**（T = 0）→ 整发标称削韧都算超出，超击破用 S = 60。
     * 用于验证公式本体 {@code 击破基数 × (1+击破特攻) × 削韧值 × (1+超击破提高)}。
     */
    @Test
    public void superBreakUsesTheWholeStanceValueWhenTheEnemyIsAlreadyBroken() {
        Character himeko = fireAttacker();
        himeko.getBuffManager().addBuff(new SuperBreakBuff(3));
        Enemy dummy = dummy(1);                                 // 1 点韧性，一发就打空
        Battle battle = newBattle(himeko, dummy);

        battle.castImmediate(new DefaultSkill(HIMEKO_CID, HIMEKO_SKILL_SLOT, 1), himeko, List.of(dummy));
        Assertions.assertTrue(dummy.isBroken(), "把韧性打空进入击破");

        double hpBefore = dummy.getCurrentHp();
        battle.castImmediate(new DefaultSkill(HIMEKO_CID, HIMEKO_SKILL_SLOT, 1), himeko, List.of(dummy));

        // 这一发：技能 100×1.0 + 超击破 376.75535×60×1.4（整发 60 都算超出）
        double expected = base(100 * 1.0) + breakBase(BASE_BREAK_80 * 60 * (1 + Constant.SUPER_BREAK_BOOST));
        Assertions.assertEquals(expected, hpBefore - dummy.getCurrentHp(), EPS,
                "已击破 → 整发 60 点削韧都转化为超击破");
    }

    /**
     * 刚好打空（S = T = 60）→ 有击破伤害，但**没有超出部分**，所以没有超击破段。
     */
    @Test
    public void noSuperBreakWhenTheStanceIsExactlyEmptied() {
        Character himeko = fireAttacker();
        himeko.getBuffManager().addBuff(new SuperBreakBuff(3));
        Enemy dummy = dummy(60);
        Battle battle = newBattle(himeko, dummy);

        double hpBefore = dummy.getCurrentHp();
        battle.castImmediate(new DefaultSkill(HIMEKO_CID, HIMEKO_SKILL_SLOT, 1), himeko, List.of(dummy));

        Assertions.assertTrue(dummy.isBroken());
        double expected = base(100 * 1.0) + breakBase(BASE_BREAK_80 * 60);   // 只有击破，没有超击破
        Assertions.assertEquals(expected, hpBefore - dummy.getCurrentHp(), EPS, "S == T → 超出部分为 0");
    }

    /**
     * 没打空（T &gt; S）→ 不触发超击破（敌人没被破）。
     */
    @Test
    public void superBreakDoesNotTriggerWhenTheHitDoesNotBreak() {
        Character himeko = fireAttacker();
        himeko.getBuffManager().addBuff(new SuperBreakBuff(3));
        Enemy dummy = dummy(90);
        Battle battle = newBattle(himeko, dummy);

        double hpBefore = dummy.getCurrentHp();
        battle.castImmediate(new DefaultSkill(HIMEKO_CID, HIMEKO_SKILL_SLOT, 1), himeko, List.of(dummy));

        Assertions.assertFalse(dummy.isBroken());
        Assertions.assertEquals(base(100 * 1.0), hpBefore - dummy.getCurrentHp(), EPS, "没打破 → 没有超击破段");
    }

    /**
     * 未挂 buff：不产生超击破段，超出部分就是浪费（只有击破伤害）。
     */
    @Test
    public void withoutTheBuffTheOverkillStanceIsWasted() {
        Character himeko = fireAttacker();                       // 故意不挂 SuperBreakBuff
        Enemy dummy = dummy(30);
        Battle battle = newBattle(himeko, dummy);

        double hpBefore = dummy.getCurrentHp();
        battle.castImmediate(new DefaultSkill(HIMEKO_CID, HIMEKO_SKILL_SLOT, 1), himeko, List.of(dummy));

        Assertions.assertTrue(dummy.isBroken());
        double expected = base(100 * 1.0) + breakBase(BASE_BREAK_80 * 30);
        Assertions.assertEquals(expected, hpBefore - dummy.getCurrentHp(), EPS,
                "只有击破伤害（按实际削掉的 30）；超出的 30 不转化");
    }

    /**
     * **非弱点攻击打已击破的敌人照样产生超击破**。
     *
     * <p>官方文案的条件是"攻击**处于弱点击破状态**的敌方目标后，会将本次攻击的削韧值转化为
     * 1 次超击破伤害" —— 条件里只有"敌人已处于击破状态"，**没有元素限制**。
     * 所以冰属性攻击打一个已击破的火弱点靶子，技能本身不削韧（非弱点），
     * 但整发标称削韧值照样转化成超击破伤害。
     *
     * <p>（对照：未击破时非弱点攻击什么都不产生，见
     * {@link #nonWeaknessHitOnAnUnbrokenEnemyProducesNoSuperBreak}。）
     */
    @Test
    public void nonWeaknessHitOnABrokenEnemyStillProducesSuperBreak() {
        // 先把靶子打破：用火属性（是弱点），30 点韧性刚好被 60 点削韧打空
        Character himeko = fireAttacker();
        Enemy dummy = dummy(30);
        Battle battle = newBattle(himeko, dummy);
        battle.castImmediate(new DefaultSkill(HIMEKO_CID, HIMEKO_SKILL_SLOT, 1), himeko, List.of(dummy));
        Assertions.assertTrue(dummy.isBroken());

        // 换一个**冰属性**攻击者（冰不是这个靶子的弱点），并给他挂超击破 buff
        Character mar7th = Character.fromAttributes("mar7th", 100_000, 100, 100, 100);
        mar7th.setAttribute(AttributeType.ICE_DAMAGE_BOOST, new DoubleValue(0.2));
        mar7th.getBuffManager().addBuff(new SuperBreakBuff(3));

        double hpBefore = dummy.getCurrentHp();
        // 三月七普攻（1001/1）= Ice、倍率 0.5、削韧 30
        battle.castImmediate(new DefaultSkill(1001, 1, 1), mar7th, List.of(dummy));

        double iceResist = 1 - dummy.getDamageResist().getOrDefault(DamageElement.ICE, 0.0);
        // 技能段 100×0.5 ×(1+0.2 冰增伤) × 防御区 × 冰抗
        double skillDamage = 100 * 0.5 * 1.2 * defenceZone() * iceResist;
        // 超击破段：整发 30 点削韧都算超出（敌人已击破），不吃增伤，只过防御区 × 冰抗
        double superBreakDamage = BASE_BREAK_80 * 30 * (1 + Constant.SUPER_BREAK_BOOST) * defenceZone() * iceResist;
        Assertions.assertEquals(skillDamage + superBreakDamage, hpBefore - dummy.getCurrentHp(), EPS,
                "非弱点打已击破的敌人 → 照样产生超击破（用整发标称削韧 30）");
    }

    /**
     * 对照用例：**未击破**时非弱点攻击什么都不产生（削韧严格"只有弱点才削"，
     * 所以没有超出部分，也就没有超击破）。
     */
    @Test
    public void nonWeaknessHitOnAnUnbrokenEnemyProducesNoSuperBreak() {
        Character himeko = fireAttacker();
        Enemy dummy = dummy(30);
        Battle battle = newBattle(himeko, dummy);
        // 先削一部分韧性但**不打破**（火属性普攻 30 点 vs 60 点韧性，见下面的靶子）
        Character mar7th = Character.fromAttributes("mar7th", 100_000, 100, 100, 100);
        mar7th.setAttribute(AttributeType.ICE_DAMAGE_BOOST, new DoubleValue(0.2));
        mar7th.getBuffManager().addBuff(new SuperBreakBuff(3));

        double hpBefore = dummy.getCurrentHp();
        battle.castImmediate(new DefaultSkill(1001, 1, 1), mar7th, List.of(dummy));   // Ice，非弱点

        Assertions.assertFalse(dummy.isBroken());
        double iceResist = 1 - dummy.getDamageResist().getOrDefault(DamageElement.ICE, 0.0);
        double skillDamage = 100 * 0.5 * 1.2 * defenceZone() * iceResist;
        Assertions.assertEquals(skillDamage, hpBefore - dummy.getCurrentHp(), EPS,
                "未击破 + 非弱点 → 不削韧 → 没有超出部分 → 没有超击破");
    }

    /**
     * **多目标时每个目标都触发超击破**（作者口径）。
     *
     * <p>一次 AOE 攻击对每个受击目标各自结算：各自用自己的剩余韧性算超出部分、
     * 各自产生一发超击破段。所以两个已击破的靶子都会各吃到一份超击破伤害。
     */
    @Test
    public void multiTargetAttackTriggersSuperBreakOnEveryTarget() {
        Character himeko = fireAttacker();
        himeko.getBuffManager().addBuff(new SuperBreakBuff(3));
        // 两个靶子：韧性 1 点，用一发 AOE 同时打破（姬子终结技 1003/3 = AoE/Fire、all = 60、倍率 1.38）
        Enemy left = dummy(1);
        Enemy right = dummy(1);
        Battle battle = new Battle(List.of(himeko), List.of(left, right), new Random(0));

        battle.castImmediate(new DefaultSkill(HIMEKO_CID, 3, 1), himeko, List.of(left));

        Assertions.assertTrue(left.isBroken() && right.isBroken(), "两个目标都被这一发打破");
        // 每个目标：技能 100×1.38 + 击破(实际 1) + 超击破(超出 59)
        double perTarget = base(100 * 1.38)
                + breakBase(BASE_BREAK_80 * 1)
                + breakBase(BASE_BREAK_80 * 59 * (1 + Constant.SUPER_BREAK_BOOST));
        Assertions.assertEquals(dummyHp() - perTarget, left.getCurrentHp(), EPS, "左目标");
        Assertions.assertEquals(dummyHp() - perTarget, right.getCurrentHp(), EPS,
                "右目标也吃到超击破 —— 多目标时逐个触发");
    }

    /**
     * **一次攻击行为产生多种伤害类型，行为本身只算一次**。
     *
     * <p>这条钉住 {@code AttackEvent} 的 {@code totalDamage} 语义：一次攻击里有
     * 技能伤害 + 击破伤害 + 超击破伤害三种类型，它们都属于**同一次攻击**，
     * 所以攻击级事件的总额必须把三者**都**算进去（不能只算技能那一段）。
     *
     * <p>反过来讲：任何按"伤害类型"计数的角色效果（"每造成 N 次伤害"、"我方每次施放攻击后"）
     * 都应该挂在这个**攻击级**事件上，而不是要求每种伤害类型各发一次事件。
     */
    @Test
    public void oneAttackActionCarriesEveryDamageTypeInItsTotal() {
        Character himeko = fireAttacker();
        himeko.getBuffManager().addBuff(new SuperBreakBuff(3));
        AttackTotalRecorder recorder = new AttackTotalRecorder();
        himeko.getBuffManager().addBuff(recorder);
        Enemy dummy = dummy(30);
        Battle battle = newBattle(himeko, dummy);

        double hpBefore = dummy.getCurrentHp();
        battle.castImmediate(new DefaultSkill(HIMEKO_CID, HIMEKO_SKILL_SLOT, 1), himeko, List.of(dummy));

        double total = hpBefore - dummy.getCurrentHp();
        Assertions.assertEquals(1, recorder.totals.size(), "一次攻击 → 恰好广播一次 AttackEvent");
        Assertions.assertEquals(total, recorder.totals.getFirst(), EPS,
                "总额包含技能伤害 + 30 击破伤害 + 30 超击破伤害三部分");
    }

    /** 记录每次 {@code AttackEvent} 的 totalDamage（一次攻击应只收到一次）。 */
    private static final class AttackTotalRecorder extends AbstractBuff implements AttackEvent {
        private final List<Double> totals = new ArrayList<>();

        private AttackTotalRecorder() {
            super(1, false);
        }

        @Override
        public boolean canAct() {
            return true;
        }

        @Override
        public void applyEffect(CanHit target) {
        }

        @Override
        public void removeBuff(CanHit target) {
        }

        @Override
        public void tickEffect(CanHit target) {
            decreaseDuration();
        }

        @Override
        public void afterAttack(Battle battle, CanHit attacker, CanHit mainTarget,
                                List<? extends CanHit> hitTargets, double totalDamage) {
            totals.add(totalDamage);
        }
    }

    // ==================================================================
    // 工具
    // ==================================================================

    /** 姬子：ATK 100（便于手算）、Fire、Lv80。 */
    private static Character fireAttacker() {
        Character c = Character.fromAttributes("himeko", 100_000, 100, 100, 100);
        c.setAttribute(AttributeType.ATTACK, new DoubleValue(100));
        c.setAttribute(AttributeType.FIRE_DAMAGE_BOOST, new DoubleValue(0.2));
        return c;
    }

    /** 靶子的初始生命上限（见 {@link #dummy(double)}）。 */
    private static double dummyHp() {
        return 1_000_000;
    }

    /**
     * 高血量靶子：HP 1,000,000 / DEF 100 / 速度 100 / **弱火** / 火抗 0.2 / 冰抗 0.2 / 韧性 = 参数。
     */
    private static Enemy dummy(double stance) {
        AttributeBuilder builder = new AttributeBuilder();
        builder.setBase(AttributeType.HEALTH, dummyHp())
                .setBase(AttributeType.DEFENCE, 100)
                .setBase(AttributeType.ATTACK, 100)
                .setBase(AttributeType.SPEED, 100);
        Enemy enemy = new Enemy("dummy", builder.build());
        enemy.setLevel(80);
        enemy.setStanceWeak(Set.of(DamageElement.FIRE));
        enemy.setStance(stance);
        enemy.setMaxStance(stance);
        enemy.setDamageResist(java.util.Map.of(
                DamageElement.FIRE, 0.2,
                DamageElement.ICE, 0.2));
        return enemy;
    }

    /** 攻击者 Lv80 时防御区的等级项。 */
    private static double defenceZone() {
        double levelTerm = Constant.DEFENCE_CONST + Constant.DEFENCE_PER_LEVEL * 80;
        return levelTerm / (100 + levelTerm);
    }

    /**
     * 把一段 base 过一遍增伤区、防御区与抗性区：{@code base × 1.2 × 防御区 × 抗性区}。
     *
     * <p>{@code × 1.2} 来自攻击者的 {@code FIRE_DAMAGE_BOOST = 0.2}（增伤区 = {@code 1 + Σ}）；
     * 靶子等级 = 攻击者等级 = 80 ⇒ 防御区 {@code 1000/1100}；火抗 0.2 ⇒ 抗性区 0.8。
     * 超击破段**不吃增伤区**（{@code DamageType.SUPER_BREAK.isBoostable() == false}），
     * 所以它那一段在调用处显式不乘 1.2。
     */
    private static double base(double value) {
        return value * 1.2 * defenceZone() * (1 - 0.2);
    }

    /** 击破 / 超击破段：不吃增伤区，只过防御区与抗性区。 */
    private static double breakBase(double value) {
        return value * defenceZone() * (1 - 0.2);
    }

    private static Battle newBattle(Character hero, Enemy enemy) {
        return new Battle(List.of(hero), List.of(enemy), new Random(0));
    }
}
