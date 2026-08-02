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
 * 姬子•启行 (Himeko • Nova, cid 1510) — 火属性 智识 (助战技).
 */
public final class HimekoNovaKit implements CharacterKit {

    @Override
    public int cid() {
        return 1510;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new HimekoNovaWhere(intParam(byId, 1510101, 0, 5)),
                new HimekoNovaPulse(),
                new HimekoNovaSilence(intParam(byId, 1510103, 0, 3), intParam(byId, 1510103, 1, 3),
                        param(byId, 1510103, 2, 0.3)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new HimekoNovaE1(intParam(e, 0, 1), intParam(e, 1, 3), intParam(e, 2, 1), intParam(e, 3, 1));
            case 2 -> new HimekoNovaE2(param(e, 0, 1.3));
            case 4 -> new HimekoNovaE4(param(e, 0, 0.1));
            case 6 -> new HimekoNovaE6(param(e, 0, 0.2), intParam(e, 1, 6), param(e, 2, 0.75),
                    intParam(e, 3, 6), param(e, 4, 1.6));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> HimekoNovaKit::himekoNovaSkill;
            case 3 -> HimekoNovaKit::himekoNovaUlt;
            case 8 -> HimekoNovaKit::himekoNovaBeam;
            case 9 -> HimekoNovaKit::himekoNovaPulseBeam;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 获得【领航旗语】#2回合, 我方全体造成的伤害提高#1%。 */
    static void himekoNovaSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double dmgBoost = ctx.firstParam();
        int turns = ctx.intParam(1, 3);
        user.removeBuff("领航旗语");
        battle.applyBuff(user, new Buff("领航旗语", Buff.Category.BUFF, user, user, turns));
        for (CanHit ally : battle.getAlivePlayerUnits()) {
            Buff buff = new Buff("领航旗语·伤", Buff.Category.BUFF, user, ally, turns)
                    .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(dmgBoost,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(ally, buff);
        }
        IO.println("  " + user.getName() + " raises 领航旗语: party DMG +"
                + String.format("%.0f", dmgBoost * 100) + "%");
    }

    /** 终结技: 操控「拓星者」, 发动6次【超频粒子光束】 (对主目标最多#9%, 其他目标#10%). */
    static void himekoNovaUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double mainMult = ctx.param(8, 3.81);
        double otherMult = ctx.param(9, 1.26);
        int beams = 6;
        CanHit main = targets.getFirst();
        for (int i = 0; i < beams; i++) {
            if (main.isDeath()) {
                break;
            }
            battle.dealAttackDamage(user, main, mainMult / beams, 0,
                    com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                            com.laosun.aluminium.models.DamageCalculator.DamageType.ULTRA, Element.FIRE));
            for (Enemy enemy : battle.getAliveEnemies()) {
                if (enemy != main) {
                    battle.dealAttackDamage(user, enemy, otherMult / beams, 0,
                            com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                    com.laosun.aluminium.models.DamageCalculator.DamageType.ULTRA, Element.FIRE));
                }
            }
        }
        HimekoNovaKit.addEnergy(user, 3);
        IO.println("  " + user.getName() + " 拓星者 fires " + beams + " 超频粒子光束!");
    }

    /** 超频粒子光束: 对敌方全体造成#1%伤害, 获得#2点【源能】. */
    static void himekoNovaBeam(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        int energy = ctx.intParam(1, 1);
        for (Enemy enemy : battle.getAliveEnemies()) {
            ctx.dealDamage(battle, user, enemy, multiplier);
            battle.breakToughness(user, enemy, ctx.stanceAll());
        }
        HimekoNovaKit.addEnergy(user, energy);
    }

    /** 轨道歼灭脉冲: 消耗1点【源能】对敌方全体造成#1%伤害. */
    static void himekoNovaPulseBeam(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        double extraMult = ctx.param(2, 0.15);
        int extraCost = ctx.intParam(1, 1);
        HimekoNovaKit.consumeEnergy(user, 1);
        for (Enemy enemy : battle.getAliveEnemies()) {
            ctx.dealDamage(battle, user, enemy, multiplier);
            battle.breakToughness(user, enemy, ctx.stanceAll());
        }
        int left = HimekoNovaKit.energy(user);
        while (left >= extraCost) {
            left -= extraCost;
            HimekoNovaKit.consumeEnergy(user, extraCost);
            List<Enemy> alive = battle.getAliveEnemies();
            if (alive.isEmpty()) {
                return;
            }
            battle.dealAttackDamage(user, alive.get((int) (Math.random() * alive.size())), extraMult, 0,
                    com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                            com.laosun.aluminium.models.DamageCalculator.DamageType.ULTRA, Element.FIRE));
        }
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 人类该向何处去: 回合开始时, 若当前助战技使用次数等于上限, 额外恢复5点能量。 */
    static class HimekoNovaWhere implements Trace {
        private final double energy;

        HimekoNovaWhere(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "人类该向何处去";
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            owner.gainEnergy(energy);
            IO.println("  [行迹] " + owner.getName() + " restores "
                    + String.format("%.0f", energy) + " energy (助战技 ready)");
        }
    }

    /** 列车的脉搏在轰鸣: 除姬子•启行外的开拓同行角色使用助战技时, 该角色立即获得1个额外回合。 */
    static class HimekoNovaPulse implements Trace {
        @Override
        public String getName() {
            return "列车的脉搏在轰鸣";
        }

        @Override
        public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                 List<? extends CanHit> targets) {
            if (actor != owner && type == SkillType.ULTRA) {
                battle.advanceByPercent(actor, 1.0);
                IO.println("  [行迹] " + actor.getName() + " gains an extra turn (同行协议)");
            }
        }
    }

    /** 银轨在旷古中静默: 施放终结技时立即获得3点【源能】。 */
    static class HimekoNovaSilence implements Trace {
        private final int energy;
        private final int threshold;
        private final double bonus;

        HimekoNovaSilence(int energy, int threshold, double bonus) {
            this.energy = energy;
            this.threshold = threshold;
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "银轨在旷古中静默";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.ULTRA) {
                HimekoNovaKit.addEnergy(owner, energy);
                IO.println("  [行迹] " + owner.getName() + " gains " + energy + " 【源能】 ("
                        + HimekoNovaKit.energy(owner) + ")");
            }
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 我们称之为路的，是开拓: 天赋额外发动助战技效果。 */
    static class HimekoNovaE1 implements Trace {
        private final int ultReduction;
        private final int chargeReduction;
        private final int extraDamage;
        private final int extraTimes;

        HimekoNovaE1(int ultReduction, int chargeReduction, int extraDamage, int extraTimes) {
            this.ultReduction = ultReduction;
            this.chargeReduction = chargeReduction;
            this.extraDamage = extraDamage;
            this.extraTimes = extraTimes;
        }

        @Override
        public String getName() {
            return "星魂1";
        }
    }

    /** 星魂2 旗帜，永不降下的帆: 助战技的使用上限提高至2次, 终结技和助战技造成的伤害提高30%。 */
    static class HimekoNovaE2 implements Trace {
        private final double dmgMult;

        HimekoNovaE2(double dmgMult) {
            this.dmgMult = dmgMult;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return type == SkillType.ULTRA ? 1 + dmgMult : 1.0;
        }
    }

    /** 星魂4 握住所有伸向天空的手: 使用助战技时, 全属性抗性穿透提高效果对我方全体生效。 */
    static class HimekoNovaE4 implements Trace {
        private final double pen;

        HimekoNovaE4(double pen) {
            this.pen = pen;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                Buff buff = new Buff("星魂4", Buff.Category.BUFF, owner, ally, -1)
                        .stat(AttributeType.RESISTANCE_PENETRATION, DoubleValue.Modifier.pure(pen,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
        }
    }

    /** 星魂6 铭记，终抵群星的誓约: 火属性抗性穿透提高20%。 */
    static class HimekoNovaE6 implements Trace {
        private final double pen;
        private final int energyCap;
        private final double damageBonus;
        private final int threshold;
        private final double extraMult;

        HimekoNovaE6(double pen, int energyCap, double damageBonus, int threshold, double extraMult) {
            this.pen = pen;
            this.energyCap = energyCap;
            this.damageBonus = damageBonus;
            this.threshold = threshold;
            this.extraMult = extraMult;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("星魂6", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.RESISTANCE_PENETRATION, DoubleValue.Modifier.pure(pen,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    // ─── 【源能】代理 (天赋 共赴灼热的远征) ──────────────────────────────

    private static final java.util.Map<CanHit, Integer> ENERGY = new java.util.concurrent.ConcurrentHashMap<>();

    static int energy(CanHit owner) {
        return ENERGY.getOrDefault(owner, 0);
    }

    static void addEnergy(CanHit owner, int amount) {
        ENERGY.put(owner, Math.min(6, energy(owner) + amount));
    }

    static void consumeEnergy(CanHit owner, int amount) {
        ENERGY.put(owner, Math.max(0, energy(owner) - amount));
    }
}
