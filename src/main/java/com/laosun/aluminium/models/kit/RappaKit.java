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
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.Trace;

import java.util.List;
import java.util.Map;

import static com.laosun.aluminium.models.kit.KitSupport.intParam;
import static com.laosun.aluminium.models.kit.KitSupport.param;

/**
 * 乱破 (Rappa, cid 1317) — 虚数属性 智识.
 */
public final class RappaKit implements CharacterKit {

    @Override
    public int cid() {
        return 1317;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new RappaMagicHeaven(param(byId, 1317101, 0, 10), intParam(byId, 1317101, 1, 1)),
                new RappaSea(param(byId, 1317102, 0, 0.6)),
                new RappaLeaf(param(byId, 1317103, 0, 0.02), param(byId, 1317103, 1, 2400),
                        param(byId, 1317103, 2, 0.01), param(byId, 1317103, 3, 0.08),
                        intParam(byId, 1317103, 4, 2)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new RappaE1(param(e, 0, 0.15), param(e, 1, 20));
            case 2 -> new RappaE2(param(e, 0, 0.5));
            case 4 -> new RappaE4(param(e, 0, 0.12));
            case 6 -> new RappaE6(intParam(e, 0, 5), intParam(e, 1, 5));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 8 -> RappaKit::rappaEnhancedBasic;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 强化普攻 忍具•降魔花弁: 前2段对主目标及相邻目标造成#1/#2%伤害, 第3段对全体造成#3%伤害。 */
    static void rappaEnhancedBasic(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit main = targets.getFirst();
        double mainMult = ctx.firstParam();
        double sideMult = ctx.param(1, 0.3);
        double aoeMult = ctx.param(2, 0.6);
        // 前2段: 主目标 + 相邻.
        for (int i = 0; i < 2; i++) {
            if (main.isDeath()) {
                break;
            }
            ctx.dealDamage(battle, user, main, mainMult);
            battle.breakToughness(user, main, ctx.stanceSingle() / 3);
            for (Enemy enemy : battle.getAliveEnemies()) {
                if (enemy != main) {
                    ctx.dealDamage(battle, user, enemy, sideMult);
                    battle.breakToughness(user, enemy, ctx.stanceSpread() / 3);
                }
            }
        }
        // 第3段: 全体.
        if (!main.isDeath()) {
            for (Enemy enemy : battle.getAliveEnemies()) {
                ctx.dealDamage(battle, user, enemy, aoeMult);
                battle.breakToughness(user, enemy, ctx.stanceAll() / 3);
            }
        }
        // 消耗1点【彩墨】, 耗尽时退出【结印】状态.
        int ink = RappaKit.consumeInk(user);
        if (ink <= 0 && user instanceof Character character && character.isEnhanced()) {
            battle.exitEnhancedState(character);
            IO.println("  " + user.getName() + " exits 结印 (no ink left)");
        }
        IO.println("  " + user.getName() + " 降魔花弁: " + ink + " ink left");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 忍法帖•魔天: 精英及以上敌方目标弱点被击破时, 额外获得1点充能并恢复10点能量。 */
    static class RappaMagicHeaven implements Trace {
        private final double energy;
        private final int charge;

        RappaMagicHeaven(double energy, int charge) {
            this.energy = energy;
            this.charge = charge;
        }

        @Override
        public String getName() {
            return "忍法帖•魔天";
        }

        @Override
        public void onEnemyBreak(Battle battle, Character owner, Enemy enemy) {
            RappaKit.addCharge(owner, charge);
            owner.gainEnergy(energy);
            IO.println("  [行迹] " + owner.getName() + " gains " + charge + " charge, +"
                    + String.format("%.0f", energy) + " energy (魔天)");
        }
    }

    /** 忍法帖•海鸣: 【结印】状态期间, 强化普攻对弱点击破状态目标造成伤害后, 将削韧值转化为60%超击破伤害。
     *  兼作 结印 状态代理: 施放终结技进入【结印】时获得3点【彩墨】。 */
    static class RappaSea implements Trace {
        private final double ratio;

        RappaSea(double ratio) {
            this.ratio = ratio;
        }

        @Override
        public String getName() {
            return "忍法帖•海鸣";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type == SkillType.ULTRA && owner.isEnhanced()) {
                RappaKit.setInk(owner, 3);
                IO.println("  [行迹] " + owner.getName() + " enters 结印 with 3 【彩墨】");
            }
        }
    }

    /** 忍法帖•枯叶: 敌方弱点被击破时受到的击破伤害提高2%, 攻击力>2400时每超过100点额外提高1%, 最多8%。 */
    static class RappaLeaf implements Trace {
        private final double base;
        private final double atkThreshold;
        private final double per100;
        private final double extraCap;
        private final int turns;

        RappaLeaf(double base, double atkThreshold, double per100, double extraCap, int turns) {
            this.base = base;
            this.atkThreshold = atkThreshold;
            this.per100 = per100;
            this.extraCap = extraCap;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "忍法帖•枯叶";
        }

        @Override
        public void onEnemyBreak(Battle battle, Character owner, Enemy enemy) {
            double atk = owner.getAttribute(AttributeType.ATTACK) != null
                    ? owner.getAttribute(AttributeType.ATTACK).get() : 0;
            double extra = atk > atkThreshold
                    ? Math.min(extraCap, Math.floor((atk - atkThreshold) / 100) * per100) : 0;
            enemy.removeBuff("忍法帖•枯叶");
            Buff buff = new Buff("忍法帖•枯叶", Buff.Category.DEBUFF, owner, enemy, turns)
                    .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(base + extra,
                            DoubleValue.Modifier.ModifierSource.DEBUFF));
            battle.applyBuff(enemy, buff);
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 常世道返三途无六钱: 结印状态期间无视15%防御, 退出后恢复20点能量。 */
    static class RappaE1 implements Trace {
        private final double defIgnore;
        private final double energy;

        RappaE1(double defIgnore, double energy) {
            this.defIgnore = defIgnore;
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("星魂1", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.DEFENCE_IGNORE, DoubleValue.Modifier.pure(defIgnore,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 星魂2 俳句暗记有识无挂碍: 强化普攻前2段对指定敌方单体的削韧值提高50%。 */
    static class RappaE2 implements Trace {
        private final double stanceBonus;

        RappaE2(double stanceBonus) {
            this.stanceBonus = stanceBonus;
        }

        @Override
        public String getName() {
            return "星魂2";
        }
    }

    /** 星魂4 经年劣化任侠无忍义: 【结印】状态期间, 我方全体速度提高12%。 */
    static class RappaE4 implements Trace {
        private final double speed;

        RappaE4(double speed) {
            this.speed = speed;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                Buff buff = new Buff("星魂4", Buff.Category.BUFF, owner, ally, -1)
                        .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(speed,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(ally, buff);
            }
        }
    }

    /** 星魂6 破邪显正应报无慈悲: 战斗开始时获得5点天赋充能, 充能上限+5。 */
    static class RappaE6 implements Trace {
        private final int startCharge;
        private final int capBonus;

        RappaE6(int startCharge, int capBonus) {
            this.startCharge = startCharge;
            this.capBonus = capBonus;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            RappaKit.addCharge(owner, startCharge);
            IO.println("  [星魂] " + owner.getName() + " starts with " + startCharge + " charge");
        }
    }

    // ─── 【彩墨】/【充能】代理 ──────────────────────────────────────────

    private static final java.util.Map<CanHit, Integer> INK = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<CanHit, Integer> CHARGE = new java.util.concurrent.ConcurrentHashMap<>();

    static int ink(CanHit owner) {
        return INK.getOrDefault(owner, 0);
    }

    static void setInk(CanHit owner, int amount) {
        INK.put(owner, Math.max(0, amount));
    }

    static int consumeInk(CanHit owner) {
        int left = Math.max(0, ink(owner) - 1);
        INK.put(owner, left);
        return left;
    }

    static int charge(CanHit owner) {
        return CHARGE.getOrDefault(owner, 0);
    }

    static void addCharge(CanHit owner, int amount) {
        CHARGE.put(owner, Math.min(10, charge(owner) + amount));
    }
}
