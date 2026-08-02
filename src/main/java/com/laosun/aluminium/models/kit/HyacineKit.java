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
 * 风堇 (Hyacine, cid 1409) — 风属性 记忆 (忆灵: 小伊卡).
 */
public final class HyacineKit implements CharacterKit {

    @Override
    public int cid() {
        return 1409;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new HyacineClouds(param(byId, 1409101, 0, 1), param(byId, 1409101, 1, 0.5),
                        param(byId, 1409101, 2, 0.25)),
                new HyacineRain(intParam(byId, 1409102, 0, 1), param(byId, 1409102, 1, 0.5)),
                new HyacineStorm(param(byId, 1409103, 0, 200), param(byId, 1409103, 1, 0.2),
                        param(byId, 1409103, 2, 1), param(byId, 1409103, 3, 0.01),
                        param(byId, 1409103, 4, 200)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new HyacineE1(param(e, 0, 0.5), param(e, 1, 0.08));
            case 2 -> new HyacineE2(param(e, 0, 0.3), intParam(e, 1, 2));
            case 4 -> new HyacineE4(param(e, 0, 1), param(e, 1, 0.02));
            case 6 -> new HyacineE6(param(e, 0, 0.12), param(e, 1, 0.2));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> HyacineKit::hyacineSkill;
            case 3 -> HyacineKit::hyacineUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 召唤忆灵小伊卡, 为除小伊卡以外我方全体回复#1生命上限+#2, 为小伊卡回复#3生命上限+#4。 */
    static void hyacineSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double partyRatio = ctx.firstParam();
        double partyFlat = ctx.param(1, 40);
        double servantRatio = ctx.param(2, 0.05);
        double servantFlat = ctx.param(3, 50);
        battle.summonRequest(user);
        for (CanHit ally : battle.getAlivePlayerUnits()) {
            battle.healTarget(user, ally, user.getMaxHp() * partyRatio + partyFlat);
        }
        IO.println("  " + user.getName() + " summons 小伊卡 and heals the party");
    }

    /** 终结技: 召唤小伊卡, 我方全体回复#1生命上限+#2, 风堇进入【雨过天晴】#5回合 (生命上限提高#3+#4)。 */
    static void hyacineUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double partyRatio = ctx.firstParam();
        double partyFlat = ctx.param(1, 50);
        double hpRatio = ctx.param(2, 0.15);
        double hpFlat = ctx.param(3, 150);
        int turns = ctx.intParam(4, 3);
        battle.summonRequest(user);
        for (CanHit ally : battle.getAlivePlayerUnits()) {
            battle.healTarget(user, ally, user.getMaxHp() * partyRatio + partyFlat);
            Buff buff = new Buff("雨过天晴·生命", Buff.Category.BUFF, user, ally, turns)
                    .stat(AttributeType.HEALTH, DoubleValue.Modifier.addPercent(hpRatio,
                            DoubleValue.Modifier.ModifierSource.BUFF))
                    .stat(AttributeType.HEALTH, DoubleValue.Modifier.pure(hpFlat,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(ally, buff);
        }
        user.removeBuff("雨过天晴");
        battle.applyBuff(user, new Buff("雨过天晴", Buff.Category.BUFF, user, user, turns));
        IO.println("  " + user.getName() + " enters 雨过天晴 (" + turns + " turns)");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 阴云莞尔: 风堇和小伊卡的暴击率提高100%, 为≤50%生命目标治疗时治疗量提高25%。 */
    static class HyacineClouds implements Trace {
        private final double crit;
        private final double hpThreshold;
        private final double healBonus;

        HyacineClouds(double crit, double hpThreshold, double healBonus) {
            this.crit = crit;
            this.hpThreshold = hpThreshold;
            this.healBonus = healBonus;
        }

        @Override
        public String getName() {
            return "阴云莞尔";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("阴云莞尔", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.CRIT_CHANCE, DoubleValue.Modifier.pure(crit,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 雷雨轻柔: 效果抵抗提高50%, 施放战技和终结技时解除我方全体1个负面效果。 */
    static class HyacineRain implements Trace {
        private final int cleanseCount;
        private final double resistance;

        HyacineRain(int cleanseCount, double resistance) {
            this.cleanseCount = cleanseCount;
            this.resistance = resistance;
        }

        @Override
        public String getName() {
            return "雷雨轻柔";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("雷雨轻柔", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.EFFECT_RESISTANCE, DoubleValue.Modifier.pure(resistance,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL && type != SkillType.ULTRA) {
                return;
            }
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                for (int i = 0; i < cleanseCount; i++) {
                    Buff toRemove = ally.getBuffs().stream()
                            .filter(b -> b.getCategory() == Buff.Category.DEBUFF)
                            .findFirst().orElse(null);
                    if (toRemove != null) {
                        battle.removeBuff(ally, toRemove);
                    } else {
                        break;
                    }
                }
            }
        }
    }

    /** 暴风停歇: 速度>200时, 小伊卡生命上限提高20%。 */
    static class HyacineStorm implements Trace {
        private final double speedThreshold;
        private final double hpRatio;
        private final double speedStep;
        private final double healPerStep;
        private final double maxExtra;

        HyacineStorm(double speedThreshold, double hpRatio, double speedStep, double healPerStep, double maxExtra) {
            this.speedThreshold = speedThreshold;
            this.hpRatio = hpRatio;
            this.speedStep = speedStep;
            this.healPerStep = healPerStep;
            this.maxExtra = maxExtra;
        }

        @Override
        public String getName() {
            return "暴风停歇";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            double speed = owner.getAttribute(AttributeType.SPEED) != null
                    ? owner.getAttribute(AttributeType.SPEED).get() : 0;
            if (speed > speedThreshold) {
                Buff buff = new Buff("暴风停歇", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.HEALTH, DoubleValue.Modifier.addPercent(hpRatio,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
            }
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 呵护黑夜中的烛火: 雨过天晴状态下, 我方全体生命上限额外提高50%。 */
    static class HyacineE1 implements Trace {
        private final double hpBonus;
        private final double healRatio;

        HyacineE1(double hpBonus, double healRatio) {
            this.hpBonus = hpBonus;
            this.healRatio = healRatio;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                Buff buff = new Buff("星魂1", Buff.Category.BUFF, owner, ally, -1)
                        .stat(AttributeType.HEALTH, DoubleValue.Modifier.addPercent(hpBonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
        }
    }

    /** 星魂2 请来我的庭院坐坐: 我方目标生命值降低时, 速度提高30%持续2回合。 */
    static class HyacineE2 implements Trace {
        private final double speed;
        private final int turns;

        HyacineE2(double speed, int turns) {
            this.speed = speed;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
            owner.removeBuff("星魂2");
            Buff buff = new Buff("星魂2", Buff.Category.BUFF, owner, owner, turns)
                    .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(speed,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 星魂4 送你一颗橙晖琥珀: 每超过1点速度, 暴击伤害额外提高2%。 */
    static class HyacineE4 implements Trace {
        private final double speedStep;
        private final double cdmgPerStep;

        HyacineE4(double speedStep, double cdmgPerStep) {
            this.speedStep = speedStep;
            this.cdmgPerStep = cdmgPerStep;
        }

        @Override
        public String getName() {
            return "星魂4";
        }
    }

    /** 星魂6 天空…应我的祈愿: 小伊卡在场时, 我方全体全属性抗性穿透提高20%。 */
    static class HyacineE6 implements Trace {
        private final double healRatio;
        private final double pen;

        HyacineE6(double healRatio, double pen) {
            this.healRatio = healRatio;
            this.pen = pen;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                Buff buff = new Buff("星魂6", Buff.Category.BUFF, owner, ally, -1)
                        .stat(AttributeType.RESISTANCE_PENETRATION, DoubleValue.Modifier.pure(pen,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
        }
    }
}
