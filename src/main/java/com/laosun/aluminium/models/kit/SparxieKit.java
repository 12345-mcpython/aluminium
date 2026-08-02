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
 * 火花 (Sparxie, cid 1501) — 火属性 欢愉.
 * 欢愉技 (skill 20) 由通用执行器处理 (ElationDamage).
 */
public final class SparxieKit implements CharacterKit {

    @Override
    public int cid() {
        return 1501;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new SparxieSigning(param(byId, 1501101, 0, 2000), param(byId, 1501101, 1, 100),
                        param(byId, 1501101, 2, 0.05), param(byId, 1501101, 3, 0.8)),
                new SparxieKaleidoscope(intParam(byId, 1501102, 0, 2), intParam(byId, 1501102, 1, 4),
                        intParam(byId, 1501102, 2, 8), intParam(byId, 1501102, 3, 1),
                        intParam(byId, 1501102, 4, 1), intParam(byId, 1501102, 5, 4)),
                new SparxiePalette(param(byId, 1501103, 0, 0.08), param(byId, 1501103, 1, 0.8)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new SparxieE1(intParam(e, 0, 5), param(e, 1, 0.015), param(e, 2, 0.15));
            case 2 -> new SparxieE2(intParam(e, 0, 2), param(e, 1, 0.1), intParam(e, 2, 2), intParam(e, 3, 4));
            case 4 -> new SparxieE4(intParam(e, 0, 5), param(e, 1, 0.36), intParam(e, 2, 3));
            case 6 -> new SparxieE6(intParam(e, 0, 1), intParam(e, 1, 40), param(e, 2, 0.2));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 3 -> SparxieKit::sparxieUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 终结技: 获得#1个笑点, 对敌方全体造成 (#3×欢愉度+#2)% 攻击力的火属性伤害. */
    static void sparxieUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        int laugh = ctx.intParam(0, 2);
        double atkRatio = ctx.param(1, 0.3);
        double elationRatio = ctx.param(2, 0.6);
        battle.addLaughPoints(laugh);
        double elation = user.getAttribute(AttributeType.ELATION_DAMAGE_BOOST) != null
                ? user.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get() : 0;
        double multiplier = elation * elationRatio + atkRatio;
        for (Enemy enemy : battle.getAliveEnemies()) {
            battle.dealElationDamage(user, enemy, multiplier);
            battle.breakToughness(user, enemy, ctx.stanceAll());
        }
        IO.println("  " + user.getName() + " gains " + laugh + " 笑点, casts the ultimate!");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 甜蜜！笑点签售会: 攻击力>2000时, 每超过100点攻击力使自身欢愉度提高5%, 最多80%。 */
    static class SparxieSigning implements Trace {
        private final double threshold;
        private final double step;
        private final double perStep;
        private final double cap;

        SparxieSigning(double threshold, double step, double perStep, double cap) {
            this.threshold = threshold;
            this.step = step;
            this.perStep = perStep;
            this.cap = cap;
        }

        @Override
        public String getName() {
            return "甜蜜！笑点签售会";
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
            double atk = owner.getAttribute(AttributeType.ATTACK) != null
                    ? owner.getAttribute(AttributeType.ATTACK).get() : 0;
            double bonus = atk > threshold ? Math.min(cap, Math.floor((atk - threshold) / step) * perStep) : 0;
            owner.removeBuff("甜蜜！笑点签售会");
            Buff buff = new Buff("甜蜜！笑点签售会", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.ELATION_DAMAGE_BOOST, DoubleValue.Modifier.pure(bonus,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 炫目！人设万花筒: 队伍中欢愉角色越多, 施放终结技额外获得越多笑点及【爆点】。 */
    static class SparxieKaleidoscope implements Trace {
        private final int laugh1;
        private final int laugh2;
        private final int laugh3;
        private final int boom1;
        private final int boom2;
        private final int boom3;

        SparxieKaleidoscope(int laugh1, int laugh2, int laugh3, int boom1, int boom2, int boom3) {
            this.laugh1 = laugh1;
            this.laugh2 = laugh2;
            this.laugh3 = laugh3;
            this.boom1 = boom1;
            this.boom2 = boom2;
            this.boom3 = boom3;
        }

        @Override
        public String getName() {
            return "炫目！人设万花筒";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.ULTRA) {
                return;
            }
            long elation = battle.getElationCharacters().stream().filter(c -> !c.isDeath()).count();
            int laugh = elation >= 3 ? laugh3 : elation == 2 ? laugh2 : elation == 1 ? laugh1 : 0;
            int boom = elation >= 3 ? boom3 : elation == 2 ? boom2 : elation == 1 ? boom1 : 0;
            if (laugh > 0) {
                battle.addLaughPoints(laugh);
                IO.println("  [行迹] " + owner.getName() + " gains " + laugh + " extra 笑点 ("
                        + elation + " 欢愉角色)");
            }
        }
    }

    /** 沸腾！真伪调色盘: 当前每拥有1个笑点, 使我方全体暴击伤害提高8%, 最多80%。 */
    static class SparxiePalette implements Trace {
        private final double perLaugh;
        private final double cap;

        SparxiePalette(double perLaugh, double cap) {
            this.perLaugh = perLaugh;
            this.cap = cap;
        }

        @Override
        public String getName() {
            return "沸腾！真伪调色盘";
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            double laugh = battle.getLaughPoints();
            double cdmg = Math.min(cap, laugh * perLaugh);
            owner.removeBuff("沸腾！真伪调色盘");
            Buff buff = new Buff("沸腾！真伪调色盘", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(cdmg,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 #全网热搜：她是谁？: 阿哈时刻结束时获得5个笑点, 每笑点使全队全属性抗性穿透提高1.5%。 */
    static class SparxieE1 implements Trace {
        private final int laugh;
        private final double perLaugh;
        private final double cap;

        SparxieE1(int laugh, double perLaugh, double cap) {
            this.laugh = laugh;
            this.perLaugh = perLaugh;
            this.cap = cap;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("星魂1", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.RESISTANCE_PENETRATION, DoubleValue.Modifier.pure(
                            Math.min(cap, battle.getLaughPoints() * perLaugh),
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 星魂2 #观众的眼睛是雪亮的: 阿哈时刻结束时获得1个额外回合和2个【爆点】。 */
    static class SparxieE2 implements Trace {
        private final int boom;
        private final double cdmgPerBoom;
        private final int turns;
        private final int maxStacks;

        SparxieE2(int boom, double cdmgPerBoom, int turns, int maxStacks) {
            this.boom = boom;
            this.cdmgPerBoom = cdmgPerBoom;
            this.turns = turns;
            this.maxStacks = maxStacks;
        }

        @Override
        public String getName() {
            return "星魂2";
        }
    }

    /** 星魂4 #硬控！顶级表情管理: 施放终结技时额外获得5个笑点, 欢愉度提高36%持续3回合。 */
    static class SparxieE4 implements Trace {
        private final int laugh;
        private final double elationBoost;
        private final int turns;

        SparxieE4(int laugh, double elationBoost, int turns) {
            this.laugh = laugh;
            this.elationBoost = elationBoost;
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
            battle.addLaughPoints(laugh);
            owner.removeBuff("星魂4");
            Buff buff = new Buff("星魂4", Buff.Category.BUFF, owner, owner, turns)
                    .stat(AttributeType.ELATION_DAMAGE_BOOST, DoubleValue.Modifier.pure(elationBoost,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 星魂6 #锵锵，即将绝版的我: 全属性抗性穿透提高20%。 */
    static class SparxieE6 implements Trace {
        private final int laughPerExtra;
        private final int maxExtra;
        private final double pen;

        SparxieE6(int laughPerExtra, int maxExtra, double pen) {
            this.laughPerExtra = laughPerExtra;
            this.maxExtra = maxExtra;
            this.pen = pen;
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
}
