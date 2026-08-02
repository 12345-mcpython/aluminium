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
 * 玲可 (Lynx, cid 1110) — 量子属性 丰饶.
 */
public final class LynxKit implements CharacterKit {

    @Override
    public int cid() {
        return 1110;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new LynxScout(param(byId, 1110101, 0, 2)),
                new LynxExplore(param(byId, 1110102, 0, 0.35)),
                new LynxSurvival(intParam(byId, 1110103, 0, 1)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new LynxE1(param(e, 0, 0.5), param(e, 1, 0.2));
            case 2 -> new LynxE2();
            case 4 -> new LynxE4(param(e, 0, 0.03), intParam(e, 1, 1));
            case 6 -> new LynxE6(param(e, 0, 0.06), param(e, 1, 0.3));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> LynxKit::lynxSkill;
            case 3 -> LynxKit::lynxUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 为指定我方单体附上【求生反应】 (生命上限提高#1+#2, 持续#3回合),
     *  并使其回复#4生命上限+#5的生命值. */
    static void lynxSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double maxHpRatio = ctx.param(0, 0.05);
        double maxHpFlat = ctx.param(1, 50);
        int turns = ctx.intParam(2, 2);
        double healRatio = ctx.param(3, 0.08);
        double healFlat = ctx.param(4, 80);
        LynxE6 e6Trace = user instanceof Character c
                ? c.getTraces().stream().filter(t -> t instanceof LynxE6).map(t -> (LynxE6) t)
                        .findFirst().orElse(null) : null;
        boolean e2 = user instanceof Character c2
                && c2.getTraces().stream().anyMatch(t -> t instanceof LynxE2);
        for (CanHit ally : KitSupport.friendlyTargets(battle, user, targets)) {
            battle.healTarget(user, ally, user.getMaxHp() * healRatio + healFlat);
            ally.removeBuff("求生反应");
            Buff survival = new Buff("求生反应", Buff.Category.BUFF, user, ally, turns)
                    .stat(AttributeType.HEALTH, DoubleValue.Modifier.addPercent(maxHpRatio,
                            DoubleValue.Modifier.ModifierSource.BUFF))
                    .stat(AttributeType.HEALTH, DoubleValue.Modifier.pure(maxHpFlat,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(ally, survival);
            // 星魂6: 【求生反应】的生命上限提高效果额外提高6%生命上限, 效果抵抗提高30%.
            if (e6Trace != null) {
                Buff boosted = new Buff("求生反应·强化", Buff.Category.BUFF, user, ally, turns)
                        .stat(AttributeType.HEALTH, DoubleValue.Modifier.addPercent(e6Trace.maxHpRatio,
                                DoubleValue.Modifier.ModifierSource.BUFF))
                        .stat(AttributeType.EFFECT_RESISTANCE, DoubleValue.Modifier.pure(e6Trace.resistance,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, boosted);
            }
            // 星魂2: 持有【求生反应】的目标可抵抗1次负面效果施加.
            if (e2) {
                Buff resist = new Buff("求生抵抗", Buff.Category.BUFF, user, ally, -1);
                battle.applyBuff(ally, resist);
                IO.println("  [星魂] " + ally.getName() + " can resist 1 debuff application");
            }
            IO.println("  " + ally.getName() + " gains 求生反应 (max HP +"
                    + String.format("%.0f", maxHpRatio * 100) + "%+" + String.format("%.0f", maxHpFlat)
                    + ", " + turns + " turns)");
        }
    }

    /** 终结技: 解除我方全体的#1个负面效果, 并回复#2生命上限+#3的生命值. */
    static void lynxUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        int cleanseCount = ctx.intParam(0, 1);
        double healRatio = ctx.param(1, 0.09);
        double healFlat = ctx.param(2, 90);
        for (CanHit ally : ctx.friendlyTargets(battle, user)) {
            for (int i = 0; i < cleanseCount; i++) {
                Buff toRemove = ally.getBuffs().stream()
                        .filter(b -> b.getCategory() == Buff.Category.DEBUFF)
                        .findFirst().orElse(null);
                if (toRemove != null) {
                    battle.removeBuff(ally, toRemove);
                    IO.println("  " + toRemove.getName() + " dispelled from " + ally.getName());
                } else {
                    break;
                }
            }
            battle.healTarget(user, ally, user.getMaxHp() * healRatio + healFlat);
        }
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 提前勘测: 当持有【求生反应】的目标受到攻击后，玲可立即恢复2点能量。 */
    static class LynxScout implements Trace {
        private final double energy;

        LynxScout(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "提前勘测";
        }

        @Override
        public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
            // 代理: 玲可自身受到攻击时, 若她持有【求生反应】则恢复能量.
            if (owner.hasBuffNamed("求生反应")) {
                owner.gainEnergy(energy);
                IO.println("  [行迹] " + owner.getName() + " gains "
                        + String.format("%.0f", energy) + " energy (求生反应 holder hit)");
            }
        }
    }

    /** 探险技术: 抵抗控制类负面状态的概率提高35%。 */
    static class LynxExplore implements Trace {
        private final double resistance;

        LynxExplore(double resistance) {
            this.resistance = resistance;
        }

        @Override
        public String getName() {
            return "探险技术";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("探险技术", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.EFFECT_RESISTANCE, DoubleValue.Modifier.pure(resistance,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 极境求生: 天赋产生的持续回复效果延长1回合。
     *  兼作天赋 户外生存 的代理: 施放战技或终结技时, 使我方目标获得持续治疗,
     *  每回合回复#2生命上限+#3的生命值, 若持有【求生反应】额外回复#4生命上限+#5。 */
    static class LynxSurvival implements Trace {
        private final int extraTurns;

        LynxSurvival(int extraTurns) {
            this.extraTurns = extraTurns;
        }

        @Override
        public String getName() {
            return "极境求生";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL && type != SkillType.ULTRA) {
                return;
            }
            double ratio = 0.024;
            double flat = 24;
            double extraRatio = 0.03;
            double extraFlat = 30;
            com.laosun.aluminium.models.SkillData talent = KitSupport.skillData(owner, SkillType.TALENT);
            if (talent != null && talent.getSkills() != null && !talent.getSkills().isEmpty()) {
                List<Double> params = talent.getSkills().getFirst();
                if (params.size() >= 5) {
                    ratio = params.get(1);
                    flat = params.get(2);
                    extraRatio = params.get(3);
                    extraFlat = params.get(4);
                }
            }
            int turns = 2 + extraTurns;
            for (CanHit ally : KitSupport.friendlyTargets(battle, owner, targets)) {
                double perTurn = owner.getMaxHp() * ratio + flat;
                if (ally.hasBuffNamed("求生反应")) {
                    perTurn += owner.getMaxHp() * extraRatio + extraFlat;
                }
                Buff hot = new Buff("持续回复", Buff.Category.BUFF, owner, ally, turns)
                        .heal(perTurn);
                battle.applyBuff(ally, hot);
                IO.println("  [行迹] " + ally.getName() + " gains HoT ("
                        + String.format("%.0f", perTurn) + "/turn, " + turns + " turns)");
            }
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1: 为生命值≤50%的目标治疗时，治疗量提高20%。 */
    static class LynxE1 implements Trace {
        private final double hpThreshold;
        private final double bonus;

        LynxE1(double hpThreshold, double bonus) {
            this.hpThreshold = hpThreshold;
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL) {
                return;
            }
            for (CanHit ally : KitSupport.friendlyTargets(battle, owner, targets)) {
                if (ally.getHpPercent() <= hpThreshold) {
                    ally.heal(owner.getMaxHp() * 0.08 * bonus);
                    log(ally.getName() + " receives boosted healing");
                }
            }
        }
    }

    /** 星魂2 便当炉午间: 持有【求生反应】的目标可抵抗1次负面效果施加。 */
    static class LynxE2 implements Trace {
        @Override
        public String getName() {
            return "星魂2";
        }
    }

    /** 星魂4: 获得【求生反应】时，攻击力提高3%生命上限，持续1回合。 */
    static class LynxE4 implements Trace {
        private final double hpRatio;
        private final int turns;

        LynxE4(double hpRatio, int turns) {
            this.hpRatio = hpRatio;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL) {
                return;
            }
            double atk = owner.getMaxHp() * hpRatio;
            for (CanHit ally : KitSupport.friendlyTargets(battle, owner, targets)) {
                Buff buff = new Buff("求生强化", Buff.Category.BUFF, owner, ally, turns)
                        .stat(AttributeType.ATTACK, DoubleValue.Modifier.pure(atk,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
        }
    }

    /** 星魂6 极夜茶会: 【求生反应】的生命上限提高效果额外提高6%生命上限, 效果抵抗提高30%。
     *  数值已并入 lynxSkill 的 求生反应 施加逻辑。 */
    static class LynxE6 implements Trace {
        private final double maxHpRatio;
        private final double resistance;

        LynxE6(double maxHpRatio, double resistance) {
            this.maxHpRatio = maxHpRatio;
            this.resistance = resistance;
        }

        @Override
        public String getName() {
            return "星魂6";
        }
    }

    private static void log(String message) {
        IO.println("  [星魂] " + message);
    }
}
