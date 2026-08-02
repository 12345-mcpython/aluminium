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
import com.laosun.aluminium.models.Trace;

import java.util.List;
import java.util.Map;

import static com.laosun.aluminium.models.kit.KitSupport.intParam;
import static com.laosun.aluminium.models.kit.KitSupport.param;

/**
 * 白厄 (Phainon, cid 1408) — 物理属性 毁灭.
 */
public final class PhainonKit implements CharacterKit {

    @Override
    public int cid() {
        return 1408;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new PhainonEnd(intParam(byId, 1408101, 0, 3), intParam(byId, 1408101, 1, 1)),
                new PhainonTorch(param(byId, 1408102, 0, 0.45), intParam(byId, 1408102, 1, 4),
                        intParam(byId, 1408102, 2, 1)),
                new PhainonHero(param(byId, 1408103, 0, 0.5), intParam(byId, 1408103, 1, 2)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new PhainonE1(param(e, 0, 0.015), param(e, 1, 0.5), intParam(e, 2, 3),
                    param(e, 3, 0.84), param(e, 4, 0.66));
            case 2 -> new PhainonE2(intParam(e, 0, 4), param(e, 1, 0.2));
            case 4 -> new PhainonE4(intParam(e, 0, 4));
            case 6 -> new PhainonE6(param(e, 0, 0.36), intParam(e, 1, 6));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> PhainonKit::phainonSkill;
            case 3 -> PhainonKit::phainonUlt;
            case 8 -> PhainonKit::phainonEnhancedBasic;
            case 9 -> PhainonKit::phainonEnhancedSkill;
            case 11 -> PhainonKit::phainonStarColumn;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 获得#3点【火种】, 对主目标造成#1%伤害, 相邻目标#2%伤害. */
    static void phainonSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit main = targets.getFirst();
        double mainMult = ctx.firstParam();
        double sideMult = ctx.param(1, 0.6);
        int embers = ctx.intParam(2, 2);
        PhainonKit.addEmber(user, embers);
        ctx.dealDamage(battle, user, main, mainMult);
        battle.breakToughness(user, main, ctx.stanceSingle());
        for (Enemy enemy : battle.getAliveEnemies()) {
            if (enemy != main) {
                ctx.dealDamage(battle, user, enemy, sideMult);
                battle.breakToughness(user, enemy, ctx.stanceSpread());
            }
        }
        IO.println("  " + user.getName() + " gains " + embers + " 【火种】 ("
                + PhainonKit.ember(user) + "/12)");
    }

    /** 终结技: 变身为卡厄斯兰那 (境界【时墟铁墓】), 造成#1%全体伤害. */
    static void phainonUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        for (Enemy enemy : battle.getAliveEnemies()) {
            ctx.dealDamage(battle, user, enemy, multiplier / Math.max(1, battle.getAliveEnemies().size()));
            battle.breakToughness(user, enemy, ctx.stanceAll());
        }
        if (user instanceof Character character) {
            battle.enterEnhancedState(character);
        }
        user.removeBuff("火种");
        PhainonKit.addEmber(user, 0);
        IO.println("  " + user.getName() + " transforms into 卡厄斯兰那!");
    }

    /** 强化普攻 创生•血棘渡亡: 获得#3点【毁伤】, 造成#1/#2%伤害. */
    static void phainonEnhancedBasic(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit main = targets.getFirst();
        double mainMult = ctx.firstParam();
        double sideMult = ctx.param(1, 0.375);
        int wounds = ctx.intParam(2, 2);
        PhainonKit.addWound(user, wounds);
        ctx.dealDamage(battle, user, main, mainMult);
        battle.breakToughness(user, main, ctx.stanceSingle());
        for (Enemy enemy : battle.getAliveEnemies()) {
            if (enemy != main) {
                ctx.dealDamage(battle, user, enemy, sideMult);
                battle.breakToughness(user, enemy, ctx.stanceSpread());
            }
        }
    }

    /** 强化战技 灾厄•弑魂焚诏: 获得等同于敌方全体数量的【毁伤】, 使敌方全体立即行动后反击. */
    static void phainonEnhancedSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double counterMult = ctx.firstParam();
        int extraHits = ctx.intParam(2, 4);
        double hitMult = ctx.param(3, 0.15);
        int embers = battle.getAliveEnemies().size();
        PhainonKit.addEmber(user, embers);
        for (Enemy enemy : battle.getAliveEnemies()) {
            battle.dealAttackDamage(user, enemy, counterMult, 0,
                    com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                            com.laosun.aluminium.models.DamageCalculator.DamageType.SKILL, Element.PHYSICAL));
        }
        for (int i = 0; i < extraHits; i++) {
            List<Enemy> alive = battle.getAliveEnemies();
            if (alive.isEmpty()) {
                return;
            }
            battle.dealAttackDamage(user, alive.get((int) (Math.random() * alive.size())), hitMult, 0,
                    com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                            com.laosun.aluminium.models.DamageCalculator.DamageType.SKILL, Element.PHYSICAL));
        }
        IO.println("  " + user.getName() + " 弑魂焚诏 counters the enemies!");
    }

    /** 支柱•死星天裁: 每消耗1点【毁伤】造成#3次#2%伤害, 消耗#4点时额外造成#1%全体伤害. */
    static void phainonStarColumn(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double extraMult = ctx.firstParam();
        double hitMult = ctx.param(1, 0.225);
        int hitsPerWound = ctx.intParam(2, 4);
        int costThreshold = ctx.intParam(3, 4);
        double totalMult = ctx.param(4, 5.85);
        int wounds = PhainonKit.wound(user);
        for (int i = 0; i < wounds * hitsPerWound; i++) {
            List<Enemy> alive = battle.getAliveEnemies();
            if (alive.isEmpty()) {
                return;
            }
            battle.dealAttackDamage(user, alive.get((int) (Math.random() * alive.size())), hitMult, 0,
                    com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                            com.laosun.aluminium.models.DamageCalculator.DamageType.ULTRA, Element.PHYSICAL));
        }
        if (wounds >= costThreshold) {
            for (Enemy enemy : battle.getAliveEnemies()) {
                battle.dealAttackDamage(user, enemy, extraMult, 0,
                        com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                com.laosun.aluminium.models.DamageCalculator.DamageType.ULTRA, Element.PHYSICAL));
            }
        }
        PhainonKit.resetWound(user);
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 行向世界终点: 战斗开始时获得1点【火种】, 变身结束时获得3点【火种】。 */
    static class PhainonEnd implements Trace {
        private final int onTransformEnd;
        private final int onBattleStart;

        PhainonEnd(int onTransformEnd, int onBattleStart) {
            this.onTransformEnd = onTransformEnd;
            this.onBattleStart = onBattleStart;
        }

        @Override
        public String getName() {
            return "行向世界终点";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            PhainonKit.addEmber(owner, onBattleStart);
            IO.println("  [行迹] " + owner.getName() + " starts with " + onBattleStart + " 【火种】");
        }
    }

    /** 身承炎炬万千: 受到队友提供的治疗效果或护盾时, 造成的伤害提高45%持续4回合。 */
    static class PhainonTorch implements Trace {
        private final double dmgBoost;
        private final int turns;
        private final int ember;

        PhainonTorch(double dmgBoost, int turns, int ember) {
            this.dmgBoost = dmgBoost;
            this.turns = turns;
            this.ember = ember;
        }

        @Override
        public String getName() {
            return "身承炎炬万千";
        }
    }

    /** 照见英雄本色: 进入战斗或变身结束时, 攻击力提高50%, 最多2层。 */
    static class PhainonHero implements Trace {
        private final double atkBonus;
        private final int maxStacks;
        private int stacks = 0;

        PhainonHero(double atkBonus, int maxStacks) {
            this.atkBonus = atkBonus;
            this.maxStacks = maxStacks;
        }

        @Override
        public String getName() {
            return "照见英雄本色";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            stacks = Math.min(maxStacks, stacks + 1);
            refresh(battle, owner);
        }

        private void refresh(Battle battle, Character owner) {
            owner.removeBuff("照见英雄本色");
            Buff buff = new Buff("照见英雄本色", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atkBonus * stacks,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 火与光，善恶的化身: 施放终结技时暴击伤害提高50%持续3回合。 */
    static class PhainonE1 implements Trace {
        private final double speedBonus;
        private final double cdmg;
        private final int turns;
        private final double cap;
        private final double inheritRatio;

        PhainonE1(double speedBonus, double cdmg, int turns, double cap, double inheritRatio) {
            this.speedBonus = speedBonus;
            this.cdmg = cdmg;
            this.turns = turns;
            this.cap = cap;
            this.inheritRatio = inheritRatio;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.ULTRA) {
                return;
            }
            owner.removeBuff("星魂1");
            Buff buff = new Buff("星魂1", Buff.Category.BUFF, owner, owner, turns)
                    .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(cdmg,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 星魂2 天与地，世间的泡沫: 物理属性抗性穿透提高20%。 */
    static class PhainonE2 implements Trace {
        private final int woundsForTurn;
        private final double pen;

        PhainonE2(int woundsForTurn, double pen) {
            this.woundsForTurn = woundsForTurn;
            this.pen = pen;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("星魂2", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.RESISTANCE_PENETRATION, DoubleValue.Modifier.pure(pen,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 星魂4 多少泰坦面目模糊: 施放【灾厄•弑魂焚诏】时额外获得4层【弑魂之炽】。 */
    static class PhainonE4 implements Trace {
        private final int extraEmbers;

        PhainonE4(int extraEmbers) {
            this.extraEmbers = extraEmbers;
        }

        @Override
        public String getName() {
            return "星魂4";
        }
    }

    /** 星魂6 亘古长升，蚀火残阳: 战斗开始时获得6点【火种】。 */
    static class PhainonE6 implements Trace {
        private final double trueDamageRatio;
        private final int startEmbers;

        PhainonE6(double trueDamageRatio, int startEmbers) {
            this.trueDamageRatio = trueDamageRatio;
            this.startEmbers = startEmbers;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            PhainonKit.addEmber(owner, startEmbers);
        }
    }

    // ─── 【火种】/【毁伤】代理 (天赋 此身为炬 / 命运•此躯即神) ──────────

    private static final java.util.Map<CanHit, Integer> EMBER = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<CanHit, Integer> WOUND = new java.util.concurrent.ConcurrentHashMap<>();

    static int ember(CanHit owner) {
        return EMBER.getOrDefault(owner, 0);
    }

    static void addEmber(CanHit owner, int amount) {
        EMBER.put(owner, Math.min(12, ember(owner) + amount));
    }

    static int wound(CanHit owner) {
        return WOUND.getOrDefault(owner, 0);
    }

    static void addWound(CanHit owner, int amount) {
        WOUND.put(owner, Math.min(12, wound(owner) + amount));
    }

    static void resetWound(CanHit owner) {
        WOUND.put(owner, 0);
    }
}
