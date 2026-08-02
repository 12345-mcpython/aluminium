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
import com.laosun.aluminium.models.Trace;

import java.util.List;
import java.util.Map;

import static com.laosun.aluminium.models.kit.KitSupport.param;

/**
 * 花火 (Sparkle, cid 1306) — 量子属性 同谐.
 */
public final class SparkleKit implements CharacterKit {

    @Override
    public int cid() {
        return 1306;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new SparkleAlmanac(param(byId, 1306101, 0, 10)),
                new SparkleArtificialFlower(),
                new SparkleNocturne(param(byId, 1306103, 0, 0.05), param(byId, 1306103, 1, 0.15),
                        param(byId, 1306103, 2, 0.3), param(byId, 1306103, 3, 0.15)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new SparkleE1(param(e, 0, 0.4));
            case 2 -> new SparkleE2(param(e, 0, 0.08));
            case 4 -> new SparkleE4();
            case 6 -> new SparkleE6(param(e, 0, 0.3));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> SparkleKit::sparkleSkill;
            case 3 -> SparkleKit::sparkleUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 使指定我方单体暴击伤害提高 (等同于花火#1暴击伤害+#2), 持续#3回合, 并行动提前#4。 */
    static void sparkleSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double cdmgRatio = ctx.firstParam();
        double cdmgFlat = ctx.param(1, 0.27);
        int turns = ctx.intParam(2, 1);
        double advance = ctx.param(3, 0.5);
        double extraRatio = 0;
        if (user instanceof Character character) {
            for (Trace trace : character.getTraces()) {
                if (trace instanceof SparkleE6 e6) {
                    extraRatio = e6.cdmgRatio;
                }
            }
        }
        for (CanHit target : KitSupport.friendlyTargets(battle, user, targets)) {
            double cdmg = user.getAttribute(AttributeType.CRIT_ATTACK) != null
                    ? user.getAttribute(AttributeType.CRIT_ATTACK).get() * cdmgRatio + cdmgFlat : cdmgFlat;
            // 星魂6: 额外提高等同于花火暴击伤害30%的暴击伤害.
            if (extraRatio > 0 && user.getAttribute(AttributeType.CRIT_ATTACK) != null) {
                cdmg += user.getAttribute(AttributeType.CRIT_ATTACK).get() * extraRatio;
            }
            target.removeBuff("谜诡·暴伤");
            Buff buff = new Buff("谜诡·暴伤", Buff.Category.BUFF, user, target, turns)
                    .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(cdmg,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(target, buff);
            if (target != user) {
                battle.advanceByPercent(target, advance);
                IO.println("  " + target.getName() + " advances " + String.format("%.0f", advance * 100) + "%");
            }
            IO.println("  " + target.getName() + " crit damage +" + String.format("%.0f", cdmg * 100) + "%");
        }
    }

    /** 终结技: 为我方恢复#2个战技点 (星魂4 额外1点), 并使我方全体获得【谜诡】#4回合. */
    static void sparkleUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        int skillPoints = ctx.intParam(1, 4);
        double perStack = ctx.param(2, 0.06);
        int turns = ctx.intParam(3, 2);
        if (user instanceof Character character) {
            for (Trace trace : character.getTraces()) {
                if (trace instanceof SparkleE4) {
                    skillPoints += 1;
                }
            }
        }
        battle.addSkillPoints(skillPoints);
        for (CanHit ally : battle.getAlivePlayerUnits()) {
            ally.removeBuff("谜诡");
            Buff buff = new Buff("谜诡", Buff.Category.BUFF, user, ally, turns);
            battle.applyBuff(ally, buff);
        }
        IO.println("  " + user.getName() + " restores " + skillPoints + " skill points, party gains 谜诡");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 岁时记: 施放普攻时额外恢复10点能量。 */
    static class SparkleAlmanac implements Trace {
        private final double energy;

        SparkleAlmanac(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "岁时记";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.COMMON) {
                owner.gainEnergy(energy);
                IO.println("  [行迹] " + owner.getName() + " restores "
                        + String.format("%.0f", energy) + " energy (岁时记)");
            }
        }
    }

    /** 人造花: 战技提供的暴击伤害提高效果延长到目标下一个回合开始。 */
    static class SparkleArtificialFlower implements Trace {
        @Override
        public String getName() {
            return "人造花";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL) {
                return;
            }
            for (CanHit target : KitSupport.friendlyTargets(battle, owner, targets)) {
                for (Buff buff : target.getBuffs()) {
                    if ("谜诡·暴伤".equals(buff.getName())) {
                        buff.setDuration(buff.getDuration() + 1);
                    }
                }
            }
        }
    }

    /** 夜想曲: 我方全体的攻击力提高15%; 队伍中量子角色越多, 量子角色攻击力额外提高。 */
    static class SparkleNocturne implements Trace {
        private final double q1;
        private final double q2;
        private final double q3;
        private final double base;

        SparkleNocturne(double q1, double q2, double q3, double base) {
            this.q1 = q1;
            this.q2 = q2;
            this.q3 = q3;
            this.base = base;
        }

        @Override
        public String getName() {
            return "夜想曲";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            long quantumCount = battle.getAliveCharacters().stream()
                    .filter(c -> c.getElement() == Element.QUANTUM).count();
            double quantumBonus = quantumCount >= 3 ? q3 : quantumCount == 2 ? q2 : quantumCount == 1 ? q1 : 0;
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                Buff buff = new Buff("夜想曲", Buff.Category.BUFF, owner, ally, -1)
                        .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(base + (quantumBonus > 0
                                && ally.getElement() == Element.QUANTUM ? quantumBonus : 0),
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
            IO.println("  [行迹] 夜想曲: party ATK +" + String.format("%.0f", base * 100)
                    + "% (quantum bonus +" + String.format("%.0f", quantumBonus * 100) + "%)");
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 悬置怀疑: 终结技【谜诡】持续时间+1回合, 持有【谜诡】的我方目标攻击力提高40%。 */
    static class SparkleE1 implements Trace {
        private final double atkBonus;

        SparkleE1(double atkBonus) {
            this.atkBonus = atkBonus;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                Buff buff = new Buff("星魂1", Buff.Category.BUFF, owner, ally, -1)
                        .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atkBonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.ULTRA) {
                return;
            }
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                for (Buff buff : ally.getBuffs()) {
                    if ("谜诡".equals(buff.getName())) {
                        buff.setDuration(buff.getDuration() + 1);
                    }
                }
            }
        }
    }

    /** 星魂2 虚构无端: 天赋每层效果额外使我方目标造成伤害时无视目标8%防御力。 */
    static class SparkleE2 implements Trace {
        private final double defIgnore;

        SparkleE2(double defIgnore) {
            this.defIgnore = defIgnore;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                Buff buff = new Buff("星魂2", Buff.Category.BUFF, owner, ally, -1)
                        .stat(AttributeType.DEFENCE_IGNORE, DoubleValue.Modifier.pure(defIgnore,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
        }
    }

    /** 星魂4 游戏人间: 终结技可以额外恢复1个战技点。 */
    static class SparkleE4 implements Trace {
        @Override
        public String getName() {
            return "星魂4";
        }
    }

    /** 星魂6 多重解答: 战技的暴击伤害提高效果额外提高 (等同于花火暴击伤害的30%)。 */
    static class SparkleE6 implements Trace {
        private final double cdmgRatio;

        SparkleE6(double cdmgRatio) {
            this.cdmgRatio = cdmgRatio;
        }

        @Override
        public String getName() {
            return "星魂6";
        }
    }
}
