package com.laosun.aluminium.models.kit;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.Eidolon;
import com.laosun.aluminium.beans.SkillPoint;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Element;
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
 * 飞霄 (Feixiao, cid 1220) — 风属性 巡猎.
 */
public final class FeixiaoKit implements CharacterKit {

    @Override
    public int cid() {
        return 1220;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new FeixiaoHeaven(param(byId, 1220101, 0, 3)),
                new FeixiaoForm(param(byId, 1220102, 0, 0.36)),
                new FeixiaoVolt(param(byId, 1220103, 0, 0.48), intParam(byId, 1220103, 1, 3)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new FeixiaoE1(param(e, 0, 0.1), intParam(e, 1, 5));
            case 2 -> new FeixiaoE2(intParam(e, 0, 6));
            case 4 -> new FeixiaoE4(param(e, 0, 1), param(e, 1, 0.08), intParam(e, 2, 2));
            case 6 -> new FeixiaoE6(param(e, 0, 0.2), param(e, 1, 1.4));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> FeixiaoKit::feixiaoSkill;
            case 3 -> FeixiaoKit::feixiaoUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /**
     * 战技 钺贯: 对指定敌方单体造成#1%伤害, 随后立即对该目标额外发动1次天赋的追加攻击.
     */
    static void feixiaoSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit target = targets.getFirst();
        ctx.dealDamage(battle, user, target, ctx.firstParam());
        battle.breakToughness(user, target, ctx.stanceSingle());
        if (user instanceof Character character) {
            followUpAttack(battle, character, target);
        }
    }

    /**
     * 终结技 凿破大荒: 先对该目标发动总计#3次【闪裂刃舞】/【钺贯天冲】, 期间可无视弱点属性
     * 削减目标韧性 (以临时植入风弱点代理), 最后造成等同于#1%攻击力的风属性伤害.
     */
    static void feixiaoUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        if (!(user instanceof Character character)) {
            return;
        }
        CanHit target = targets.getFirst();
        double hitMult = enhancedHitMultiplier(character);
        double finalMult = ctx.firstParam();
        int hits = ctx.intParam(2, 6);
        double stance = ctx.stanceSingle() / (hits + 1);
        for (int i = 0; i < hits; i++) {
            if (target.isDeath()) {
                break;
            }
            ignoreWeakness(battle, character, target);
            battle.dealAttackDamage(character, target, hitMult, 0,
                    com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                            com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP, Element.WIND));
            battle.breakToughness(character, target, stance);
        }
        if (target.isDeath()) {
            List<Enemy> alive = battle.getAliveEnemies();
            if (alive.isEmpty()) {
                return;
            }
            target = alive.get((int) (Math.random() * alive.size()));
        }
        battle.dealAttackDamage(character, target, finalMult, 0,
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.ULTRA, Element.WIND));
        battle.breakToughness(character, target, stance);
    }

    /** 天赋 雷狩 的追加攻击 (倍率#1, 发动时使自身造成的伤害提高#5, 持续#6回合;
     *  星魂6 使倍率额外提高, 星魂4 使削韧值提高). */
    static void followUpAttack(Battle battle, Character owner, CanHit target) {
        if (target.isDeath()) {
            List<Enemy> alive = battle.getAliveEnemies();
            if (alive.isEmpty()) {
                return;
            }
            target = alive.get((int) (Math.random() * alive.size()));
        }
        List<Double> p = rawParams(owner, 4);
        double mult = p.size() > 0 ? p.get(0) : 0.55;
        double dmgBuff = p.size() > 4 ? p.get(4) : 0.3;
        int buffTurns = p.size() > 5 ? p.get(5).intValue() : 2;
        double toughness = 0.5;
        double extraMult = 0;
        for (Trace trace : owner.getTraces()) {
            if (trace instanceof FeixiaoE6 e6) {
                extraMult += e6.multBonus;
            }
            if (trace instanceof FeixiaoE4 e4) {
                toughness *= 1 + e4.toughBonus;
            }
        }
        battle.dealAttackDamage(owner, target, mult + extraMult, 0,
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP, Element.WIND));
        battle.breakToughness(owner, target, toughness);
        owner.removeBuff("雷狩");
        Buff buff = new Buff("雷狩", Buff.Category.BUFF, owner, owner, buffTurns)
                .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(dmgBuff,
                        DoubleValue.Modifier.ModifierSource.BUFF));
        battle.applyBuff(owner, buff);
        for (Trace trace : owner.getTraces()) {
            if (trace instanceof FeixiaoE4 e4) {
                e4.speedUp(battle, owner);
            }
        }
        IO.println("  [天赋] " + owner.getName() + " follows up on " + target.getName());
    }

    /** 终极技的无视弱点: 给非风弱目标临时植入风弱点后再削韧 (代理, 文档见 desc). */
    private static void ignoreWeakness(Battle battle, Character owner, CanHit target) {
        if (target instanceof Enemy enemy && !enemy.isBroken() && !enemy.isWeakTo(Element.WIND)) {
            enemy.addTemporaryWeakness(Element.WIND, 2);
        }
    }

    /** 强化普攻 (技能8/9) 的伤害倍率: 0.36, 用于终结技内部的多次连击. */
    private static double enhancedHitMultiplier(Character owner) {
        List<Double> p = rawParams(owner, 8);
        return p.size() > 0 ? p.get(0) : 0.36;
    }

    private static List<Double> rawParams(Character owner, int skillId) {
        Map<Integer, com.laosun.aluminium.beans.Skill> map = Constant.SKILLS.get(owner.getCid());
        com.laosun.aluminium.beans.Skill skill = map == null ? null : map.get(skillId);
        if (skill == null || skill.paramList() == null || skill.paramList().isEmpty()) {
            return List.of();
        }
        return skill.paramList().getFirst();
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /**
     * 天通: 战斗开始时获得#1点【飞黄】; 回合开始时, 若上回合未通过天赋发动追加攻击,
     * 计入1次获得【飞黄】所需的攻击次数。兼作天赋 雷狩 的代理: 队友攻击后飞霄累计【飞黄】
     * (每#2次攻击1点), 并立即对主目标发动追加攻击 (每回合最多1次)。
     */
    static class FeixiaoHeaven implements Trace {
        private final double initialCharge;
        private int feihuang = 0;
        private int attackCount = 0;
        private boolean followUpUsedThisTurn = false;
        private boolean followUpTriggeredLastTurn = false;

        FeixiaoHeaven(double initialCharge) {
            this.initialCharge = initialCharge;
        }

        @Override
        public String getName() {
            return "天通";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            feihuang = (int) Math.round(initialCharge);
            attackCount = 0;
            followUpUsedThisTurn = false;
            followUpTriggeredLastTurn = false;
            IO.println("  [行迹] " + owner.getName() + " gains " + feihuang + " 【飞黄】 (天通)");
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            followUpUsedThisTurn = false;
            if (!followUpTriggeredLastTurn) {
                attackCount++;
                if (attackCount >= attacksPerCharge(owner)) {
                    attackCount = 0;
                    if (feihuang < maxFeihuang(owner)) {
                        feihuang++;
                    }
                }
            }
            followUpTriggeredLastTurn = false;
        }

        @Override
        public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                 List<? extends CanHit> targets) {
            if (!isAttack(type) || targets == null || targets.isEmpty()) {
                return;
            }
            CanHit main = targets.getFirst();
            if (main.getCamp() == owner.getCamp()) {
                return;
            }
            attackCount++;
            if (attackCount >= attacksPerCharge(owner)) {
                attackCount = 0;
                if (feihuang < maxFeihuang(owner)) {
                    feihuang++;
                }
            }
            if (followUpUsedThisTurn) {
                return;
            }
            followUpUsedThisTurn = true;
            followUpTriggeredLastTurn = true;
            followUpAttack(battle, owner, main);
        }

        /** 终结技消耗6点【飞黄】 (引擎仍以能量为准, 此处仅作计数代理). */
        void onUltCast() {
            feihuang = Math.max(0, feihuang - 6);
        }

        /** 追加 1 点【飞黄】 (星魂2). */
        void addCharge(Character owner) {
            if (feihuang < maxFeihuang(owner)) {
                feihuang++;
            }
        }

        int getFeihuang() {
            return feihuang;
        }

        private int attacksPerCharge(Character owner) {
            List<Double> p = rawParams(owner, 4);
            return p.size() > 1 ? p.get(1).intValue() : 2;
        }

        private int maxFeihuang(Character owner) {
            List<Double> p = rawParams(owner, 4);
            return p.size() > 3 ? p.get(3).intValue() : 12;
        }
    }

    /** 解形: 施放终结技对敌方目标造成伤害时，被视为发动了追加攻击。追加攻击的暴击伤害提高#1%
     *  (暴击伤害提高以伤害倍率代理, 见 desc)。 */
    static class FeixiaoForm implements Trace {
        private final double bonus;

        FeixiaoForm(double bonus) {
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return "解形";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return type == SkillType.ULTRA ? 1 + bonus : 1.0;
        }
    }

    /** 电举: 施放战技时，攻击力提高#1%，持续#2回合。 */
    static class FeixiaoVolt implements Trace {
        private final double atkPercent;
        private final int turns;

        FeixiaoVolt(double atkPercent, int turns) {
            this.atkPercent = atkPercent;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "电举";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL) {
                return;
            }
            owner.removeBuff("电举");
            Buff buff = new Buff("电举", Buff.Category.BUFF, owner, owner, turns)
                    .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atkPercent,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
            IO.println("  [行迹] " + owner.getName() + " gains +" + String.format("%.0f%%", atkPercent * 100)
                    + " ATK (电举, " + turns + " turns)");
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 镇绥天钧: 发动【闪裂刃舞】或【钺贯天冲】后, 终结技伤害提高#1, 最多叠加#2层,
     *  持续至终结技行动结束。 */
    static class FeixiaoE1 implements Trace {
        private final double bonus;
        private final int maxStacks;
        private int stacks = 0;

        FeixiaoE1(double bonus, int maxStacks) {
            this.bonus = bonus;
            this.maxStacks = maxStacks;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.COMMON && owner.isEnhanced() && stacks < maxStacks) {
                stacks++;
                IO.println("  [星魂] " + owner.getName() + " stacks 镇绥天钧 (" + stacks + "/" + maxStacks + ")");
            } else if (type == SkillType.ULTRA) {
                stacks = 0;
            }
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return type == SkillType.ULTRA ? 1 + stacks * bonus : 1.0;
        }
    }

    /** 星魂2 礼辰祷月: 我方目标每施放1次追加攻击即可使飞霄获得1点【飞黄】, 每回合最多#1次
     *  (追加攻击以队友攻击代理). */
    static class FeixiaoE2 implements Trace {
        private final int maxPerTurn;
        private int usedThisTurn = 0;

        FeixiaoE2(int maxPerTurn) {
            this.maxPerTurn = maxPerTurn;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            usedThisTurn = 0;
        }

        @Override
        public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                 List<? extends CanHit> targets) {
            if (!isAttack(type) || usedThisTurn >= maxPerTurn) {
                return;
            }
            usedThisTurn++;
            for (Trace trace : owner.getTraces()) {
                if (trace instanceof FeixiaoHeaven heaven) {
                    heaven.addCharge(owner);
                    IO.println("  [星魂] " + owner.getName() + " gains 1 【飞黄】 (礼辰祷月, "
                            + heaven.getFeihuang() + " total)");
                }
            }
        }
    }

    /** 星魂4 驱飓听冰: 天赋的追加攻击的削韧值提高#1, 发动时使自身速度提高#2, 持续#3回合。 */
    static class FeixiaoE4 implements Trace {
        private final double toughBonus;
        private final double speedPercent;
        private final int turns;

        FeixiaoE4(double toughBonus, double speedPercent, int turns) {
            this.toughBonus = toughBonus;
            this.speedPercent = speedPercent;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        void speedUp(Battle battle, Character owner) {
            owner.removeBuff("驱飓听冰");
            Buff buff = new Buff("驱飓听冰", Buff.Category.BUFF, owner, owner, turns)
                    .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(speedPercent,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
            IO.println("  [星魂] " + owner.getName() + " speeds up (+"
                    + String.format("%.0f%%", speedPercent * 100) + ", 驱飓听冰)");
        }
    }

    /** 星魂6 惟首正丘: 终结技伤害的全属性抗性穿透提高#1; 天赋的追加攻击伤害倍率提高#2。 */
    static class FeixiaoE6 implements Trace {
        private final double penetration;
        private final double multBonus;

        FeixiaoE6(double penetration, double multBonus) {
            this.penetration = penetration;
            this.multBonus = multBonus;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            owner.removeBuff("惟首正丘");
            Buff buff = new Buff("惟首正丘", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.RESISTANCE_PENETRATION, DoubleValue.Modifier.pure(penetration,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 是否为攻击类行动 (普攻/战技/终结技). */
    static boolean isAttack(SkillType type) {
        return type == SkillType.COMMON || type == SkillType.SKILL || type == SkillType.ULTRA;
    }
}
