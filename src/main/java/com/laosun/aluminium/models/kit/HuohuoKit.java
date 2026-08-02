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
import com.laosun.aluminium.models.SkillData;
import com.laosun.aluminium.models.Trace;

import java.util.List;
import java.util.Map;

import static com.laosun.aluminium.models.kit.KitSupport.intParam;
import static com.laosun.aluminium.models.kit.KitSupport.param;

/**
 * 藿藿 (Huohuo, cid 1217) — 风属性 丰饶.
 *
 * <p>核心机制【禳命】: 施放战技后获得 (持续#1回合, 每回合开始时持续回合数减1), 持有期间
 * 我方目标回合开始或施放终结技时回复自身生命值, 并对每个生命值百分比≤#6的我方目标各产生
 * 1次回复效果; 触发治疗时净化该目标#2个负面效果 (可触发#7次, 施放战技后刷新)。天赋由
 * 行迹 不敢自专 代理 — 引擎没有"队友回合开始"的钩子, 代理为每次队友行动后结算一次回复。
 *
 * <p>星魂2 的"受到致命攻击不会阵亡"同样没有拦截钩子, 代理为: 持有【禳命】期间,
 * 在队友行动时检测到倒地的队友则立即按生命上限#1%复活 (并消耗1次救援次数)。
 */
public final class HuohuoKit implements CharacterKit {

    @Override
    public int cid() {
        return 1217;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new HuohuoAid(intParam(byId, 1217101, 0, 1)),
                new HuohuoDoom(param(byId, 1217102, 0, 0.35)),
                new HuohuoStress(param(byId, 1217103, 0, 1)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new HuohuoE1(param(e, 0, 0.12), intParam(e, 1, 1));
            case 2 -> new HuohuoE2(param(e, 0, 0.5), intParam(e, 1, 2));
            case 4 -> new HuohuoE4(param(e, 0, 0.8));
            case 6 -> new HuohuoE6(param(e, 0, 0.5), intParam(e, 1, 2));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> HuohuoKit::huohuoSkill;
            case 3 -> HuohuoKit::huohuoUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /**
     * 战技 灵符•保命护身: 解除指定我方单体的#5个负面效果, 立即为其回复等同于藿藿#1生命
     * 上限+#2的生命值, 同时为相邻目标回复等同于藿藿#3生命上限+#4的生命值。
     * (星魂4 治疗量随目标当前生命值提升, 星魂6 治疗后使目标伤害提高.)
     */
    static void huohuoSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double mainHpRatio = ctx.firstParam();
        double mainFlat = ctx.param(1, 140);
        double sideHpRatio = ctx.param(2, 0.112);
        double sideFlat = ctx.param(3, 112);
        int cleanseCount = ctx.intParam(4, 1);
        List<? extends CanHit> allies = KitSupport.friendlyTargets(battle, user, targets);
        if (allies.isEmpty()) {
            return;
        }
        CanHit main = allies.getFirst();
        healWithBonuses(battle, user, main, user.getMaxHp() * mainHpRatio + mainFlat);
        cleanse(battle, main, cleanseCount);
        for (CanHit ally : battle.getAlivePlayerUnits()) {
            if (ally != main) {
                healWithBonuses(battle, user, ally, user.getMaxHp() * sideHpRatio + sideFlat);
            }
        }
    }

    /** 终结技 尾巴•遣神役鬼: 为除自身以外的队友恢复等同于各自#1能量上限的能量,
     *  同时使其攻击力提高#2, 持续#3回合。 */
    static void huohuoUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double energyRatio = ctx.param(0, 0.15);
        double atkPercent = ctx.param(1, 0.24);
        int turns = ctx.intParam(2, 2);
        for (CanHit ally : battle.getAlivePlayerUnits()) {
            if (ally == user) {
                continue;
            }
            double energy = ally.getMaxEnergy() * energyRatio;
            ally.gainEnergy(energy);
            ally.removeBuff("尾巴");
            Buff buff = new Buff("尾巴", Buff.Category.BUFF, user, ally, turns)
                    .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atkPercent,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(ally, buff);
            IO.println("  " + ally.getName() + " restores " + String.format("%.0f", energy)
                    + " energy and gains +" + String.format("%.0f%%", atkPercent * 100)
                    + " ATK (尾巴)");
        }
    }

    /** 星魂4: 目标当前生命值越低治疗量越高, 最多提高#1; 星魂6: 治疗后目标伤害提高. */
    private static void healWithBonuses(Battle battle, CanHit user, CanHit target, double baseHeal) {
        double multiplier = 1.0;
        double damageBoost = 0;
        int boostTurns = 2;
        if (user instanceof Character character) {
            HuohuoE4 e4 = findTrace(character, HuohuoE4.class);
            if (e4 != null) {
                multiplier = 1 + e4.maxBonus * (1 - target.getHpPercent());
            }
            HuohuoE6 e6 = findTrace(character, HuohuoE6.class);
            if (e6 != null) {
                damageBoost = e6.boost;
                boostTurns = e6.turns;
            }
        }
        battle.healTarget(user, target, baseHeal * multiplier);
        if (damageBoost > 0) {
            target.removeBuff("同休共戚");
            Buff buff = new Buff("同休共戚", Buff.Category.BUFF, user, target, boostTurns)
                    .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(damageBoost,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(target, buff);
            IO.println("  " + target.getName() + " damage +"
                    + String.format("%.0f%%", damageBoost * 100) + " (同休共戚)");
        }
    }

    /** 解除目标身上的负面效果. */
    private static void cleanse(Battle battle, CanHit target, int count) {
        for (int i = 0; i < count; i++) {
            Buff debuff = target.getBuffs().stream()
                    .filter(b -> b.getCategory() == Buff.Category.DEBUFF)
                    .findFirst().orElse(null);
            if (debuff == null) {
                return;
            }
            battle.removeBuff(target, debuff);
            IO.println("  " + target.getName() + " cleansed of " + debuff.getName());
        }
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /**
     * 不敢自专: 战斗开始时，藿藿获得【禳命】，持续#1回合。
     * 兼作天赋 凭附•气通天真 的代理: 施放战技后获得【禳命】持续#1回合 (每回合自动-1),
     * 持有期间我方目标行动或施放终结技时回复自身等同于藿藿#3生命上限+#5的生命值, 并对每个
     * 生命值百分比≤#6的我方目标各产生1次回复效果; 触发治疗时解除该目标#2个负面效果,
     * 可触发#7次 (施放战技后刷新)。
     */
    static class HuohuoAid implements Trace {
        private final int startTurns;
        private int cleanseCharges = 0;

        HuohuoAid(int startTurns) {
            this.startTurns = startTurns;
        }

        @Override
        public String getName() {
            return "不敢自专";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            applyRangMing(battle, owner, startTurns);
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL) {
                return;
            }
            // 天赋: 施放战技后获得禳命 (星魂1 延长#1回合), 并刷新净化次数.
            double talentTurns = 2;
            SkillData talent = KitSupport.skillData(owner, SkillType.TALENT);
            if (talent != null && talent.getSkills() != null && !talent.getSkills().isEmpty()
                    && !talent.getSkills().getFirst().isEmpty()) {
                talentTurns = talent.getSkills().getFirst().get(0);
            }
            HuohuoE1 e1 = findTrace(owner, HuohuoE1.class);
            if (e1 != null) {
                talentTurns += e1.extraTurns;
            }
            applyRangMing(battle, owner, talentTurns);
            cleanseCharges = talentCharges(owner);
            IO.println("  [行迹] " + owner.getName() + " 禳命 cleanse charges: " + cleanseCharges);
        }

        private int talentCharges(Character owner) {
            SkillData talent = KitSupport.skillData(owner, SkillType.TALENT);
            if (talent != null && talent.getSkills() != null && !talent.getSkills().isEmpty()
                    && talent.getSkills().getFirst().size() > 6) {
                return (int) Math.round(talent.getSkills().getFirst().get(6));
            }
            return 6;
        }

        @Override
        public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                 List<? extends CanHit> targets) {
            // 星魂2 的致命救援 (由 HuohuoE2 处理).
            HuohuoE2 e2 = findTrace(owner, HuohuoE2.class);
            if (e2 != null) {
                e2.rescue(battle, owner);
            }
            if (!owner.hasBuffNamed("禳命")) {
                return;
            }
            if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                return;
            }
            // 我方目标行动/施放终结技: 回复自身 (代理: 每次行动回复).
            healProc(battle, owner, actor);
            // 对每个当前生命值百分比≤#6的我方目标各产生1次回复效果.
            double lowHpThreshold = 0.5;
            SkillData talent = KitSupport.skillData(owner, SkillType.TALENT);
            if (talent != null && talent.getSkills() != null && !talent.getSkills().isEmpty()
                    && talent.getSkills().getFirst().size() > 5) {
                lowHpThreshold = talent.getSkills().getFirst().get(5);
            }
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                if (ally != actor && ally.getHpPercent() <= lowHpThreshold) {
                    healProc(battle, owner, ally);
                }
            }
        }

        /** 【禳命】触发的一次治疗: 回复 + 净化 + 怯惧应激 回能. */
        private void healProc(Battle battle, Character owner, CanHit target) {
            if (target.isDeath()) {
                return;
            }
            double hpRatio = 0.03;
            double flat = 30;
            SkillData talent = KitSupport.skillData(owner, SkillType.TALENT);
            if (talent != null && talent.getSkills() != null && !talent.getSkills().isEmpty()
                    && talent.getSkills().getFirst().size() > 4) {
                hpRatio = talent.getSkills().getFirst().get(2);
                flat = talent.getSkills().getFirst().get(4);
            }
            healWithBonuses(battle, owner, target, owner.getMaxHp() * hpRatio + flat);
            // 净化#2个负面效果 (可触发#7次).
            if (cleanseCharges > 0) {
                cleanseCharges--;
                cleanse(battle, target, 1);
            }
            // 怯惧应激: 触发天赋治疗时, 藿藿恢复#1点能量.
            HuohuoStress stress = findTrace(owner, HuohuoStress.class);
            if (stress != null) {
                owner.gainEnergy(stress.energy);
            }
        }

        /** 施放战技 / 战斗开始: 获得【禳命】. */
        private void applyRangMing(Battle battle, Character owner, double turns) {
            owner.removeBuff("禳命");
            Buff buff = new Buff("禳命", Buff.Category.BUFF, owner, owner, (int) Math.round(turns));
            battle.applyBuff(owner, buff);
            IO.println("  " + owner.getName() + " gains 【禳命】 (" + (int) Math.round(turns) + " turns)");
        }
    }

    /** 贞凶之命: 抵抗控制类负面状态的概率提高35%。 */
    static class HuohuoDoom implements Trace {
        private final double resistance;

        HuohuoDoom(double resistance) {
            this.resistance = resistance;
        }

        @Override
        public String getName() {
            return "贞凶之命";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            owner.removeBuff("贞凶之命");
            Buff buff = new Buff("贞凶之命", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.EFFECT_RESISTANCE, DoubleValue.Modifier.pure(resistance,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
            IO.println("  [行迹] " + owner.getName() + " control resistance +"
                    + String.format("%.0f%%", resistance * 100) + " (贞凶之命)");
        }
    }

    /** 怯惧应激: 触发天赋为我方目标提供治疗时，藿藿恢复#1点能量。 (由行迹 不敢自专 读取.) */
    static class HuohuoStress implements Trace {
        private final double energy;

        HuohuoStress(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "怯惧应激";
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /**
     * 星魂1 岁阳寄体，妖邪依凭: 天赋产生的【禳命】持续时间延长#2回合, 且当藿藿拥有
     * 【禳命】时我方全体提高#1的速度。
     */
    static class HuohuoE1 implements Trace {
        private final double speedPercent;
        private final int extraTurns;

        HuohuoE1(double speedPercent, int extraTurns) {
            this.speedPercent = speedPercent;
            this.extraTurns = extraTurns;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            if (owner.hasBuffNamed("禳命")) {
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    ally.removeBuff("岁阳寄体");
                    Buff buff = new Buff("岁阳寄体", Buff.Category.BUFF, owner, ally, 1)
                            .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(speedPercent,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
                IO.println("  [星魂] party speed +" + String.format("%.0f%%", speedPercent * 100)
                        + " (岁阳寄体, 禳命 active)");
            } else {
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    ally.removeBuff("岁阳寄体");
                }
            }
        }
    }

    /**
     * 星魂2 判官书符，镇尾锁灵: 当藿藿拥有【禳命】时，若我方目标受到致命攻击，不会陷入
     * 无法战斗状态, 并立即回复其生命上限#1%的生命值, 使【禳命】的持续回合数减1。
     * 该效果单场战斗中可以触发#2次。 (引擎无致命拦截钩子, 代理为队友行动时复活倒地队友.)
     */
    static class HuohuoE2 implements Trace {
        private final double hpRatio;
        private final int maxUses;
        private int uses = 0;

        HuohuoE2(double hpRatio, int maxUses) {
            this.hpRatio = hpRatio;
            this.maxUses = maxUses;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        void rescue(Battle battle, Character owner) {
            if (uses >= maxUses || !owner.hasBuffNamed("禳命")) {
                return;
            }
            for (Character ally : battle.characters) {
                if (ally != owner && ally.isDeath()) {
                    uses++;
                    ally.revive(ally.getMaxHp() * hpRatio);
                    // 使禳命的持续回合数减1.
                    for (Buff buff : owner.getBuffs()) {
                        if ("禳命".equals(buff.getName())) {
                            buff.setDuration(Math.max(0, buff.getDuration() - 1));
                        }
                    }
                    IO.println("  [星魂] " + ally.getName() + " survives the fatal blow at "
                            + String.format("%.0f%%", hpRatio * 100) + " HP (判官书符, "
                            + uses + "/" + maxUses + ")");
                    return;
                }
            }
        }
    }

    /** 星魂4 坐卧不离，争拗难宁: 施放战技或触发天赋治疗时, 目标当前生命值越低治疗量越高,
     *  最多使藿藿提供的治疗量提高#1。 (由治疗辅助函数读取.) */
    static class HuohuoE4 implements Trace {
        private final double maxBonus;

        HuohuoE4(double maxBonus) {
            this.maxBonus = maxBonus;
        }

        @Override
        public String getName() {
            return "星魂4";
        }
    }

    /** 星魂6 同休共戚，相须而行: 为我方目标提供治疗时, 使目标造成的伤害提高#1, 持续#2回合。
     *  (由治疗辅助函数读取.) */
    static class HuohuoE6 implements Trace {
        private final double boost;
        private final int turns;

        HuohuoE6(double boost, int turns) {
            this.boost = boost;
            this.turns = turns;
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
