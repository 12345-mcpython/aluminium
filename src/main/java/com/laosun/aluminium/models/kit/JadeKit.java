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
 * 翡翠 (Jade, cid 1314) — 量子属性 智识.
 */
public final class JadeKit implements CharacterKit {

    @Override
    public int cid() {
        return 1314;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new JadeRepo(intParam(byId, 1314101, 0, 3), intParam(byId, 1314101, 1, 1)),
                new JadeDiscounted(param(byId, 1314102, 0, 0.5)),
                new JadeForeclosed(param(byId, 1314103, 0, 0.005)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new JadeE1(param(e, 0, 0.32), intParam(e, 1, 1), intParam(e, 2, 2));
            case 2 -> new JadeE2(intParam(e, 0, 15), param(e, 1, 0.18));
            case 4 -> new JadeE4(param(e, 0, 0.12), intParam(e, 1, 3));
            case 6 -> new JadeE6(param(e, 0, 0.2));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> JadeKit::jadeSkill;
            case 3 -> JadeKit::jadeUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 指定我方单体成为【收债人】, 使其速度提高#1点持续#4回合. */
    static void jadeSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double speedBonus = ctx.firstParam();
        int turns = ctx.intParam(3, 3);
        for (CanHit target : KitSupport.friendlyTargets(battle, user, targets)) {
            // 【收债人】仅对最新目标生效.
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                ally.removeBuff("收债人");
            }
            Buff buff = new Buff("收债人", Buff.Category.BUFF, user, target, turns)
                    .stat(AttributeType.SPEED, DoubleValue.Modifier.pure(speedBonus,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(target, buff);
            IO.println("  " + target.getName() + " becomes the 收债人 (SPD +"
                    + String.format("%.0f", speedBonus) + ")");
        }
    }

    /** 终结技: 对敌方全体造成#3%伤害, 天赋追加攻击伤害倍率提高#1, 强化效果可生效#2次. */
    static void jadeUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double strengthen = ctx.firstParam();
        int uses = ctx.intParam(1, 2);
        double multiplier = ctx.param(2, 1.2);
        for (Enemy enemy : battle.getAliveEnemies()) {
            ctx.dealDamage(battle, user, enemy, multiplier);
            battle.breakToughness(user, enemy, ctx.stanceAll());
        }
        user.removeBuff("剔烁之牙·强化");
        Buff buff = new Buff("剔烁之牙·强化", Buff.Category.BUFF, user, user, uses);
        battle.applyBuff(user, buff);
        IO.println("  " + user.getName() + " strengthens her follow-up (" + uses + " uses)");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 逆回购: 敌方目标进入战斗时获得1层【当品】; 【收债人】回合开始时额外获得3层。 */
    static class JadeRepo implements Trace {
        private final int perTurn;
        private final int onEntry;

        JadeRepo(int perTurn, int onEntry) {
            this.perTurn = perTurn;
            this.onEntry = onEntry;
        }

        @Override
        public String getName() {
            return "逆回购";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            JadeKit.addPawn(owner, onEntry);
            IO.println("  [行迹] " + owner.getName() + " gains " + onEntry + " 【当品】");
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            boolean hasCollector = battle.getAlivePlayerUnits().stream()
                    .anyMatch(u -> u.hasBuffNamed("收债人"));
            if (hasCollector) {
                JadeKit.addPawn(owner, perTurn);
                JadeKit.refreshPawn(battle, owner);
            }
        }

        @Override
        public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                 List<? extends CanHit> targets) {
            // 天赋 剔烁之牙 代理: 【收债人】施放攻击后, 翡翠对击中目标造成附加伤害.
            if (actor != owner && actor.hasBuffNamed("收债人")
                    && (type == SkillType.COMMON || type == SkillType.SKILL || type == SkillType.ULTRA)) {
                JadeKit.collectorFollowUp(battle, owner, targets);
            }
        }
    }

    /** 折牙票: 战斗开始时, 翡翠的行动提前50%。 */
    static class JadeDiscounted implements Trace {
        private final double advance;

        JadeDiscounted(double advance) {
            this.advance = advance;
        }

        @Override
        public String getName() {
            return "折牙票";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            battle.advanceByPercent(owner, advance);
        }
    }

    /** 绝当品: 天赋中每层【当品】额外使翡翠的攻击力提高0.5%。 */
    static class JadeForeclosed implements Trace {
        private final double perStack;

        JadeForeclosed(double perStack) {
            this.perStack = perStack;
        }

        @Override
        public String getName() {
            return "绝当品";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            JadeKit.refreshPawn(battle, owner);
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 无私？亦可交割: 天赋追加攻击伤害提高32%。 */
    static class JadeE1 implements Trace {
        private final double followUpBonus;
        private final int charge2;
        private final int charge1;

        JadeE1(double followUpBonus, int charge2, int charge1) {
            this.followUpBonus = followUpBonus;
            this.charge2 = charge2;
            this.charge1 = charge1;
        }

        @Override
        public String getName() {
            return "星魂1";
        }
    }

    /** 星魂2 道德？谨此核押: 【当品】叠加至15层时, 翡翠暴击率提高18%。 */
    static class JadeE2 implements Trace {
        private final int threshold;
        private final double crit;

        JadeE2(int threshold, double crit) {
            this.threshold = threshold;
            this.crit = crit;
        }

        @Override
        public String getName() {
            return "星魂2";
        }
    }

    /** 星魂4 真诚？唯凭认沽: 施放终结技时, 造成的伤害无视12%防御力, 持续3回合。 */
    static class JadeE4 implements Trace {
        private final double defIgnore;
        private final int turns;

        JadeE4(double defIgnore, int turns) {
            this.defIgnore = defIgnore;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.ULTRA) {
                return;
            }
            owner.removeBuff("星魂4");
            Buff buff = new Buff("星魂4", Buff.Category.BUFF, owner, owner, turns)
                    .stat(AttributeType.DEFENCE_IGNORE, DoubleValue.Modifier.pure(defIgnore,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 星魂6 公平？仍须保荐: 场上有【收债人】时, 量子属性抗性穿透提高20%。 */
    static class JadeE6 implements Trace {
        private final double pen;

        JadeE6(double pen) {
            this.pen = pen;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("星魂6", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.RESISTANCE_PENETRATION, DoubleValue.Modifier.pure(pen,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    // ─── 【当品】层数代理 (天赋 剔烁之牙) ────────────────────────────────

    private static final java.util.Map<CanHit, Integer> PAWN = new java.util.concurrent.ConcurrentHashMap<>();

    static int pawnStacks(CanHit owner) {
        return PAWN.getOrDefault(owner, 0);
    }

    static void addPawn(CanHit owner, int amount) {
        PAWN.put(owner, Math.min(50, pawnStacks(owner) + amount));
    }

    static void refreshPawn(Battle battle, Character owner) {
        int stacks = pawnStacks(owner);
        double atkBonus = 0;
        double critBonus = 0;
        for (Trace trace : owner.getTraces()) {
            if (trace instanceof JadeForeclosed foreclosed) {
                atkBonus = stacks * foreclosed.perStack;
            }
            if (trace instanceof JadeE2 e2 && stacks >= e2.threshold) {
                critBonus = e2.crit;
            }
        }
        owner.removeBuff("当品");
        Buff buff = new Buff("当品", Buff.Category.BUFF, owner, owner, -1)
                .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atkBonus,
                        DoubleValue.Modifier.ModifierSource.BUFF))
                .stat(AttributeType.CRIT_CHANCE, DoubleValue.Modifier.pure(critBonus,
                        DoubleValue.Modifier.ModifierSource.BUFF));
        battle.applyBuff(owner, buff);
        IO.println("  【当品】 " + owner.getName() + ": " + stacks + " stacks");
    }

    /** 天赋 剔烁之牙 代理: 收债人攻击后, 翡翠对每个击中目标造成#3%附加伤害, 并获得充能. */
    static void collectorFollowUp(Battle battle, Character owner, List<? extends CanHit> targets) {
        if (targets == null) {
            return;
        }
        double mult = 0.15;
        com.laosun.aluminium.models.SkillData skill = KitSupport.skillData(owner, SkillType.SKILL);
        if (skill != null && skill.getSkills() != null && !skill.getSkills().isEmpty()
                && skill.getSkills().getFirst().size() > 2) {
            mult = skill.getSkills().getFirst().get(2);
        }
        boolean strengthened = owner.hasBuffNamed("剔烁之牙·强化");
        for (CanHit target : targets) {
            if (target.isDeath() || target.getCamp() == owner.getCamp()) {
                continue;
            }
            double m = mult;
            if (strengthened) {
                m *= 1 + 0.4;
            }
            battle.dealAttackDamage(owner, target, m, 0,
                    com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                            com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP, Element.QUANTUM));
        }
        if (strengthened) {
            Buff buff = owner.getBuffs().stream()
                    .filter(b -> "剔烁之牙·强化".equals(b.getName())).findFirst().orElse(null);
            if (buff != null && buff.getDuration() > 0) {
                buff.setDuration(buff.getDuration() - 1);
            }
        }
    }
}
