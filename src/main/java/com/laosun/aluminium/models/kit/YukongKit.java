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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.laosun.aluminium.models.kit.KitSupport.intParam;
import static com.laosun.aluminium.models.kit.KitSupport.param;

/**
 * 驭空 (Yukong, cid 1207) — 虚数属性 同谐.
 *
 * <p>核心机制【鸣弦号令】(最多2层) 以持有者身上的同名 Buff 代理:
 * 持续时间即层数, 施放战技获得2层, 每当我方其他目标行动后移除1层;
 * 层数降为0时移除我方全体的【鸣弦号令·攻】攻击力增益。
 */
public final class YukongKit implements CharacterKit {

    @Override
    public int cid() {
        return 1207;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new YukongBowstring(intParam(byId, 1207101, 0, 2)),
                new YukongImaginary(param(byId, 1207102, 0, 0.12)),
                new YukongVigor(param(byId, 1207103, 0, 2)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new YukongE1(param(e, 0, 0.1), intParam(e, 1, 2));
            case 2 -> new YukongE2(intParam(e, 0, 5));
            case 4 -> new YukongE4(param(e, 0, 0.3));
            case 6 -> new YukongE6(intParam(e, 0, 1));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> YukongKit::yukongSkill;
            case 3 -> YukongKit::yukongUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 获得#1层【鸣弦号令】(最多2层); 持有【鸣弦号令】时, 我方全体攻击力提高#2%.
     *  (层数按"每层1回合"存入 Buff 持续时间, 由行迹 气壮 在队友行动后递减.) */
    static void yukongSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double atkPercent = ctx.param(1, 0.4);
        setOrderLayers(battle, user, 2);
        for (CanHit ally : ctx.friendlyTargets(battle, user)) {
            ally.removeBuff("鸣弦号令·攻");
            Buff buff = new Buff("鸣弦号令·攻", Buff.Category.BUFF, user, ally, -1)
                    .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atkPercent,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(ally, buff);
        }
        IO.println("  " + user.getName() + " gains 2 layers of 【鸣弦号令】: party ATK +"
                + String.format("%.0f%%", atkPercent * 100));
    }

    /** 终结技: 对指定敌方单体造成#1%攻击力的虚数属性伤害; 若持有【鸣弦号令】,
     *  额外使我方全体暴击率提高#2%、暴击伤害提高#3%, 持续2回合. */
    static void yukongUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        double critChance = ctx.param(1, 0.21);
        double critDamage = ctx.param(2, 0.39);
        CanHit target = targets.getFirst();
        ctx.dealDamage(battle, user, target, multiplier);
        battle.breakToughness(user, target, ctx.stanceSingle());
        if (user.hasBuffNamed("鸣弦号令")) {
            for (CanHit ally : ctx.friendlyTargets(battle, user)) {
                ally.removeBuff("鸣弦号令·暴");
                Buff buff = new Buff("鸣弦号令·暴", Buff.Category.BUFF, user, ally, 2)
                        .stat(AttributeType.CRIT_CHANCE, DoubleValue.Modifier.pure(critChance,
                                DoubleValue.Modifier.ModifierSource.BUFF))
                        .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(critDamage,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
            IO.println("  " + user.getName() + " grants party crit buffs (鸣弦号令·暴)!");
        }
    }

    /** 当前持有的【鸣弦号令】层数 (Buff 持续时间即层数). */
    static int orderLayers(CanHit user) {
        for (Buff buff : user.getBuffs()) {
            if ("鸣弦号令".equals(buff.getName())) {
                return Math.max(0, buff.getDuration());
            }
        }
        return 0;
    }

    /** 设置【鸣弦号令】层数 (0 表示移除). */
    static void setOrderLayers(Battle battle, CanHit user, int layers) {
        user.removeBuff("鸣弦号令");
        if (layers > 0) {
            Buff order = new Buff("鸣弦号令", Buff.Category.BUFF, user, user, layers);
            battle.applyBuff(user, order);
        }
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /**
     * 襄尺: 被施加负面效果时可抵抗1次负面效果, 2回合后可再次触发。
     * (引擎无法拦截单次负面效果施加, 代理为常驻效果抵抗100%;)
     * 兼作天赋 箭彻七札 的代理: 施放普攻时额外造成#1%攻击力的虚数属性伤害,
     * 并使本次攻击的削韧值提高#2%, 该效果在#3回合后可再次触发。
     */
    static class YukongBowstring implements Trace {
        private final int cooldown;
        private boolean talentReady = true;

        YukongBowstring(int cooldown) {
            this.cooldown = cooldown;
        }

        @Override
        public String getName() {
            return "襄尺";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("襄尺", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.EFFECT_RESISTANCE, DoubleValue.Modifier.pure(1.0,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            talentReady = true;
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.COMMON || targets.isEmpty() || !talentReady) {
                return;
            }
            double ratio = 0.4;
            com.laosun.aluminium.models.SkillData talent = KitSupport.skillData(owner, SkillType.TALENT);
            if (talent != null && talent.getSkills() != null && !talent.getSkills().isEmpty()
                    && !talent.getSkills().getFirst().isEmpty()) {
                ratio = talent.getSkills().getFirst().getFirst();
            }
            talentReady = false;
            CanHit target = targets.getFirst();
            battle.dealAttackDamage(owner, target, ratio, 0,
                    com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                            com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, Element.IMAGINARY));
            // 削韧值提高100%: 普攻基础削韧为1单位, 追加1单位.
            battle.breakToughness(owner, target, 1.0);
            IO.println("  [行迹] " + owner.getName() + " 箭彻七札: extra hit + toughness ("
                    + cooldown + " turn cooldown)");
        }
    }

    /** 迟彝: 驭空在场时，我方全体造成的虚数属性伤害提高12%。 */
    static class YukongImaginary implements Trace {
        private final double bonus;

        YukongImaginary(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "迟彝";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (Character ally : battle.getAliveCharacters()) {
                Buff buff = new Buff("迟彝", Buff.Category.BUFF, owner, ally, -1)
                        .stat(AttributeType.IMAGINARY_DAMAGE_BOOST, DoubleValue.Modifier.pure(bonus,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
            IO.println("  [行迹] " + owner.getName() + ": party imaginary damage +"
                    + String.format("%.0f%%", bonus * 100));
        }
    }

    /** 气壮: 持有【鸣弦号令】时，每当我方目标行动后，驭空额外恢复#1点能量。
     *  兼作【鸣弦号令】的层数衰减: 每当我方其他目标行动后移除1层 (战技施放回合除外). */
    static class YukongVigor implements Trace {
        private final double energy;

        YukongVigor(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "气壮";
        }

        @Override
        public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                 List<? extends CanHit> targets) {
            if (actor == owner) {
                return;
            }
            int layers = orderLayers(owner);
            if (layers > 0) {
                if (layers <= 1) {
                    setOrderLayers(battle, owner, 0);
                    for (CanHit ally : battle.getAlivePlayerUnits()) {
                        ally.removeBuff("鸣弦号令·攻");
                    }
                    IO.println("  [行迹] " + owner.getName() + "'s 【鸣弦号令】 expired (party ATK buff lost)");
                } else {
                    setOrderLayers(battle, owner, layers - 1);
                }
            }
            if (orderLayers(owner) > 0) {
                owner.gainEnergy(energy);
                IO.println("  [行迹] " + owner.getName() + " restores "
                        + String.format("%.0f", energy) + " energy (鸣弦号令, 气壮)");
            }
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 天舶飞将，枕弧待战: 进入战斗时，我方全体速度提高10%，持续2回合。 */
    static class YukongE1 implements Trace {
        private final double speedPercent;
        private final int turns;

        YukongE1(double speedPercent, int turns) {
            this.speedPercent = speedPercent;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (Character ally : battle.getAliveCharacters()) {
                Buff buff = new Buff("星魂1", Buff.Category.BUFF, owner, ally, turns)
                        .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(speedPercent,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
            IO.println("  [星魂] " + owner.getName() + ": party speed +"
                    + String.format("%.0f%%", speedPercent * 100) + " (" + turns + " turns)");
        }
    }

    /** 星魂2 青霄驰骋，驱驭苍穹: 当我方任意单体当前能量值等于其能量上限时，驭空额外恢复5点能量。
     *  每个单体仅可触发1次, 驭空施放终结技后重置触发次数。 */
    static class YukongE2 implements Trace {
        private final double energy;
        private final Set<Character> triggered = new HashSet<>();

        YukongE2(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.ULTRA) {
                triggered.clear();
                IO.println("  [星魂] " + owner.getName() + " resets E2 trigger marks (ult)");
            }
        }

        @Override
        public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                 List<? extends CanHit> targets) {
            if (actor.getMaxEnergy() > 0 && actor.getEnergy() >= actor.getMaxEnergy()
                    && triggered.add(actor)) {
                owner.gainEnergy(energy);
                IO.println("  [星魂] " + owner.getName() + " restores "
                        + String.format("%.0f", energy) + " energy (" + actor.getName() + " energy full)");
            }
        }
    }

    /** 星魂4 百里闻风，九曲响镝: 持有【鸣弦号令】时，驭空造成的伤害提高30%。 */
    static class YukongE4 implements Trace {
        private final double bonus;

        YukongE4(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return orderLayers(owner) > 0 ? 1 + bonus : 1.0;
        }
    }

    /** 星魂6 弦栝如雷，铣珧激荡: 驭空施放终结技时，立即先获得1层【鸣弦号令】。 */
    static class YukongE6 implements Trace {
        private final int layers;

        YukongE6(int layers) {
            this.layers = layers;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.ULTRA) {
                setOrderLayers(battle, owner, Math.min(2, orderLayers(owner) + layers));
                IO.println("  [星魂] " + owner.getName() + " gains "
                        + layers + " layer of 【鸣弦号令】 (E6)");
            }
        }
    }
}
