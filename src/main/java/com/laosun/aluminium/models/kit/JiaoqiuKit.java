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
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.SkillData;
import com.laosun.aluminium.models.Trace;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.laosun.aluminium.models.kit.KitSupport.intParam;
import static com.laosun.aluminium.models.kit.KitSupport.param;

/**
 * 椒丘 (Jiaoqiu, cid 1218) — 火属性 虚无.
 *
 * <p>核心机制【烬煨】: 普攻/战技/终结技命中敌人时, 有#1基础概率施加1层 (每次行动近似
 * 1层, 星魂1 额外+1层); 1层使敌人受到的伤害提高#2, 此后每叠加1层提高#3, 最多#4层,
 * 持续#5回合; 处于【烬煨】状态时被视为同时陷入灼烧, 每回合开始受到等同于椒丘#6攻击力
 * 的火属性持续伤害。由行迹 炙香 的 Map 计数器 + buff/DoT 代理 (星魂6: 上限#2, 每层
 * 全属性抗性降低#3 → 代理为易伤)。
 *
 * <p>【结界】(终结技): 将敌方烬煨层数统一为最高值, 开启结界#4回合 — 代理为椒丘身上的
 * 结界标记 buff + 敌方受到的终结技伤害提高#3 (易伤) debuff (星魂4: 敌方攻击力降低);
 * 敌方行动时被施加烬煨的机制没有对应钩子, 略去。
 */
public final class JiaoqiuKit implements CharacterKit {

    @Override
    public int cid() {
        return 1218;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new JiaoqiuBlaze(param(byId, 1218101, 0, 15)),
                new JiaoqiuCooking(param(byId, 1218102, 0, 0.8), param(byId, 1218102, 1, 0.15),
                        param(byId, 1218102, 2, 0.6), param(byId, 1218102, 3, 2.4)),
                new JiaoqiuRoast(param(byId, 1218103, 0, 1)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new JiaoqiuE1(param(e, 0, 0.4), intParam(e, 1, 1));
            case 2 -> new JiaoqiuE2(param(e, 0, 3));
            case 4 -> new JiaoqiuE4(param(e, 0, 0.15));
            case 6 -> new JiaoqiuE6(intParam(e, 0, 1), intParam(e, 1, 9), param(e, 2, 0.03));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> JiaoqiuKit::jiaoqiuSkill;
            case 3 -> JiaoqiuKit::jiaoqiuUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /**
     * 战技 燔燎急袭: 对指定敌方单体造成#1%攻击力的火属性伤害, 同时对其相邻目标造成#2%
     * 攻击力的火属性伤害, 有#3基础概率对主目标施加1层【烬煨】。主目标的天赋烬煨由本行为
     * 一并结算, 相邻目标的天赋烬煨由行迹 炙香 在 afterAction 中结算。
     */
    static void jiaoqiuSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double mainMultiplier = ctx.firstParam();
        double sideMultiplier = ctx.param(1, 0.45);
        double chance = ctx.param(2, 1.0);
        CanHit main = targets.getFirst();
        ctx.dealDamage(battle, user, main, mainMultiplier);
        battle.breakToughness(user, main, ctx.stanceSingle());
        if (user instanceof Character character) {
            JiaoqiuRoast roast = findTrace(character, JiaoqiuRoast.class);
            if (roast != null && main instanceof Enemy enemy) {
                roast.applyStack(battle, character, enemy, chance, 1);
            }
        }
        for (Enemy enemy : battle.getAliveEnemies()) {
            if (enemy != main) {
                ctx.dealDamage(battle, user, enemy, sideMultiplier);
                battle.breakToughness(user, enemy, ctx.stanceSpread());
            }
        }
    }

    /**
     * 终结技 鼎阵妙法，奇正相生: 将敌方目标具有的【烬煨】层数统一设置为场上最高值,
     * 随后开启结界并对敌方全体造成#1%攻击力的火属性伤害; 结界持续#4回合,
     * 处于结界中时敌方受到的终结技伤害提高#3 (星魂4: 敌方攻击力降低#1)。
     */
    static void jiaoqiuUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        double ultVuln = ctx.param(2, 0.09);
        int fieldTurns = ctx.intParam(3, 3);
        if (user instanceof Character character) {
            JiaoqiuRoast roast = findTrace(character, JiaoqiuRoast.class);
            if (roast != null) {
                roast.unifyStacks(battle, character);
            }
        }
        // 结界标记 (用于 炙香 与星魂判断).
        user.removeBuff("结界");
        battle.applyBuff(user, new Buff("结界", Buff.Category.BUFF, user, user, fieldTurns));
        // 结界效果: 敌方受到的终结技伤害提高 (易伤) + 敌方攻击力降低 (星魂4).
        for (Enemy enemy : battle.getAliveEnemies()) {
            enemy.removeBuff("结界");
            Buff debuff = new Buff("结界", Buff.Category.DEBUFF, user, enemy, fieldTurns)
                    .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(ultVuln,
                            DoubleValue.Modifier.ModifierSource.DEBUFF));
            if (user instanceof Character character) {
                JiaoqiuE4 e4 = findTrace(character, JiaoqiuE4.class);
                if (e4 != null) {
                    debuff.stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(-e4.atkDown,
                            DoubleValue.Modifier.ModifierSource.DEBUFF));
                }
            }
            battle.applyBuff(enemy, debuff);
            ctx.dealDamage(battle, user, enemy, multiplier);
            battle.breakToughness(user, enemy, ctx.stanceAll());
        }
        IO.println("  " + user.getName() + " opens 【结界】 (" + fieldTurns
                + " turns, ult vuln +" + String.format("%.0f%%", ultVuln * 100) + ")");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 爟火: 战斗开始时，立即恢复15点能量。 */
    static class JiaoqiuBlaze implements Trace {
        private final double energy;

        JiaoqiuBlaze(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "爟火";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            owner.gainEnergy(energy);
            IO.println("  [行迹] " + owner.getName() + " restores "
                    + String.format("%.0f", energy) + " energy (爟火)");
        }
    }

    /** 举炊: 椒丘效果命中大于#1时, 每超过#2, 则额外提高#3攻击力, 最高不超过#4。 */
    static class JiaoqiuCooking implements Trace {
        private final double threshold;
        private final double step;
        private final double atkPerStep;
        private final double cap;

        JiaoqiuCooking(double threshold, double step, double atkPerStep, double cap) {
            this.threshold = threshold;
            this.step = step;
            this.atkPerStep = atkPerStep;
            this.cap = cap;
        }

        @Override
        public String getName() {
            return "举炊";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            double ehr = owner.getAttribute(AttributeType.EFFECT_HIT_RATE) != null
                    ? owner.getAttribute(AttributeType.EFFECT_HIT_RATE).get() : 0;
            double extra = Math.max(0, Math.floor((ehr - threshold) / step)) * atkPerStep;
            double boost = Math.min(cap, extra);
            if (boost <= 0) {
                return;
            }
            owner.removeBuff("举炊");
            Buff buff = new Buff("举炊", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(boost,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
            IO.println("  [行迹] " + owner.getName() + " ATK +"
                    + String.format("%.0f%%", boost * 100) + " (举炊, EHR "
                    + String.format("%.0f%%", ehr * 100) + ")");
        }
    }

    /**
     * 炙香: 结界存在时，敌方目标进入战斗时, 会被施加【烬煨】，层数与结界展开期间
     * 【烬煨】层数最高者相同, 最低为#1层 (引擎的敌人都在战斗开始时登场, 仅在结界已
     * 存在时于战斗开始结算)。
     * 兼作天赋 四示八权，纤滋精味 的代理:
     * <ul>
     *   <li>普攻/战技/终结技击中敌人时施加1层【烬煨】 (星魂1 额外+1层),
     *   每层使受到的伤害提高, 最多#4层, 持续#5回合。</li>
     *   <li>【烬煨】视为灼烧: 每回合开始受到#6攻击力的火属性持续伤害。</li>
     *   <li>星魂6: 上限提升至#2, 每层使全属性抗性降低#3 (代理为易伤), 敌方被消灭时
     *   其烬煨转移给场上存活层数最低的敌人。</li>
     * </ul>
     */
    static class JiaoqiuRoast implements Trace {
        private final double minStacks;
        private final Map<Enemy, Integer> stacks = new HashMap<>();

        JiaoqiuRoast(double minStacks) {
            this.minStacks = minStacks;
        }

        @Override
        public String getName() {
            return "炙香";
        }

        /** 天赋参数: [1, 0.075, 0.025, 5, 2, 0.9]. */
        private List<Double> talentParams(Character owner) {
            SkillData talent = KitSupport.skillData(owner, SkillType.TALENT);
            if (talent != null && talent.getSkills() != null && !talent.getSkills().isEmpty()
                    && talent.getSkills().getFirst().size() >= 6) {
                return talent.getSkills().getFirst();
            }
            return List.of(1.0, 0.075, 0.025, 5.0, 2.0, 0.9);
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            // 结界存在时, 进入战斗的敌人获得最低#1层烬煨.
            if (owner.hasBuffNamed("结界")) {
                for (Enemy enemy : battle.getAliveEnemies()) {
                    applyStack(battle, owner, enemy, 1.0, (int) Math.round(minStacks));
                }
            }
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                return;
            }
            // 战技的天赋烬煨由技能行为结算 (与战技自身的施加层数区分).
            if (type == SkillType.SKILL) {
                return;
            }
            List<Double> t = talentParams(owner);
            double chance = t.get(0);
            if (type == SkillType.ULTRA) {
                for (Enemy enemy : battle.getAliveEnemies()) {
                    applyStack(battle, owner, enemy, chance, 1);
                }
            } else if (targets != null) {
                for (CanHit target : targets) {
                    if (target instanceof Enemy enemy) {
                        applyStack(battle, owner, enemy, chance, 1);
                    }
                }
            }
        }

        @Override
        public void onKill(Battle battle, Character owner, CanHit victim) {
            // 星魂6: 敌方被消灭时, 烬煨转移给场上存活层数最低的敌人.
            if (!(victim instanceof Enemy dead)) {
                return;
            }
            Integer removed = stacks.remove(dead);
            int deadStacks = removed != null ? removed : 0;
            JiaoqiuE6 e6 = findTrace(owner, JiaoqiuE6.class);
            if (e6 == null || deadStacks <= 0) {
                return;
            }
            Enemy lowest = battle.getAliveEnemies().stream()
                    .filter(e -> !e.isDeath())
                    .min(Comparator.comparingInt(e -> stacks.getOrDefault(e, 0)))
                    .orElse(null);
            if (lowest != null) {
                applyStack(battle, owner, lowest, 1.0, deadStacks);
                IO.println("  [星魂] " + dead.getName() + "'s 【烬煨】 " + deadStacks
                        + " transferred to " + lowest.getName());
            }
        }

        /** 终结技: 将烬煨层数统一设置为场上最高值. */
        void unifyStacks(Battle battle, Character owner) {
            int max = stacks.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            if (max <= 0) {
                return;
            }
            for (Enemy enemy : battle.getAliveEnemies()) {
                if (enemy.isDeath() || stacks.getOrDefault(enemy, 0) >= max) {
                    continue;
                }
                stacks.put(enemy, max);
                refreshBuff(battle, owner, enemy, max);
                IO.println("  " + enemy.getName() + " 【烬煨】 unified to " + max + " stacks");
            }
        }

        /** 施加#count层【烬煨】 (星魂1 额外+1层), 并视为灼烧. */
        void applyStack(Battle battle, Character owner, Enemy target, double chance, int count) {
            if (target.isDeath() || count <= 0) {
                return;
            }
            if (!battle.checkEffectHit(owner, target, chance)) {
                return;
            }
            int extra = 0;
            JiaoqiuE1 e1 = findTrace(owner, JiaoqiuE1.class);
            if (e1 != null) {
                extra = e1.extraStack;
            }
            List<Double> t = talentParams(owner);
            int maxStacks = (int) Math.round(t.get(3));
            int duration = (int) Math.round(t.get(4));
            double dotRatio = t.get(5);
            JiaoqiuE6 e6 = findTrace(owner, JiaoqiuE6.class);
            if (e6 != null) {
                maxStacks = e6.maxStacks;
            }
            int next = Math.min(maxStacks, stacks.getOrDefault(target, 0) + count + extra);
            stacks.put(target, next);
            // 星魂2: 烬煨的火属性持续伤害倍率提高#1.
            JiaoqiuE2 e2 = findTrace(owner, JiaoqiuE2.class);
            if (e2 != null) {
                dotRatio *= (1 + e2.dotBonus);
            }
            double vuln = vulnFor(owner, next);
            target.removeBuff("烬煨");
            Buff buff = new Buff("烬煨", Buff.Category.DEBUFF, owner, target, duration)
                    .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(vuln,
                            DoubleValue.Modifier.ModifierSource.DEBUFF));
            battle.applyBuff(target, buff);
            double dot = owner.getAttribute(AttributeType.ATTACK) != null
                    ? owner.getAttribute(AttributeType.ATTACK).get() * dotRatio : 0;
            target.applyDot(new Buff.Dot("Burn (灼烧)", owner, target, dot, Element.FIRE, duration));
            IO.println("  " + target.getName() + " 【烬煨】 " + next + "/" + maxStacks
                    + " stacks (vuln +" + String.format("%.0f%%", vuln * 100) + ", burn)");
        }

        /** 每层烬煨的易伤数值 (星魂6: 每层#3, 否则 1层#2, 之后每层#3). */
        private double vulnFor(Character owner, int stacksNow) {
            JiaoqiuE6 e6 = findTrace(owner, JiaoqiuE6.class);
            if (e6 != null) {
                return e6.perStackVuln * stacksNow;
            }
            List<Double> t = talentParams(owner);
            return t.get(1) + t.get(2) * (stacksNow - 1);
        }

        /** 层数变化时刷新烬煨易伤 buff (终结技统一层数用). */
        private void refreshBuff(Battle battle, Character owner, Enemy target, int stacksNow) {
            List<Double> t = talentParams(owner);
            int duration = (int) Math.round(t.get(4));
            target.removeBuff("烬煨");
            Buff buff = new Buff("烬煨", Buff.Category.DEBUFF, owner, target, duration)
                    .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(vulnFor(owner, stacksNow),
                            DoubleValue.Modifier.ModifierSource.DEBUFF));
            battle.applyBuff(target, buff);
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 五味五走，生熟有定: 我方目标对处于【烬煨】状态的敌方目标造成的伤害提高#1;
     *  天赋施加烬煨时, 本次叠加的层数额外提高#2层。 (由行迹 炙香 读取.) */
    static class JiaoqiuE1 implements Trace {
        private final double bonus;
        private final int extraStack;

        JiaoqiuE1(double bonus, int extraStack) {
            this.bonus = bonus;
            this.extraStack = extraStack;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return defender.hasBuffNamed("烬煨") ? 1 + bonus : 1.0;
        }
    }

    /** 星魂2 爽口作疾，厚味措毒: 敌方目标处于【烬煨】状态时, 其火属性持续伤害倍率提高#1。
     *  (由行迹 炙香 读取.) */
    static class JiaoqiuE2 implements Trace {
        private final double dotBonus;

        JiaoqiuE2(double rawPercent) {
            // 数据为整数百分比 (如 3), 转换为小数.
            this.dotBonus = rawPercent > 1 ? rawPercent / 100.0 : rawPercent;
        }

        @Override
        public String getName() {
            return "星魂2";
        }
    }

    /** 星魂4 藏腑和平，血气资荣: 结界存在时，敌方目标的攻击力降低#1。
     *  (由终结技行为读取.) */
    static class JiaoqiuE4 implements Trace {
        private final double atkDown;

        JiaoqiuE4(double atkDown) {
            this.atkDown = atkDown;
        }

        @Override
        public String getName() {
            return "星魂4";
        }
    }

    /**
     * 星魂6 九沸九变，火为之纪: 敌方目标被消灭时, 其【烬煨】会叠加给场上存活的烬煨层数
     * 最低的敌人; 烬煨的层数上限提升至#2, 每层烬煨使目标的全属性抗性降低#3 (代理为易伤)。
     * (由行迹 炙香 读取.)
     */
    static class JiaoqiuE6 implements Trace {
        private final int transferFlag;
        private final int maxStacks;
        private final double perStackVuln;

        JiaoqiuE6(int transferFlag, int maxStacks, double perStackVuln) {
            this.transferFlag = transferFlag;
            this.maxStacks = maxStacks;
            this.perStackVuln = perStackVuln;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            IO.println("  [星魂] " + owner.getName() + ": 烬煨 cap " + maxStacks
                    + ", all-res down " + String.format("%.0f%%", perStackVuln * 100)
                    + " per stack (九沸九变)");
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
