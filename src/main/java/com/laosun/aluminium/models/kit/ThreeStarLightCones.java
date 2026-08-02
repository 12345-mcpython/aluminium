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
 * 三星光锥被动 (20 系列).
 *
 * <p>Each cone's passive is implemented as a {@link Trace} attached to the
 * equipped character at build time. Values are read from the weapon's
 * level-1 {@code skill_value} params, mirroring the game descriptions.
 */
public final class ThreeStarLightCones {

    private ThreeStarLightCones() {
    }

    /**
     * The hand-written passive for a ThreeStarLightCones light cone ID, or {@code null}
     * if the cone falls back to the generic interpreter.
     */
    public static Trace forWeapon(int wid, List<Double> params) {
        double p0 = param(params, 0, 0);
        double p1 = param(params, 1, 0);
        double p2 = param(params, 2, 0);
        return switch (wid) {
            case 20000 -> new Ctx.Arrows(p0, (int) p1);                       // 锋镝
            case 20001 -> new Ctx.Grain(p0);                                  // 物穰
            case 20002 -> new Ctx.Skyfall(p0);                                // 天倾
            case 20003 -> new Ctx.Amber(p2, p1);                              // 琥珀 (永久部分在属性里)
            case 20004 -> new Ctx.Abyss(p0, (int) p1);                        // 幽邃
            case 20005 -> new Ctx.Hymn(p0);                                   // 齐颂
            case 20006 -> new Ctx.Library(p0);                                // 智库
            case 20007 -> new Ctx.Bowstring(p0, (int) p1);                    // 离弦
            case 20008 -> new Ctx.Fruit(p0);                                  // 嘉果
            case 20009 -> new Ctx.Ruin(p1, p0);                               // 乐圮
            case 20010 -> new Ctx.Bastion(p0);                                // 戍御
            case 20011 -> new Ctx.Depth(p0);                                  // 渊环
            case 20012 -> new Ctx.TurningWheel(p0);                           // 轮契
            case 20013 -> new Ctx.Key(p0);                                    // 灵钥
            case 20014 -> new Ctx.Resist(p0, (int) p1);                       // 相抗
            case 20015 -> new Ctx.Growth(p0);                                 // 蕃息
            case 20016 -> new Ctx.Perish(p1, p0);                             // 俱殁
            case 20017 -> new Ctx.Frontier(p0);                               // 开疆
            case 20018 -> new Ctx.Conceal(p0);                                // 匿影
            case 20019 -> new Ctx.Harmony(p0, (int) p1);                      // 调和
            case 20020 -> new Ctx.Foresight(p0, (int) p1);                    // 睿见
            case 20021 -> new Ctx.Pyre((int) p0, p1);                         // 焚影
            case 20022 -> new Ctx.Recall(p0, (int) p1);                       // 溯忆
            case 20023 -> new Ctx.Snicker(p0);                                // 嗤笑
            case 20024 -> new Ctx.Tears(p0, p1);                              // 残泪
            default -> null;
        };
    }

    private static double param(List<Double> params, int index, double fallback) {
        return params != null && index < params.size() ? params.get(index) : fallback;
    }

    private static final class Ctx {

        /** 锋镝: 战斗开始时, 使装备者的暴击率提高#1, 持续#2回合. */
        static final class Arrows implements Trace {
            private final double crit;
            private final int turns;

            Arrows(double crit, int turns) {
                this.crit = crit;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "锋镝";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                Buff buff = new Buff("锋镝", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.CRIT_CHANCE, DoubleValue.Modifier.pure(crit,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 物穰: 装备者施放战技和终结技时, 治疗量提高#1. */
        static final class Grain implements Trace {
            private final double healBoost;

            Grain(double healBoost) {
                this.healBoost = healBoost;
            }

            @Override
            public String getName() {
                return "物穰";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.SKILL && type != SkillType.ULTRA) {
                    return;
                }
                owner.removeBuff("物穰");
                Buff buff = new Buff("物穰", Buff.Category.BUFF, owner, owner, 1)
                        .stat(AttributeType.OUTGOING_HEALING_BOOST, DoubleValue.Modifier.pure(healBoost,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 天倾: 使装备者普攻和战技造成的伤害提高#1. */
        static final class Skyfall implements Trace {
            private final double bonus;

            Skyfall(double bonus) {
                this.bonus = bonus;
            }

            @Override
            public String getName() {
                return "天倾";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return type == SkillType.COMMON || type == SkillType.SKILL ? 1 + bonus : 1.0;
            }
        }

        /** 琥珀: 生命值<#2时, 防御力额外提高#3 (永久#1在属性中). */
        static final class Amber implements Trace {
            private final double extraDef;
            private final double hpThreshold;

            Amber(double extraDef, double hpThreshold) {
                this.extraDef = extraDef;
                this.hpThreshold = hpThreshold;
            }

            @Override
            public String getName() {
                return "琥珀";
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
                boolean active = owner.hasBuffNamed("琥珀");
                boolean shouldBe = owner.getHpPercent() < hpThreshold;
                if (active && !shouldBe) {
                    owner.removeBuff("琥珀");
                } else if (!active && shouldBe) {
                    Buff buff = new Buff("琥珀", Buff.Category.BUFF, owner, owner, -1)
                            .stat(AttributeType.DEFENCE, DoubleValue.Modifier.addPercent(extraDef,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(owner, buff);
                }
            }
        }

        /** 幽邃: 战斗开始时, 使装备者的效果命中提高#1, 持续#2回合. */
        static final class Abyss implements Trace {
            private final double ehr;
            private final int turns;

            Abyss(double ehr, int turns) {
                this.ehr = ehr;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "幽邃";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                Buff buff = new Buff("幽邃", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.EFFECT_HIT_RATE, DoubleValue.Modifier.pure(ehr,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 齐颂: 进入战斗后, 使我方全体的攻击力提高#1. */
        static final class Hymn implements Trace {
            private final double atkBonus;

            Hymn(double atkBonus) {
                this.atkBonus = atkBonus;
            }

            @Override
            public String getName() {
                return "齐颂";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    Buff buff = new Buff("齐颂", Buff.Category.BUFF, owner, ally, -1)
                            .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atkBonus,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
            }
        }

        /** 智库: 使装备者终结技造成的伤害提高#1. */
        static final class Library implements Trace {
            private final double bonus;

            Library(double bonus) {
                this.bonus = bonus;
            }

            @Override
            public String getName() {
                return "智库";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return type == SkillType.ULTRA ? 1 + bonus : 1.0;
            }
        }

        /** 离弦: 使装备者消灭敌方目标后, 攻击力提高#1, 持续#2回合. */
        static final class Bowstring implements Trace {
            private final double atkBonus;
            private final int turns;

            Bowstring(double atkBonus, int turns) {
                this.atkBonus = atkBonus;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "离弦";
            }

            @Override
            public void onKill(Battle battle, Character owner, CanHit victim) {
                if (!(victim instanceof Enemy)) {
                    return;
                }
                owner.removeBuff("离弦");
                Buff buff = new Buff("离弦", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atkBonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 嘉果: 战斗开始时, 立即为我方全体恢复#1点能量. */
        static final class Fruit implements Trace {
            private final double energy;

            Fruit(double energy) {
                this.energy = energy;
            }

            @Override
            public String getName() {
                return "嘉果";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    ally.gainEnergy(energy);
                }
                IO.println("  [光锥] 嘉果: party restores " + String.format("%.0f", energy) + " energy");
            }
        }

        /** 乐圮: 使装备者对当前生命值百分比大于#1的敌方目标造成的伤害提高#2. */
        static final class Ruin implements Trace {
            private final double bonus;
            private final double hpThreshold;

            Ruin(double bonus, double hpThreshold) {
                this.bonus = bonus;
                this.hpThreshold = hpThreshold;
            }

            @Override
            public String getName() {
                return "乐圮";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return defender.getHpPercent() > hpThreshold ? 1 + bonus : 1.0;
            }
        }

        /** 戍御: 使装备者施放终结技时, 回复等同于自身生命上限#1的生命值. */
        static final class Bastion implements Trace {
            private final double healRatio;

            Bastion(double healRatio) {
                this.healRatio = healRatio;
            }

            @Override
            public String getName() {
                return "戍御";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type == SkillType.ULTRA) {
                    owner.heal(owner.getMaxHp() * healRatio);
                    IO.println("  [光锥] 戍御: recovers " + String.format("%.0f", owner.getMaxHp() * healRatio));
                }
            }
        }

        /** 渊环: 使装备者对减速状态下的敌方目标造成的伤害提高#1. */
        static final class Depth implements Trace {
            private final double bonus;

            Depth(double bonus) {
                this.bonus = bonus;
            }

            @Override
            public String getName() {
                return "渊环";
            }

            @Override
            public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
                return defender.hasBuffNamed("Slow") || defender.hasBuffNamed("Slow+") ? 1 + bonus : 1.0;
            }
        }

        /** 轮契: 施放攻击或受到攻击后, 额外恢复#1点能量 (单个回合内不可重复触发). */
        static final class TurningWheel implements Trace {
            private final double energy;
            private boolean usedThisTurn = false;

            TurningWheel(double energy) {
                this.energy = energy;
            }

            @Override
            public String getName() {
                return "轮契";
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

            @Override
            public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
                if (!usedThisTurn) {
                    usedThisTurn = true;
                    owner.gainEnergy(energy);
                }
            }
        }

        /** 灵钥: 使装备者施放战技后额外恢复#1点能量 (单个回合内不可重复触发). */
        static final class Key implements Trace {
            private final double energy;
            private boolean usedThisTurn = false;

            Key(double energy) {
                this.energy = energy;
            }

            @Override
            public String getName() {
                return "灵钥";
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                usedThisTurn = false;
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type == SkillType.SKILL && !usedThisTurn) {
                    usedThisTurn = true;
                    owner.gainEnergy(energy);
                }
            }
        }

        /** 相抗: 使装备者在消灭敌方目标后, 速度提高#1, 持续#2回合. */
        static final class Resist implements Trace {
            private final double speed;
            private final int turns;

            Resist(double speed, int turns) {
                this.speed = speed;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "相抗";
            }

            @Override
            public void onKill(Battle battle, Character owner, CanHit victim) {
                if (!(victim instanceof Enemy)) {
                    return;
                }
                owner.removeBuff("相抗");
                Buff buff = new Buff("相抗", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(speed,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 蕃息: 当装备者施放普攻后, 使下一次行动提前#1. */
        static final class Growth implements Trace {
            private final double advance;

            Growth(double advance) {
                this.advance = advance;
            }

            @Override
            public String getName() {
                return "蕃息";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type == SkillType.COMMON) {
                    battle.advanceByPercent(owner, advance);
                }
            }
        }

        /** 俱殁: 装备者当前生命值百分比小于#1时, 暴击率提高#2. */
        static final class Perish implements Trace {
            private final double crit;
            private final double hpThreshold;

            Perish(double crit, double hpThreshold) {
                this.crit = crit;
                this.hpThreshold = hpThreshold;
            }

            @Override
            public String getName() {
                return "俱殁";
            }

            @Override
            public double critChanceBonus(Battle battle, Character owner, CanHit defender, SkillType type) {
                return owner.getHpPercent() < hpThreshold ? crit : 0;
            }
        }

        /** 开疆: 当装备者击破敌方目标的弱点时, 回复等同于自身生命上限#1的生命值. */
        static final class Frontier implements Trace {
            private final double healRatio;

            Frontier(double healRatio) {
                this.healRatio = healRatio;
            }

            @Override
            public String getName() {
                return "开疆";
            }

            @Override
            public void onEnemyBreak(Battle battle, Character owner, Enemy enemy) {
                owner.heal(owner.getMaxHp() * healRatio);
                IO.println("  [光锥] 开疆: recovers " + String.format("%.0f", owner.getMaxHp() * healRatio));
            }
        }

        /** 匿影: 施放战技后, 使装备者的下一次普攻造成等同于自身#1攻击力的附加伤害. */
        static final class Conceal implements Trace {
            private final double atkRatio;
            private boolean empowered = false;

            Conceal(double atkRatio) {
                this.atkRatio = atkRatio;
            }

            @Override
            public String getName() {
                return "匿影";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type == SkillType.SKILL) {
                    empowered = true;
                } else if (type == SkillType.COMMON && empowered && !targets.isEmpty()) {
                    empowered = false;
                    battle.dealAttackDamage(owner, targets.getFirst(), atkRatio, 0,
                            com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                    com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL,
                                    owner.getElement()));
                    IO.println("  [光锥] 匿影: extra hit!");
                }
            }
        }

        /** 调和: 进入战斗时, 我方全体速度提高#1点, 持续#2回合. */
        static final class Harmony implements Trace {
            private final double speed;
            private final int turns;

            Harmony(double speed, int turns) {
                this.speed = speed;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "调和";
            }

            @Override
            public void onBattleStart(Battle battle, Character owner) {
                for (CanHit ally : battle.getAlivePlayerUnits()) {
                    Buff buff = new Buff("调和", Buff.Category.BUFF, owner, ally, turns)
                            .stat(AttributeType.SPEED, DoubleValue.Modifier.pure(speed,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
            }
        }

        /** 睿见: 当装备者施放终结技时, 攻击力提高#1, 持续#2回合. */
        static final class Foresight implements Trace {
            private final double atkBonus;
            private final int turns;

            Foresight(double atkBonus, int turns) {
                this.atkBonus = atkBonus;
                this.turns = turns;
            }

            @Override
            public String getName() {
                return "睿见";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (type != SkillType.ULTRA) {
                    return;
                }
                owner.removeBuff("睿见");
                Buff buff = new Buff("睿见", Buff.Category.BUFF, owner, owner, turns)
                        .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atkBonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }

        /** 焚影: 装备者首次召唤忆灵时, 恢复#1个战技点, 并恢复自身#2点能量. */
        static final class Pyre implements Trace {
            private final int skillPoints;
            private final double energy;
            private boolean used = false;

            Pyre(int skillPoints, double energy) {
                this.skillPoints = skillPoints;
                this.energy = energy;
            }

            @Override
            public String getName() {
                return "焚影";
            }

            @Override
            public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
                if (used || owner.getSummons().isEmpty()) {
                    return;
                }
                used = true;
                battle.addSkillPoints(skillPoints);
                owner.gainEnergy(energy);
                IO.println("  [光锥] 焚影: +" + skillPoints + " skill point, +"
                        + String.format("%.0f", energy) + " energy (first summon)");
            }
        }

        /** 溯忆: 忆灵回合开始时, 装备者与忆灵获得1层【缅怀】 (伤害+#1/层, 最多#2层). */
        static final class Recall implements Trace {
            private final double perStack;
            private final int maxStacks;
            private int stacks = 0;

            Recall(double perStack, int maxStacks) {
                this.perStack = perStack;
                this.maxStacks = maxStacks;
            }

            @Override
            public String getName() {
                return "溯忆";
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                if (owner.getSummons().isEmpty()) {
                    stacks = 0;
                    owner.removeBuff("缅怀");
                    return;
                }
                stacks = Math.min(maxStacks, stacks + 1);
                owner.removeBuff("缅怀");
                Buff buff = new Buff("缅怀", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(perStack * stacks,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
                IO.println("  [光锥] 溯忆: 【缅怀】 " + stacks + "/" + maxStacks + " stacks");
            }
        }

        /** 嗤笑: 阿哈时刻发动时, 使装备者的欢愉度提高#1, 持续到阿哈时刻结束. */
        static final class Snicker implements Trace {
            private final double elationBonus;

            Snicker(double elationBonus) {
                this.elationBonus = elationBonus;
            }

            @Override
            public String getName() {
                return "嗤笑";
            }

            @Override
            public void onAhaMoment(Battle battle, Character owner) {
                owner.removeBuff("嗤笑");
                Buff buff = new Buff("嗤笑", Buff.Category.BUFF, owner, owner, 1)
                        .stat(AttributeType.ELATION_DAMAGE_BOOST, DoubleValue.Modifier.pure(elationBonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
                IO.println("  [光锥] 嗤笑: 欢愉度 +" + String.format("%.0f", elationBonus * 100) + "%");
            }
        }

        /** 残泪: 当拥有的笑点大于等于#1时, 装备者的暴击伤害提高#2. */
        static final class Tears implements Trace {
            private final double laughThreshold;
            private final double cdmg;

            Tears(double laughThreshold, double cdmg) {
                this.laughThreshold = laughThreshold;
                this.cdmg = cdmg;
            }

            @Override
            public String getName() {
                return "残泪";
            }

            @Override
            public void onTurnStart(Battle battle, Character owner) {
                boolean active = owner.hasBuffNamed("残泪");
                boolean shouldBe = battle.getLaughPoints() >= laughThreshold;
                if (active && !shouldBe) {
                    owner.removeBuff("残泪");
                } else if (!active && shouldBe) {
                    Buff buff = new Buff("残泪", Buff.Category.BUFF, owner, owner, -1)
                            .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(cdmg,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(owner, buff);
                    IO.println("  [光锥] 残泪: 暴击伤害 +" + String.format("%.0f", cdmg * 100) + "%");
                }
            }
        }

    }
}
