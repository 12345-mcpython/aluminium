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
 * 星期日 (Sunday, cid 1313) — 虚数属性 同谐.
 */
public final class SundayKit implements CharacterKit {

    @Override
    public int cid() {
        return 1313;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new SundayLonging(param(byId, 1313101, 0, 40)),
                new SundayDuster(param(byId, 1313102, 0, 25)),
                new SundayHarbor(intParam(byId, 1313103, 0, 1)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new SundayE1(intParam(e, 0, 2), param(e, 1, 0.16), param(e, 2, 0.4));
            case 2 -> new SundayE2(param(e, 0, 0.3), intParam(e, 1, 2));
            case 4 -> new SundayE4(param(e, 0, 8));
            case 6 -> new SundayE6(intParam(e, 0, 3), param(e, 1, 0.02), intParam(e, 2, 1));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> SundayKit::sundaySkill;
            case 3 -> SundayKit::sundayUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 使指定我方单体立即行动, 并使其造成的伤害提高#2 (有召唤物额外#4), 持续#3回合。 */
    static void sundaySkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double dmgBoost = ctx.param(1, 0.15);
        int turns = ctx.intParam(2, 2);
        double summonBoost = ctx.param(3, 0.25);
        for (CanHit target : KitSupport.friendlyTargets(battle, user, targets)) {
            double boost = dmgBoost;
            if (target instanceof Character character && !character.getSummons().isEmpty()) {
                boost += summonBoost;
            }
            target.removeBuff("纸与仪典的恩赐");
            Buff buff = new Buff("纸与仪典的恩赐", Buff.Category.BUFF, user, target, turns)
                    .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(boost,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(target, buff);
            if (target != user) {
                battle.advanceByPercent(target, 1.0);
                IO.println("  " + target.getName() + " acts immediately!");
            }
            // 行迹 掌中安港: 解除目标1个负面效果.
            target.getBuffs().stream()
                    .filter(b -> b.getCategory() == Buff.Category.DEBUFF)
                    .findFirst()
                    .ifPresent(b -> battle.removeBuff(target, b));
            // 对【蒙福者】施放战技后恢复1个战技点.
            if (target.hasBuffNamed("蒙福者")) {
                battle.addSkillPoints(1);
                IO.println("  " + user.getName() + " restores 1 skill point (蒙福者)");
            }
            // 天赋 倾诉之肉身 代理: 使目标暴击率提高10%, 持续3回合.
            target.removeBuff("倾诉之肉身");
            Buff crit = new Buff("倾诉之肉身", Buff.Category.BUFF, user, target, 3)
                    .stat(AttributeType.CRIT_CHANCE, DoubleValue.Modifier.pure(0.1,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(target, crit);
        }
    }

    /** 终结技: 为指定我方单体恢复#1能量上限的能量, 并使其成为【蒙福者】#3回合
     *  (暴击伤害提高, 等同于星期日#2暴击伤害+#4). */
    static void sundayUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double energyRatio = ctx.firstParam();
        double cdmgRatio = ctx.param(1, 0.12);
        int turns = ctx.intParam(2, 3);
        double cdmgFlat = ctx.param(3, 0.08);
        for (CanHit target : KitSupport.friendlyTargets(battle, user, targets)) {
            double restored = Math.max(target.getMaxEnergy() * energyRatio, 40);
            target.gainEnergy(restored);
            target.removeBuff("蒙福者");
            double cdmg = user.getAttribute(AttributeType.CRIT_ATTACK) != null
                    ? user.getAttribute(AttributeType.CRIT_ATTACK).get() * cdmgRatio + cdmgFlat : cdmgFlat;
            Buff buff = new Buff("蒙福者", Buff.Category.BUFF, user, target, turns)
                    .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(cdmg,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(target, buff);
            IO.println("  " + target.getName() + " restores " + String.format("%.0f", restored)
                    + " energy, becomes 蒙福者 (CDMG +" + String.format("%.0f", cdmg * 100) + "%)");
        }
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 主日渴慕: 施放终结技时, 若为目标恢复的能量不足40点, 恢复的能量提高至40点。 */
    static class SundayLonging implements Trace {
        private final double minEnergy;

        SundayLonging(double minEnergy) {
            this.minEnergy = minEnergy;
        }

        @Override
        public String getName() {
            return "主日渴慕";
        }
    }

    /** 崇高拂尘: 战斗开始时, 星期日恢复25点能量。 */
    static class SundayDuster implements Trace {
        private final double energy;

        SundayDuster(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "崇高拂尘";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            owner.gainEnergy(energy);
            IO.println("  [行迹] " + owner.getName() + " starts with +"
                    + String.format("%.0f", energy) + " energy");
        }
    }

    /** 掌中安港: 施放战技时, 解除目标的1个负面效果。 */
    static class SundayHarbor implements Trace {
        private final int cleanseCount;

        SundayHarbor(int cleanseCount) {
            this.cleanseCount = cleanseCount;
        }

        @Override
        public String getName() {
            return "掌中安港";
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 千纪沉寂之末: 战技使目标造成伤害时无视16%防御力 (召唤物40%), 持续2回合。 */
    static class SundayE1 implements Trace {
        private final int turns;
        private final double defIgnore;
        private final double summonDefIgnore;

        SundayE1(int turns, double defIgnore, double summonDefIgnore) {
            this.turns = turns;
            this.defIgnore = defIgnore;
            this.summonDefIgnore = summonDefIgnore;
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

    /** 星魂2 笃信补完微缺: 首次施放终结技后恢复2个战技点, 【蒙福者】造成的伤害提高30%。 */
    static class SundayE2 implements Trace {
        private final double dmgBoost;
        private final int skillPoints;
        private boolean firstUltUsed = false;

        SundayE2(double dmgBoost, int skillPoints) {
            this.dmgBoost = dmgBoost;
            this.skillPoints = skillPoints;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.ULTRA || firstUltUsed) {
                return;
            }
            firstUltUsed = true;
            battle.addSkillPoints(skillPoints);
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                if (ally.hasBuffNamed("蒙福者")) {
                    Buff buff = new Buff("星魂2", Buff.Category.BUFF, owner, ally, -1)
                            .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(dmgBoost,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
            }
            IO.println("  [星魂] " + owner.getName() + " restores " + skillPoints + " skill points");
        }
    }

    /** 星魂4 雕塑的卷首语: 回合开始时, 恢复8点能量。 */
    static class SundayE4 implements Trace {
        private final double energy;

        SundayE4(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            owner.gainEnergy(energy);
            IO.println("  [星魂] " + owner.getName() + " restores "
                    + String.format("%.0f", energy) + " energy");
        }
    }

    /** 星魂6 群星喧哗之始: 天赋的暴击率提高效果可叠加3层, 持续时间+1回合。 */
    static class SundayE6 implements Trace {
        private final int maxStacks;
        private final double overflowCdmg;
        private final int extraTurns;

        SundayE6(int maxStacks, double overflowCdmg, int extraTurns) {
            this.maxStacks = maxStacks;
            this.overflowCdmg = overflowCdmg;
            this.extraTurns = extraTurns;
        }

        @Override
        public String getName() {
            return "星魂6";
        }
    }
}
