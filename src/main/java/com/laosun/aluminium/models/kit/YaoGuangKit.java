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

import java.util.List;
import java.util.Map;

import static com.laosun.aluminium.models.kit.KitSupport.intParam;
import static com.laosun.aluminium.models.kit.KitSupport.param;

/**
 * 爻光 (Yao Guang, cid 1502) — 物理属性 欢愉.
 * 欢愉技 (skill 20) 由通用执行器处理 (ElationDamage).
 */
public final class YaoGuangKit implements CharacterKit {

    @Override
    public int cid() {
        return 1502;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new YaoGuangSplash(param(byId, 1502101, 0, 120), param(byId, 1502101, 1, 0.3),
                        param(byId, 1502101, 2, 1), param(byId, 1502101, 3, 0.01),
                        param(byId, 1502101, 4, 200)),
                new YaoGuangContent(intParam(byId, 1502102, 0, 1), param(byId, 1502102, 1, 0.25),
                        param(byId, 1502102, 2, 0.4), param(byId, 1502102, 3, 0.6)),
                new YaoGuangLuck(intParam(byId, 1502103, 0, 20), intParam(byId, 1502103, 1, 1)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new YaoGuangE1(param(e, 0, 0.2), intParam(e, 1, 40));
            case 2 -> new YaoGuangE2(param(e, 0, 0.16), param(e, 1, 0.12));
            case 4 -> new YaoGuangE4(param(e, 0, 1.5));
            case 6 -> new YaoGuangE6(param(e, 0, 0.25), param(e, 1, 1));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> YaoGuangKit::yaoGuangSkill;
            case 3 -> YaoGuangKit::yaoGuangUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 展开结界#1回合, 结界持续期间我方全体欢愉度提高 (等同于爻光欢愉度的#2)。 */
    static void yaoGuangSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        int turns = ctx.intParam(0, 3);
        double elationRatio = ctx.param(1, 0.1);
        double elation = user.getAttribute(AttributeType.ELATION_DAMAGE_BOOST) != null
                ? user.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get() : 0;
        double bonus = elation * elationRatio;
        user.removeBuff("结界");
        battle.applyBuff(user, new Buff("结界", Buff.Category.BUFF, user, user, turns));
        for (CanHit ally : battle.getAlivePlayerUnits()) {
            Buff buff = new Buff("结界·欢愉", Buff.Category.BUFF, user, ally, turns)
                    .stat(AttributeType.ELATION_DAMAGE_BOOST, DoubleValue.Modifier.pure(bonus,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(ally, buff);
        }
        IO.println("  " + user.getName() + " opens 结界 (" + turns + " turns): party 欢愉度 +"
                + String.format("%.1f%%", bonus * 100));
    }

    /** 终结技: 获得#1个笑点, 使阿哈立即获得1个额外回合, 我方全体全属性抗性穿透提高#2持续#3回合。 */
    static void yaoGuangUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        int laugh = ctx.intParam(0, 5);
        double pen = ctx.param(1, 0.1);
        int turns = ctx.intParam(2, 3);
        battle.addLaughPoints(laugh);
        for (CanHit ally : battle.getAlivePlayerUnits()) {
            Buff buff = new Buff("霓裳铁羽", Buff.Category.BUFF, user, ally, turns)
                    .stat(AttributeType.RESISTANCE_PENETRATION, DoubleValue.Modifier.pure(pen,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(ally, buff);
        }
        IO.println("  " + user.getName() + " gains " + laugh + " 笑点, Aha gets an extra turn!");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 开屏有礼: 速度≥120时, 使自身欢愉度提高30%, 之后每超过1点速度欢愉度提高1%。 */
    static class YaoGuangSplash implements Trace {
        private final double speedThreshold;
        private final double base;
        private final double step;
        private final double perStep;
        private final double maxExtra;

        YaoGuangSplash(double speedThreshold, double base, double step, double perStep, double maxExtra) {
            this.speedThreshold = speedThreshold;
            this.base = base;
            this.step = step;
            this.perStep = perStep;
            this.maxExtra = maxExtra;
        }

        @Override
        public String getName() {
            return "开屏有礼";
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
            double speed = owner.getAttribute(AttributeType.SPEED) != null
                    ? owner.getAttribute(AttributeType.SPEED).get() : 0;
            double bonus = 0;
            if (speed >= speedThreshold) {
                bonus = base + Math.min(maxExtra, speed - speedThreshold) / step * perStep;
            }
            owner.removeBuff("开屏有礼");
            Buff buff = new Buff("开屏有礼", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.ELATION_DAMAGE_BOOST, DoubleValue.Modifier.pure(bonus,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 神闲意满: 自身暴击伤害提高60%, 施放欢愉技后为我方恢复1个战技点。 */
    static class YaoGuangContent implements Trace {
        private final int skillPoints;
        private final double ignored1;
        private final double ignored2;
        private final double cdmg;

        YaoGuangContent(int skillPoints, double ignored1, double ignored2, double cdmg) {
            this.skillPoints = skillPoints;
            this.ignored1 = ignored1;
            this.ignored2 = ignored2;
            this.cdmg = cdmg;
        }

        @Override
        public String getName() {
            return "神闲意满";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("神闲意满", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(cdmg,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.ELATION) {
                battle.addSkillPoints(skillPoints);
                IO.println("  [行迹] " + owner.getName() + " restores " + skillPoints + " skill point (欢愉技)");
            }
        }
    }

    /** 鸿运鳞集: 爻光获得【好活当赏】时, 使其持续时间增加1回合。 */
    static class YaoGuangLuck implements Trace {
        private final int ignored;
        private final int extraTurns;

        YaoGuangLuck(int ignored, int extraTurns) {
            this.ignored = ignored;
            this.extraTurns = extraTurns;
        }

        @Override
        public String getName() {
            return "鸿运鳞集";
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 何处落玉，盈盈笑语: 我方全体造成欢愉伤害时无视20%防御力。 */
    static class YaoGuangE1 implements Trace {
        private final double defIgnore;
        private final int laugh;

        YaoGuangE1(double defIgnore, int laugh) {
            this.defIgnore = defIgnore;
            this.laugh = laugh;
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

    /** 星魂2 飞箭无目，青翎为瞳: 结界持续期间, 我方全体速度提高12%。 */
    static class YaoGuangE2 implements Trace {
        private final double elationBonus;
        private final double speed;

        YaoGuangE2(double elationBonus, double speed) {
            this.elationBonus = elationBonus;
            this.speed = speed;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                Buff buff = new Buff("星魂2", Buff.Category.BUFF, owner, ally, -1)
                        .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(speed,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
        }
    }

    /** 星魂4 命若素绡，拈羽点彩: 阿哈的额外回合中, 我方全体欢愉技伤害提高150%。 */
    static class YaoGuangE4 implements Trace {
        private final double elationMult;

        YaoGuangE4(double elationMult) {
            this.elationMult = elationMult;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return type == SkillType.ELATION ? elationMult : 1.0;
        }
    }

    /** 星魂6 牵丝渡引，天星垂虹: 我方全体欢愉伤害增笑25%。 */
    static class YaoGuangE6 implements Trace {
        private final double laughBonus;
        private final double skillMult;

        YaoGuangE6(double laughBonus, double skillMult) {
            this.laughBonus = laughBonus;
            this.skillMult = skillMult;
        }

        @Override
        public String getName() {
            return "星魂6";
        }
    }

    // ─── 天赋 屏开千光，遍观自在 代理 (好活当赏 → 大吉大利欢愉伤害) ──────

    /** 天赋代理: 我方目标施放攻击后, 触发【大吉大利】: 对随机1个击中的目标造成#1%欢愉伤害。 */
    static void greatFortune(Battle battle, Character owner, List<? extends CanHit> targets) {
        if (targets == null || targets.isEmpty()) {
            return;
        }
        double mult = 0.1;
        com.laosun.aluminium.models.SkillData talent = KitSupport.skillData(owner, SkillType.TALENT);
        if (talent != null && talent.getSkills() != null && !talent.getSkills().isEmpty()
                && !talent.getSkills().getFirst().isEmpty()) {
            mult = talent.getSkills().getFirst().getFirst();
        }
        List<CanHit> alive = new java.util.ArrayList<>();
        for (CanHit t : targets) {
            if (!t.isDeath()) {
                alive.add(t);
            }
        }
        if (!alive.isEmpty()) {
            battle.dealElationDamage(owner, alive.get((int) (Math.random() * alive.size())), mult);
            IO.println("  [天赋] 大吉大利! " + owner.getName() + " deals elation DMG");
        }
    }
}
