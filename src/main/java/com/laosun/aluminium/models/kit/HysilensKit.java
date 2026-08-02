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
 * 海瑟音 (Hysilens, cid 1410) — 物理属性 虚无.
 */
public final class HysilensKit implements CharacterKit {

    @Override
    public int cid() {
        return 1410;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new HysilensFlag(intParam(byId, 1410101, 0, 3), intParam(byId, 1410101, 1, 1)),
                new HysilensFoam(param(byId, 1410102, 0, 1.5)),
                new HysilensPearl(param(byId, 1410103, 0, 0.6), param(byId, 1410103, 1, 0.1),
                        param(byId, 1410103, 2, 0.15), param(byId, 1410103, 3, 0.9)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new HysilensE1(param(e, 0, 1.16), param(e, 1, 1));
            case 2 -> new HysilensE2();
            case 4 -> new HysilensE4(param(e, 0, 0.2));
            case 6 -> new HysilensE6(intParam(e, 0, 12), param(e, 1, 0.2));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> HysilensKit::hysilensSkill;
            case 3 -> HysilensKit::hysilensUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 有#2基础概率使敌方全体受到的伤害提高#3持续#4回合, 造成#1%伤害. */
    static void hysilensSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        double chance = ctx.param(1, 1.0);
        double vuln = ctx.param(2, 0.1);
        int turns = ctx.intParam(3, 3);
        for (Enemy enemy : battle.getAliveEnemies()) {
            if (battle.checkEffectHit(user, enemy, chance)) {
                Buff debuff = new Buff("泛音", Buff.Category.DEBUFF, user, enemy, turns)
                        .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(vuln,
                                DoubleValue.Modifier.ModifierSource.DEBUFF));
                battle.applyBuff(enemy, debuff);
            }
            ctx.dealDamage(battle, user, enemy, multiplier);
            battle.breakToughness(user, enemy, ctx.stanceAll());
        }
    }

    /** 终结技: 展开结界#2回合 (敌方攻击力降低#6, 防御力降低#3), 造成#1%伤害. */
    static void hysilensUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        int turns = ctx.intParam(1, 3);
        double defDown = ctx.param(2, 0.15);
        double dotRatio = ctx.param(3, 0.32);
        int maxTriggers = ctx.intParam(4, 8);
        double atkDown = ctx.param(5, 0.15);
        user.removeBuff("结界");
        battle.applyBuff(user, new Buff("结界", Buff.Category.BUFF, user, user, turns));
        for (Enemy enemy : battle.getAliveEnemies()) {
            enemy.removeBuff("绝海回涛");
            Buff debuff = new Buff("绝海回涛", Buff.Category.DEBUFF, user, enemy, turns)
                    .stat(AttributeType.DEFENCE, DoubleValue.Modifier.addPercent(-defDown,
                            DoubleValue.Modifier.ModifierSource.DEBUFF))
                    .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(-atkDown,
                            DoubleValue.Modifier.ModifierSource.DEBUFF));
            battle.applyBuff(enemy, debuff);
            ctx.dealDamage(battle, user, enemy, multiplier);
            battle.breakToughness(user, enemy, ctx.stanceAll());
        }
        IO.println("  " + user.getName() + " opens 结界 (" + turns + " turns)");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 征服的剑旗: 战斗开始时展开与终结技相同的结界#3回合, 每次展开结界恢复1个战技点。 */
    static class HysilensFlag implements Trace {
        private final int turns;
        private final int skillPoints;

        HysilensFlag(int turns, int skillPoints) {
            this.turns = turns;
            this.skillPoints = skillPoints;
        }

        @Override
        public String getName() {
            return "征服的剑旗";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            owner.removeBuff("结界");
            battle.applyBuff(owner, new Buff("结界", Buff.Category.BUFF, owner, owner, turns));
            battle.addSkillPoints(skillPoints);
            IO.println("  [行迹] 征服的剑旗: opens 结界 (" + turns + " turns), +"
                    + skillPoints + " skill point");
        }
    }

    /** 盛会的泡沫: 施放终结技时, 若敌方目标处于持续伤害状态, 使其当前承受的所有持续伤害立即产生伤害。 */
    static class HysilensFoam implements Trace {
        private final double ratio;

        HysilensFoam(double ratio) {
            this.ratio = ratio;
        }

        @Override
        public String getName() {
            return "盛会的泡沫";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.ULTRA) {
                return;
            }
            for (Enemy enemy : battle.getAliveEnemies()) {
                if (enemy.isDeath() || enemy.getDots().isEmpty()) {
                    continue;
                }
                for (Buff.Dot dot : new java.util.ArrayList<>(enemy.getDots())) {
                    battle.applyDotDamage(enemy, dot);
                }
                IO.println("  [行迹] 盛会的泡沫: detonates DoTs on " + enemy.getName());
            }
        }
    }

    /** 珍珠的琴弦: 效果命中>60%时, 每超过10%使自身造成的伤害提高15%, 最多90%。 */
    static class HysilensPearl implements Trace {
        private final double threshold;
        private final double step;
        private final double perStep;
        private final double cap;

        HysilensPearl(double threshold, double step, double perStep, double cap) {
            this.threshold = threshold;
            this.step = step;
            this.perStep = perStep;
            this.cap = cap;
        }

        @Override
        public String getName() {
            return "珍珠的琴弦";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            double ehr = owner.getAttribute(AttributeType.EFFECT_HIT_RATE) != null
                    ? owner.getAttribute(AttributeType.EFFECT_HIT_RATE).get() : 0;
            if (ehr <= threshold) {
                return 1.0;
            }
            return 1 + Math.min(cap, Math.floor((ehr - threshold) / step) * perStep);
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 你问我心为何悲伤: 我方目标造成的持续伤害为原伤害的116%。 */
    static class HysilensE1 implements Trace {
        private final double dotRatio;
        private final double extraChance;

        HysilensE1(double dotRatio, double extraChance) {
            this.dotRatio = dotRatio;
            this.extraChance = extraChance;
        }

        @Override
        public String getName() {
            return "星魂1";
        }
    }

    /** 星魂2 你说浪花为何喧响: 结界持续期间, 行迹【珍珠的琴弦】的增伤效果对我方全体生效。 */
    static class HysilensE2 implements Trace {
        @Override
        public String getName() {
            return "星魂2";
        }
    }

    /** 星魂4 看呀，时间为何流淌: 结界持续期间, 敌方全体全属性抗性降低20%。 */
    static class HysilensE4 implements Trace {
        private final double resDown;

        HysilensE4(double resDown) {
            this.resDown = resDown;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (Enemy enemy : battle.getAliveEnemies()) {
                Buff buff = new Buff("星魂4", Buff.Category.DEBUFF, owner, enemy, -1)
                        .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(resDown,
                                DoubleValue.Modifier.ModifierSource.DEBUFF));
                battle.applyBuff(enemy, buff);
            }
        }
    }

    /** 星魂6 沉没的你，何日返乡: 结界持续期间, 物理持续伤害触发次数上限提高至12次。 */
    static class HysilensE6 implements Trace {
        private final int maxTriggers;
        private final double dmgBonus;

        HysilensE6(int maxTriggers, double dmgBonus) {
            this.maxTriggers = maxTriggers;
            this.dmgBonus = dmgBonus;
        }

        @Override
        public String getName() {
            return "星魂6";
        }
    }
}
