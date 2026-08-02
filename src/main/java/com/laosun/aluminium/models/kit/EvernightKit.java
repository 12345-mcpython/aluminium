package com.laosun.aluminium.models.kit;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.Eidolon;
import com.laosun.aluminium.beans.SkillPoint;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Element;
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
 * 长夜月 (Evernight, cid 1413) — 冰属性 记忆 (忆灵: 长夜).
 */
public final class EvernightKit implements CharacterKit {

    @Override
    public int cid() {
        return 1413;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new EvernightDark(param(byId, 1413101, 0, 0.35), param(byId, 1413101, 1, 0.05),
                        param(byId, 1413101, 2, 0.15), intParam(byId, 1413101, 3, 2)),
                new EvernightCandle(intParam(byId, 1413102, 0, 1), param(byId, 1413102, 1, 70),
                        intParam(byId, 1413102, 2, 1), param(byId, 1413102, 3, 5)),
                new EvernightRain(param(byId, 1413103, 0, 0.05), param(byId, 1413103, 1, 0.15),
                        param(byId, 1413103, 2, 0.5), param(byId, 1413103, 3, 0.65)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new EvernightE1(param(e, 0, 1.2), param(e, 1, 1.25), param(e, 2, 1.3), param(e, 3, 1.5));
            case 2 -> new EvernightE2(intParam(e, 0, 2), intParam(e, 1, 2), param(e, 2, 0.4));
            case 4 -> new EvernightE4(param(e, 0, 0.25), param(e, 1, 0.25));
            case 6 -> new EvernightE6(param(e, 0, 0.3), param(e, 1, 0.2));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> EvernightKit::evernightSkill;
            case 3 -> EvernightKit::evernightUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 消耗#6当前生命值召唤忆灵「长夜」, 我方全体忆灵暴击伤害提高 (等同于长夜月#1暴击伤害), 持续#2回合。 */
    static void evernightSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double cdmgRatio = ctx.firstParam();
        int turns = ctx.intParam(1, 2);
        int memory = ctx.intParam(2, 2);
        double hpCostRatio = ctx.param(5, 0.1);
        double cost = user.getCurrentHp() * hpCostRatio;
        if (user.getCurrentHp() > cost) {
            user.takeDamage(cost);
        } else if (user.getCurrentHp() > 1) {
            user.takeDamage(user.getCurrentHp() - 1);
        }
        battle.summonRequest(user);
        EvernightKit.addMemory(user, memory);
        double cdmg = user.getAttribute(AttributeType.CRIT_ATTACK) != null
                ? user.getAttribute(AttributeType.CRIT_ATTACK).get() * cdmgRatio : 0;
        for (CanHit ally : battle.getAlivePlayerUnits()) {
            Buff buff = new Buff("白昼悄然离去", Buff.Category.BUFF, user, ally, turns)
                    .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(cdmg,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(ally, buff);
        }
        IO.println("  " + user.getName() + " summons 长夜, +" + memory + " 【忆质】 ("
                + EvernightKit.memory(user) + ")");
    }

    /** 终结技: 召唤忆灵「长夜」, 使其对敌方全体造成#1生命上限伤害, 进入【至暗之谜】状态 (敌方受到的伤害提高#4)。 */
    static void evernightUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        int charge = ctx.intParam(1, 2);
        double selfDmg = ctx.param(2, 0.3);
        double vuln = ctx.param(3, 0.15);
        battle.summonRequest(user);
        for (Enemy enemy : battle.getAliveEnemies()) {
            battle.dealAttackDamageBase(user, enemy, user.getMaxHp() * multiplier,
                    com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                            com.laosun.aluminium.models.DamageCalculator.DamageType.ULTRA, Element.ICE));
            battle.breakToughness(user, enemy, ctx.stanceAll());
            Buff debuff = new Buff("至暗之谜", Buff.Category.DEBUFF, user, enemy, 2)
                    .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(vuln,
                            DoubleValue.Modifier.ModifierSource.DEBUFF));
            battle.applyBuff(enemy, debuff);
        }
        user.removeBuff("至暗之谜·状态");
        battle.applyBuff(user, new Buff("至暗之谜·状态", Buff.Category.BUFF, user, user, 2));
        EvernightKit.addDarkness(user, charge);
        IO.println("  " + user.getName() + " enters 至暗之谜!");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 天黑黑，月寂寂: 长夜月和忆灵「长夜」的暴击率提高35%。 */
    static class EvernightDark implements Trace {
        private final double crit;
        private final double hpCost;
        private final double cdmg;
        private final int turns;

        EvernightDark(double crit, double hpCost, double cdmg, int turns) {
            this.crit = crit;
            this.hpCost = hpCost;
            this.cdmg = cdmg;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "天黑黑，月寂寂";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("天黑黑，月寂寂", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.CRIT_CHANCE, DoubleValue.Modifier.pure(crit,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 烛火起，烛火熄: 战斗开始时恢复70点能量并获得1点【忆质】。 */
    static class EvernightCandle implements Trace {
        private final int memoryPerSkill;
        private final double startEnergy;
        private final int startMemory;
        private final double energyPerSkill;

        EvernightCandle(int memoryPerSkill, double startEnergy, int startMemory, double energyPerSkill) {
            this.memoryPerSkill = memoryPerSkill;
            this.startEnergy = startEnergy;
            this.startMemory = startMemory;
            this.energyPerSkill = energyPerSkill;
        }

        @Override
        public String getName() {
            return "烛火起，烛火熄";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            owner.gainEnergy(startEnergy);
            EvernightKit.addMemory(owner, startMemory);
            IO.println("  [行迹] " + owner.getName() + " starts with +"
                    + String.format("%.0f", startEnergy) + " energy, +" + startMemory + " 【忆质】");
        }
    }

    /** 天亮了，雨落了: 队伍中记忆命途角色越多, 忆灵暴击伤害提高越多。 */
    static class EvernightRain implements Trace {
        private final double one;
        private final double two;
        private final double three;
        private final double four;

        EvernightRain(double one, double two, double three, double four) {
            this.one = one;
            this.two = two;
            this.three = three;
            this.four = four;
        }

        @Override
        public String getName() {
            return "天亮了，雨落了";
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 睡吧，长夜有所梦: 敌方目标越多, 我方忆灵造成的伤害越高 (4名时150%)。 */
    static class EvernightE1 implements Trace {
        private final double four;
        private final double three;
        private final double two;
        private final double one;

        EvernightE1(double four, double three, double two, double one) {
            this.four = four;
            this.three = three;
            this.two = two;
            this.one = one;
        }

        @Override
        public String getName() {
            return "星魂1";
        }
    }

    /** 星魂2 听，沉眠中的呓语: 长夜月和忆灵「长夜」的暴击伤害提高40%。 */
    static class EvernightE2 implements Trace {
        private final int memoryBonus;
        private final int darknessBonus;
        private final double cdmg;

        EvernightE2(int memoryBonus, int darknessBonus, double cdmg) {
            this.memoryBonus = memoryBonus;
            this.darknessBonus = darknessBonus;
            this.cdmg = cdmg;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("星魂2", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(cdmg,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 星魂4 醒来，便是你的明天: 我方忆灵弱点击破效率提高25%。 */
    static class EvernightE4 implements Trace {
        private final double efficiency;
        private final double extra;

        EvernightE4(double efficiency, double extra) {
            this.efficiency = efficiency;
            this.extra = extra;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("星魂4", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.WEAKNESS_BREAK_EFFICIENCY, DoubleValue.Modifier.pure(efficiency,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 星魂6 就这样，一直: 我方全体目标的全属性抗性穿透提高20%。 */
    static class EvernightE6 implements Trace {
        private final double memoryReturn;
        private final double pen;

        EvernightE6(double memoryReturn, double pen) {
            this.memoryReturn = memoryReturn;
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

    // ─── 【忆质】/【至暗之谜】代理 ──────────────────────────────────────

    private static final java.util.Map<CanHit, Integer> MEMORY = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<CanHit, Integer> DARKNESS = new java.util.concurrent.ConcurrentHashMap<>();

    static int memory(CanHit owner) {
        return MEMORY.getOrDefault(owner, 0);
    }

    static void addMemory(CanHit owner, int amount) {
        MEMORY.put(owner, Math.min(16, memory(owner) + amount));
    }

    static void addDarkness(CanHit owner, int amount) {
        DARKNESS.put(owner, Math.max(0, DARKNESS.getOrDefault(owner, 0) + amount));
    }
}
