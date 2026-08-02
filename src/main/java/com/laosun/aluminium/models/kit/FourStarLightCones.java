package com.laosun.aluminium.models.kit;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Element;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Buff;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.Trace;

import java.util.List;

/**
 * 四星光锥被动 (21 系列).
 *
 * <p>Each cone's passive is implemented as a {@link Trace} attached to the
 * equipped character at build time. Values are read from the weapon's
 * level-1 {@code skill_value} params, mirroring the game descriptions.
 */
public final class FourStarLightCones {

    private FourStarLightCones() {
    }

    /**
     * The hand-written passive for a FourStarLightCones light cone ID, or {@code null}
     * if the cone falls back to the generic interpreter.
     */
    public static Trace forWeapon(int wid, List<Double> params) {
        double p0 = param(params, 0, 0);
        double p1 = param(params, 1, 0);
        double p2 = param(params, 2, 0);
        double p3 = param(params, 3, 0);
        double p4 = param(params, 4, 0);
        double p5 = param(params, 5, 0);
        double p6 = param(params, 6, 0);
        return switch (wid) {
            case 21000 -> new Ctx.PostOp(p1);                                 // 一场术后对话
            case 21001 -> new Ctx.Goodnight(p0, (int) p1);                    // 晚安与睡颜
            case 21002 -> new Ctx.FirstDay(p1);                               // 余生的第一天
            case 21003 -> new Ctx.OnlySilence(p1);                            // 唯有沉默
            case 21004 -> new Ctx.MemoryShape(p1);                            // 记忆中的模样
            case 21005 -> new Ctx.Mole(p0);                                   // 鼹鼠党欢迎你
            case 21006 -> new Ctx.MyBirth(p0, p1, p2);                        // 「我」的诞生
            case 21007 -> new Ctx.SameFeeling(p1);                            // 同一种心情
            case 21008 -> new Ctx.PreySight(p1);                              // 猎物的视线
            case 21009 -> new Ctx.Landau(p1);                                 // 朗道的选择
            case 21010 -> new Ctx.Swordplay(p0, (int) p1);                    // 论剑
            case 21011 -> new Ctx.Planets(p0);                                // 与行星相会
            case 21012 -> new Ctx.SolemnVow(p1);                              // 秘密誓心
            case 21013 -> new Ctx.WorldQuiet(p0, p1);                         // 别让世界静下来
            case 21014 -> new Ctx.Moment(p1, p2);                             // 此时恰好
            case 21015 -> new Ctx.Resolve(p0, p1, (int) p2);                  // 决心如汗珠般闪耀
            case 21016 -> new Ctx.MarketTrend(p1, p2, (int) p3);              // 宇宙市场趋势
            case 21017 -> new Ctx.Follow(p0, p1);                             // 点个关注吧！
            case 21018 -> new Ctx.DanceDance(p0);                             // 舞！舞！舞！
            case 21019 -> new Ctx.BlueSky(p1, (int) p2);                      // 在蓝天下
            case 21020 -> new Ctx.GeniusRest(p1, (int) p2);                   // 天才们的休憩
            case 21021 -> new Ctx.Equivalent(p0, p1);                         // 等价交换
            case 21022 -> new Ctx.Prolonged(p1);                              // 延长记号
            case 21023 -> new Ctx.Groundfire(p0, p1, (int) p2);               // 我们是地火
            case 21024 -> new Ctx.Springs(p0, p1);                            // 春水初生
            case 21025 -> new Ctx.PastFuture(p0, (int) p1);                   // 过往未来
            case 21026 -> new Ctx.WalkTime(p1);                               // 汪！散步时间！
            case 21027 -> new Ctx.Breakfast(p1, (int) p2);                    // 早餐的仪式感
            case 21028 -> new Ctx.WarmNight(p1);                              // 暖夜不会漫长
            case 21029 -> new Ctx.SeeYou(p0);                                 // 后会有期
            case 21030 -> new Ctx.Me(p1);                                     // 这就是我啦！
            case 21031 -> new Ctx.Phantom(p1);                                // 重返幽冥
            case 21032 -> new Ctx.MoonCarve(p0, p1, p2);                      // 镂月裁云之意
            case 21033 -> new Ctx.Escape(p1);                                 // 无处可逃
            case 21034 -> new Ctx.PeaceDay(p0, p1);                           // 今日亦是和平的一日
            case 21035 -> new Ctx.WhatIsReal(p1, p2);                         // 何物为真
            case 21036 -> new Ctx.DreamTown(p0);                              // 美梦小镇大冒险
            case 21037 -> new Ctx.Winner(p1, (int) p2);                       // 最后的赢家
            case 21038 -> new Ctx.FireDistance(p0, p1, p2, (int) p3, (int) p4); // 在火的远处
            case 21039 -> new Ctx.FateThread(p1, p2, p3);                     // 织造命运之线
            case 21040 -> new Ctx.GalaxyFall(p1, (int) p2);                   // 银河沦陷日
            case 21041 -> new Ctx.Show(p0, (int) p1, (int) p2, p3, p4);       // 好戏开演
            case 21042 -> new Ctx.Promise(p1, (int) p2);                      // 铭记于心的约定
            case 21043 -> new Ctx.Concert(p1);                                // 两个人的演唱会
            case 21044 -> new Ctx.Boundless(p1);                              // 无边曼舞
            case 21045 -> new Ctx.AfterHarmony(p1, (int) p2);                 // 谐乐静默之后
            case 21046 -> new Ctx.Bloom(p1);                                  // 芳华待灼
            case 21047 -> new Ctx.NightShadow(p1, (int) p2);                  // 黑夜如影随行
            case 21048 -> new Ctx.Montage(p1, (int) p2);                      // 梦的蒙太奇
            case 21050 -> new Ctx.MorningEvening(p1, (int) p2);               // 胜利只在朝夕间
            case 21051 -> new Ctx.GeniusGreet(p1, (int) p2);                  // 天才们的问候
            case 21052 -> new Ctx.SweatTears(p1);                             // 多流汗，少流泪
            case 21053 -> new Ctx.WishJourney(p1);                            // 愿旅途永远坦然
            case 21054 -> new Ctx.NextPage(p1, (int) p2);                     // 故事的下一页
            case 21055 -> new Ctx.Tomorrow(p1, p2);                           // 直到明天的明天
            case 21056 -> new Ctx.ChasingWind(p0);                            // 追逐风的时候
            case 21057 -> new Ctx.Flowers(p1);                                // 花儿不会忘记
            case 21058 -> new Ctx.BloodLine(p1);                              // 一行往日的血
            case 21060 -> new Ctx.DreamMalt(p1);                              // 氤氲麦香的梦
            case 21061 -> new Ctx.Resort(p1, p2, (int) p3);                   // 假日浴场大冒险
            case 21062 -> new Ctx.Farewell(p1);                               // 于那终点再见
            case 21064 -> new Ctx.Mushroom(p1, (int) p2);                     // 菇菇嘎嘎历险记
            case 21065 -> new Ctx.LuckyDay(p1, (int) p2);                     // 今日好手气
            default -> null;
        };
    }

    private static double param(List<Double> params, int index, double fallback) {
        return params != null && index < params.size() ? params.get(index) : fallback;
    }

    private static final class Ctx {
        // ═══ 21*** 四星光锥 ═══════════════════════════════════════════

        /** 一场术后对话: 施放终结技时治疗量提高#2. */
        static final class PostOp implements Trace {
            private final double healBoost;

            PostOp(double healBoost) {
                this.healBoost = healBoost;
            }

            @Override
            public String getName() {
                return "一场术后对话";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.ULTRA) {
                    return;
                }
                owner.removeBuff("一场术后对话");
                Buff buff = new Buff("一场术后对话", Buff.Category.BUFF, owner, owner, 1)
                        .stat(AttributeType.OUTGOING_HEALING_BOOST, DoubleValue.Modifier.pure(healBoost,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 晚安与睡颜: 目标每有1个负面效果, 伤害提高#1, 最多#2层. */
        static final class Goodnight implements Trace {
            private final double perDebuff;
            private final int maxStacks;

            Goodnight(double perDebuff, int maxStacks) {
                this.perDebuff = perDebuff;
                this.maxStacks = maxStacks;
            }

            @Override
            public String getName() {
                return "晚安与睡颜";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                long debuffs = defender.getBuffs().stream()
                        .filter(b -> b.getCategory() == Buff.Category.DEBUFF).count();
                return 1 + Math.min(maxStacks, debuffs) * perDebuff;
            }
        }

        /** 余生的第一天: 进入战斗后, 我方全体全属性抗性提高#2 (近似: 减伤). */
        static final class FirstDay implements Trace {
            private final double reduction;

            FirstDay(double reduction) {
                this.reduction = reduction;
            }

            @Override
            public String getName() {
                return "余生的第一天";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    Buff buff = new Buff("余生的第一天", Buff.Category.BUFF, owner, ally, -1)
                            .stat(AttributeType.DAMAGE_REDUCTION, DoubleValue.Modifier.multiplyPercent(-reduction,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
            }
        }

        /** 唯有沉默: 场上敌方目标数量≤2时, 暴击率提高#2. */
        static final class OnlySilence implements Trace {
            private final double crit;

            OnlySilence(double crit) {
                this.crit = crit;
            }

            @Override
            public String getName() {
                return "唯有沉默";
            }

            @Override
            public double critChanceBonus(Battle battle, Character owner, CanHit defender, SkillType type) {
                return battle != null && battle.getAliveEnemies().size() <= 2 ? crit : 0;
            }
        }

        /** 记忆中的模样: 施放攻击后, 额外恢复#2点能量 (每回合1次). */
        static final class MemoryShape implements Trace {
            private final double energy;
            private boolean usedThisTurn = false;

            MemoryShape(double energy) {
                this.energy = energy;
            }

            @Override
            public String getName() {
                return "记忆中的模样";
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                usedThisTurn = false;
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (usedThisTurn || type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                    return;
                }
                usedThisTurn = true;
                owner.gainEnergy(energy);
            }
        }

        /** 鼹鼠党欢迎你: 每次攻击获得1层【淘气值】, 每层攻击力+#1 (最多5层). */
        static final class Mole implements Trace {
            private final double perStack;
            private int stacks = 0;

            Mole(double perStack) {
                this.perStack = perStack;
            }

            @Override
            public String getName() {
                return "鼹鼠党欢迎你";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                    return;
                }
                stacks = Math.min(5, stacks + 1);
                owner.removeBuff("淘气值");
                Buff buff = new Buff("淘气值", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(perStack * stacks,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 「我」的诞生: 追加攻击伤害+#1, 目标生命≤#2时额外+#3 (近似: 全类型). */
        static final class MyBirth implements Trace {
            private final double bonus;
            private final double hpThreshold;
            private final double extra;

            MyBirth(double bonus, double hpThreshold, double extra) {
                this.bonus = bonus;
                this.hpThreshold = hpThreshold;
                this.extra = extra;
            }

            @Override
            public String getName() {
                return "「我」的诞生";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return defender.getHpPercent() <= hpThreshold ? 1 + bonus + extra : 1 + bonus;
            }
        }

        /** 同一种心情: 施放战技时, 为我方全体恢复#2点能量. */
        static final class SameFeeling implements Trace {
            private final double energy;

            SameFeeling(double energy) {
                this.energy = energy;
            }

            @Override
            public String getName() {
                return "同一种心情";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type == SkillType.SKILL) {
                    for (CanHit ally : battle.getAlivePlayerUnits()) {
                        ally.gainEnergy(energy);
                    }
                }
            }
        }

        /** 猎物的视线: 造成的持续伤害提高#2. */
        static final class PreySight implements Trace {
            private final double dotBoost;

            PreySight(double dotBoost) {
                this.dotBoost = dotBoost;
            }

            @Override
            public String getName() {
                return "猎物的视线";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                Buff buff = new Buff("猎物的视线", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.DOT_DAMAGE_BOOST, DoubleValue.Modifier.pure(dotBoost,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 朗道的选择: 受到攻击的概率提高, 受到的伤害降低#2. */
        static final class Landau implements Trace {
            private final double reduction;

            Landau(double reduction) {
                this.reduction = reduction;
            }

            @Override
            public String getName() {
                return "朗道的选择";
            }

            @Override
            public double aggroMultiplier(Battle battle, Character owner) {
                return 2.0;
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                Buff buff = new Buff("朗道的选择", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.DAMAGE_REDUCTION, DoubleValue.Modifier.multiplyPercent(-reduction,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 论剑: 多次击中同一目标时, 每次伤害+#1, 最多#2层; 目标变化时重置. */
        static final class Swordplay implements Trace {
            private final double perStack;
            private final int maxStacks;
            private CanHit lastTarget = null;
            private int stacks = 0;

            Swordplay(double perStack, int maxStacks) {
                this.perStack = perStack;
                this.maxStacks = maxStacks;
            }

            @Override
            public String getName() {
                return "论剑";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (targets == null || targets.isEmpty()) {
                    return;
                }
                CanHit target = targets.getFirst();
                if (target == lastTarget && !target.isDeath()) {
                    stacks = Math.min(maxStacks, stacks + 1);
                } else {
                    stacks = 1;
                    lastTarget = target;
                }
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return defender == lastTarget ? 1 + perStack * stacks : 1.0;
            }
        }

        /** 与行星相会: 我方目标造成与装备者相同属性的伤害时, 伤害提高#1. */
        static final class Planets implements Trace {
            private final double bonus;

            Planets(double bonus) {
                this.bonus = bonus;
            }

            @Override
            public String getName() {
                return "与行星相会";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    if (ally.getElement() == owner.getElement()) {
                        Buff buff = new Buff("与行星相会", Buff.Category.BUFF, owner, ally, -1)
                                .stat(owner.getElement().boostAttribute, DoubleValue.Modifier.pure(bonus,
                                        DoubleValue.Modifier.ModifierSource.BUFF));
                        battle.applyBuff(ally, buff);
                    }
                }
            }
        }

        /** 秘密誓心: 对当前生命值百分比≥自身的目标伤害额外提高#2. */
        static final class SolemnVow implements Trace {
            private final double extra;

            SolemnVow(double extra) {
                this.extra = extra;
            }

            @Override
            public String getName() {
                return "秘密誓心";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return defender.getHpPercent() >= owner.getHpPercent() ? 1 + extra : 1.0;
            }
        }

        /** 别让世界静下来: 进入战斗时恢复#2能量, 终结技伤害提高#1. */
        static final class WorldQuiet implements Trace {
            private final double ultBonus;
            private final double startEnergy;

            WorldQuiet(double ultBonus, double startEnergy) {
                this.ultBonus = ultBonus;
                this.startEnergy = startEnergy;
            }

            @Override
            public String getName() {
                return "别让世界静下来";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                owner.gainEnergy(startEnergy);
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return type == SkillType.ULTRA ? 1 + ultBonus : 1.0;
            }
        }

        /** 此时恰好: 治疗量提高, 数值等同于效果抵抗的#2, 最多#3. */
        static final class Moment implements Trace {
            private final double ratio;
            private final double cap;

            Moment(double ratio, double cap) {
                this.ratio = ratio;
                this.cap = cap;
            }

            @Override
            public String getName() {
                return "此时恰好";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                refresh(battle, owner);
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                refresh(battle, owner);
            }

            private void refresh(Battle battle, Character owner) {
                double er = owner.getAttribute(AttributeType.EFFECT_RESISTANCE) != null
                        ? owner.getAttribute(AttributeType.EFFECT_RESISTANCE).get() : 0;
                double bonus = Math.min(cap, er * ratio);
                owner.removeBuff("此时恰好");
                Buff buff = new Buff("此时恰好", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.OUTGOING_HEALING_BOOST, DoubleValue.Modifier.pure(bonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 决心如汗珠般闪耀: 击中目标时, #1基础概率使其陷入【攻陷】(防御降低#2, #3回合). */
        static final class Resolve implements Trace {
            private final double chance;
            private final double defDown;
            private final int turns;

            Resolve(double chance, double defDown, int turns) {
                this.chance = chance;
                this.defDown = defDown;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "决心如汗珠般闪耀";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                    return;
                }
                for (CanHit target : targets) {
                    if (target.isDeath() || target.hasBuffNamed("攻陷")) {
                        continue;
                    }
                    if (battle.checkEffectHit(owner, target, chance)) {
                        Buff debuff = new Buff("攻陷", Buff.Category.DEBUFF, owner, target, turns)
                                .stat(AttributeType.DEFENCE, DoubleValue.Modifier.addPercent(-defDown,
                                        DoubleValue.Modifier.ModifierSource.DEBUFF));
                        battle.applyBuff(target, debuff);
                    }
                }
            }
        }

        /** 宇宙市场趋势: 受到攻击后, #2基础概率使敌方目标陷入灼烧 (防御力#3, #4回合). */
        static final class MarketTrend implements Trace {
            private final double chance;
            private final double defRatio;
            private final int turns;

            MarketTrend(double chance, double defRatio, int turns) {
                this.chance = chance;
                this.defRatio = defRatio;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "宇宙市场趋势";
            }

            @Override
            public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
                if (attacker == null || attacker.isDeath() || !(attacker instanceof Enemy)) {
                    return;
                }
                if (battle.checkEffectHit(owner, attacker, chance)) {
                    double def = owner.getAttribute(AttributeType.DEFENCE) != null
                            ? owner.getAttribute(AttributeType.DEFENCE).get() : 0;
                    attacker.applyDot(new Buff.Dot("Burn (灼烧)", owner, attacker, def * defRatio,
                            Element.FIRE, turns));
                    IO.println("  [光锥] 宇宙市场趋势: attacker burns!");
                }
            }
        }

        /** 点个关注吧！: 普攻和战技伤害+#1, 能量满时额外+#2. */
        static final class Follow implements Trace {
            private final double bonus;
            private final double fullEnergyBonus;

            Follow(double bonus, double fullEnergyBonus) {
                this.bonus = bonus;
                this.fullEnergyBonus = fullEnergyBonus;
            }

            @Override
            public String getName() {
                return "点个关注吧！";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                if (type != SkillType.COMMON && type != SkillType.SKILL) {
                    return 1.0;
                }
                double extra = owner.getEnergy() >= owner.getMaxEnergy() ? fullEnergyBonus : 0;
                return 1 + bonus + extra;
            }
        }

        /** 舞！舞！舞！: 施放终结技后, 我方全体行动提前#1. */
        static final class DanceDance implements Trace {
            private final double advance;

            DanceDance(double advance) {
                this.advance = advance;
            }

            @Override
            public String getName() {
                return "舞！舞！舞！";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type == SkillType.ULTRA) {
                    for (CanHit ally : battle.getAlivePlayerUnits()) {
                        battle.advanceByPercent(ally, advance);
                    }
                }
            }
        }

        /** 在蓝天下: 消灭敌方目标后, 暴击率提高#2, 持续#3回合. */
        static final class BlueSky implements Trace {
            private final double crit;
            private final int turns;

            BlueSky(double crit, int turns) {
                this.crit = crit;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "在蓝天下";
            }

            @Override
            public void onKill(Battle battle, Character owner, CanHit victim) {
                if (!(victim instanceof Enemy)) {
                    return;
                }
                owner.removeBuff("在蓝天下");
                Buff buff = new Buff("在蓝天下", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.CRIT_CHANCE, DoubleValue.Modifier.pure(crit,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 天才们的休憩: 消灭敌方目标后, 暴击伤害提高#2, 持续#3回合. */
        static final class GeniusRest implements Trace {
            private final double cdmg;
            private final int turns;

            GeniusRest(double cdmg, int turns) {
                this.cdmg = cdmg;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "天才们的休憩";
            }

            @Override
            public void onKill(Battle battle, Character owner, CanHit victim) {
                if (!(victim instanceof Enemy)) {
                    return;
                }
                owner.removeBuff("天才们的休憩");
                Buff buff = new Buff("天才们的休憩", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(cdmg,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 等价交换: 回合开始时, 随机为1个能量<#1的其他目标恢复#2点能量. */
        static final class Equivalent implements Trace {
            private final double threshold;
            private final double energy;

            Equivalent(double threshold, double energy) {
                this.threshold = threshold;
                this.energy = energy;
            }

            @Override
            public String getName() {
                return "等价交换";
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                List<CanHit> candidates = battle.getAlivePlayerUnits().stream()
                        .filter(u -> u != owner && u.getMaxEnergy() > 0
                                && u.getEnergy() < u.getMaxEnergy() * threshold)
                        .toList();
                if (!candidates.isEmpty()) {
                    candidates.get((int) (Math.random() * candidates.size())).gainEnergy(energy);
                }
            }
        }

        /** 延长记号: 对处于触电或风化状态的敌方目标造成的伤害提高#2. */
        static final class Prolonged implements Trace {
            private final double bonus;

            Prolonged(double bonus) {
                this.bonus = bonus;
            }

            @Override
            public String getName() {
                return "延长记号";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return defender.hasDotOfElement(Element.THUNDER) || defender.hasDotOfElement(Element.WIND)
                        ? 1 + bonus : 1.0;
            }
        }

        /** 我们是地火: 战斗开始全体受到伤害降低#2 (#3回合), 并回复已损失生命值#1. */
        static final class Groundfire implements Trace {
            private final double healRatio;
            private final double reduction;
            private final int turns;

            Groundfire(double healRatio, double reduction, int turns) {
                this.healRatio = healRatio;
                this.reduction = reduction;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "我们是地火";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    ally.heal((ally.getMaxHp() - ally.getCurrentHp()) * healRatio);
                    Buff buff = new Buff("我们是地火", Buff.Category.BUFF, owner, ally, turns)
                            .stat(AttributeType.DAMAGE_REDUCTION, DoubleValue.Modifier.multiplyPercent(-reduction,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
            }
        }

        /** 春水初生: 进入战斗后速度+#1、伤害+#2; 受到伤害后失效, 下个回合结束时恢复. */
        static final class Springs implements Trace {
            private final double speed;
            private final double dmg;

            Springs(double speed, double dmg) {
                this.speed = speed;
                this.dmg = dmg;
            }

            @Override
            public String getName() {
                return "春水初生";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                refresh(battle, owner);
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                refresh(battle, owner);
            }

            @Override
            public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
                owner.removeBuff("春水初生");
            }

            private void refresh(Battle battle, Character owner) {
                if (owner.hasBuffNamed("春水初生")) {
                    return;
                }
                Buff buff = new Buff("春水初生", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(speed,
                                DoubleValue.Modifier.ModifierSource.BUFF))
                        .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(dmg,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 过往未来: 施放战技后, 使下一个行动的我方其他目标伤害提高#1 (#2回合). */
        static final class PastFuture implements Trace {
            private final double bonus;
            private final int turns;

            PastFuture(double bonus, int turns) {
                this.bonus = bonus;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "过往未来";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.SKILL) {
                    return;
                }
                // 下一个行动的我方其他目标 (行动条上最近的非装备者友方).
                for (Signal signal : battle.getQueueSnapshot()) {
                    CanHit next = signal.getCanHit();
                    if (next != owner && next.getCamp() == owner.getCamp() && !next.isDeath()) {
                        Buff buff = new Buff("过往未来", Buff.Category.BUFF, owner, next, turns)
                                .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(bonus,
                                        DoubleValue.Modifier.ModifierSource.BUFF));
                        battle.applyBuff(next, buff);
                        return;
                    }
                }
            }
        }

        /** 汪！散步时间！: 对处于灼烧或裂伤状态的敌方目标造成的伤害提高#2. */
        static final class WalkTime implements Trace {
            private final double bonus;

            WalkTime(double bonus) {
                this.bonus = bonus;
            }

            @Override
            public String getName() {
                return "汪！散步时间！";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return defender.hasDotOfElement(Element.FIRE) || defender.hasDotOfElement(Element.PHYSICAL)
                        ? 1 + bonus : 1.0;
            }
        }

        /** 早餐的仪式感: 每消灭1个敌方目标, 攻击力+#2, 最多#3层. */
        static final class Breakfast implements Trace {
            private final double perKill;
            private final int maxStacks;
            private int stacks = 0;

            Breakfast(double perKill, int maxStacks) {
                this.perKill = perKill;
                this.maxStacks = maxStacks;
            }

            @Override
            public String getName() {
                return "早餐的仪式感";
            }

            @Override
            public void onKill(Battle battle, Character owner, CanHit victim) {
                if (!(victim instanceof Enemy)) {
                    return;
                }
                stacks = Math.min(maxStacks, stacks + 1);
                owner.removeBuff("早餐的仪式感");
                Buff buff = new Buff("早餐的仪式感", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(perKill * stacks,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 暖夜不会漫长: 施放普攻或战技后, 为我方全体回复各自生命上限#2的生命值. */
        static final class WarmNight implements Trace {
            private final double healRatio;

            WarmNight(double healRatio) {
                this.healRatio = healRatio;
            }

            @Override
            public String getName() {
                return "暖夜不会漫长";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type == SkillType.COMMON || type == SkillType.SKILL) {
                    for (CanHit ally : battle.getAlivePlayerUnits()) {
                        ally.heal(ally.getMaxHp() * healRatio);
                    }
                }
            }
        }

        /** 后会有期: 施放普攻或战技后, 对随机1个受到攻击的目标造成#1攻击力附加伤害. */
        static final class SeeYou implements Trace {
            private final double atkRatio;

            SeeYou(double atkRatio) {
                this.atkRatio = atkRatio;
            }

            @Override
            public String getName() {
                return "后会有期";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if ((type == SkillType.COMMON || type == SkillType.SKILL) && !targets.isEmpty()) {
                    List<CanHit> alive = new java.util.ArrayList<>();
                    for (CanHit t : targets) {
                        if (!t.isDeath()) {
                            alive.add(t);
                        }
                    }
                    if (!alive.isEmpty()) {
                        battle.dealAttackDamage(owner, alive.get((int) (Math.random() * alive.size())),
                                atkRatio, 0, com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                        com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL,
                                        owner.getElement()));
                    }
                }
            }
        }

        /** 这就是我啦！: 施放终结技时, 造成的伤害值提高, 数值等同于防御力的#2. */
        static final class Me implements Trace {
            private final double defRatio;

            Me(double defRatio) {
                this.defRatio = defRatio;
            }

            @Override
            public String getName() {
                return "这就是我啦！";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                if (type != SkillType.ULTRA) {
                    return 1.0;
                }
                double def = owner.getAttribute(AttributeType.DEFENCE) != null
                        ? owner.getAttribute(AttributeType.DEFENCE).get() : 0;
                double atk = owner.getAttribute(AttributeType.ATTACK) != null
                        ? owner.getAttribute(AttributeType.ATTACK).get() : 1;
                return 1 + def * defRatio / Math.max(1, atk);
            }
        }

        /** 重返幽冥: 暴击后, #2固定概率解除被攻击目标的1个增益效果 (每次攻击1次). */
        static final class Phantom implements Trace {
            private final double chance;
            private boolean usedThisAction = false;

            Phantom(double chance) {
                this.chance = chance;
            }

            @Override
            public String getName() {
                return "重返幽冥";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                usedThisAction = false;
                if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                    return;
                }
                for (CanHit target : targets) {
                    if (target.isDeath() || usedThisAction || Math.random() >= chance) {
                        continue;
                    }
                    Buff toRemove = target.getBuffs().stream()
                            .filter(b -> b.getCategory() == Buff.Category.BUFF)
                            .findFirst().orElse(null);
                    if (toRemove != null) {
                        battle.removeBuff(target, toRemove);
                        usedThisAction = true;
                    }
                }
            }
        }

        /** 镂月裁云之意: 战斗开始及回合开始时, 随机生效1种全队增益 (攻击/暴伤/回能, 不与上次重复). */
        static final class MoonCarve implements Trace {
            private final double atk;
            private final double cdmg;
            private final double err;
            private int lastEffect = -1;

            MoonCarve(double atk, double cdmg, double err) {
                this.atk = atk;
                this.cdmg = cdmg;
                this.err = err;
            }

            @Override
            public String getName() {
                return "镂月裁云之意";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                refresh(battle, owner);
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                refresh(battle, owner);
            }

            private void refresh(Battle battle, Character owner) {
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    ally.removeBuff("镂月裁云·攻");
                    ally.removeBuff("镂月裁云·暴");
                    ally.removeBuff("镂月裁云·能");
                }
                int effect;
                do {
                    effect = (int) (Math.random() * 3);
                } while (effect == lastEffect);
                lastEffect = effect;
                AttributeType attribute = effect == 0 ? AttributeType.ATTACK
                        : effect == 1 ? AttributeType.CRIT_ATTACK : AttributeType.ENERGY_REGENERATION_RATE;
                double value = effect == 0 ? atk : effect == 1 ? cdmg : err;
                String name = effect == 0 ? "镂月裁云·攻" : effect == 1 ? "镂月裁云·暴" : "镂月裁云·能";
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    Buff buff = new Buff(name, Buff.Category.BUFF, owner, ally, -1)
                            .stat(attribute, DoubleValue.Modifier.pure(value,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
            }
        }

        /** 无处可逃: 消灭敌方目标时, 回复等同于自身#2攻击力的生命值. */
        static final class Escape implements Trace {
            private final double atkRatio;

            Escape(double atkRatio) {
                this.atkRatio = atkRatio;
            }

            @Override
            public String getName() {
                return "无处可逃";
            }

            @Override
            public void onKill(Battle battle, Character owner, CanHit victim) {
                if (!(victim instanceof Enemy)) {
                    return;
                }
                double atk = owner.getAttribute(AttributeType.ATTACK) != null
                        ? owner.getAttribute(AttributeType.ATTACK).get() : 0;
                owner.heal(atk * atkRatio);
            }
        }

        /** 今日亦是和平的一日: 进入战斗后, 每点能量上限使伤害提高#1, 最多计入#2点. */
        static final class PeaceDay implements Trace {
            private final double perEnergy;
            private final double cap;

            PeaceDay(double perEnergy, double cap) {
                this.perEnergy = perEnergy;
                this.cap = cap;
            }

            @Override
            public String getName() {
                return "今日亦是和平的一日";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                double bonus = Math.min(cap, owner.getMaxEnergy()) * perEnergy;
                return 1 + bonus;
            }
        }

        /** 何物为真: 施放普攻后, 回复#2生命上限+#3的生命值. */
        static final class WhatIsReal implements Trace {
            private final double healRatio;
            private final double flat;

            WhatIsReal(double healRatio, double flat) {
                this.healRatio = healRatio;
                this.flat = flat;
            }

            @Override
            public String getName() {
                return "何物为真";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type == SkillType.COMMON) {
                    owner.heal(owner.getMaxHp() * healRatio + flat);
                }
            }
        }

        /** 美梦小镇大冒险: 施放某类型技能后, 我方全体该类型技能伤害提高#1. */
        static final class DreamTown implements Trace {
            private final double bonus;
            private SkillType lastType = null;

            DreamTown(double bonus) {
                this.bonus = bonus;
            }

            @Override
            public String getName() {
                return "美梦小镇大冒险";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type == SkillType.COMMON || type == SkillType.SKILL || type == SkillType.ULTRA) {
                    lastType = type;
                }
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return type == lastType ? 1 + bonus : 1.0;
            }
        }

        /** 最后的赢家: 暴击后获得1层【好运】 (暴击伤害+#2/层, 最多#3层, 回合结束移除). */
        static final class Winner implements Trace {
            private final double perStack;
            private final int maxStacks;
            private int stacks = 0;

            Winner(double perStack, int maxStacks) {
                this.perStack = perStack;
                this.maxStacks = maxStacks;
            }

            @Override
            public String getName() {
                return "最后的赢家";
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                stacks = 0;
                owner.removeBuff("好运");
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                    return;
                }
                stacks = Math.min(maxStacks, stacks + 1);
                owner.removeBuff("好运");
                Buff buff = new Buff("好运", Buff.Category.BUFF, owner, owner, 1)
                        .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(perStack * stacks,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 在火的远处: 单次受击损失生命>#1时, 回复生命上限#3并伤害提高#2 (#4回合, 每#5回合1次). */
        static final class FireDistance implements Trace {
            private final double threshold;
            private final double dmg;
            private final double healRatio;
            private final int turns;
            private final int cooldown;
            private int cdLeft = 0;

            FireDistance(double threshold, double dmg, double healRatio, int turns, int cooldown) {
                this.threshold = threshold;
                this.dmg = dmg;
                this.healRatio = healRatio;
                this.turns = turns;
                this.cooldown = cooldown;
            }

            @Override
            public String getName() {
                return "在火的远处";
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                if (cdLeft > 0) {
                    cdLeft--;
                }
            }

            @Override
            public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
                if (cdLeft > 0 || owner.isDeath()) {
                    return;
                }
                if (damage > owner.getMaxHp() * threshold) {
                    cdLeft = cooldown;
                    owner.heal(owner.getMaxHp() * healRatio);
                    owner.removeBuff("在火的远处");
                    Buff buff = new Buff("在火的远处", Buff.Category.BUFF, owner, owner, turns)
                            .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(dmg,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(owner, buff);
                }
            }
        }

        /** 织造命运之线: 每有#2点防御力, 造成的伤害提高#3, 最多#4. */
        static final class FateThread implements Trace {
            private final double perDef;
            private final double perValue;
            private final double cap;

            FateThread(double perDef, double perValue, double cap) {
                this.perDef = perDef;
                this.perValue = perValue;
                this.cap = cap;
            }

            @Override
            public String getName() {
                return "织造命运之线";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                refresh(battle, owner);
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                refresh(battle, owner);
            }

            private void refresh(Battle battle, Character owner) {
                double def = owner.getAttribute(AttributeType.DEFENCE) != null
                        ? owner.getAttribute(AttributeType.DEFENCE).get() : 0;
                double bonus = Math.min(cap, def / perDef * perValue);
                owner.removeBuff("织造命运之线");
                Buff buff = new Buff("织造命运之线", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(bonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 银河沦陷日: 攻击后若不少于2个目标具有对应属性弱点, 暴击伤害提高#2 (#3回合). */
        static final class GalaxyFall implements Trace {
            private final double cdmg;
            private final int turns;

            GalaxyFall(double cdmg, int turns) {
                this.cdmg = cdmg;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "银河沦陷日";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                    return;
                }
                long weak = targets.stream()
                        .filter(t -> t instanceof Enemy e && e.isWeakTo(owner.getElement())).count();
                if (weak >= 2) {
                    owner.removeBuff("银河沦陷日");
                    Buff buff = new Buff("银河沦陷日", Buff.Category.BUFF, owner, owner, turns)
                            .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(cdmg,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(owner, buff);
                }
            }
        }

        /** 好戏开演: 施加负面状态后获得1层【戏法】 (伤害+#1/层, 最多#2层, #3回合); 效果命中≥#4时攻击力+#5. */
        static final class Show implements Trace {
            private final double perStack;
            private final int maxStacks;
            private final int turns;
            private final double ehrThreshold;
            private final double atkBonus;
            private int stacks = 0;

            Show(double perStack, int maxStacks, int turns, double ehrThreshold, double atkBonus) {
                this.perStack = perStack;
                this.maxStacks = maxStacks;
                this.turns = turns;
                this.ehrThreshold = ehrThreshold;
                this.atkBonus = atkBonus;
            }

            @Override
            public String getName() {
                return "好戏开演";
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                stacks = 0;
                double ehr = owner.getAttribute(AttributeType.EFFECT_HIT_RATE) != null
                        ? owner.getAttribute(AttributeType.EFFECT_HIT_RATE).get() : 0;
                boolean active = owner.hasBuffNamed("好戏开演·攻");
                boolean shouldBe = ehr >= ehrThreshold;
                if (active && !shouldBe) {
                    owner.removeBuff("好戏开演·攻");
                } else if (!active && shouldBe) {
                    Buff buff = new Buff("好戏开演·攻", Buff.Category.BUFF, owner, owner, -1)
                            .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atkBonus,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(owner, buff);
                }
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                    return;
                }
                boolean appliedDebuff = targets.stream().anyMatch(t -> !t.isDeath() && t.hasDebuff());
                if (appliedDebuff) {
                    stacks = Math.min(maxStacks, stacks + 1);
                    owner.removeBuff("戏法");
                    Buff buff = new Buff("戏法", Buff.Category.BUFF, owner, owner, turns)
                            .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(perStack * stacks,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(owner, buff);
                }
            }
        }

        /** 铭记于心的约定: 施放终结技时, 暴击率提高#2 (#3回合). */
        static final class Promise implements Trace {
            private final double crit;
            private final int turns;

            Promise(double crit, int turns) {
                this.crit = crit;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "铭记于心的约定";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.ULTRA) {
                    return;
                }
                owner.removeBuff("铭记于心的约定");
                Buff buff = new Buff("铭记于心的约定", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.CRIT_CHANCE, DoubleValue.Modifier.pure(crit,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 两个人的演唱会: 场上每有一名持有护盾的角色, 装备者造成的伤害提高#2. */
        static final class Concert implements Trace {
            private final double perShielded;

            Concert(double perShielded) {
                this.perShielded = perShielded;
            }

            @Override
            public String getName() {
                return "两个人的演唱会";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                if (battle == null) {
                    return 1.0;
                }
                long shielded = battle.getAlivePlayerUnits().stream()
                        .filter(u -> u.getShield() > 0).count();
                return 1 + shielded * perShielded;
            }
        }

        /** 无边曼舞: 对处于防御降低或减速状态的敌人造成的暴击伤害提高#2. */
        static final class Boundless implements Trace {
            private final double cdmg;

            Boundless(double cdmg) {
                this.cdmg = cdmg;
            }

            @Override
            public String getName() {
                return "无边曼舞";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                boolean defDown = defender.getBuffs().stream()
                        .anyMatch(b -> b.getCategory() == Buff.Category.DEBUFF
                                && b.getModifiers() != null
                                && b.getModifiers().stream().anyMatch(m -> m.attribute() == AttributeType.DEFENCE));
                return defDown || defender.hasBuffNamed("Slow") || defender.hasBuffNamed("Slow+")
                        ? 1 + cdmg : 1.0;
            }
        }

        /** 谐乐静默之后: 施放终结技后, 速度提高#2 (#3回合). */
        static final class AfterHarmony implements Trace {
            private final double speed;
            private final int turns;

            AfterHarmony(double speed, int turns) {
                this.speed = speed;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "谐乐静默之后";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.ULTRA) {
                    return;
                }
                owner.removeBuff("谐乐静默之后");
                Buff buff = new Buff("谐乐静默之后", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(speed,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 芳华待灼: 进入战斗时, 若有两名及以上同命途角色, 这些角色暴击伤害提高#2. */
        static final class Bloom implements Trace {
            private final double cdmg;

            Bloom(double cdmg) {
                this.cdmg = cdmg;
            }

            @Override
            public String getName() {
                return "芳华待灼";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                java.util.Map<String, Long> paths = new java.util.HashMap<>();
                for (Character c : battle.getAliveCharacters()) {
                    var data = Constant.CHARACTERS.get(c.getCid());
                    String path = data != null ? data.mt() : "all";
                    paths.merge(path, 1L, Long::sum);
                }
                if (paths.values().stream().anyMatch(v -> v >= 2)) {
                    for (CanHit ally : battle.getAlivePlayerUnits()) {
                        Buff buff = new Buff("芳华待灼", Buff.Category.BUFF, owner, ally, -1)
                                .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(cdmg,
                                        DoubleValue.Modifier.ModifierSource.BUFF));
                        battle.applyBuff(ally, buff);
                    }
                }
            }
        }

        /** 黑夜如影随行: 进入战斗时或造成击破伤害后, 速度提高#2 (#3回合, 每回合1次). */
        static final class NightShadow implements Trace {
            private final double speed;
            private final int turns;
            private boolean usedThisTurn = false;

            NightShadow(double speed, int turns) {
                this.speed = speed;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "黑夜如影随行";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                refresh(battle, owner);
                // 进入战斗的触发不计入当回合次数, 首次击破仍可触发.
                usedThisTurn = false;
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                usedThisTurn = false;
                refresh(battle, owner);
            }

            @Override
            public void onEnemyBreak(Battle battle, Character owner, Enemy enemy) {
                refresh(battle, owner);
            }

            private void refresh(Battle battle, Character owner) {
                if (usedThisTurn) {
                    return;
                }
                usedThisTurn = true;
                owner.removeBuff("黑夜如影随行");
                Buff buff = new Buff("黑夜如影随行", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(speed,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 梦的蒙太奇: 攻击弱点击破状态的目标后, 恢复#2点能量 (每回合最多#3次). */
        static final class Montage implements Trace {
            private final double energy;
            private final int maxPerTurn;
            private int usedThisTurn = 0;

            Montage(double energy, int maxPerTurn) {
                this.energy = energy;
                this.maxPerTurn = maxPerTurn;
            }

            @Override
            public String getName() {
                return "梦的蒙太奇";
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                usedThisTurn = 0;
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (usedThisTurn >= maxPerTurn || targets == null) {
                    return;
                }
                boolean hitBroken = targets.stream()
                        .anyMatch(t -> t instanceof Enemy e && e.isBroken());
                if (hitBroken) {
                    usedThisTurn++;
                    owner.gainEnergy(energy);
                }
            }
        }

        /** 胜利只在朝夕间: 忆灵施放技能时, 我方全体伤害提高#2 (#3回合). */
        static final class MorningEvening implements Trace {
            private final double bonus;
            private final int turns;

            MorningEvening(double bonus, int turns) {
                this.bonus = bonus;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "胜利只在朝夕间";
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                if (owner.getSummons().isEmpty()) {
                    return;
                }
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    Buff buff = new Buff("胜利只在朝夕间", Buff.Category.BUFF, owner, ally, turns)
                            .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(bonus,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
            }
        }

        /** 天才们的问候: 施放终结技后, 装备者与忆灵普攻伤害提高#2 (#3回合). */
        static final class GeniusGreet implements Trace {
            private final double basicBonus;
            private final int turns;

            GeniusGreet(double basicBonus, int turns) {
                this.basicBonus = basicBonus;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "天才们的问候";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.ULTRA) {
                    return;
                }
                owner.removeBuff("天才们的问候");
                Buff buff = new Buff("天才们的问候", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.NORMAL_DAMAGE_BOOST, DoubleValue.Modifier.pure(basicBonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 多流汗，少流泪: 忆灵在场上时, 装备者与忆灵造成的伤害提高#2. */
        static final class SweatTears implements Trace {
            private final double bonus;

            SweatTears(double bonus) {
                this.bonus = bonus;
            }

            @Override
            public String getName() {
                return "多流汗，少流泪";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return owner.getSummons().isEmpty() ? 1.0 : 1 + bonus;
            }
        }

        /** 愿旅途永远坦然: 我方目标持有护盾时, 造成的伤害提高#2. */
        static final class WishJourney implements Trace {
            private final double bonus;

            WishJourney(double bonus) {
                this.bonus = bonus;
            }

            @Override
            public String getName() {
                return "愿旅途永远坦然";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return owner.getShield() > 0 ? 1 + bonus : 1.0;
            }
        }

        /** 故事的下一页: 忆灵攻击后, 装备者与忆灵治疗量提高#2 (#3回合). */
        static final class NextPage implements Trace {
            private final double healBoost;
            private final int turns;

            NextPage(double healBoost, int turns) {
                this.healBoost = healBoost;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "故事的下一页";
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                if (owner.getSummons().isEmpty()) {
                    return;
                }
                owner.removeBuff("故事的下一页");
                Buff buff = new Buff("故事的下一页", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.OUTGOING_HEALING_BOOST, DoubleValue.Modifier.pure(healBoost,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 直到明天的明天: 我方目标生命≥#2时, 造成的伤害提高#3. */
        static final class Tomorrow implements Trace {
            private final double hpThreshold;
            private final double bonus;

            Tomorrow(double hpThreshold, double bonus) {
                this.hpThreshold = hpThreshold;
                this.bonus = bonus;
            }

            @Override
            public String getName() {
                return "直到明天的明天";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return owner.getHpPercent() >= hpThreshold ? 1 + bonus : 1.0;
            }
        }

        /** 追逐风的时候: 进入战斗后, 我方全体击破伤害提高#1 (近似: 击破特攻). */
        static final class ChasingWind implements Trace {
            private final double bonus;

            ChasingWind(double bonus) {
                this.bonus = bonus;
            }

            @Override
            public String getName() {
                return "追逐风的时候";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    Buff buff = new Buff("追逐风的时候", Buff.Category.BUFF, owner, ally, -1)
                            .stat(AttributeType.BREAKING_EFFECT, DoubleValue.Modifier.pure(bonus,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
            }
        }

        /** 花儿不会忘记: 忆灵造成的暴击伤害额外提高#2 (近似: 忆灵在场时装备者暴伤+额外). */
        static final class Flowers implements Trace {
            private final double cdmg;

            Flowers(double cdmg) {
                this.cdmg = cdmg;
            }

            @Override
            public String getName() {
                return "花儿不会忘记";
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                boolean active = owner.hasBuffNamed("花儿不会忘记");
                boolean shouldBe = !owner.getSummons().isEmpty();
                if (active && !shouldBe) {
                    owner.removeBuff("花儿不会忘记");
                } else if (!active && shouldBe) {
                    Buff buff = new Buff("花儿不会忘记", Buff.Category.BUFF, owner, owner, -1)
                            .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(cdmg,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(owner, buff);
                }
            }
        }

        /** 一行往日的血: 战技和终结技伤害提高#2. */
        static final class BloodLine implements Trace {
            private final double bonus;

            BloodLine(double bonus) {
                this.bonus = bonus;
            }

            @Override
            public String getName() {
                return "一行往日的血";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return type == SkillType.SKILL || type == SkillType.ULTRA ? 1 + bonus : 1.0;
            }
        }

        /** 氤氲麦香的梦: 终结技和追加攻击伤害提高#2 (近似: 全类型). */
        static final class DreamMalt implements Trace {
            private final double bonus;

            DreamMalt(double bonus) {
                this.bonus = bonus;
            }

            @Override
            public String getName() {
                return "氤氲麦香的梦";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return type == SkillType.ULTRA || type == SkillType.TALENT ? 1 + bonus : 1.0;
            }
        }

        /** 假日浴场大冒险: 攻击后, #2基础概率使目标易伤 (#3, #4回合). */
        static final class Resort implements Trace {
            private final double chance;
            private final double vuln;
            private final int turns;

            Resort(double chance, double vuln, int turns) {
                this.chance = chance;
                this.vuln = vuln;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "假日浴场大冒险";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                    return;
                }
                for (CanHit target : targets) {
                    if (target.isDeath() || target.hasBuffNamed("假日浴场")) {
                        continue;
                    }
                    if (battle.checkEffectHit(owner, target, chance)) {
                        Buff debuff = new Buff("假日浴场", Buff.Category.DEBUFF, owner, target, turns)
                                .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(vuln,
                                        DoubleValue.Modifier.ModifierSource.DEBUFF));
                        battle.applyBuff(target, debuff);
                    }
                }
            }
        }

        /** 于那终点再见: 战技和追加攻击伤害提高#2 (近似: 战技/全类型). */
        static final class Farewell implements Trace {
            private final double bonus;

            Farewell(double bonus) {
                this.bonus = bonus;
            }

            @Override
            public String getName() {
                return "于那终点再见";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return type == SkillType.SKILL || type == SkillType.TALENT ? 1 + bonus : 1.0;
            }
        }

        /** 菇菇嘎嘎历险记: 施放欢愉技时, 敌方全体受到的欢愉伤害提高#2 (#3回合). */
        static final class Mushroom implements Trace {
            private final double vuln;
            private final int turns;

            Mushroom(double vuln, int turns) {
                this.vuln = vuln;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "菇菇嘎嘎历险记";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.ELATION) {
                    return;
                }
                for (com.laosun.aluminium.models.Enemy enemy : battle.getAliveEnemies()) {
                    Buff debuff = new Buff("菇菇嘎嘎", Buff.Category.DEBUFF, owner, enemy, turns)
                            .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(vuln,
                                    DoubleValue.Modifier.ModifierSource.DEBUFF));
                    battle.applyBuff(enemy, debuff);
                }
            }
        }

        /** 今日好手气: 施放欢愉技时, 欢愉度提高#2 (最多叠加#3次). */
        static final class LuckyDay implements Trace {
            private final double perStack;
            private final int maxStacks;
            private int stacks = 0;

            LuckyDay(double perStack, int maxStacks) {
                this.perStack = perStack;
                this.maxStacks = maxStacks;
            }

            @Override
            public String getName() {
                return "今日好手气";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.ELATION) {
                    return;
                }
                stacks = Math.min(maxStacks, stacks + 1);
                owner.removeBuff("今日好手气");
                Buff buff = new Buff("今日好手气", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.ELATION_DAMAGE_BOOST, DoubleValue.Modifier.pure(perStack * stacks,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

    }
}
