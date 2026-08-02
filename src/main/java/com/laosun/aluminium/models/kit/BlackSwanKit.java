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
 * 黑天鹅 (Black Swan, cid 1307) — 风属性 虚无.
 */
public final class BlackSwanKit implements CharacterKit {

    @Override
    public int cid() {
        return 1307;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new BlackSwanRestless(param(byId, 1307101, 0, 0.65)),
                new BlackSwanDregs(param(byId, 1307102, 0, 0.65), intParam(byId, 1307102, 1, 3)),
                new BlackSwanOmen(param(byId, 1307103, 0, 0.6), param(byId, 1307103, 1, 0.72)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new BlackSwanE1(param(e, 0, 0.25));
            case 2 -> new BlackSwanE2(param(e, 0, 1.0), intParam(e, 1, 6));
            case 4 -> new BlackSwanE4(param(e, 0, 0.1), param(e, 1, 8));
            case 6 -> new BlackSwanE6(param(e, 0, 0.5), param(e, 1, 0.65), intParam(e, 2, 1));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 1 -> BlackSwanKit::blackSwanBasic;
            case 2 -> BlackSwanKit::blackSwanSkill;
            case 3 -> BlackSwanKit::blackSwanUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 普攻: 造成#1%伤害, 有#2基础概率使目标陷入1层【奥迹】. */
    static void blackSwanBasic(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit target = targets.getFirst();
        double multiplier = ctx.firstParam();
        double chance = ctx.param(1, 0.5);
        ctx.dealDamage(battle, user, target, multiplier);
        battle.breakToughness(user, target, ctx.stanceSingle());
        applyArcana(battle, user, target, chance, 1);
    }

    /** 战技: 对目标及相邻目标造成#1%伤害, 有#2概率使其陷入1层【奥迹】,
     *  有#3概率使其防御力降低#4, 持续#5回合. */
    static void blackSwanSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        double arcanaChance = ctx.param(1, 1.0);
        double defChance = ctx.param(2, 1.0);
        double defDown = ctx.param(3, 0.148);
        int turns = ctx.intParam(4, 3);
        CanHit main = targets.getFirst();
        ctx.dealDamage(battle, user, main, multiplier);
        battle.breakToughness(user, main, ctx.stanceSingle());
        applyArcana(battle, user, main, arcanaChance, 1);
        if (!main.isDeath() && battle.checkEffectHit(user, main, defChance)) {
            Buff debuff = new Buff("防御力降低", Buff.Category.DEBUFF, user, main, turns)
                    .stat(AttributeType.DEFENCE, DoubleValue.Modifier.addPercent(-defDown,
                            DoubleValue.Modifier.ModifierSource.DEBUFF));
            battle.applyBuff(main, debuff);
        }
        for (Enemy enemy : battle.getAliveEnemies()) {
            if (enemy != main) {
                ctx.dealDamage(battle, user, enemy, multiplier);
                battle.breakToughness(user, enemy, ctx.stanceSpread());
                applyArcana(battle, user, enemy, arcanaChance, 1);
                if (!enemy.isDeath() && battle.checkEffectHit(user, enemy, defChance)) {
                    Buff debuff = new Buff("防御力降低", Buff.Category.DEBUFF, user, enemy, turns)
                            .stat(AttributeType.DEFENCE, DoubleValue.Modifier.addPercent(-defDown,
                                    DoubleValue.Modifier.ModifierSource.DEBUFF));
                    battle.applyBuff(enemy, debuff);
                }
            }
        }
    }

    /** 终结技: 使敌方全体陷入【揭露】#2回合 (受到的伤害提高#3), 并造成#1%伤害. */
    static void blackSwanUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        int turns = ctx.intParam(1, 2);
        double vuln = ctx.param(2, 0.15);
        for (Enemy enemy : battle.getAliveEnemies()) {
            enemy.removeBuff("揭露");
            Buff debuff = new Buff("揭露", Buff.Category.DEBUFF, user, enemy, turns)
                    .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(vuln,
                            DoubleValue.Modifier.ModifierSource.DEBUFF));
            battle.applyBuff(enemy, debuff);
            ctx.dealDamage(battle, user, enemy, multiplier);
            battle.breakToughness(user, enemy, ctx.stanceAll());
        }
        IO.println("  " + user.getName() + " reveals the enemies (揭露)!");
    }

    // ─── 【奥迹】层数代理 (天赋 无端命运的机杼) ──────────────────────────

    private static final java.util.Map<CanHit, Integer> ARCANA = new java.util.concurrent.ConcurrentHashMap<>();

    static int arcanaStacks(CanHit target) {
        return ARCANA.getOrDefault(target, 0);
    }

    static void addArcana(CanHit target, int amount) {
        int cap = 50;
        ARCANA.put(target, Math.min(cap, arcanaStacks(target) + amount));
    }

    static void resetArcana(CanHit target) {
        ARCANA.put(target, 1);
    }

    static void applyArcana(Battle battle, CanHit user, CanHit target, double chance, int amount) {
        if (target.isDeath() || !(target instanceof Enemy)) {
            return;
        }
        if (battle.checkEffectHit(user, target, chance)) {
            addArcana(target, amount);
            IO.println("  " + target.getName() + " gains " + amount + " 【奥迹】 ("
                    + arcanaStacks(target) + " stacks)");
        }
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 脏中躁动: 施放战技攻击陷入风化/裂伤/灼烧/触电的目标后, 各有65%基础概率额外陷入1层【奥迹】。 */
    static class BlackSwanRestless implements Trace {
        private final double chance;

        BlackSwanRestless(double chance) {
            this.chance = chance;
        }

        @Override
        public String getName() {
            return "脏中躁动";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL) {
                return;
            }
            for (CanHit target : targets) {
                if (target.isDeath()) {
                    continue;
                }
                boolean dotHit = target.hasDotOfElement(Element.WIND)
                        || target.hasDotOfElement(Element.PHYSICAL)
                        || target.hasDotOfElement(Element.FIRE)
                        || target.hasDotOfElement(Element.THUNDER);
                if (dotHit) {
                    applyArcana(battle, owner, target, chance, 1);
                }
            }
        }
    }

    /** 杯底端倪: 敌方目标进入战斗时有65%基础概率陷入1层【奥迹】;
     *  敌方目标每次受到持续伤害时有65%基础概率陷入1层【奥迹】。 */
    static class BlackSwanDregs implements Trace {
        private final double chance;
        private final int perAttackCap;

        BlackSwanDregs(double chance, int perAttackCap) {
            this.chance = chance;
            this.perAttackCap = perAttackCap;
        }

        @Override
        public String getName() {
            return "杯底端倪";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (Enemy enemy : battle.getAliveEnemies()) {
                applyArcana(battle, owner, enemy, chance, 1);
            }
        }

        @Override
        public void onDotDamage(Battle battle, Character owner, CanHit victim, double damage) {
            if (victim instanceof Enemy) {
                applyArcana(battle, owner, victim, chance, 1);
            }
        }
    }

    /** 烛影朕兆: 造成的伤害提高, 数值等同于效果命中的60%, 最多提高72%。 */
    static class BlackSwanOmen implements Trace {
        private final double ratio;
        private final double cap;

        BlackSwanOmen(double ratio, double cap) {
            this.ratio = ratio;
            this.cap = cap;
        }

        @Override
        public String getName() {
            return "烛影朕兆";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            double ehr = owner.getAttribute(AttributeType.EFFECT_HIT_RATE) != null
                    ? owner.getAttribute(AttributeType.EFFECT_HIT_RATE).get() : 0;
            return 1 + Math.min(cap, ehr * ratio);
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 三磅石，七阶柱: 处于风化/裂伤/灼烧/触电状态的目标对应抗性降低25%。 */
    static class BlackSwanE1 implements Trace {
        private final double resDown;

        BlackSwanE1(double resDown) {
            this.resDown = resDown;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (Enemy enemy : battle.getAliveEnemies()) {
                Buff buff = new Buff("星魂1", Buff.Category.DEBUFF, owner, enemy, -1)
                        .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(resDown,
                                DoubleValue.Modifier.ModifierSource.DEBUFF));
                battle.applyBuff(enemy, buff);
            }
        }
    }

    /** 星魂2 羔羊，请勿为我恸哭: 【奥迹】目标被消灭时, 100%基础概率使相邻目标陷入6层【奥迹】。 */
    static class BlackSwanE2 implements Trace {
        private final double chance;
        private final int stacks;

        BlackSwanE2(double chance, int stacks) {
            this.chance = chance;
            this.stacks = stacks;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void onKill(Battle battle, Character owner, CanHit victim) {
            if (!(victim instanceof Enemy) || arcanaStacks(victim) <= 0) {
                return;
            }
            for (Enemy enemy : battle.getAliveEnemies()) {
                applyArcana(battle, owner, enemy, chance, stacks);
            }
        }
    }

    /** 星魂4 泪水，亦是礼物: 【揭露】状态下敌方效果抵抗降低10%, 且每回合开始时恢复8点能量。 */
    static class BlackSwanE4 implements Trace {
        private final double resDown;
        private final double energy;

        BlackSwanE4(double resDown, double energy) {
            this.resDown = resDown;
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            boolean anyRevealed = battle.getAliveEnemies().stream()
                    .anyMatch(e -> e.hasBuffNamed("揭露"));
            if (anyRevealed) {
                owner.gainEnergy(energy);
                IO.println("  [星魂] " + owner.getName() + " restores "
                        + String.format("%.0f", energy) + " energy (揭露)");
            }
        }
    }

    /** 星魂6 万神皆善，苦役者未知: 队友攻击时, 65%基础概率使目标陷入1层【奥迹】;
     *  使目标陷入【奥迹】时有50%固定概率使层数额外提高1层。 */
    static class BlackSwanE6 implements Trace {
        private final double extraChance;
        private final double applyChance;
        private final int extraStacks;

        BlackSwanE6(double extraChance, double applyChance, int extraStacks) {
            this.extraChance = extraChance;
            this.applyChance = applyChance;
            this.extraStacks = extraStacks;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                 List<? extends CanHit> targets) {
            if (actor == owner || targets == null || targets.isEmpty()) {
                return;
            }
            CanHit target = targets.getFirst();
            if (target.isDeath() || !(target instanceof Enemy)) {
                return;
            }
            int amount = 1;
            if (Math.random() < extraChance) {
                amount += extraStacks;
            }
            applyArcana(battle, owner, target, applyChance, amount);
        }
    }
}
