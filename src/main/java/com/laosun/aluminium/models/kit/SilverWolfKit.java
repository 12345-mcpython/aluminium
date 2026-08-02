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
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.Trace;

import java.util.List;
import java.util.Map;

import static com.laosun.aluminium.models.kit.KitSupport.intParam;
import static com.laosun.aluminium.models.kit.KitSupport.param;

/**
 * 银狼 (Silver Wolf, cid 1006) — 量子属性 虚无.
 */
public final class SilverWolfKit implements CharacterKit {

    @Override
    public int cid() {
        return 1006;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new SilverWolfGeneration(intParam(byId, 1006101, 0, 1), param(byId, 1006101, 1, 0.65)),
                new SilverWolfInjection(intParam(byId, 1006102, 0, 1)),
                new SilverWolfMargin(intParam(byId, 1006103, 0, 3), param(byId, 1006103, 1, 0.03)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new SilverWolfE1(param(e, 0, 7), intParam(e, 1, 5));
            case 2 -> new SilverWolfE2(param(e, 0, 0.2));
            case 4 -> new SilverWolfE4(param(e, 0, 0.2), intParam(e, 1, 5));
            case 6 -> new SilverWolfE6(param(e, 0, 0.2), param(e, 1, 1.0));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> SilverWolfKit::silverWolfSkill;
            case 3 -> SilverWolfKit::silverWolfUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 有X%基础概率为目标添加1个我方属性的弱点, 并降低其全属性抗性. */
    static void silverWolfSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit target = targets.getFirst();
        double multiplier = ctx.firstParam();
        double implantChance = ctx.param(1, 0.75);
        int implantTurns = ctx.intParam(2, 2);
        double allResChance = ctx.param(4, 1.0);
        double allResDown = ctx.param(5, 0.075);
        int allResTurns = ctx.intParam(6, 2);
        ctx.dealDamage(battle, user, target, multiplier);
        battle.breakToughness(user, target, ctx.stanceSingle());
        if (target instanceof Enemy enemy && battle.checkEffectHit(user, target, implantChance)) {
            enemy.addTemporaryWeakness(user.getElement(), implantTurns);
            IO.println("  " + enemy.getName() + " gains " + user.getElement().string + " weakness!");
        }
        if (battle.checkEffectHit(user, target, allResChance)) {
            Buff allRes = new Buff("全属性抗性降低", Buff.Category.DEBUFF, user, target, allResTurns)
                    .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(allResDown,
                            DoubleValue.Modifier.ModifierSource.DEBUFF));
            battle.applyBuff(target, allRes);
        }
    }

    /** 终结技: 造成伤害, 有X%基础概率使目标防御力降低. */
    static void silverWolfUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit target = targets.getFirst();
        double multiplier = ctx.firstParam();
        double defDownChance = ctx.param(1, 0.85);
        double defDownValue = ctx.param(2, 0.36);
        int defDownTurns = ctx.intParam(3, 3);
        ctx.dealDamage(battle, user, target, multiplier);
        battle.breakToughness(user, target, ctx.stanceSingle());
        if (battle.checkEffectHit(user, target, defDownChance)) {
            Buff defDown = new Buff("DEF Down", Buff.Category.DEBUFF, user, target, defDownTurns)
                    .stat(AttributeType.DEFENCE, DoubleValue.Modifier.addPercent(-defDownValue,
                            DoubleValue.Modifier.ModifierSource.DEBUFF));
            battle.applyBuff(target, defDown);
        }
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 生成: 每当有敌方目标的弱点被击破时，银狼有65%基础概率对该目标植入1个随机【缺陷】。 */
    static class SilverWolfGeneration implements Trace {
        private final double defectTurns;
        private final double chance;

        SilverWolfGeneration(double defectTurns, double chance) {
            this.defectTurns = defectTurns;
            this.chance = chance;
        }

        @Override
        public String getName() {
            return "生成";
        }

        @Override
        public void onEnemyBreak(Battle battle, Character owner, Enemy enemy) {
            if (battle.checkEffectHit(owner, enemy, chance)) {
                Buff defect = new Buff("缺陷", Buff.Category.DEBUFF, owner, enemy, (int) defectTurns)
                        .stat(AttributeType.DEFENCE, DoubleValue.Modifier.addPercent(-0.1,
                                DoubleValue.Modifier.ModifierSource.DEBUFF));
                battle.applyBuff(enemy, defect);
            }
        }
    }

    /** 注入: 施放战技时，为敌方目标添加的弱点持续时间增加1回合。 */
    static class SilverWolfInjection implements Trace {
        private final int extraTurns;

        SilverWolfInjection(int extraTurns) {
            this.extraTurns = extraTurns;
        }

        @Override
        public String getName() {
            return "注入";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL) {
                return;
            }
            for (CanHit target : targets) {
                if (target instanceof Enemy enemy && enemy.getTemporaryWeaknessTurns(owner.getElement()) > 0) {
                    enemy.extendTemporaryWeakness(owner.getElement(), extraTurns);
                    IO.println("  [行迹] " + enemy.getName() + "'s " + owner.getElement().string
                            + " weakness extended (+" + extraTurns + " turn)");
                }
            }
        }
    }

    /** 旁注: 施放战技时，若敌方目标负面效果 ≥ 3个，战技使目标全属性抗性降低效果额外降低3%。 */
    static class SilverWolfMargin implements Trace {
        private final int debuffThreshold;
        private final double extra;

        SilverWolfMargin(int debuffThreshold, double extra) {
            this.debuffThreshold = debuffThreshold;
            this.extra = extra;
        }

        @Override
        public String getName() {
            return "旁注";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL) {
                return;
            }
            for (CanHit target : targets) {
                long debuffs = target.getBuffs().stream()
                        .filter(b -> b.getCategory() == Buff.Category.DEBUFF).count();
                if (debuffs >= debuffThreshold) {
                    Buff buff = new Buff("旁注", Buff.Category.DEBUFF, owner, target, 2)
                            .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(extra,
                                    DoubleValue.Modifier.ModifierSource.DEBUFF));
                    battle.applyBuff(target, buff);
                }
            }
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 社会工程: 终结技后，目标每有1个负面效果恢复7点能量，最多5次。 */
    static class SilverWolfE1 implements Trace {
        private final double energyPerDebuff;
        private final int maxTriggers;

        SilverWolfE1(double energyPerDebuff, int maxTriggers) {
            this.energyPerDebuff = energyPerDebuff;
            this.maxTriggers = maxTriggers;
        }

        @Override
        public String getName() {
            return "社会工程";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.ULTRA) {
                for (CanHit target : targets) {
                    long debuffs = target.getBuffs().stream()
                            .filter(b -> b.getCategory() == Buff.Category.DEBUFF).count();
                    int triggers = (int) Math.min(maxTriggers, debuffs);
                    if (triggers > 0) {
                        owner.gainEnergy(energyPerDebuff * triggers);
                        IO.println("  [星魂] " + owner.getName() + " restores "
                                + String.format("%.0f", energyPerDebuff * triggers)
                                + " energy (" + triggers + " debuffs)");
                    }
                }
            }
        }
    }

    /** 星魂2 僵尸网络: 敌方目标进入战斗时，效果抵抗降低20%。 */
    static class SilverWolfE2 implements Trace {
        private final double resistanceDown;

        SilverWolfE2(double resistanceDown) {
            this.resistanceDown = resistanceDown;
        }

        @Override
        public String getName() {
            return "僵尸网络";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (Enemy enemy : battle.getAliveEnemies()) {
                Buff buff = new Buff("僵尸网络", Buff.Category.DEBUFF, owner, enemy, -1)
                        .stat(AttributeType.EFFECT_RESISTANCE, DoubleValue.Modifier.pure(-resistanceDown,
                                DoubleValue.Modifier.ModifierSource.DEBUFF));
                battle.applyBuff(enemy, buff);
            }
        }
    }

    /** 星魂4 反弹端口: 终结技后，目标每有1个负面效果额外造成20%攻击力的量子伤害，最多5次。 */
    static class SilverWolfE4 implements Trace {
        private final double atkRatio;
        private final int maxTriggers;

        SilverWolfE4(double atkRatio, int maxTriggers) {
            this.atkRatio = atkRatio;
            this.maxTriggers = maxTriggers;
        }

        @Override
        public String getName() {
            return "反弹端口";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.ULTRA) {
                for (CanHit target : targets) {
                    long debuffs = target.getBuffs().stream()
                            .filter(b -> b.getCategory() == Buff.Category.DEBUFF).count();
                    int triggers = (int) Math.min(maxTriggers, debuffs);
                    for (int i = 0; i < triggers; i++) {
                        battle.dealAttackDamage(owner, target, atkRatio, 0,
                                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                        com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL,
                                        com.laosun.aluminium.enums.Element.QUANTUM));
                    }
                    if (triggers > 0) {
                        IO.println("  [星魂] " + owner.getName() + " deals " + triggers + " extra quantum hits");
                    }
                }
            }
        }
    }

    /** 星魂6 重叠网络: 目标每有1个负面效果，伤害提高20%，最多100%。 */
    static class SilverWolfE6 implements Trace {
        private final double perDebuff;
        private final double cap;

        SilverWolfE6(double perDebuff, double cap) {
            this.perDebuff = perDebuff;
            this.cap = cap;
        }

        @Override
        public String getName() {
            return "重叠网络";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            long debuffs = defender.getBuffs().stream()
                    .filter(b -> b.getCategory() == Buff.Category.DEBUFF).count();
            return 1 + Math.min(cap, debuffs * perDebuff);
        }
    }
}
