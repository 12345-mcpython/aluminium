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
 * 绯英 (Evanescia, cid 1505) — 物理属性 欢愉.
 * 欢愉技 (skill 20) 由通用执行器处理 (ElationDamage).
 */
public final class EvanesciaKit implements CharacterKit {

    @Override
    public int cid() {
        return 1505;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new EvanesciaJoy(param(byId, 1505101, 0, 0.3), intParam(byId, 1505101, 1, 1),
                        intParam(byId, 1505101, 2, 2), intParam(byId, 1505101, 3, 4),
                        param(byId, 1505101, 4, 0.5)),
                new EvanesciaJudge(param(byId, 1505102, 0, 0.12), intParam(byId, 1505102, 1, 3)),
                new EvanesciaBloom(param(byId, 1505103, 0, 0.5)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new EvanesciaE1(param(e, 0, 0.2), intParam(e, 1, 10));
            case 2 -> new EvanesciaE2(param(e, 0, 0.36), param(e, 1, 0.5), param(e, 2, 1));
            case 4 -> new EvanesciaE4(param(e, 0, 0.15));
            case 6 -> new EvanesciaE6(param(e, 0, 0.15), intParam(e, 1, 1), param(e, 2, 0.02),
                    intParam(e, 3, 120), intParam(e, 4, 4));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> EvanesciaKit::evanesciaSkill;
            case 3 -> EvanesciaKit::evanesciaUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 对主目标造成#2%伤害, 相邻目标#3%伤害, 并额外获得#4点笑点. */
    static void evanesciaSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit main = targets.getFirst();
        double mainMult = ctx.param(1, 1.5);
        double sideMult = ctx.param(2, 0.75);
        int laugh = ctx.intParam(3, 10);
        ctx.dealDamage(battle, user, main, mainMult);
        battle.breakToughness(user, main, ctx.stanceSingle());
        for (Enemy enemy : battle.getAliveEnemies()) {
            if (enemy != main) {
                ctx.dealDamage(battle, user, enemy, sideMult);
                battle.breakToughness(user, enemy, ctx.stanceSpread());
            }
        }
        battle.addLaughPoints(laugh);
        IO.println("  " + user.getName() + " gains " + laugh + " 笑点");
    }

    /** 终结技: 对敌方全体造成#1%伤害, 随后造成5次随机单体#3%伤害. */
    static void evanesciaUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        int bounces = 5;
        double bounceMult = ctx.param(2, 0.72);
        // 行迹 瞰众乐: 敌方数量越少, 弹射次数越多.
        int enemyCount = battle.getAliveEnemies().size();
        bounces += enemyCount >= 3 ? 1 : enemyCount == 2 ? 2 : 4;
        for (Enemy enemy : battle.getAliveEnemies()) {
            ctx.dealDamage(battle, user, enemy, multiplier);
            battle.breakToughness(user, enemy, ctx.stanceAll());
        }
        for (int i = 0; i < bounces; i++) {
            List<Enemy> alive = battle.getAliveEnemies();
            if (alive.isEmpty()) {
                return;
            }
            battle.dealAttackDamage(user, alive.get((int) (Math.random() * alive.size())),
                    bounceMult, 0, com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                            com.laosun.aluminium.models.DamageCalculator.DamageType.ULTRA, Element.PHYSICAL));
        }
        IO.println("  " + user.getName() + " fires " + bounces + " sword bounces!");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 瞰众乐: 暴击率提高30%, 敌方数量越少终结技弹射次数越多。 */
    static class EvanesciaJoy implements Trace {
        private final double crit;
        private final int bounce3;
        private final int bounce2;
        private final int bounce1;
        private final double shareRatio;

        EvanesciaJoy(double crit, int bounce3, int bounce2, int bounce1, double shareRatio) {
            this.crit = crit;
            this.bounce3 = bounce3;
            this.bounce2 = bounce2;
            this.bounce1 = bounce1;
            this.shareRatio = shareRatio;
        }

        @Override
        public String getName() {
            return "瞰众乐";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("瞰众乐", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.CRIT_CHANCE, DoubleValue.Modifier.pure(crit,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 行裁断: 【狐狸老师】施放攻击时会额外对目标施加易伤12%持续3回合。 */
    static class EvanesciaJudge implements Trace {
        private final double vuln;
        private final int turns;

        EvanesciaJudge(double vuln, int turns) {
            this.vuln = vuln;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "行裁断";
        }
    }

    /** 开不败: 队友持有的【好活当赏】结束时, 绯英将其中的50%转化为自身的。 */
    static class EvanesciaBloom implements Trace {
        private final double ratio;

        EvanesciaBloom(double ratio) {
            this.ratio = ratio;
        }

        @Override
        public String getName() {
            return "开不败";
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 故乡，为祈福起舞: 全属性抗性穿透提高20%。 */
    static class EvanesciaE1 implements Trace {
        private final double pen;
        private final int reward;

        EvanesciaE1(double pen, int reward) {
            this.pen = pen;
            this.reward = reward;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("星魂1", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.RESISTANCE_PENETRATION, DoubleValue.Modifier.pure(pen,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 星魂2 遥途，求长开不谢: 暴击伤害提高36%。 */
    static class EvanesciaE2 implements Trace {
        private final double cdmg;
        private final double shareBonus;
        private final double extraShare;

        EvanesciaE2(double cdmg, double shareBonus, double extraShare) {
            this.cdmg = cdmg;
            this.shareBonus = shareBonus;
            this.extraShare = extraShare;
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

    /** 星魂4 花田，遭恶徒摘落: 造成的伤害无视15%防御力。 */
    static class EvanesciaE4 implements Trace {
        private final double defIgnore;

        EvanesciaE4(double defIgnore) {
            this.defIgnore = defIgnore;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            Buff buff = new Buff("星魂4", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.DEFENCE_IGNORE, DoubleValue.Modifier.pure(defIgnore,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 星魂6 少女，见浮生入梦: 绯英造成的欢愉伤害增笑15%。 */
    static class EvanesciaE6 implements Trace {
        private final double laughBonus;
        private final int extraTurns;
        private final double per100;
        private final int maxReward;
        private final int ultInterval;

        EvanesciaE6(double laughBonus, int extraTurns, double per100, int maxReward, int ultInterval) {
            this.laughBonus = laughBonus;
            this.extraTurns = extraTurns;
            this.per100 = per100;
            this.maxReward = maxReward;
            this.ultInterval = ultInterval;
        }

        @Override
        public String getName() {
            return "星魂6";
        }
    }

    // ─── 天赋 青春•韶华无限 代理 (狐狸老师追加攻击) ─────────────────────

    /** 天赋代理: 累计获得240点能量时, 【狐狸老师】发动追加攻击 (50%全体伤害)。 */
    static void foxTeacherFollowUp(Battle battle, Character owner) {
        double mult = 0.5;
        com.laosun.aluminium.models.SkillData talent = KitSupport.skillData(owner, SkillType.TALENT);
        if (talent != null && talent.getSkills() != null && !talent.getSkills().isEmpty()
                && !talent.getSkills().getFirst().isEmpty()) {
            mult = talent.getSkills().getFirst().getFirst();
        }
        for (Enemy enemy : battle.getAliveEnemies()) {
            battle.dealAttackDamage(owner, enemy, mult, 0,
                    com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                            com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP, Element.PHYSICAL));
        }
        IO.println("  [天赋] 狐狸老师 follows up on all enemies!");
    }
}
