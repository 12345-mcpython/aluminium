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
 * 克拉拉 (Clara, cid 1107) — 物理属性 毁灭.
 */
public final class ClaraKit implements CharacterKit {

    @Override
    public int cid() {
        return 1107;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new ClaraFamily(param(byId, 1107101, 0, 0.35)),
                new ClaraGuard(param(byId, 1107102, 0, 0.35)),
                new ClaraRevenge(param(byId, 1107103, 0, 0.3)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new ClaraE1();
            case 2 -> new ClaraE2(param(e, 0, 0.3), intParam(e, 1, 2));
            case 4 -> new ClaraE4(param(e, 0, 0.3));
            case 6 -> new ClaraE6(param(e, 0, 0.5), intParam(e, 1, 1));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> ClaraKit::claraSkill;
            case 3 -> ClaraKit::claraUlt;
            default -> null;
        };
    }

    // ─── 反击标记 (【反击标记】: 攻击克拉拉的敌方目标被史瓦罗标上) ─────────

    private static final java.util.Map<CanHit, java.util.Set<CanHit>> COUNTER_MARKS =
            new java.util.concurrent.ConcurrentHashMap<>();

    static void mark(CanHit owner, CanHit enemy) {
        COUNTER_MARKS.computeIfAbsent(owner, k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(enemy);
    }

    static boolean isMarked(CanHit owner, CanHit enemy) {
        java.util.Set<CanHit> marks = COUNTER_MARKS.get(owner);
        return marks != null && marks.contains(enemy);
    }

    static void clearMarks(CanHit owner) {
        COUNTER_MARKS.remove(owner);
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 对敌方全体造成#1%伤害, 对所有被标上【反击标记】的目标额外造成#2%伤害,
     *  施放后所有【反击标记】失效 (星魂1 使其不失效). */
    static void claraSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        double markedBonus = ctx.param(1, 0.6);
        for (com.laosun.aluminium.models.Enemy enemy : battle.getAliveEnemies()) {
            ctx.dealDamage(battle, user, enemy, multiplier);
            battle.breakToughness(user, enemy, ctx.stanceAll());
            if (isMarked(user, enemy)) {
                ctx.dealDamage(battle, user, enemy, markedBonus);
                IO.println("  " + enemy.getName() + " is counter-marked: extra damage!");
            }
        }
        boolean keepMarks = user instanceof Character c
                && c.getTraces().stream().anyMatch(t -> t instanceof ClaraE1);
        if (!keepMarks) {
            clearMarks(user);
            IO.println("  战技后所有【反击标记】失效");
        }
    }

    /** 终结技: 施放后克拉拉受到的伤害额外降低#4%, 史瓦罗的反击强化 (伤害倍率提高#2%),
     *  持续#3回合. */
    static void claraUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        int turns = ctx.intParam(2, 2);
        double reduction = ctx.param(3, 0.15);
        user.removeBuff("反击强化");
        Buff buff = new Buff("反击强化", Buff.Category.BUFF, user, user, turns)
                .stat(AttributeType.DAMAGE_REDUCTION, DoubleValue.Modifier.multiplyPercent(-reduction,
                        DoubleValue.Modifier.ModifierSource.BUFF));
        battle.applyBuff(user, buff);
        IO.println("  " + user.getName() + " enters 反击强化 state (" + turns + " turns)");
    }

    /** 史瓦罗反击的伤害倍率: 基础 1.0, 行迹 复仇 +30%, 反击强化 +#2%. */
    static double counterMultiplier(Battle battle, Character owner) {
        double mult = 1.0;
        for (Trace trace : owner.getTraces()) {
            if (trace instanceof ClaraRevenge revenge) {
                mult *= 1 + revenge.bonus;
            }
        }
        if (owner.hasBuffNamed("反击强化")) {
            com.laosun.aluminium.models.SkillData ult = KitSupport.skillData(owner, SkillType.ULTRA);
            if (ult != null && ult.getSkills() != null && !ult.getSkills().isEmpty()
                    && ult.getSkills().getFirst().size() > 1) {
                mult *= 1 + ult.getSkills().getFirst().get(1);
            }
        }
        return mult;
    }

    /** 史瓦罗对攻击者发动反击 (伤害类型 = 追加攻击). */
    static void counterAttack(Battle battle, Character owner, CanHit attacker) {
        if (owner.isDeath() || attacker == null || attacker.isDeath()) {
            return;
        }
        mark(owner, attacker);
        battle.dealAttackDamage(owner, attacker, counterMultiplier(battle, owner), 0,
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP,
                        owner.getElement()));
        IO.println("  史瓦罗 counter-attacks " + attacker.getName() + "!");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 家人: 受到攻击时有35%固定概率解除自身1个负面效果。 */
    static class ClaraFamily implements Trace {
        private final double chance;

        ClaraFamily(double chance) {
            this.chance = chance;
        }

        @Override
        public String getName() {
            return "家人";
        }

        @Override
        public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
            if (Math.random() >= chance) {
                return;
            }
            owner.getBuffs().stream()
                    .filter(b -> b.getCategory() == Buff.Category.DEBUFF)
                    .findFirst()
                    .ifPresent(buff -> {
                        battle.removeBuff(owner, buff);
                        IO.println("  [行迹] " + owner.getName() + " cleanses " + buff.getName());
                    });
        }
    }

    /** 守护: 抵抗控制类负面状态的概率提高35%。 */
    static class ClaraGuard implements Trace {
        private final double resistance;

        ClaraGuard(double resistance) {
            this.resistance = resistance;
        }

        @Override
        public String getName() {
            return "守护";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("守护", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.EFFECT_RESISTANCE, DoubleValue.Modifier.pure(resistance,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 复仇: 史瓦罗的反击造成的伤害提高30%。兼作天赋 斯瓦罗的守护 的代理:
     *  攻击克拉拉的敌方目标被标上【反击标记】并遭到史瓦罗的反击。 */
    static class ClaraRevenge implements Trace {
        private final double bonus;

        ClaraRevenge(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "复仇";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            clearMarks(owner);
        }

        @Override
        public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
            if (owner.isDeath() || attacker == null) {
                return;
            }
            counterAttack(battle, owner, attacker);
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 高大的身影: 战技施放后不会使【反击标记】失效。 */
    static class ClaraE1 implements Trace {
        @Override
        public String getName() {
            return "星魂1";
        }
    }

    /** 星魂2: 终结技后攻击力提高30%，持续2回合。 */
    static class ClaraE2 implements Trace {
        private final double atkPercent;
        private final int turns;

        ClaraE2(double atkPercent, int turns) {
            this.atkPercent = atkPercent;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.ULTRA) {
                return;
            }
            owner.removeBuff("星魂2");
            Buff buff = new Buff("星魂2", Buff.Category.BUFF, owner, owner, turns)
                    .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atkPercent,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 星魂4: 受到攻击后伤害降低30%，持续到自己的下个回合开始。 */
    static class ClaraE4 implements Trace {
        private final double reduction;

        ClaraE4(double reduction) {
            this.reduction = reduction;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
            owner.removeBuff("星魂4");
            Buff buff = new Buff("星魂4", Buff.Category.BUFF, owner, owner, 1)
                    .stat(AttributeType.DAMAGE_REDUCTION, DoubleValue.Modifier.multiplyPercent(-reduction,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 星魂6: 我方其他目标遭到攻击后，史瓦罗有50%固定概率触发反击。 */
    static class ClaraE6 implements Trace {
        private final double chance;
        private final int extraCounters;
        private int countersLeft = 0;

        ClaraE6(double chance, int extraCounters) {
            this.chance = chance;
            this.extraCounters = extraCounters;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.ULTRA) {
                countersLeft += extraCounters;
                IO.println("  [星魂] " + owner.getName() + " gains " + extraCounters + " guaranteed counter(s)");
            }
        }

        @Override
        public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
            if (owner.isDeath() || attacker == null) {
                return;
            }
            boolean counter = countersLeft > 0 ? (countersLeft-- > 0) : Math.random() < chance;
            if (counter) {
                counterAttack(battle, owner, attacker);
            }
        }
    }
}
