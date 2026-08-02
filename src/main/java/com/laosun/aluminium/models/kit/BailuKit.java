package com.laosun.aluminium.models.kit;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.Eidolon;
import com.laosun.aluminium.beans.SkillPoint;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Buff;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Trace;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.laosun.aluminium.models.kit.KitSupport.intParam;
import static com.laosun.aluminium.models.kit.KitSupport.param;

/**
 * 白露 (Bailu, cid 1211) — 雷属性 丰饶.
 *
 * <p>核心机制【生息】: 终结技为没有【生息】的我方目标附加【生息】(每回合回复少量生命值,
 * 受到的伤害降低; 引擎以 HoT Buff 代理 "受到攻击后回复" 与触发次数), 已有【生息】的目标延长1回合。
 * 天赋的复活 (队友受致命伤时提供治疗) 由行迹 岐黄精义 代理。
 */
public final class BailuKit implements CharacterKit {

    @Override
    public int cid() {
        return 1211;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new BailuVitality(param(byId, 1211101, 0, 0.1), intParam(byId, 1211101, 1, 2)),
                new BailuDragonVein(intParam(byId, 1211102, 0, 1)),
                new BailuBlessing(param(byId, 1211103, 0, 0.1)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new BailuE1(intParam(e, 0, 8));
            case 2 -> new BailuE2(param(e, 0, 0.15), intParam(e, 1, 2));
            case 4 -> new BailuE4(param(e, 0, 0.1), intParam(e, 1, 3), intParam(e, 2, 2));
            case 6 -> new BailuE6(intParam(e, 0, 1));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> BailuKit::bailuSkill;
            case 3 -> BailuKit::bailuUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /**
     * 战技: 立即为指定我方单体回复等同于白露#1生命上限+#2的生命值, 然后白露随机为我方单体
     * 进行#4次治疗, 每提供1次治疗, 下一次治疗回复的生命值降低#3。
     * 每次治疗若产生过量治疗, 触发行迹 岐黄精义 (生命上限提高); 星魂4 每1次治疗使受治疗者
     * 造成的伤害提高 (最多3层, 持续2回合)。
     */
    static void bailuSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double healRatio = ctx.firstParam();
        double flat = ctx.param(1, 78);
        double decay = ctx.param(2, 0.15);
        int extraHeals = ctx.intParam(3, 2);
        List<? extends CanHit> allies = KitSupport.friendlyTargets(battle, user, targets);
        double baseHeal = user.getMaxHp() * healRatio + flat;
        for (CanHit ally : allies) {
            healOnce(battle, ctx, user, ally, baseHeal);
        }
        double next = baseHeal;
        for (int i = 0; i < extraHeals; i++) {
            List<CanHit> candidates = new ArrayList<>(battle.getAlivePlayerUnits());
            if (candidates.isEmpty()) {
                return;
            }
            next *= (1 - decay);
            healOnce(battle, ctx, user, candidates.get((int) (Math.random() * candidates.size())), next);
        }
    }

    /** 终结技: 立即为我方全体回复等同于白露#1生命上限+#2的生命值。
     *  为没有【生息】的我方目标附上【生息】, 已有【生息】的目标持续时间延长1回合。
     *  【生息】可持续#3回合, 每回合回复等同于白露生命上限X%+Y的生命值 (数值来自天赋),
     *  并使其受到的伤害降低 (行迹 鳞渊福泽)。 */
    static void bailuUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double healRatio = ctx.firstParam();
        double flat = ctx.param(1, 90);
        int turns = ctx.intParam(2, 2);
        double hotRatio = 0.036;
        double hotFlat = 36;
        com.laosun.aluminium.models.SkillData talent = null;
        if (user instanceof Character character) {
            talent = KitSupport.skillData(character, SkillType.TALENT);
        }
        if (talent != null && talent.getSkills() != null && !talent.getSkills().isEmpty()
                && talent.getSkills().getFirst().size() >= 2) {
            hotRatio = talent.getSkills().getFirst().get(0);
            hotFlat = talent.getSkills().getFirst().get(1);
        }
        for (CanHit ally : ctx.friendlyTargets(battle, user)) {
            healOnce(battle, ctx, user, ally, user.getMaxHp() * healRatio + flat);
            Buff existing = findBuff(ally, "生息");
            int duration = existing != null ? existing.getDuration() + 1 : turns;
            double reduction = 0.1;
            BailuBlessing blessing = findTrace(user, BailuBlessing.class);
            if (blessing != null) {
                reduction = blessing.reduction;
            }
            ally.removeBuff("生息");
            Buff vitality = new Buff("生息", Buff.Category.BUFF, user, ally, duration)
                    .heal(user.getMaxHp() * hotRatio + hotFlat)
                    .stat(AttributeType.DAMAGE_REDUCTION, DoubleValue.Modifier.multiplyPercent(-reduction,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(ally, vitality);
        }
        IO.println("  " + user.getName() + " heals the party and applies 【生息】 (" + turns
                + " turns, HoT " + String.format("%.0f", user.getMaxHp() * hotRatio + hotFlat) + "/turn)");
    }

    /** 单次治疗: 回复生命值, 并处理 过量治疗 (岐黄精义) 与 星魂4 伤害增益. */
    private static void healOnce(Battle battle, SkillContext ctx, CanHit user, CanHit ally, double amount) {
        if (ally.isDeath()) {
            return;
        }
        battle.healTarget(user, ally, amount);
        BailuVitality vitality = findTrace(user, BailuVitality.class);
        if (vitality != null && ally.getHpPercent() >= 0.999) {
            vitality.onOverheal(battle, user, ally);
        }
        BailuE4 e4 = findTrace(user, BailuE4.class);
        if (e4 != null) {
            e4.onHeal(battle, user, ally);
        }
    }

    /** 按名称查找目标身上的 Buff. */
    static Buff findBuff(CanHit target, String name) {
        for (Buff buff : target.getBuffs()) {
            if (name.equals(buff.getName())) {
                return buff;
            }
        }
        return null;
    }

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

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /**
     * 岐黄精义: 白露对我方目标造成过量治疗时会提高目标#1的生命上限, 持续#2回合
     * (由战技/终结技行为在过量治疗后调用 {@link #onOverheal})。
     * 兼作天赋 奔走悬壶济世长 的复活代理: 队友受到致命攻击时不会陷入无法战斗状态,
     * 白露立即为其治疗 (回复等同于白露#3生命上限+#4的生命值), 单场战斗1次 (星魂6 增加1次)。
     */
    static class BailuVitality implements Trace {
        private final double hpRatio;
        private final int turns;
        private int revives = 0;

        BailuVitality(double hpRatio, int turns) {
            this.hpRatio = hpRatio;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "岐黄精义";
        }

        /** 过量治疗: 提高目标生命上限. */
        void onOverheal(Battle battle, CanHit owner, CanHit ally) {
            ally.removeBuff("岐黄精义");
            Buff buff = new Buff("岐黄精义", Buff.Category.BUFF, owner, ally, turns)
                    .stat(AttributeType.HEALTH, DoubleValue.Modifier.addPercent(hpRatio,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(ally, buff);
            IO.println("  [行迹] " + ally.getName() + " overhealed: max HP +"
                    + String.format("%.0f%%", hpRatio * 100) + " (" + turns + " turns)");
        }

        @Override
        public void onKill(Battle battle, Character owner, CanHit victim) {
            if (!(victim instanceof Character fallen)
                    || !fallen.isDeath()
                    || fallen.getCamp() != com.laosun.aluminium.enums.Camp.PLAYER) {
                return;
            }
            double reviveRatio = 0.12;
            double flat = 120;
            com.laosun.aluminium.models.SkillData talent = KitSupport.skillData(owner, SkillType.TALENT);
            if (talent != null && talent.getSkills() != null && !talent.getSkills().isEmpty()
                    && talent.getSkills().getFirst().size() >= 4) {
                List<Double> t = talent.getSkills().getFirst();
                reviveRatio = t.get(2);
                flat = t.get(3);
            }
            int cap = owner.getEidolonLevel() >= 6 ? 2 : 1;
            if (revives >= cap) {
                return;
            }
            revives++;
            fallen.revive(owner.getMaxHp() * reviveRatio + flat);
            IO.println("  [行迹] " + owner.getName() + " revives " + fallen.getName()
                    + " with " + String.format("%.0f", owner.getMaxHp() * reviveRatio + flat)
                    + " HP (奔走悬壶济世长, " + revives + "/" + cap + ")");
        }
    }

    /** 持明龙脉: 【生息】效果的触发次数增加#1次。
     *  (引擎以 HoT 代理【生息】的"受到攻击后回复", 触发次数并入终结技行为的【生息】近似.) */
    static class BailuDragonVein implements Trace {
        @SuppressWarnings("unused")
        private final int extraTriggers;

        BailuDragonVein(int extraTriggers) {
            this.extraTriggers = extraTriggers;
        }

        @Override
        public String getName() {
            return "持明龙脉";
        }
    }

    /** 鳞渊福泽: 拥有【生息】的角色受到的伤害降低#1。
     *  (减伤并入终结技行为施加的【生息】Buff.) */
    static class BailuBlessing implements Trace {
        private final double reduction;

        BailuBlessing(double reduction) {
            this.reduction = reduction;
        }

        @Override
        public String getName() {
            return "鳞渊福泽";
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /**
     * 星魂1 百脉甘津宁神久: 【生息】结束时若我方目标当前生命值等于其生命上限，则额外恢复目标8点能量。
     * (代理: 持有【生息】且满血的我方目标, 每个【生息】实例触发1次.）
     */
    static class BailuE1 implements Trace {
        private final double energy;
        private final Set<CanHit> rewarded = new HashSet<>();

        BailuE1(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                if (ally.hasBuffNamed("生息")) {
                    if (ally.getCurrentHp() >= ally.getMaxHp() && rewarded.add(ally)) {
                        ally.gainEnergy(energy);
                        IO.println("  [星魂] " + ally.getName() + " restores "
                                + String.format("%.0f", energy) + " energy (生息 full HP, 百脉甘津宁神久)");
                    }
                } else {
                    rewarded.remove(ally);
                }
            }
        }
    }

    /** 星魂2 壶中洞天云螭眠: 施放终结技后，白露的治疗量提高#1，持续#2回合。 */
    static class BailuE2 implements Trace {
        private final double healBoost;
        private final int turns;

        BailuE2(double healBoost, int turns) {
            this.healBoost = healBoost;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.ULTRA) {
                return;
            }
            owner.removeBuff("壶中洞天云螭眠");
            Buff buff = new Buff("壶中洞天云螭眠", Buff.Category.BUFF, owner, owner, turns)
                    .stat(AttributeType.OUTGOING_HEALING_BOOST, DoubleValue.Modifier.pure(healBoost,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /**
     * 星魂4 肘后备急除外障: 战技提供的每1次治疗会额外使受治疗者造成的伤害提高#1,
     * 最多叠加#2层, 持续#3回合 (由战技行为在每次治疗后调用 {@link #onHeal})。
     */
    static class BailuE4 implements Trace {
        private final double dmgPerStack;
        private final int maxStacks;
        private final int turns;
        private final Map<CanHit, Integer> stacks = new java.util.HashMap<>();

        BailuE4(double dmgPerStack, int maxStacks, int turns) {
            this.dmgPerStack = dmgPerStack;
            this.maxStacks = maxStacks;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        void onHeal(Battle battle, CanHit owner, CanHit ally) {
            int next = Math.min(maxStacks, stacks.getOrDefault(ally, 0) + 1);
            stacks.put(ally, next);
            ally.removeBuff("肘后备急除外障");
            Buff buff = new Buff("肘后备急除外障", Buff.Category.BUFF, owner, ally, turns)
                    .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(dmgPerStack * next,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(ally, buff);
            IO.println("  [星魂] " + ally.getName() + " damage +"
                    + String.format("%.0f%%", dmgPerStack * next * 100) + " (" + next + "/"
                    + maxStacks + " stacks)");
        }
    }

    /** 星魂6 龙漦吐哺胜金丹: 白露单场战斗中累计可以对受到致命攻击的我方目标提供治疗的效果
     *  触发次数增加#1次。 (复活次数上限由行迹 岐黄精义 按星魂等级读取.) */
    static class BailuE6 implements Trace {
        @SuppressWarnings("unused")
        private final int extraRevives;

        BailuE6(int extraRevives) {
            this.extraRevives = extraRevives;
        }

        @Override
        public String getName() {
            return "星魂6";
        }
    }
}
