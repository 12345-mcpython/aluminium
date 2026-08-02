package com.laosun.aluminium.models.kit;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
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
 * 貊泽 (Moze, cid 1223) — 雷属性 巡猎.
 */
public final class MozeKit implements CharacterKit {

    @Override
    public int cid() {
        return 1223;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new MozeInk(intParam(byId, 1223101, 0, 1), intParam(byId, 1223101, 1, 1)),
                new MozeAdvance(param(byId, 1223102, 0, 0.2), param(byId, 1223102, 1, 0.3)),
                new MozeUnbroken(param(byId, 1223103, 0, 0.25)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new MozeE1(intParam(e, 0, 2), param(e, 1, 20));
            case 2 -> new MozeE2(param(e, 0, 0.4));
            case 4 -> new MozeE4(param(e, 0, 0.3), intParam(e, 1, 2));
            case 6 -> new MozeE6(param(e, 0, 0.25));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> MozeKit::mozeSkill;
            case 3 -> MozeKit::mozeUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技 迅羽掠袭: 使指定敌方单体目标成为【猎物】, 造成#1%伤害, 并获得#2点充能。 */
    static void mozeSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit target = targets.getFirst();
        int charge = ctx.intParam(1, 9);
        for (Enemy enemy : battle.getAliveEnemies()) {
            if (enemy != target) {
                enemy.removeBuff("猎物");
            }
        }
        target.removeBuff("猎物");
        battle.applyBuff(target, new Buff("猎物", Buff.Category.DEBUFF, user, target, -1));
        ctx.dealDamage(battle, user, target, ctx.firstParam());
        battle.breakToughness(user, target, ctx.stanceSingle());
        if (user instanceof Character character) {
            for (Trace trace : character.getTraces()) {
                if (trace instanceof MozeInk ink) {
                    ink.setCharge(charge);
                }
            }
        }
        IO.println("  " + target.getName() + " becomes 【猎物】 (" + charge + " 充能)");
    }

    /** 终结技 锋入幽渺: 对目标造成#1%伤害, 并对该目标发动天赋的追加攻击。 */
    static void mozeUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit target = targets.getFirst();
        ctx.dealDamage(battle, user, target, ctx.firstParam());
        battle.breakToughness(user, target, ctx.stanceSingle());
        if (user instanceof Character character) {
            talentFollowUp(battle, character, target);
        }
    }

    // ─── 天赋代理 (伸天卑飞) ─────────────────────────────────────────────

    /** 天赋的追加攻击: 对【猎物】造成#3%雷属性伤害 (伤害倍率 #1; 星魂6 使倍率提高#2). */
    static void talentFollowUp(Battle battle, Character owner, CanHit target) {
        if (target == null || target.isDeath()) {
            List<Enemy> alive = battle.getAliveEnemies();
            if (alive.isEmpty()) {
                return;
            }
            target = alive.get((int) (Math.random() * alive.size()));
        }
        List<Double> p = rawParams(owner, 4);
        double mult = p.size() > 2 ? p.get(2) : 0.8;
        for (Trace trace : owner.getTraces()) {
            if (trace instanceof MozeE6 e6) {
                mult += e6.multBonus;
            }
        }
        battle.dealAttackDamage(owner, target, mult, 0,
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP, Element.THUNDER));
        battle.breakToughness(owner, target, 1.0);
        IO.println("  [天赋] " + owner.getName() + " follows up on " + target.getName());
    }

    private static List<Double> rawParams(Character owner, int skillId) {
        Map<Integer, com.laosun.aluminium.beans.Skill> map = Constant.SKILLS.get(owner.getCid());
        com.laosun.aluminium.beans.Skill skill = map == null ? null : map.get(skillId);
        if (skill == null || skill.paramList() == null || skill.paramList().isEmpty()) {
            return List.of();
        }
        return skill.paramList().getFirst();
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /**
     * 墨毫绣衣: 施放天赋的追加攻击后恢复#1个战技点 (该效果在#2回合后可再次触发)。
     * 兼作天赋 伸天卑飞，折翅为芒 的代理: 场上存在【猎物】时貊泽进入离场状态; 我方目标攻击
     * 【猎物】后, 貊泽额外造成1次#1%的雷属性附加伤害并消耗1点充能; 每消耗#2点充能对【猎物】
     * 发动1次追加攻击; 充能为0时解除【猎物】状态并重置。
     */
    static class MozeInk implements Trace {
        private final int skillPoints;
        private final int cooldown;
        private int charge = 0;
        private int spCooldown = 0;

        MozeInk(int skillPoints, int cooldown) {
            this.skillPoints = skillPoints;
            this.cooldown = cooldown;
        }

        @Override
        public String getName() {
            return "墨毫绣衣";
        }

        void setCharge(int charge) {
            this.charge = charge;
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            if (spCooldown > 0) {
                spCooldown--;
            }
        }

        @Override
        public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                 List<? extends CanHit> targets) {
            if (!isAttack(type) || targets == null || targets.isEmpty()) {
                return;
            }
            CanHit prey = targets.getFirst();
            if (prey.isDeath() || !prey.hasBuffNamed("猎物")) {
                return;
            }
            // 附加伤害 (倍率#1) + 消耗1点充能.
            List<Double> p = rawParams(owner, 4);
            double extraMult = p.size() > 0 ? p.get(0) : 0.15;
            battle.dealAttackDamage(owner, prey, extraMult, 0,
                    com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                            com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP, Element.THUNDER));
            // 星魂1: 每触发1次天赋的附加伤害, 恢复#1点能量.
            for (Trace trace : owner.getTraces()) {
                if (trace instanceof MozeE1 e1) {
                    owner.gainEnergy(e1.energy);
                }
            }
            charge--;
            int chargePerFollowUp = p.size() > 1 ? p.get(1).intValue() : 3;
            if (charge > 0 && charge % chargePerFollowUp == 0) {
                talentFollowUp(battle, owner, prey);
                // 墨毫绣衣: 追加攻击后恢复1个战技点 (冷却#2回合).
                if (spCooldown <= 0) {
                    spCooldown = cooldown;
                    battle.addSkillPoints(skillPoints);
                    IO.println("  [行迹] " + owner.getName() + " restores " + skillPoints
                            + " skill point (墨毫绣衣)");
                }
                for (Trace trace : owner.getTraces()) {
                    if (trace instanceof MozeAdvance advance) {
                        advance.advanceOnLeave(battle, owner);
                    }
                }
            }
            if (charge <= 0) {
                for (Enemy enemy : battle.getAliveEnemies()) {
                    enemy.removeBuff("猎物");
                }
                charge = 0;
                for (Trace trace : owner.getTraces()) {
                    if (trace instanceof MozeAdvance advance) {
                        advance.advanceOnLeave(battle, owner);
                    }
                }
                IO.println("  [天赋] 【猎物】 removed, " + owner.getName() + " leaves 离场");
            }
        }
    }

    /** 手奋匕尺: 貊泽解除离场状态时行动提前#1; 每个波次开始时行动提前#2 (以战斗开始代理). */
    static class MozeAdvance implements Trace {
        private final double leaveAdvance;
        private final double waveAdvance;

        MozeAdvance(double leaveAdvance, double waveAdvance) {
            this.leaveAdvance = leaveAdvance;
            this.waveAdvance = waveAdvance;
        }

        @Override
        public String getName() {
            return "手奋匕尺";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            battle.advanceByPercent(owner, waveAdvance);
            IO.println("  [行迹] " + owner.getName() + " advances "
                    + String.format("%.0f%%", waveAdvance * 100) + " (手奋匕尺, wave start)");
        }

        void advanceOnLeave(Battle battle, Character owner) {
            battle.advanceByPercent(owner, leaveAdvance);
            IO.println("  [行迹] " + owner.getName() + " advances "
                    + String.format("%.0f%%", leaveAdvance * 100) + " (手奋匕尺, leaves 离场)");
        }
    }

    /** 不折镆干: 施放终结技造成伤害时被视为发动了追加攻击; 【猎物】受到的追加攻击伤害提高#1
     *  (终结技视为追加攻击以伤害倍率代理, 见 desc)。 */
    static class MozeUnbroken implements Trace {
        private final double bonus;

        MozeUnbroken(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "不折镆干";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return type == SkillType.ULTRA && defender.hasBuffNamed("猎物") ? 1 + bonus : 1.0;
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 矢志: 进入战斗后恢复#2点能量; 每触发1次天赋的附加伤害恢复#1点能量。 */
    static class MozeE1 implements Trace {
        private final double energy;
        private final double startEnergy;

        MozeE1(double energy, double startEnergy) {
            this.energy = energy;
            this.startEnergy = startEnergy;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            owner.gainEnergy(startEnergy);
            IO.println("  [星魂] " + owner.getName() + " restores "
                    + String.format("%.0f", startEnergy) + " energy (矢志)");
        }
    }

    /** 星魂2 惩膺: 我方全体目标对成为【猎物】的敌方目标造成伤害时暴击伤害提高#1
     *  (以常驻暴击伤害buff代理, 见 desc)。 */
    static class MozeE2 implements Trace {
        private final double critDamage;

        MozeE2(double critDamage) {
            this.critDamage = critDamage;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                ally.removeBuff("惩膺");
                Buff buff = new Buff("惩膺", Buff.Category.BUFF, owner, ally, -1)
                        .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(critDamage,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
        }
    }

    /** 星魂4 逐薮: 施放终结技时，貊泽造成的伤害提高#1，持续#2回合。 */
    static class MozeE4 implements Trace {
        private final double dmgBoost;
        private final int turns;

        MozeE4(double dmgBoost, int turns) {
            this.dmgBoost = dmgBoost;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.ULTRA) {
                return;
            }
            owner.removeBuff("逐薮");
            Buff buff = new Buff("逐薮", Buff.Category.BUFF, owner, owner, turns)
                    .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(dmgBoost,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 星魂6 丹心: 天赋的追加攻击的伤害倍率提高#1。 */
    static class MozeE6 implements Trace {
        private final double multBonus;

        MozeE6(double multBonus) {
            this.multBonus = multBonus;
        }

        @Override
        public String getName() {
            return "星魂6";
        }
    }

    /** 是否为攻击类行动 (普攻/战技/终结技). */
    static boolean isAttack(SkillType type) {
        return type == SkillType.COMMON || type == SkillType.SKILL || type == SkillType.ULTRA;
    }
}
