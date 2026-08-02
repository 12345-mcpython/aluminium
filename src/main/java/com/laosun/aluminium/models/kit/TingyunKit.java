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
import com.laosun.aluminium.models.SkillData;
import com.laosun.aluminium.models.Trace;

import java.util.List;
import java.util.Map;

import static com.laosun.aluminium.models.kit.KitSupport.param;

/**
 * 停云 (Tingyun, cid 1202) — 雷属性 同谐.
 */
public final class TingyunKit implements CharacterKit {

    @Override
    public int cid() {
        return 1202;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new TingyunClearSky(param(byId, 1202101, 0, 0.2)),
                new TingyunHalt(param(byId, 1202102, 0, 0.4)),
                new TingyunProsper(param(byId, 1202103, 0, 5)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new TingyunE1(param(e, 0, 0.2));
            case 2 -> new TingyunE2(param(e, 0, 5));
            case 4 -> new TingyunE4(param(e, 0, 0.2));
            case 6 -> new TingyunE6(param(e, 0, 10));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> TingyunKit::tingyunSkill;
            case 3 -> TingyunKit::tingyunUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /**
     * 战技: 为指定我方单体提供【赐福】: 攻击力提高25% (最高不超过停云攻击力的15%),
     * 持续3回合。获得【赐福】的目标施放攻击后, 额外造成1次自身攻击力X%的雷属性附加伤害
     * (由行迹 驻晴 的钩子代理)。
     */
    static void tingyunSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double atkRatio = ctx.param(1, 0.25);
        double capRatio = ctx.param(3, 0.15);
        int turns = ctx.intParam(2, 3);
        List<? extends CanHit> allies = KitSupport.friendlyTargets(battle, user, targets);
        if (allies.isEmpty()) {
            return;
        }
        // 【赐福】仅对停云战技最新的施放目标生效.
        CanHit target = allies.getFirst();
        for (CanHit ally : battle.getAlivePlayerUnits()) {
            if (ally != target) {
                ally.removeBuff("赐福");
            }
        }
        double userAtk = user.getAttribute(AttributeType.ATTACK) != null
                ? user.getAttribute(AttributeType.ATTACK).get() : 0;
        double targetAtk = target.getAttribute(AttributeType.ATTACK) != null
                ? target.getAttribute(AttributeType.ATTACK).get() : 0;
        double bonus = Math.min(targetAtk * atkRatio, userAtk * capRatio);
        double percent = targetAtk > 0 ? bonus / targetAtk : 0;
        target.removeBuff("赐福");
        Buff buff = new Buff("赐福", Buff.Category.BUFF, user, target, turns)
                .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(percent,
                        DoubleValue.Modifier.ModifierSource.BUFF));
        battle.applyBuff(target, buff);
        IO.println("  " + target.getName() + " gains 【赐福】 (ATK +"
                + String.format("%.1f%%", percent * 100) + ", " + turns + " turns)");
    }

    /**
     * 终结技: 为指定我方单体恢复50点能量, 并使其造成的伤害提高20%, 持续2回合
     * (星魂6 额外恢复10点能量)。
     */
    static void tingyunUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double energy = ctx.firstParam();
        int turns = ctx.intParam(1, 2);
        double dmgBoost = ctx.param(2, 0.2);
        List<? extends CanHit> allies = KitSupport.friendlyTargets(battle, user, targets);
        if (allies.isEmpty()) {
            return;
        }
        CanHit target = allies.getFirst();
        target.gainEnergy(energy);
        target.removeBuff("庆云光覆仪祷");
        Buff buff = new Buff("庆云光覆仪祷", Buff.Category.BUFF, user, target, turns)
                .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(dmgBoost,
                        DoubleValue.Modifier.ModifierSource.BUFF));
        battle.applyBuff(target, buff);
        IO.println("  " + target.getName() + " restores " + String.format("%.0f", energy)
                + " energy and gains +" + String.format("%.0f%%", dmgBoost * 100)
                + " damage (" + turns + " turns)");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /**
     * 驻晴: 施放战技时，停云自身速度提高20%，持续1回合。
     * 同时代理战技/天赋的【赐福】附加伤害 (天赋: 敌方目标受到停云攻击后,
     * 获得【赐福】的目标立即对其造成附加伤害; 战技: 赐福目标施放攻击后触发).
     */
    static class TingyunClearSky implements Trace {
        private final double speedPercent;

        TingyunClearSky(double speedPercent) {
            this.speedPercent = speedPercent;
        }

        @Override
        public String getName() {
            return "驻晴";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.SKILL) {
                owner.removeBuff("驻晴");
                Buff buff = new Buff("驻晴", Buff.Category.BUFF, owner, owner, 1)
                        .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(speedPercent,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
                IO.println("  [行迹] " + owner.getName() + " speeds up (+"
                        + String.format("%.0f%%", speedPercent * 100) + ", 驻晴)");
            }
            if ((type == SkillType.COMMON || type == SkillType.SKILL || type == SkillType.ULTRA)
                    && targets != null && !targets.isEmpty()) {
                // 天赋 紫电扶摇: 敌方目标受到停云攻击后, 赐福目标对其造成附加伤害.
                blessedFollowUp(battle, owner, targets.getFirst());
            }
        }

        @Override
        public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                 List<? extends CanHit> targets) {
            if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                return;
            }
            if (targets == null || targets.isEmpty()) {
                return;
            }
            // 战技效果: 获得【赐福】的目标施放攻击后, 额外造成1次雷属性附加伤害.
            blessedFollowUp(battle, owner, targets.getFirst());
        }

        /** 赐福目标立即对攻击目标造成 附加伤害 (倍率 = 战技#1 + 星魂4). */
        private static void blessedFollowUp(Battle battle, Character owner, CanHit victim) {
            if (victim.isDeath() || victim.getCamp() == owner.getCamp()) {
                return;
            }
            CanHit holder = battle.getAlivePlayerUnits().stream()
                    .filter(u -> u.hasBuffNamed("赐福"))
                    .findFirst().orElse(null);
            if (holder == null) {
                return;
            }
            double mult = 0.2;
            SkillData data = KitSupport.skillData(owner, SkillType.SKILL);
            if (data != null && data.getSkills() != null && !data.getSkills().isEmpty()
                    && !data.getSkills().getFirst().isEmpty()) {
                mult = data.getSkills().getFirst().getFirst();
            }
            double bonus = 0;
            for (Trace trace : owner.getTraces()) {
                if (trace instanceof TingyunE4 e4) {
                    bonus = e4.bonus;
                }
            }
            battle.dealAttackDamage(holder, victim, mult + bonus, 0,
                    com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                            com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP, Element.THUNDER));
            IO.println("  [行迹] 【赐福】 " + holder.getName() + " deals bonus thunder DMG to "
                    + victim.getName() + " (附加伤害)");
        }
    }

    /** 止厄: 普攻造成的伤害提高40%。 */
    static class TingyunHalt implements Trace {
        private final double bonus;

        TingyunHalt(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "止厄";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return type == SkillType.COMMON ? 1 + bonus : 1.0;
        }
    }

    /** 亨通: 停云的回合开始时，自身立即恢复5点能量。 */
    static class TingyunProsper implements Trace {
        private final double energy;

        TingyunProsper(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "亨通";
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            owner.gainEnergy(energy);
            IO.println("  [行迹] " + owner.getName() + " restores "
                    + String.format("%.0f", energy) + " energy (亨通)");
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 春风得意，时运驰骋: 得到【赐福】的我方单体施放终结技后速度提高20%，持续1回合。 */
    static class TingyunE1 implements Trace {
        private final double speedPercent;

        TingyunE1(double speedPercent) {
            this.speedPercent = speedPercent;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                 List<? extends CanHit> targets) {
            if (type != SkillType.ULTRA || !actor.hasBuffNamed("赐福")) {
                return;
            }
            actor.removeBuff("星魂1");
            Buff buff = new Buff("星魂1", Buff.Category.BUFF, owner, actor, 1)
                    .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(speedPercent,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(actor, buff);
            IO.println("  [星魂] " + actor.getName() + " speeds up after ultimate (赐福)");
        }
    }

    /** 星魂2 君子惠渥，晏笑承之: 得到【赐福】的我方单体在消灭敌方目标时恢复5点能量，每回合1次。 */
    static class TingyunE2 implements Trace {
        private final double energy;
        private boolean usedThisTurn = false;

        TingyunE2(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            usedThisTurn = false;
        }

        @Override
        public void onKill(Battle battle, Character owner, CanHit victim) {
            if (usedThisTurn) {
                return;
            }
            CanHit holder = battle.getAlivePlayerUnits().stream()
                    .filter(u -> u.hasBuffNamed("赐福"))
                    .findFirst().orElse(null);
            if (holder != null) {
                usedThisTurn = true;
                holder.gainEnergy(energy);
                IO.println("  [星魂] " + holder.getName() + " restores "
                        + String.format("%.0f", energy) + " energy (赐福 kill)");
            }
        }
    }

    /** 星魂4 鸣火机变，度时察势: 【赐福】附加的伤害倍率提高20%。 */
    static class TingyunE4 implements Trace {
        private final double bonus;

        TingyunE4(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "星魂4";
        }
    }

    /** 星魂6 和气生财，泽盈四方: 终结技为我方目标恢复的能量提高10点。 */
    static class TingyunE6 implements Trace {
        private final double energy;

        TingyunE6(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.ULTRA) {
                return;
            }
            List<? extends CanHit> allies = KitSupport.friendlyTargets(battle, owner, targets);
            if (allies.isEmpty()) {
                return;
            }
            allies.getFirst().gainEnergy(energy);
            IO.println("  [星魂] " + allies.getFirst().getName() + " restores "
                    + String.format("%.0f", energy) + " more energy (星魂6)");
        }
    }
}
