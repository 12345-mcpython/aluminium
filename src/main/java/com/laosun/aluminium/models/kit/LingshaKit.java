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
import java.util.concurrent.ConcurrentHashMap;

import static com.laosun.aluminium.models.kit.KitSupport.intParam;
import static com.laosun.aluminium.models.kit.KitSupport.param;

/**
 * 灵砂 (Lingsha, cid 1222) — 火属性 丰饶.
 */
public final class LingshaKit implements CharacterKit {

    /** 【浮元】在场标记: 传统召唤物, 以追加攻击代理 (见 TopazKit 账账). */
    private static final java.util.Set<CanHit> FUYUAN_ACTIVE = ConcurrentHashMap.newKeySet();
    /** 【浮元】累计的行动提前进度, 达到1.0时立即行动. */
    private static final Map<CanHit, Double> FUYUAN_CHARGE = new ConcurrentHashMap<>();

    @Override
    public int cid() {
        return 1222;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new LingshaBlaze(param(byId, 1222101, 0, 0.25), param(byId, 1222101, 1, 0.1),
                        param(byId, 1222101, 2, 0.5), param(byId, 1222101, 3, 0.2)),
                new LingshaSmoke(param(byId, 1222102, 0, 10)),
                new LingshaRemnant(param(byId, 1222103, 0, 0.6), intParam(byId, 1222103, 1, 2)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new LingshaE1(param(e, 0, 0.2), param(e, 1, 0.5));
            case 2 -> new LingshaE2(param(e, 0, 0.4), intParam(e, 1, 3));
            case 4 -> new LingshaE4(param(e, 0, 0.4));
            case 6 -> new LingshaE6(param(e, 0, 0.2), intParam(e, 1, 4), param(e, 2, 0.5), param(e, 3, 5));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> LingshaKit::lingshaSkill;
            case 3 -> LingshaKit::lingshaUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技 识烟飞彩: 对敌方全体造成#1%伤害, 为我方全体回复#2%攻击力+#3生命, 使【浮元】行动提前#4。 */
    static void lingshaSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        if (!(user instanceof Character character)) {
            return;
        }
        double mult = ctx.firstParam();
        double healRatio = ctx.param(1, 0.1);
        double healFlat = ctx.param(2, 105);
        double advance = ctx.param(3, 0.2);
        for (Enemy enemy : battle.getAliveEnemies()) {
            ctx.dealDamage(battle, user, enemy, mult);
            battle.breakToughness(user, enemy, ctx.stanceAll());
        }
        healParty(battle, user, healRatio, healFlat);
        fuyuanAdvance(battle, character, advance);
    }

    /** 终结技 幔亭缭霞: 使敌方全体陷入【醇醉】状态 (击破伤害提高#4, 以易伤代理), 造成#1%伤害,
     *  为我方全体回复#2%攻击力+#3生命, 使【浮元】行动提前#6。 */
    static void lingshaUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        if (!(user instanceof Character character)) {
            return;
        }
        double mult = ctx.firstParam();
        double healRatio = ctx.param(1, 0.08);
        double healFlat = ctx.param(2, 90);
        double breakTaken = ctx.param(3, 0.15);
        int debuffTurns = ctx.intParam(4, 2);
        double advance = ctx.param(5, 0.15);
        for (Enemy enemy : battle.getAliveEnemies()) {
            enemy.removeBuff("醇醉");
            Buff drunk = new Buff("醇醉", Buff.Category.DEBUFF, user, enemy, debuffTurns)
                    .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(breakTaken,
                            DoubleValue.Modifier.ModifierSource.DEBUFF));
            battle.applyBuff(enemy, drunk);
            ctx.dealDamage(battle, user, enemy, mult);
            battle.breakToughness(user, enemy, ctx.stanceAll());
        }
        healParty(battle, user, healRatio, healFlat);
        fuyuanAdvance(battle, character, advance);
    }

    // ─── 【浮元】代理 (传统召唤物) ───────────────────────────────────────

    /** 首次施放战技时召唤【浮元】 (立即行动1次); 之后战技/终结技使【浮元】行动提前. */
    static void fuyuanAdvance(Battle battle, Character owner, double ratio) {
        if (!FUYUAN_ACTIVE.contains(owner)) {
            FUYUAN_ACTIVE.add(owner);
            FUYUAN_CHARGE.put(owner, 0.0);
            IO.println("  " + owner.getName() + " summons 【浮元】!");
            fuyuanAction(battle, owner);
            return;
        }
        double charge = FUYUAN_CHARGE.merge(owner, ratio, Double::sum);
        if (charge >= 1.0) {
            FUYUAN_CHARGE.put(owner, charge - 1.0);
            fuyuanAction(battle, owner);
        }
    }

    /** 【浮元】行动: 发动追加攻击, 对敌方全体造成#2%攻击力的火属性伤害, 额外对随机敌方单体造成#8%伤害,
     *  解除我方全体#6个负面效果, 并回复#3%攻击力+#4生命值。 */
    static void fuyuanAction(Battle battle, Character owner) {
        if (owner.isDeath()) {
            return;
        }
        List<Double> p = rawParams(owner, 4);
        double aoeMult = p.size() > 1 ? p.get(1) : 0.375;
        double extraMult = p.size() > 7 ? p.get(7) : 0.375;
        double healRatio = p.size() > 2 ? p.get(2) : 0.08;
        double healFlat = p.size() > 3 ? p.get(3) : 90;
        int cleanse = p.size() > 5 ? p.get(5).intValue() : 1;
        com.laosun.aluminium.models.DamageCalculator.DamageContext ctx =
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP, Element.FIRE);
        for (Enemy enemy : battle.getAliveEnemies()) {
            battle.dealAttackDamage(owner, enemy, aoeMult, 0, ctx);
            battle.breakToughness(owner, enemy, 1.0);
        }
        List<Enemy> alive = battle.getAliveEnemies();
        if (!alive.isEmpty()) {
            List<Enemy> fireWeak = alive.stream()
                    .filter(e -> !e.isBroken() && e.isWeakTo(Element.FIRE)).toList();
            List<Enemy> pool = fireWeak.isEmpty() ? alive : fireWeak;
            Enemy extra = pool.get((int) (Math.random() * pool.size()));
            battle.dealAttackDamage(owner, extra, extraMult, 0, ctx);
        }
        healParty(battle, owner, healRatio, healFlat);
        for (CanHit ally : battle.getAlivePlayerUnits()) {
            for (int i = 0; i < cleanse; i++) {
                ally.getBuffs().stream()
                        .filter(b -> b.getCategory() == Buff.Category.DEBUFF)
                        .findFirst()
                        .ifPresent(buff -> battle.removeBuff(ally, buff));
            }
        }
        // 星魂4: 浮元行动时为生命值最低的我方目标回复#1%攻击力的生命.
        for (Trace trace : owner.getTraces()) {
            if (trace instanceof LingshaE4 e4) {
                e4.healLowest(battle, owner);
            }
        }
        // 星魂6: 浮元攻击时额外造成#2次伤害, 每次#3%攻击力 + #4削韧值.
        for (Trace trace : owner.getTraces()) {
            if (trace instanceof LingshaE6 e6) {
                e6.extraHits(battle, owner, alive);
            }
        }
        IO.println("  【浮元】 attacks the enemy field!");
    }

    private static void healParty(Battle battle, CanHit user, double ratio, double flat) {
        double heal = user.getAttribute(AttributeType.ATTACK) != null
                ? user.getAttribute(AttributeType.ATTACK).get() * ratio + flat : flat;
        for (CanHit ally : battle.getAlivePlayerUnits()) {
            battle.healTarget(user, ally, heal);
        }
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

    /** 朱燎: 使自身攻击力/治疗量提高, 数值等同于击破特攻的#1/#2, 最多提高#3/#4。 */
    static class LingshaBlaze implements Trace {
        private final double atkRatio;
        private final double healRatio;
        private final double atkCap;
        private final double healCap;

        LingshaBlaze(double atkRatio, double healRatio, double atkCap, double healCap) {
            this.atkRatio = atkRatio;
            this.healRatio = healRatio;
            this.atkCap = atkCap;
            this.healCap = healCap;
        }

        @Override
        public String getName() {
            return "朱燎";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            double breakEffect = owner.getAttribute(AttributeType.BREAKING_EFFECT) != null
                    ? owner.getAttribute(AttributeType.BREAKING_EFFECT).get() : 0;
            double atkBonus = Math.min(atkCap, breakEffect * atkRatio);
            double healBonus = Math.min(healCap, breakEffect * healRatio);
            owner.removeBuff("朱燎");
            Buff buff = new Buff("朱燎", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atkBonus,
                            DoubleValue.Modifier.ModifierSource.BUFF))
                    .stat(AttributeType.OUTGOING_HEALING_BOOST, DoubleValue.Modifier.pure(healBonus,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
            IO.println("  [行迹] " + owner.getName() + " gains +"
                    + String.format("%.1f%%", atkBonus * 100) + " ATK / +"
                    + String.format("%.1f%%", healBonus * 100) + " healing (朱燎)");
        }
    }

    /** 兰烟: 施放普攻时额外恢复#1点能量。 */
    static class LingshaSmoke implements Trace {
        private final double energy;

        LingshaSmoke(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "兰烟";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.COMMON) {
                owner.gainEnergy(energy);
                IO.println("  [行迹] " + owner.getName() + " restores "
                        + String.format("%.0f", energy) + " extra energy (兰烟)");
            }
        }
    }

    /**
     * 遗爇: 【浮元】在场时, 我方任意角色受到伤害后, 若队伍中存在生命值百分比≤#1的角色,
     * 【浮元】立即对敌人发动天赋的追加攻击 (该效果在#2回合后可再次触发)。此处以队友行动钩子代理
     * (受到伤害无全局钩子, 近似为每次行动后检查生命值最低的队友)。
     */
    static class LingshaRemnant implements Trace {
        private final double hpThreshold;
        private final int cooldown;
        private int cooldownLeft = 0;

        LingshaRemnant(double hpThreshold, int cooldown) {
            this.hpThreshold = hpThreshold;
            this.cooldown = cooldown;
        }

        @Override
        public String getName() {
            return "遗爇";
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            if (cooldownLeft > 0) {
                cooldownLeft--;
            }
            if (!FUYUAN_ACTIVE.contains(owner)) {
                return;
            }
            // 【浮元】累计的行动次数 ≥ 1 时行动 (代理浮元的独立回合).
            double charge = FUYUAN_CHARGE.getOrDefault(owner, 0.0);
            if (charge >= 1.0) {
                FUYUAN_CHARGE.put(owner, charge - 1.0);
                fuyuanAction(battle, owner);
            }
        }

        @Override
        public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                 List<? extends CanHit> targets) {
            if (!FUYUAN_ACTIVE.contains(owner) || cooldownLeft > 0) {
                return;
            }
            CanHit lowest = battle.getAlivePlayerUnits().stream()
                    .min(java.util.Comparator.comparingDouble(CanHit::getHpPercent))
                    .orElse(null);
            if (lowest != null && lowest.getHpPercent() <= hpThreshold) {
                cooldownLeft = cooldown;
                fuyuanAction(battle, owner);
                IO.println("  [行迹] 【浮元】 follows up to protect the wounded (遗爇)");
            }
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 馨气当盛，除邪避讳: 灵砂的弱点击破效率提高#2; 敌方弱点被击破时防御力降低#1。 */
    static class LingshaE1 implements Trace {
        private final double defDown;
        private final double breakEfficiency;

        LingshaE1(double defDown, double breakEfficiency) {
            this.defDown = defDown;
            this.breakEfficiency = breakEfficiency;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            owner.removeBuff("馨气当盛");
            Buff buff = new Buff("馨气当盛", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.WEAKNESS_BREAK_EFFICIENCY, DoubleValue.Modifier.pure(breakEfficiency,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }

        @Override
        public void onEnemyBreak(Battle battle, Character owner, Enemy enemy) {
            enemy.removeBuff("馨气当盛");
            Buff debuff = new Buff("馨气当盛", Buff.Category.DEBUFF, owner, enemy, 2)
                    .stat(AttributeType.DEFENCE, DoubleValue.Modifier.addPercent(-defDown,
                            DoubleValue.Modifier.ModifierSource.DEBUFF));
            battle.applyBuff(enemy, debuff);
        }
    }

    /** 星魂2 欹枕垂云，烟奁红雪: 施放终结技时，使我方全体击破特攻提高#1，持续#2回合。 */
    static class LingshaE2 implements Trace {
        private final double breakBoost;
        private final int turns;

        LingshaE2(double breakBoost, int turns) {
            this.breakBoost = breakBoost;
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
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                ally.removeBuff("欹枕垂云");
                Buff buff = new Buff("欹枕垂云", Buff.Category.BUFF, owner, ally, turns)
                        .stat(AttributeType.BREAKING_EFFECT, DoubleValue.Modifier.pure(breakBoost,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
            IO.println("  [星魂] " + owner.getName() + ": party break effect +"
                    + String.format("%.0f%%", breakBoost * 100) + " (欹枕垂云)");
        }
    }

    /** 星魂4 横垂宝幄，半拂琼筵: 【浮元】行动时，为当前生命值最低的我方目标回复等同于灵砂#1%攻击力的生命值。 */
    static class LingshaE4 implements Trace {
        private final double healRatio;

        LingshaE4(double healRatio) {
            this.healRatio = healRatio;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        void healLowest(Battle battle, Character owner) {
            CanHit lowest = battle.getAlivePlayerUnits().stream()
                    .min(java.util.Comparator.comparingDouble(CanHit::getHpPercent))
                    .orElse(null);
            if (lowest == null) {
                return;
            }
            double heal = owner.getAttribute(AttributeType.ATTACK) != null
                    ? owner.getAttribute(AttributeType.ATTACK).get() * healRatio : 0;
            battle.healTarget(owner, lowest, heal);
        }
    }

    /** 星魂6 晓兰深藏，宿香暗贮: 【浮元】在场时使敌方全体全属性抗性降低#1 (以易伤代理);
     *  【浮元】攻击时额外造成#2次伤害, 每次#3%攻击力的火属性伤害和#4点削韧值。 */
    static class LingshaE6 implements Trace {
        private final double allResDown;
        private final int extraHits;
        private final double hitMult;
        private final double toughness;

        LingshaE6(double allResDown, int extraHits, double hitMult, double toughness) {
            this.allResDown = allResDown;
            this.extraHits = extraHits;
            this.hitMult = hitMult;
            this.toughness = toughness;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (Enemy enemy : battle.getAliveEnemies()) {
                enemy.removeBuff("晓兰深藏");
                Buff debuff = new Buff("晓兰深藏", Buff.Category.DEBUFF, owner, enemy, -1)
                        .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(allResDown,
                                DoubleValue.Modifier.ModifierSource.DEBUFF));
                battle.applyBuff(enemy, debuff);
            }
        }

        void extraHits(Battle battle, Character owner, List<Enemy> alive) {
            com.laosun.aluminium.models.DamageCalculator.DamageContext ctx =
                    com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                            com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP, Element.FIRE);
            for (int i = 0; i < extraHits; i++) {
                List<Enemy> pool = alive.stream().filter(e -> !e.isDeath()).toList();
                if (pool.isEmpty()) {
                    return;
                }
                List<Enemy> fireWeak = pool.stream()
                        .filter(e -> !e.isBroken() && e.isWeakTo(Element.FIRE)).toList();
                Enemy target = (fireWeak.isEmpty() ? pool : fireWeak)
                        .get((int) (Math.random() * (fireWeak.isEmpty() ? pool : fireWeak).size()));
                battle.dealAttackDamage(owner, target, hitMult, 0, ctx);
                battle.breakToughness(owner, target, toughness / 30.0);
            }
        }
    }
}
