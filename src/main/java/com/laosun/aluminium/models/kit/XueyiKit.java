package com.laosun.aluminium.models.kit;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.Eidolon;
import com.laosun.aluminium.beans.SkillPoint;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Element;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Buff;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DamageCalculator;
import com.laosun.aluminium.models.DamageCalculator.DamageContext;
import com.laosun.aluminium.models.DamageCalculator.DamageType;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.SkillData;
import com.laosun.aluminium.models.Trace;

import java.util.List;
import java.util.Map;

import static com.laosun.aluminium.models.kit.KitSupport.intParam;
import static com.laosun.aluminium.models.kit.KitSupport.param;

/**
 * 雪衣 (Xueyi, cid 1214) — 量子属性 毁灭.
 *
 * <p>核心机制【恶报】: 攻击削减敌方韧性时叠加层数 (队友削减韧性也叠加), 叠至上限后
 * 消耗所有【恶报】立即发动追加攻击 (3次随机单体伤害)。由行迹 伺观中枢 的计数器字段
 * 代理 (每次攻击行为近似1层, 并累计溢出层数); 层数上限来自天赋 (8层, 星魂6 降至6层)。
 *
 * <p>终结技 无视弱点属性削减韧性: 引擎的 {@link Battle#breakToughness} 会检查弱点,
 * 因此代理为直接操作 {@link Enemy} 的韧性字段绕过检查, 击破时手动触发量子弱点击破效果
 * (纠缠)。"削减的韧性越多伤害越高" 以本次削韧量/韧性上限的比例近似。
 */
public final class XueyiKit implements CharacterKit {

    @Override
    public int cid() {
        return 1214;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new XueyiOmen(param(byId, 1214101, 0, 1.0), param(byId, 1214101, 1, 2.4)),
                new XueyiUltraBoost(param(byId, 1214102, 0, 0.5), param(byId, 1214102, 1, 0.1)),
                new XueyiHub(intParam(byId, 1214103, 0, 6)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new XueyiE1(param(e, 0, 0.4));
            case 2 -> new XueyiE2(param(e, 0, 0.05));
            case 4 -> new XueyiE4(param(e, 0, 0.4), intParam(e, 1, 2));
            case 6 -> new XueyiE6(intParam(e, 0, 6));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 3 -> XueyiKit::xueyiUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /**
     * 终结技 天罚贯身: 对指定敌方单体造成#1%攻击力的量子属性伤害, 本次攻击无视弱点属性
     * 削减韧性, 击破弱点时触发量子弱点击破效果; 削减的韧性越多伤害越高, 最多提高#3。
     * (星魂4: 施放终结技时击破特攻提高#1, 持续#2回合.)
     */
    static void xueyiUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        double bonusCap = ctx.param(2, 0.36);
        // 星魂4: 施放终结技时击破特攻提高.
        if (user instanceof Character character) {
            XueyiE4 e4 = findTrace(character, XueyiE4.class);
            if (e4 != null) {
                user.removeBuff("断业根");
                Buff buff = new Buff("断业根", Buff.Category.BUFF, user, user, e4.turns)
                        .stat(AttributeType.BREAKING_EFFECT, DoubleValue.Modifier.pure(e4.boost,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(user, buff);
                IO.println("  " + user.getName() + " break effect +"
                        + String.format("%.0f%%", e4.boost * 100) + " (断业根)");
            }
        }
        CanHit main = targets.getFirst();
        if (main instanceof Enemy enemy) {
            double units = ctx.stanceSingle();
            // 削减韧性越多伤害越高: 以削韧量占韧性上限的比例近似, 最多#3.
            double reduced = Math.min(enemy.getCurrentToughness(), units);
            double ratio = enemy.getMaxToughness() > 0 ? reduced / enemy.getMaxToughness() : 0;
            double bonus = Math.min(bonusCap, ratio);
            reduceToughnessIgnoreWeakness(battle, user, enemy, units);
            battle.dealAttackDamage(user, enemy, multiplier * (1 + bonus), 0,
                    DamageContext.of(DamageType.ULTRA, Element.QUANTUM));
            IO.println("  " + user.getName() + " 天罚贯身: toughness cut "
                    + String.format("%.1f", reduced) + "/" + String.format("%.1f", enemy.getMaxToughness())
                    + " -> damage +" + String.format("%.0f%%", bonus * 100));
        }
    }

    /**
     * 无视弱点削减韧性: {@link Enemy#reduceToughness} 会拒绝非弱点的削韧,
     * 因此直接操作韧性字段, 击破时手动触发量子弱点击破效果 (纠缠 + 击破伤害 + 行动延后)。
     */
    static void reduceToughnessIgnoreWeakness(Battle battle, CanHit attacker, Enemy enemy, double units) {
        if (enemy.isBroken() || units <= 0) {
            return;
        }
        double reduced = Math.min(enemy.getCurrentToughness(), units);
        enemy.setCurrentToughness(Math.max(0, enemy.getCurrentToughness() - reduced));
        IO.println("[TOUGHNESS] " + enemy.getName() + " -" + String.format("%.1f", reduced)
                + " (无视弱点, " + String.format("%.0f/%.0f", enemy.getCurrentToughness(), enemy.getMaxToughness()) + ")");
        if (enemy.getCurrentToughness() <= 0) {
            enemy.setBroken(true);
            enemy.setBreakElement(Element.QUANTUM);
            quantumBreak(battle, attacker, enemy);
        }
    }

    /** 量子弱点击破效果: 击破伤害 + 纠缠 (禁锢, 回复时延迟行动并造成附加伤害) + 25%行动延后. */
    private static void quantumBreak(Battle battle, CanHit attacker, Enemy enemy) {
        double units = Math.max(1, enemy.getMaxToughness() / 30.0);
        double breakDamage = DamageCalculator.calculateBreakDamage(attacker, units);
        IO.println("[BREAK!] " + enemy.getName() + " broken (无视弱点) for "
                + String.format("%.0f", breakDamage) + " break DMG!");
        battle.applyDamage(enemy, breakDamage);
        Buff entanglement = new Buff("Entanglement", Buff.Category.DEBUFF, attacker, enemy, 1)
                .control(Buff.ControlType.IMPRISONED)
                .delayOnExpire(0.2)
                .damageOnExpire(breakDamage * 0.6);
        enemy.applyBuff(entanglement);
        enemy.setControlState(Buff.ControlType.IMPRISONED);
        IO.println("  -> " + enemy.getName() + " is ENTANGLED!");
        battle.delayByPercent(enemy, 0.25);
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 预兆机杼: 使自身造成的伤害提高, 提高数值等同于击破特攻的#1, 最多提高#2。 */
    static class XueyiOmen implements Trace {
        private final double breakRatio;
        private final double cap;

        XueyiOmen(double breakRatio, double cap) {
            this.breakRatio = breakRatio;
            this.cap = cap;
        }

        @Override
        public String getName() {
            return "预兆机杼";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            double breaking = owner.getAttribute(AttributeType.BREAKING_EFFECT) != null
                    ? owner.getAttribute(AttributeType.BREAKING_EFFECT).get() : 0;
            return 1 + Math.min(cap, breaking * breakRatio);
        }
    }

    /** 摧锋轴承: 如果敌方目标当前韧性大于等于其韧性上限的#1, 施放终结技时伤害提高#2。 */
    static class XueyiUltraBoost implements Trace {
        private final double toughnessRatio;
        private final double bonus;

        XueyiUltraBoost(double toughnessRatio, double bonus) {
            this.toughnessRatio = toughnessRatio;
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "摧锋轴承";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            if (type != SkillType.ULTRA || !(defender instanceof Enemy enemy)) {
                return 1.0;
            }
            double ratio = enemy.getMaxToughness() > 0
                    ? enemy.getCurrentToughness() / enemy.getMaxToughness() : 0;
            return ratio >= toughnessRatio ? 1 + bonus : 1.0;
        }
    }

    /**
     * 伺观中枢: 雪衣会累计溢出的【恶报】层数, 最多累计#1层; 触发天赋后获得相应溢出层数。
     * 兼作天赋 十王圣断，业报恒常 的代理: 雪衣攻击削减敌方韧性时叠加【恶报】(每次攻击
     * 行为近似1层, 上限#1), 队友施放攻击后叠加#3层; 叠至上限时消耗全部【恶报】立即
     * 发动追加攻击 (3次随机单体, 每次#2%攻击力的量子属性伤害; 追加攻击无法叠加恶报)。
     */
    static class XueyiHub implements Trace {
        private final int overflowCap;
        private int stacks = 0;
        private int overflow = 0;

        XueyiHub(int overflowCap) {
            this.overflowCap = overflowCap;
        }

        @Override
        public String getName() {
            return "伺观中枢";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.COMMON || type == SkillType.SKILL || type == SkillType.ULTRA) {
                gain(battle, owner, 1);
            }
        }

        @Override
        public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                 List<? extends CanHit> targets) {
            if (type == SkillType.COMMON || type == SkillType.SKILL || type == SkillType.ULTRA) {
                // 天赋: 当雪衣的队友施放攻击削减敌方韧性后, 雪衣叠加#3层恶报.
                gain(battle, owner, allyStacks(owner));
            }
        }

        private int allyStacks(Character owner) {
            SkillData talent = KitSupport.skillData(owner, SkillType.TALENT);
            if (talent != null && talent.getSkills() != null && !talent.getSkills().isEmpty()
                    && talent.getSkills().getFirst().size() > 2) {
                return (int) Math.round(talent.getSkills().getFirst().get(2));
            }
            return 1;
        }

        private void gain(Battle battle, Character owner, int amount) {
            if (amount <= 0) {
                return;
            }
            int cap = cap(owner);
            int total = stacks + amount;
            if (total > cap) {
                overflow = Math.min(overflowCap, overflow + (total - cap));
                stacks = cap;
            } else {
                stacks = total;
            }
            IO.println("  [行迹] " + owner.getName() + " 【恶报】 " + stacks + "/" + cap
                    + (overflow > 0 ? " (溢出 " + overflow + ")" : ""));
            if (stacks >= cap) {
                followUp(battle, owner);
            }
        }

        private int cap(Character owner) {
            int cap = 8;
            SkillData talent = KitSupport.skillData(owner, SkillType.TALENT);
            if (talent != null && talent.getSkills() != null && !talent.getSkills().isEmpty()
                    && !talent.getSkills().getFirst().isEmpty()) {
                cap = (int) Math.round(talent.getSkills().getFirst().get(0));
            }
            // 星魂6: 恶报的层数上限降低至#1层.
            XueyiE6 e6 = findTrace(owner, XueyiE6.class);
            if (e6 != null) {
                cap = e6.cap;
            }
            return cap;
        }

        /** 【恶报】叠满: 立即发动追加攻击, 3次随机单体伤害; 随后获得溢出的恶报层数. */
        private void followUp(Battle battle, Character owner) {
            double multiplier = 0.45;
            SkillData talent = KitSupport.skillData(owner, SkillType.TALENT);
            if (talent != null && talent.getSkills() != null && !talent.getSkills().isEmpty()
                    && talent.getSkills().getFirst().size() > 1) {
                multiplier = talent.getSkills().getFirst().get(1);
            }
            double followUpBonus = 0;
            double healRatio = 0;
            XueyiE1 e1 = findTrace(owner, XueyiE1.class);
            if (e1 != null) {
                followUpBonus = e1.bonus;
            }
            XueyiE2 e2 = findTrace(owner, XueyiE2.class);
            if (e2 != null) {
                healRatio = e2.healRatio;
            }
            List<Enemy> alive = battle.getAliveEnemies();
            IO.println("  [行迹] " + owner.getName() + " 【恶报】 maxed: talent follow-up (3 hits)!");
            for (int i = 0; i < 3 && !alive.isEmpty(); i++) {
                Enemy target = alive.get((int) (Math.random() * alive.size()));
                if (target.isDeath()) {
                    continue;
                }
                battle.dealAttackDamage(owner, target, multiplier * (1 + followUpBonus), 0,
                        DamageContext.of(DamageType.FOLLOW_UP, Element.QUANTUM));
                // 星魂2: 追加攻击无视弱点削减韧性, 击破时触发量子弱点击破效果.
                if (e2 != null) {
                    reduceToughnessIgnoreWeakness(battle, owner, target, 0.5);
                } else {
                    battle.breakToughness(owner, target, 0.5);
                }
            }
            // 星魂2: 为自身恢复生命上限#1%的生命值.
            if (healRatio > 0) {
                battle.healTarget(owner, owner, owner.getMaxHp() * healRatio);
            }
            // 追加攻击不叠加恶报; 获得溢出的恶报层数 (最多累计#1层).
            stacks = Math.min(overflowCap, overflow);
            overflow = 0;
            IO.println("  [行迹] " + owner.getName() + " 恶报 reset -> " + stacks + " (溢出回填)");
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 缚心魔: 天赋的追加攻击造成的伤害提高#1。 (由行迹 伺观中枢 读取.) */
    static class XueyiE1 implements Trace {
        private final double bonus;

        XueyiE1(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "星魂1";
        }
    }

    /**
     * 星魂2 破五尘: 天赋的追加攻击无视弱点属性削减敌方韧性, 同时为自身恢复生命上限#1%
     * 的生命值; 击破弱点时触发量子属性的弱点击破效果。 (由行迹 伺观中枢 读取.)
     */
    static class XueyiE2 implements Trace {
        private final double healRatio;

        XueyiE2(double healRatio) {
            this.healRatio = healRatio;
        }

        @Override
        public String getName() {
            return "星魂2";
        }
    }

    /**
     * 星魂4 断业根: 施放终结技时, 击破特攻提高#1, 持续#2回合。
     * (由终结技行为在施放时施加.)
     */
    static class XueyiE4 implements Trace {
        private final double boost;
        private final int turns;

        XueyiE4(double boost, int turns) {
            this.boost = boost;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "星魂4";
        }
    }

    /** 星魂6 司死生: 【恶报】的层数上限降低至#1层。 (由行迹 伺观中枢 读取.) */
    static class XueyiE6 implements Trace {
        private final int cap;

        XueyiE6(int cap) {
            this.cap = cap;
        }

        @Override
        public String getName() {
            return "星魂6";
        }
    }

    // ─── 工具 ───────────────────────────────────────────────────────────

    private static <T extends Trace> T findTrace(CanHit user, Class<T> type) {
        if (!(user instanceof Character character)) {
            return null;
        }
        for (Trace trace : character.getTraces()) {
            if (type.isInstance(trace)) {
                return type.cast(trace);
            }
        }
        return null;
    }
}
