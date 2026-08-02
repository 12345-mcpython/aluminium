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
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.SkillData;
import com.laosun.aluminium.models.Trace;

import java.util.List;
import java.util.Map;

import static com.laosun.aluminium.models.kit.KitSupport.intParam;
import static com.laosun.aluminium.models.kit.KitSupport.param;

/**
 * 刃 (Blade, cid 1205) — 风属性 毁灭.
 */
public final class BladeKit implements CharacterKit {

    @Override
    public int cid() {
        return 1205;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new BladeInfinite(param(byId, 1205101, 0, 0.2)),
                new BladeEndure(param(byId, 1205102, 0, 0.05), param(byId, 1205102, 1, 100)),
                new BladeDestruction(param(byId, 1205103, 0, 0.2)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new BladeE1(param(e, 0, 1.5), param(e, 1, 0.9));
            case 2 -> new BladeE2(param(e, 0, 0.15));
            case 4 -> new BladeE4(param(e, 0, 0.2), intParam(e, 1, 2));
            case 6 -> new BladeE6(param(e, 0, 0.5));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> BladeKit::bladeSkill;
            case 3 -> BladeKit::bladeUlt;
            case 8 -> BladeKit::bladeEnhancedBasic;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /**
     * 战技: 消耗等同于生命上限30%的生命值 (不足时降至1点), 进入【地狱变】状态,
     * 造成伤害提高12%, 持续3回合。引擎在战技 (Enhance) 执行后自动进入强化状态
     * (普攻 → 无间剑树)。
     */
    static void bladeSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double hpCostRatio = ctx.firstParam();
        int turns = ctx.intParam(1, 3);
        double dmgBoost = ctx.param(3, 0.12);
        double cost = Math.min(user.getCurrentHp() - 1, user.getMaxHp() * hpCostRatio);
        if (cost > 0) {
            user.takeDamage(cost);
            addLostLife(user, cost);
        }
        user.removeBuff("地狱变");
        Buff buff = new Buff("地狱变", Buff.Category.BUFF, user, user, turns)
                .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(dmgBoost,
                        DoubleValue.Modifier.ModifierSource.BUFF));
        battle.applyBuff(user, buff);
        IO.println("  " + user.getName() + " enters 【地狱变】 (HP cost "
                + String.format("%.0f", user.getMaxHp() * hpCostRatio) + ", damage +"
                + String.format("%.0f%%", dmgBoost * 100) + ")");
    }

    /**
     * 终结技: 将当前生命值转化为生命上限的50%, 对敌方单体及相邻目标造成
     * 攻击力+生命上限+本场战斗累计已损失生命值 混合比例的风属性伤害,
     * 施放后清空累计已损失生命值。
     */
    static void bladeUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double atkMain = ctx.param(0, 0.24);
        double hpMain = ctx.param(1, 0.6);
        double atkSide = ctx.param(2, 0.096);
        double hpSide = ctx.param(3, 0.24);
        double lostMain = ctx.param(4, 0.6);
        double lostSide = ctx.param(5, 0.24);
        // 当前生命值转化为生命上限的50%.
        double targetHp = user.getMaxHp() * 0.5;
        double current = user.getCurrentHp();
        if (current > targetHp) {
            double converted = current - targetHp;
            user.takeDamage(converted);
            addLostLife(user, converted);
        } else if (current < targetHp) {
            user.heal(targetHp - current);
        }
        double atk = user.getAttribute(AttributeType.ATTACK) != null
                ? user.getAttribute(AttributeType.ATTACK).get() : 0;
        double maxHp = user.getMaxHp();
        BladeDestruction destruction = findTrace(user, BladeDestruction.class);
        double lost = destruction != null ? destruction.consumeLostLife() : 0;
        // 星魂1: 对指定敌方单体造成的伤害额外提高 150%×累计已损失生命值.
        BladeE1 e1 = findTrace(user, BladeE1.class);
        double lostE1 = e1 != null ? lost * e1.ratio : 0;
        CanHit main = targets.getFirst();
        battle.dealAttackDamageBase(user, main, atk * atkMain + maxHp * hpMain + lost * lostMain + lostE1,
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.ULTRA, Element.WIND));
        battle.breakToughness(user, main, ctx.stanceSingle());
        for (Enemy enemy : battle.getAliveEnemies()) {
            if (enemy != main) {
                battle.dealAttackDamageBase(user, enemy,
                        atk * atkSide + maxHp * hpSide + lost * lostSide,
                        com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                com.laosun.aluminium.models.DamageCalculator.DamageType.ULTRA, Element.WIND));
                battle.breakToughness(user, enemy, ctx.stanceSpread());
            }
        }
        IO.println("  " + user.getName() + " HP set to 50% and casts 大辟万死"
                + (e1 != null ? " (+" + String.format("%.0f", lostE1) + " lost-life DMG)" : ""));
    }

    /**
     * 强化普攻 无间剑树: 消耗等同于生命上限10%的生命值 (不足时降至1点),
     * 对敌方单体及相邻目标造成 攻击力+生命上限 混合比例的风属性伤害。
     */
    static void bladeEnhancedBasic(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double hpCostRatio = ctx.param(0, 0.1);
        double atkMain = ctx.param(1, 0.2);
        double atkSide = ctx.param(2, 0.08);
        double hpMain = ctx.param(3, 0.5);
        double hpSide = ctx.param(4, 0.2);
        double cost = Math.min(user.getCurrentHp() - 1, user.getMaxHp() * hpCostRatio);
        if (cost > 0) {
            user.takeDamage(cost);
            addLostLife(user, cost);
        }
        double atk = user.getAttribute(AttributeType.ATTACK) != null
                ? user.getAttribute(AttributeType.ATTACK).get() : 0;
        double maxHp = user.getMaxHp();
        CanHit main = targets.getFirst();
        battle.dealAttackDamageBase(user, main, atk * atkMain + maxHp * hpMain,
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, Element.WIND));
        battle.breakToughness(user, main, ctx.stanceSingle());
        for (Enemy enemy : battle.getAliveEnemies()) {
            if (enemy != main) {
                battle.dealAttackDamageBase(user, enemy, atk * atkSide + maxHp * hpSide,
                        com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                com.laosun.aluminium.models.DamageCalculator.DamageType.NORMAL, Element.WIND));
                battle.breakToughness(user, enemy, ctx.stanceSpread());
            }
        }
        IO.println("  " + user.getName() + " uses 无间剑树 (HP cost "
                + String.format("%.0f", user.getMaxHp() * hpCostRatio) + ")");
    }

    /** 将自身消耗的生命值计入本场战斗累计已损失生命值 (上限为生命上限的90%). */
    private static void addLostLife(CanHit user, double amount) {
        if (!(user instanceof Character character) || amount <= 0) {
            return;
        }
        BladeDestruction destruction = findTrace(character, BladeDestruction.class);
        if (destruction != null) {
            destruction.addLostLife(amount);
        }
    }

    private static <T extends Trace> T findTrace(CanHit user, Class<T> type) {
        if (!(user instanceof Character character)) {
            return null;
        }
        for (Trace trace : character.getTraces()) {
            if (type.isInstance(trace)) {
                return type.cast(trace);
            }
        }
        return null;
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 无尽形寿: 当前生命值≤50%时，受到治疗时的回复量提高20%。 */
    static class BladeInfinite implements Trace {
        private final double healBoost;

        BladeInfinite(double healBoost) {
            this.healBoost = healBoost;
        }

        @Override
        public String getName() {
            return "无尽形寿";
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            if (owner.getHpPercent() > 0.5) {
                return;
            }
            owner.removeBuff("无尽形寿");
            Buff buff = new Buff("无尽形寿", Buff.Category.BUFF, owner, owner, 1)
                    .stat(AttributeType.HEAL_TAKEN_RATIO, DoubleValue.Modifier.multiplyPercent(healBoost,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
            IO.println("  [行迹] " + owner.getName() + " healing +"
                    + String.format("%.0f%%", healBoost * 100) + " (无尽形寿)");
        }
    }

    /** 吞忍百死: 施放【无间剑树】后，若击中处于弱点击破状态的敌方目标，回复生命上限5%+100。 */
    static class BladeEndure implements Trace {
        private final double hpRatio;
        private final double flat;

        BladeEndure(double hpRatio, double flat) {
            this.hpRatio = hpRatio;
            this.flat = flat;
        }

        @Override
        public String getName() {
            return "吞忍百死";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.COMMON || !owner.isEnhanced()) {
                return;
            }
            boolean hitBroken = targets != null && targets.stream()
                    .anyMatch(t -> t instanceof Enemy enemy && enemy.isBroken());
            if (hitBroken) {
                battle.healTarget(owner, owner, owner.getMaxHp() * hpRatio + flat);
                IO.println("  [行迹] " + owner.getName() + " recovers HP (broken target hit, 吞忍百死)");
            }
        }
    }

    /**
     * 坏劫隳亡: 天赋施放的追加攻击伤害提高20%。
     * 同时代理天赋 倏忽恩赐: 受到伤害或消耗生命值时获得1层充能 (最多5层, 星魂6 为4层),
     * 充能叠满时立即对敌方全体施放1次追加攻击 (攻击力22%+生命上限55%的风属性伤害),
     * 并回复生命上限25%的生命值; 并累计本场战斗已损失生命值供终结技使用。
     */
    static class BladeDestruction implements Trace {
        private final double followUpBonus;
        private int charge = 0;
        private int chargeCap = 5;
        private double lostLife = 0;

        BladeDestruction(double followUpBonus) {
            this.followUpBonus = followUpBonus;
        }

        @Override
        public String getName() {
            return "坏劫隳亡";
        }

        @Override
        public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
            if (owner.isDeath()) {
                return;
            }
            // 本场战斗累计已损失生命值 (上限为生命上限的90%).
            lostLife = Math.min(owner.getMaxHp() * 0.9, lostLife + damage);
            // 充能: 每次受到攻击最多叠加1层.
            charge++;
            int cap = chargeCap;
            for (Trace trace : owner.getTraces()) {
                if (trace instanceof BladeE6) {
                    cap = 4;
                }
            }
            if (charge >= cap) {
                charge = 0;
                followUp(battle, owner);
            }
        }

        /** 充能叠满: 对敌方全体施放追加攻击并回复生命值. */
        private void followUp(Battle battle, Character owner) {
            double atkRatio = 0.22;
            double hpRatio = 0.55;
            double healRatio = 0.25;
            SkillData talent = KitSupport.skillData(owner, SkillType.TALENT);
            if (talent != null && talent.getSkills() != null && !talent.getSkills().isEmpty()) {
                List<Double> params = talent.getSkills().getFirst();
                if (params.size() > 1) {
                    atkRatio = params.get(1);
                }
                if (params.size() > 2) {
                    healRatio = params.get(2);
                }
                if (params.size() > 3) {
                    hpRatio = params.get(3);
                }
            }
            double atk = owner.getAttribute(AttributeType.ATTACK) != null
                    ? owner.getAttribute(AttributeType.ATTACK).get() : 0;
            // 星魂6: 天赋追加攻击造成的伤害额外提高 50%×生命上限.
            double e6Extra = 0;
            for (Trace trace : owner.getTraces()) {
                if (trace instanceof BladeE6 e6) {
                    e6Extra = owner.getMaxHp() * e6.extraRatio;
                }
            }
            double base = (atk * atkRatio + owner.getMaxHp() * hpRatio + e6Extra) * (1 + followUpBonus);
            IO.println("  [行迹] " + owner.getName() + "'s charge is full: talent follow-up!");
            for (Enemy enemy : battle.getAliveEnemies()) {
                battle.dealAttackDamageBase(owner, enemy, base,
                        com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP, Element.WIND));
                battle.breakToughness(owner, enemy, 1.0);
            }
            battle.healTarget(owner, owner, owner.getMaxHp() * healRatio);
        }

        /** 累计已损失生命值 (供终结技与星魂1使用). */
        void addLostLife(double amount) {
            lostLife += amount;
        }

        /** 终结技消耗并清空累计已损失生命值. */
        double consumeLostLife() {
            double value = lostLife;
            lostLife = 0;
            return value;
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /**
     * 星魂1 剑录大限，地狱变相: 终结技对指定敌方单体造成的伤害额外提高,
     * 数值等同于刃150%本场战斗中累计的已损失生命值 (施放终结技后清空)。
     * 累计值由行迹 坏劫隳亡 代理, 此处仅提供倍率。
     */
    static class BladeE1 implements Trace {
        private final double ratio;
        @SuppressWarnings("unused")
        private final double capRatio;

        BladeE1(double ratio, double capRatio) {
            this.ratio = ratio;
            this.capRatio = capRatio;
        }

        @Override
        public String getName() {
            return "星魂1";
        }
    }

    /** 星魂2 支离旧梦，万端遗恨: 刃处于【地狱变】状态时，暴击率提高15%。 */
    static class BladeE2 implements Trace {
        private final double critChance;

        BladeE2(double critChance) {
            this.critChance = critChance;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public double critChanceBonus(Battle battle, Character owner, CanHit defender, SkillType type) {
            return owner.hasBuffNamed("地狱变") ? critChance : 0;
        }
    }

    /** 星魂4 泉台歧路，百骸回春: 生命百分比从>50%降至≤50%时, 生命上限提高20%, 最多叠加2层。 */
    static class BladeE4 implements Trace {
        private final double hpBoost;
        private final int maxStacks;
        private int stacks = 0;
        private double lastHpPercent = 1.0;

        BladeE4(double hpBoost, int maxStacks) {
            this.hpBoost = hpBoost;
            this.maxStacks = maxStacks;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            lastHpPercent = owner.getHpPercent();
        }

        @Override
        public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
            if (owner.isDeath()) {
                return;
            }
            double now = owner.getHpPercent();
            if (lastHpPercent > 0.5 && now <= 0.5 && stacks < maxStacks) {
                stacks++;
                owner.removeBuff("百骸回春");
                Buff buff = new Buff("百骸回春", Buff.Category.BUFF, owner, owner, -1)
                        .stat(AttributeType.HEALTH, DoubleValue.Modifier.addPercent(hpBoost * stacks,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(owner, buff);
                IO.println("  [星魂] " + owner.getName() + " max HP +"
                        + String.format("%.0f%%", hpBoost * stacks * 100) + " (" + stacks + "/" + maxStacks + ")");
            }
            lastHpPercent = now;
        }
    }

    /**
     * 星魂6 命留魂销，复返此身: 充能层数上限降低至4层, 天赋追加攻击伤害额外提高
     * 生命上限50% (由行迹 坏劫隳亡 在充能/追加攻击时读取).
     */
    static class BladeE6 implements Trace {
        private final double extraRatio;

        BladeE6(double extraRatio) {
            this.extraRatio = extraRatio;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            IO.println("  [星魂] " + owner.getName() + ": charge cap 4, talent follow-up +"
                    + String.format("%.0f%%", extraRatio * 100) + " max HP");
        }
    }
}
