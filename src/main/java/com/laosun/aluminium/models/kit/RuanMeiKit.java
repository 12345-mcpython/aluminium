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
 * 阮·梅 (Ruan Mei, cid 1303) — 冰属性 同谐.
 */
public final class RuanMeiKit implements CharacterKit {

    @Override
    public int cid() {
        return 1303;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new RuanMeiBreathing(param(byId, 1303101, 0, 0.2)),
                new RuanMeiLonging(param(byId, 1303102, 0, 5)),
                new RuanMeiCandle(param(byId, 1303103, 0, 1.2), param(byId, 1303103, 1, 0.1),
                        param(byId, 1303103, 2, 0.06), param(byId, 1303103, 3, 0.36)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new RuanMeiE1(param(e, 0, 0.2));
            case 2 -> new RuanMeiE2(param(e, 0, 0.4));
            case 4 -> new RuanMeiE4(param(e, 0, 1.0), intParam(e, 1, 3));
            case 6 -> new RuanMeiE6(intParam(e, 0, 1), param(e, 1, 0.2));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> RuanMeiKit::ruanMeiSkill;
            case 3 -> RuanMeiKit::ruanMeiUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 获得【弦外音】#3回合, 期间我方全体伤害提高#1%, 弱点击破效率提高#2%。 */
    static void ruanMeiSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double dmgBoost = ctx.firstParam();
        double breakEfficiency = ctx.param(1, 0.5);
        int turns = ctx.intParam(2, 3);
        user.removeBuff("弦外音");
        Buff buff = new Buff("弦外音", Buff.Category.BUFF, user, user, turns);
        battle.applyBuff(user, buff);
        for (CanHit ally : battle.getAlivePlayerUnits()) {
            Buff dmg = new Buff("弦外音·伤", Buff.Category.BUFF, user, ally, turns)
                    .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(dmgBoost,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(ally, dmg);
            Buff eff = new Buff("弦外音·破", Buff.Category.BUFF, user, ally, turns)
                    .stat(AttributeType.WEAKNESS_BREAK_EFFICIENCY, DoubleValue.Modifier.pure(breakEfficiency,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(ally, eff);
        }
        IO.println("  " + user.getName() + ": 弦外音 active (" + turns + " turns): party DMG +"
                + String.format("%.0f", dmgBoost * 100) + "%, break efficiency +"
                + String.format("%.0f", breakEfficiency * 100) + "%");
    }

    /** 终结技: 展开结界#2回合, 我方全体全属性抗性穿透提高#1%。 */
    static void ruanMeiUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double pen = ctx.firstParam();
        int turns = ctx.intParam(1, 2);
        if (user instanceof Character character) {
            for (Trace trace : character.getTraces()) {
                if (trace instanceof RuanMeiE6 e6) {
                    turns += e6.extraTurns;
                }
            }
        }
        user.removeBuff("结界");
        battle.applyBuff(user, new Buff("结界", Buff.Category.BUFF, user, user, turns));
        for (CanHit ally : battle.getAlivePlayerUnits()) {
            Buff buff = new Buff("残梅绽·结界", Buff.Category.BUFF, user, ally, turns)
                    .stat(AttributeType.RESISTANCE_PENETRATION, DoubleValue.Modifier.pure(pen,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(ally, buff);
        }
        IO.println("  " + user.getName() + " opens 结界 (" + turns + " turns): party RES PEN +"
                + String.format("%.0f", pen * 100) + "%");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 物体呼吸中: 我方全体击破特攻提高20%。 */
    static class RuanMeiBreathing implements Trace {
        private final double breaking;

        RuanMeiBreathing(double breaking) {
            this.breaking = breaking;
        }

        @Override
        public String getName() {
            return "物体呼吸中";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                Buff buff = new Buff("物体呼吸中", Buff.Category.BUFF, owner, ally, -1)
                        .stat(AttributeType.BREAKING_EFFECT, DoubleValue.Modifier.pure(breaking,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
            IO.println("  [行迹] party break effect +" + String.format("%.0f", breaking * 100) + "%");
        }
    }

    /** 日消遐思长: 阮·梅的回合开始时，自身恢复5点能量。 */
    static class RuanMeiLonging implements Trace {
        private final double energy;

        RuanMeiLonging(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "日消遐思长";
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            owner.gainEnergy(energy);
            IO.println("  [行迹] " + owner.getName() + " restores "
                    + String.format("%.0f", energy) + " energy");
        }
    }

    /** 落烛照水燃: 击破特攻>120%时, 每超过10%使战技伤害提高效果额外提高6%, 最高36%。 */
    static class RuanMeiCandle implements Trace {
        private final double threshold;
        private final double step;
        private final double perStep;
        private final double cap;

        RuanMeiCandle(double threshold, double step, double perStep, double cap) {
            this.threshold = threshold;
            this.step = step;
            this.perStep = perStep;
            this.cap = cap;
        }

        @Override
        public String getName() {
            return "落烛照水燃";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL) {
                return;
            }
            double be = owner.getAttribute(AttributeType.BREAKING_EFFECT) != null
                    ? owner.getAttribute(AttributeType.BREAKING_EFFECT).get() : 0;
            if (be <= threshold) {
                return;
            }
            double bonus = Math.min(cap, Math.floor((be - threshold) / step) * perStep);
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                Buff buff = new Buff("落烛照水燃", Buff.Category.BUFF, owner, ally, 3)
                        .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(bonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
            IO.println("  [行迹] 落烛照水燃: extra party DMG +"
                    + String.format("%.1f%%", bonus * 100));
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 神经仿绣图: 结界期间我方全体伤害无视目标20%防御力。 */
    static class RuanMeiE1 implements Trace {
        private final double defIgnore;

        RuanMeiE1(double defIgnore) {
            this.defIgnore = defIgnore;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("星魂1", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.DEFENCE_IGNORE, DoubleValue.Modifier.pure(defIgnore,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 星魂2 芦前漫步: 我方全体对弱点击破状态目标造成伤害时, 攻击力提高40%。 */
    static class RuanMeiE2 implements Trace {
        private final double atkBonus;

        RuanMeiE2(double atkBonus) {
            this.atkBonus = atkBonus;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return defender instanceof Enemy enemy && enemy.isBroken() ? 1 + atkBonus : 1.0;
        }
    }

    /** 星魂4 寻神铜镜前: 敌方弱点被击破时, 阮·梅击破特攻提高100%持续3回合。 */
    static class RuanMeiE4 implements Trace {
        private final double bonus;
        private final int turns;

        RuanMeiE4(double bonus, int turns) {
            this.bonus = bonus;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void onEnemyBreak(Battle battle, Character owner, Enemy enemy) {
            owner.removeBuff("星魂4");
            Buff buff = new Buff("星魂4", Buff.Category.BUFF, owner, owner, turns)
                    .stat(AttributeType.BREAKING_EFFECT, DoubleValue.Modifier.pure(bonus,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 星魂6 半脱纱巾落团扇: 结界持续时间延长1回合, 天赋击破伤害倍率额外提高20%。 */
    static class RuanMeiE6 implements Trace {
        private final int extraTurns;
        private final double breakBonus;

        RuanMeiE6(int extraTurns, double breakBonus) {
            this.extraTurns = extraTurns;
            this.breakBonus = breakBonus;
        }

        @Override
        public String getName() {
            return "星魂6";
        }
    }
}
