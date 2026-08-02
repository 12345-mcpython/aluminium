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
 * 云璃 (Yunli, cid 1221) — 物理属性 毁灭.
 */
public final class YunliKit implements CharacterKit {

    @Override
    public int cid() {
        return 1221;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new YunliHub(),
                new YunliGuard(param(byId, 1221102, 0, 0.2)),
                new YunliTrue(param(byId, 1221103, 0, 0.3)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new YunliE1(param(e, 0, 0.2), intParam(e, 1, 3));
            case 2 -> new YunliE2(param(e, 0, 0.2));
            case 4 -> new YunliE4(param(e, 0, 0.5), intParam(e, 1, 1));
            case 6 -> new YunliE6(param(e, 0, 0.15), param(e, 1, 0.2));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> YunliKit::yunliSkill;
            case 3 -> YunliKit::yunliUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技 飞铗震赫: 回复等同于#3%攻击力+#4的生命值, 并对主目标造成#1%伤害, 相邻目标#2%伤害. */
    static void yunliSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double healRatio = ctx.param(2, 0.2);
        double healFlat = ctx.param(3, 50);
        double heal = user.getAttribute(AttributeType.ATTACK) != null
                ? user.getAttribute(AttributeType.ATTACK).get() * healRatio + healFlat : healFlat;
        battle.healTarget(user, user, heal);
        CanHit main = targets.getFirst();
        ctx.dealDamage(battle, user, main, ctx.firstParam());
        battle.breakToughness(user, main, ctx.stanceSingle());
        for (Enemy enemy : battle.getAliveEnemies()) {
            if (enemy != main) {
                ctx.dealDamage(battle, user, enemy, ctx.param(1, ctx.firstParam() * 0.5));
                battle.breakToughness(user, enemy, ctx.stanceSpread());
            }
        }
    }

    /**
     * 终结技 剑为地纪，刃惊天宗: 云璃获得【格挡】并使敌方全体陷入嘲讽状态, 下一次反击的暴击伤害
     * 提高#2%。【格挡】状态期间受击触发反击【勘破•灭】并移除【格挡】; 状态结束时若未触发反击,
     * 立即对随机敌方目标发动【勘破•斩】 (由行迹 灼毂 的钩子代理)。嘲讽以仇恨值 (aggro) 代理。
     */
    static void yunliUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        if (!(user instanceof Character character)) {
            return;
        }
        int turns = ctx.intParam(3, 2);
        double critBonus = ctx.param(1, 0.6);
        character.removeBuff("格挡");
        battle.applyBuff(character, new Buff("格挡", Buff.Category.BUFF, character, character, turns));
        character.removeBuff("反击暴伤");
        Buff crit = new Buff("反击暴伤", Buff.Category.BUFF, character, character, turns)
                .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(critBonus,
                        DoubleValue.Modifier.ModifierSource.BUFF));
        battle.applyBuff(character, crit);
        for (Enemy enemy : battle.getAliveEnemies()) {
            enemy.removeBuff("嘲讽");
            battle.applyBuff(enemy, new Buff("嘲讽", Buff.Category.DEBUFF, character, enemy, turns));
        }
        IO.println("  " + character.getName() + " enters 【格挡】 and taunts all enemies ("
                + turns + " turns)");
    }

    // ─── 反击 (勘破•斩 / 勘破•灭) ────────────────────────────────────────

    /** 反击伤害倍率: 天赋 闪铄 = #1主/#2邻, 终结技 勘破 = #1主/#6邻 + #4次×#7额外. */
    static void counterAttack(Battle battle, Character owner, CanHit attacker) {
        if (owner.isDeath()) {
            return;
        }
        if (attacker == null || attacker.isDeath()) {
            List<Enemy> alive = battle.getAliveEnemies();
            if (alive.isEmpty()) {
                return;
            }
            attacker = alive.get((int) (Math.random() * alive.size()));
        }
        // 天赋: 受到敌方目标攻击后, 额外恢复#3点能量.
        List<Double> talent = rawParams(owner, 4);
        double energy = talent.size() > 2 ? talent.get(2) : 15;
        owner.gainEnergy(energy);

        boolean inBlock = owner.hasBuffNamed("格挡");
        boolean mie = inBlock || ((owner.getTraces().stream()
                .filter(t -> t instanceof YunliHub).findFirst().map(t -> ((YunliHub) t).toggle())
                .orElse(false)));
        List<Double> ult = rawParams(owner, 3);
        double mainMult = inBlock || mie ? (ult.size() > 0 ? ult.get(0) : 1.32)
                : (talent.size() > 0 ? talent.get(0) : 0.6);
        double sideMult = inBlock || mie ? (ult.size() > 5 ? ult.get(5) : 0.66)
                : (talent.size() > 1 ? talent.get(1) : 0.3);
        int extraHits = inBlock || mie ? (ult.size() > 3 ? ult.get(3).intValue() : 6) : 0;
        double extraMult = ult.size() > 6 ? ult.get(6) : 0.432;

        double multBonus = 0;
        for (Trace trace : owner.getTraces()) {
            if (trace instanceof YunliE1 e1) {
                multBonus += e1.bonus;
                extraHits += e1.extraHits;
            }
        }
        // 星魂6: 发动斩/灭时暴击率提高, 物理抗性穿透提高.
        for (Trace trace : owner.getTraces()) {
            if (trace instanceof YunliE6 e6) {
                e6.empower(battle, owner);
            }
        }
        // 行迹 真刚: 施放反击时攻击力提高.
        for (Trace trace : owner.getTraces()) {
            if (trace instanceof YunliTrue trueSteel) {
                trueSteel.atkUp(battle, owner);
            }
        }
        // 星魂4: 发动斩/灭后效果抵抗提高.
        for (Trace trace : owner.getTraces()) {
            if (trace instanceof YunliE4 e4) {
                e4.resistUp(battle, owner);
            }
        }

        com.laosun.aluminium.models.DamageCalculator.DamageContext ctx =
                com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                        inBlock ? com.laosun.aluminium.models.DamageCalculator.DamageType.ULTRA
                                : com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP,
                        Element.PHYSICAL);
        battle.dealAttackDamage(owner, attacker, mainMult * (1 + multBonus), 0, ctx);
        battle.breakToughness(owner, attacker, inBlock ? 2.0 : 1.0);
        for (Enemy enemy : battle.getAliveEnemies()) {
            if (enemy != attacker) {
                battle.dealAttackDamage(owner, enemy, sideMult * (1 + multBonus), 0, ctx);
                battle.breakToughness(owner, enemy, inBlock ? 1.0 : 1.0);
            }
        }
        for (int i = 0; i < extraHits; i++) {
            List<Enemy> alive = battle.getAliveEnemies();
            if (alive.isEmpty()) {
                return;
            }
            Enemy target = alive.get((int) (Math.random() * alive.size()));
            battle.dealAttackDamage(owner, target, extraMult * (1 + multBonus), 0, ctx);
        }
        if (inBlock) {
            owner.removeBuff("格挡");
        }
        IO.println("  " + owner.getName() + " counter-attacks " + attacker.getName()
                + (mie ? " (勘破•灭)" : " (勘破•斩)"));
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
     * 灼毂: 每发动1次【勘破•斩】后，下一次【勘破•斩】将替换为【勘破•灭】。
     * 兼作天赋 闪铄 的代理: 受到敌方目标攻击后恢复#3点能量, 并立即向攻击者发起反击
     * (格挡状态下改为发动终结技的【勘破•灭】并移除格挡)。
     */
    static class YunliHub implements Trace {
        private boolean nextIsMie = false;

        @Override
        public String getName() {
            return "灼毂";
        }

        /** 斩/灭交替: 返回本次是否发动【勘破•灭】. */
        boolean toggle() {
            boolean mie = nextIsMie;
            nextIsMie = !nextIsMie;
            return mie;
        }

        @Override
        public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
            if (owner.isDeath() || attacker == null) {
                return;
            }
            counterAttack(battle, owner, attacker);
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            // 【格挡】状态结束时若未触发反击, 立即对随机敌方目标发动【勘破•斩】.
            if (owner.hasBuffNamed("格挡")) {
                owner.removeBuff("格挡");
                counterAttack(battle, owner, null);
            }
        }
    }

    /** 却邪: 【格挡】状态下抵抗受到的控制类负面效果, 并使受到的伤害降低#1。 */
    static class YunliGuard implements Trace {
        private final double reduction;

        YunliGuard(double reduction) {
            this.reduction = reduction;
        }

        @Override
        public String getName() {
            return "却邪";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.ULTRA) {
                return;
            }
            owner.removeBuff("却邪");
            Buff buff = new Buff("却邪", Buff.Category.BUFF, owner, owner, 2)
                    .stat(AttributeType.DAMAGE_REDUCTION, DoubleValue.Modifier.multiplyPercent(-reduction,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
            IO.println("  [行迹] " + owner.getName() + " takes -"
                    + String.format("%.0f%%", reduction * 100) + " damage while 格挡 (却邪)");
        }

        @Override
        public double aggroMultiplier(Battle battle, Character owner) {
            // 嘲讽 (格挡状态使敌人更倾向攻击云璃) 以仇恨值代理.
            return owner.hasBuffNamed("格挡") ? 5.0 : 1.0;
        }
    }

    /** 真刚: 施放反击时，云璃的攻击力提高#1，持续1回合。 */
    static class YunliTrue implements Trace {
        private final double atkPercent;

        YunliTrue(double atkPercent) {
            this.atkPercent = atkPercent;
        }

        @Override
        public String getName() {
            return "真刚";
        }

        void atkUp(Battle battle, Character owner) {
            owner.removeBuff("真刚");
            Buff buff = new Buff("真刚", Buff.Category.BUFF, owner, owner, 1)
                    .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atkPercent,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 沉锋离垢: 【勘破•斩】与【勘破•灭】造成的伤害提高#1, 【勘破•灭】的额外伤害次数增加#2次。 */
    static class YunliE1 implements Trace {
        private final double bonus;
        private final int extraHits;

        YunliE1(double bonus, int extraHits) {
            this.bonus = bonus;
            this.extraHits = extraHits;
        }

        @Override
        public String getName() {
            return "星魂1";
        }
    }

    /** 星魂2 初芒破生: 发动反击造成伤害时无视敌方目标#1的防御力。 */
    static class YunliE2 implements Trace {
        private final double defIgnore;

        YunliE2(double defIgnore) {
            this.defIgnore = defIgnore;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            owner.removeBuff("初芒破生");
            Buff buff = new Buff("初芒破生", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.DEFENCE_IGNORE, DoubleValue.Modifier.pure(defIgnore,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 星魂4 大匠击橐: 发动【勘破•斩】或【勘破•灭】后使自身效果抵抗提高#1, 持续#2回合。 */
    static class YunliE4 implements Trace {
        private final double resistance;
        private final int turns;

        YunliE4(double resistance, int turns) {
            this.resistance = resistance;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        void resistUp(Battle battle, Character owner) {
            owner.removeBuff("大匠击橐");
            Buff buff = new Buff("大匠击橐", Buff.Category.BUFF, owner, owner, turns)
                    .stat(AttributeType.EFFECT_RESISTANCE, DoubleValue.Modifier.pure(resistance,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 星魂6 剑胆琴心: 【格挡】状态期间敌方主动施放技能时无论是否攻击云璃都会触发【勘破•灭】;
     *  发动【勘破•斩】或【勘破•灭】时暴击率提高#1, 物理属性抗性穿透提高#2
     *  (格挡期间任意受击均触发灭, 以 灼毂 的钩子代理)。 */
    static class YunliE6 implements Trace {
        private final double critRate;
        private final double penetration;

        YunliE6(double critRate, double penetration) {
            this.critRate = critRate;
            this.penetration = penetration;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        void empower(Battle battle, Character owner) {
            owner.removeBuff("剑胆琴心");
            Buff buff = new Buff("剑胆琴心", Buff.Category.BUFF, owner, owner, 1)
                    .stat(AttributeType.CRIT_CHANCE, DoubleValue.Modifier.pure(critRate,
                            DoubleValue.Modifier.ModifierSource.BUFF))
                    .stat(AttributeType.RESISTANCE_PENETRATION, DoubleValue.Modifier.pure(penetration,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }
}
