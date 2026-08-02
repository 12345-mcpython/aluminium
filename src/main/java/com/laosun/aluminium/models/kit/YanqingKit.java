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
import com.laosun.aluminium.models.Trace;

import java.util.List;
import java.util.Map;

import static com.laosun.aluminium.models.kit.KitSupport.intParam;
import static com.laosun.aluminium.models.kit.KitSupport.param;

/**
 * 彦卿 (Yanqing, cid 1209) — 冰属性 巡猎.
 *
 * <p>核心机制【智剑连心】: 战技施放后附加 (天赋提供的 暴击率/暴击伤害 加成一并并入),
 * 受到伤害后消失; 行迹 凌霜 / 星魂2 在持有【智剑连心】时提供效果抵抗与能量恢复效率。
 */
public final class YanqingKit implements CharacterKit {

    @Override
    public int cid() {
        return 1209;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new YanqingIceBonus(param(byId, 1209101, 0, 0.3)),
                new YanqingHeart(param(byId, 1209102, 0, 0.2)),
                new YanqingSpeed(param(byId, 1209103, 0, 0.1), intParam(byId, 1209103, 1, 2)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new YanqingE1(param(e, 0, 0.6));
            case 2 -> new YanqingE2(param(e, 0, 0.1));
            case 4 -> new YanqingE4(param(e, 0, 0.8), param(e, 1, 0.12));
            case 6 -> new YanqingE6();
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> YanqingKit::yanqingSkill;
            case 3 -> YanqingKit::yanqingUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 对指定敌方单体造成#1%攻击力的冰属性伤害, 并为彦卿附加【智剑连心】, 持续1回合.
     *  (天赋被动 暴击率+15%/暴击伤害+15% 随【智剑连心】一并生效.) */
    static void yanqingSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        CanHit target = targets.getFirst();
        ctx.dealDamage(battle, user, target, multiplier);
        battle.breakToughness(user, target, ctx.stanceSingle());
        user.removeBuff("智剑连心");
        Buff heart = new Buff("智剑连心", Buff.Category.BUFF, user, user, 1)
                .stat(AttributeType.CRIT_CHANCE, DoubleValue.Modifier.pure(0.15,
                        DoubleValue.Modifier.ModifierSource.BUFF))
                .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(0.15,
                        DoubleValue.Modifier.ModifierSource.BUFF));
        battle.applyBuff(user, heart);
        IO.println("  " + user.getName() + " gains 【智剑连心】 (+15% crit rate / crit damage, 1 turn)");
    }

    /** 终结技: 提高自身#1暴击率 (#2额外暴击伤害, 需【智剑连心】), 持续1回合;
     *  随后对指定敌方单体造成#3%攻击力的冰属性伤害。 */
    static void yanqingUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double critChance = ctx.firstParam();
        double extraCritDamage = ctx.param(1, 0.3);
        double multiplier = ctx.param(2, 2.1);
        user.removeBuff("快雨燕相逐");
        Buff ultBuff = new Buff("快雨燕相逐", Buff.Category.BUFF, user, user, 1)
                .stat(AttributeType.CRIT_CHANCE, DoubleValue.Modifier.pure(critChance,
                        DoubleValue.Modifier.ModifierSource.BUFF));
        if (user.hasBuffNamed("智剑连心")) {
            ultBuff.stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(extraCritDamage,
                    DoubleValue.Modifier.ModifierSource.BUFF));
        }
        battle.applyBuff(user, ultBuff);
        CanHit target = targets.getFirst();
        ctx.dealDamage(battle, user, target, multiplier);
        battle.breakToughness(user, target, ctx.stanceSingle());
        IO.println("  " + user.getName() + " casts 快雨燕相逐 (crit +"
                + String.format("%.0f%%", critChance * 100) + ")");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /**
     * 颁冰: 施放攻击后，对携带冰属性弱点的敌方目标造成#1%攻击力的冰属性附加伤害。
     * 兼作天赋 呼剑如影 的代理: 攻击后有#3固定概率发动追加攻击 (造成#6%攻击力的冰属性伤害,
     * 并有#4基础概率使目标冻结1回合, 冻结期间每回合受到#5%攻击力的冰属性附加伤害);
     * 彦卿受到伤害后【智剑连心】消失。
     */
    static class YanqingIceBonus implements Trace {
        private final double bonus;

        YanqingIceBonus(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "颁冰";
        }

        @Override
        public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
            owner.removeBuff("智剑连心");
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                return;
            }
            // 颁冰: 对携带冰属性弱点的目标造成附加伤害.
            for (CanHit target : targets) {
                if (target instanceof Enemy enemy && enemy.isWeakTo(Element.ICE)) {
                    battle.dealAttackDamage(owner, enemy, bonus, 0,
                            com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                    com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, Element.ICE));
                }
            }
            // 天赋代理: 固定概率追加攻击 + 冻结.
            double followUpChance = 0.5;
            double freezeChance = 0.25;
            double dotRatio = 0.25;
            double followUpRatio = 0.65;
            com.laosun.aluminium.models.SkillData talent = KitSupport.skillData(owner, SkillType.TALENT);
            if (talent != null && talent.getSkills() != null && !talent.getSkills().isEmpty()
                    && talent.getSkills().getFirst().size() >= 6) {
                List<Double> t = talent.getSkills().getFirst();
                followUpChance = t.get(2);
                freezeChance = t.get(3);
                dotRatio = t.get(4);
                followUpRatio = t.get(5);
            }
            if (Math.random() < followUpChance) {
                for (CanHit target : targets) {
                    if (target.isDeath()) {
                        continue;
                    }
                    battle.dealAttackDamage(owner, target, followUpRatio, 0,
                            com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                    com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP,
                                    Element.ICE));
                    if (target.isDeath() || target.getControlState() != null) {
                        continue;
                    }
                    if (battle.checkEffectHit(owner, target, freezeChance)) {
                        double dot = owner.getAttribute(AttributeType.ATTACK) != null
                                ? owner.getAttribute(AttributeType.ATTACK).get() * dotRatio : 0;
                        Buff freeze = new Buff("Freeze", Buff.Category.DEBUFF, owner, target, 1)
                                .control(Buff.ControlType.FROZEN)
                                .dot(dot, Element.ICE, 1);
                        battle.applyBuff(target, freeze);
                        target.setControlState(Buff.ControlType.FROZEN);
                        IO.println("  [行迹] " + target.getName() + " is FROZEN! (呼剑如影)");
                    }
                }
            }
        }
    }

    /** 凌霜: 处于【智剑连心】效果时，效果抵抗提高#1。 */
    static class YanqingHeart implements Trace {
        private final double resistance;

        YanqingHeart(double resistance) {
            this.resistance = resistance;
        }

        @Override
        public String getName() {
            return "凌霜";
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            boolean active = owner.hasBuffNamed("智剑连心");
            boolean buffed = owner.hasBuffNamed("凌霜");
            if (active && !buffed) {
                Buff buff = new Buff("凌霜", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.EFFECT_RESISTANCE, DoubleValue.Modifier.pure(resistance,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            } else if (!active && buffed) {
                owner.removeBuff("凌霜");
            }
        }
    }

    /** 轻吕: 触发暴击时，速度提高#1，持续#2回合。
     *  (引擎无法捕获暴击事件, 代理为每次攻击后刷新速度增益.) */
    static class YanqingSpeed implements Trace {
        private final double speedPercent;
        private final int turns;

        YanqingSpeed(double speedPercent, int turns) {
            this.speedPercent = speedPercent;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "轻吕";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                return;
            }
            owner.removeBuff("轻吕");
            Buff buff = new Buff("轻吕", Buff.Category.BUFF, owner, owner, turns)
                    .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(speedPercent,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 素刃: 攻击敌方目标时，如果目标处于冻结状态，则立即对目标造成#1%攻击力的冰属性附加伤害。 */
    static class YanqingE1 implements Trace {
        private final double ratio;

        YanqingE1(double ratio) {
            this.ratio = ratio;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                return;
            }
            for (CanHit target : targets) {
                if (target.getControlState() == Buff.ControlType.FROZEN) {
                    battle.dealAttackDamage(owner, target, ratio, 0,
                            com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                    com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, Element.ICE));
                    IO.println("  [星魂] " + owner.getName() + " hits the frozen foe again (素刃)");
                }
            }
        }
    }

    /** 星魂2 空明: 处于【智剑连心】效果时，额外提高#1的能量恢复效率。 */
    static class YanqingE2 implements Trace {
        private final double regen;

        YanqingE2(double regen) {
            this.regen = regen;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            boolean active = owner.hasBuffNamed("智剑连心");
            boolean buffed = owner.hasBuffNamed("空明");
            if (active && !buffed) {
                Buff buff = new Buff("空明", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.ENERGY_REGENERATION_RATE, DoubleValue.Modifier.pure(regen,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            } else if (!active && buffed) {
                owner.removeBuff("空明");
            }
        }
    }

    /** 星魂4 霜厉: 当前生命值百分比大于等于#1时，提高自身#2的冰属性抗性穿透。 */
    static class YanqingE4 implements Trace {
        private final double hpThreshold;
        private final double penetration;

        YanqingE4(double hpThreshold, double penetration) {
            this.hpThreshold = hpThreshold;
            this.penetration = penetration;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        private void refresh(Battle battle, Character owner) {
            boolean shouldBe = owner.getHpPercent() >= hpThreshold;
            boolean active = owner.hasBuffNamed("霜厉");
            if (shouldBe && !active) {
                Buff buff = new Buff("霜厉", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.RESISTANCE_PENETRATION, DoubleValue.Modifier.pure(penetration,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            } else if (!shouldBe && active) {
                owner.removeBuff("霜厉");
            }
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            refresh(battle, owner);
        }

        @Override
        public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
            refresh(battle, owner);
        }
    }

    /** 星魂6 自在: 消灭敌方目标时，如果当前持有【智剑连心】或终结技的增益效果，
     *  则使这些增益效果的持续时间全部延长1回合。 */
    static class YanqingE6 implements Trace {
        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void onKill(Battle battle, Character owner, CanHit victim) {
            if (!(victim instanceof Enemy)) {
                return;
            }
            boolean any = false;
            for (Buff buff : new java.util.ArrayList<>(owner.getBuffs())) {
                if (buff.getCategory() == Buff.Category.BUFF && buff.getDuration() > 0
                        && ("智剑连心".equals(buff.getName()) || "快雨燕相逐".equals(buff.getName()))) {
                    buff.setDuration(buff.getDuration() + 1);
                    any = true;
                }
            }
            if (any) {
                IO.println("  [星魂] " + owner.getName() + " extends its buffs by 1 turn (自在)");
            }
        }
    }
}
