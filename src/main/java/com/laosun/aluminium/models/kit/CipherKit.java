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
 * 赛飞儿 (Cipher, cid 1406) — 量子属性 巡猎.
 */
public final class CipherKit implements CharacterKit {

    @Override
    public int cid() {
        return 1406;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new CipherBoots(param(byId, 1406101, 0, 0.25), param(byId, 1406101, 1, 0.5),
                        param(byId, 1406101, 2, 0.5), param(byId, 1406101, 3, 1)),
                new CipherBandits(param(byId, 1406102, 0, 0.08)),
                new CipherSwitch(param(byId, 1406103, 0, 0.4), param(byId, 1406103, 1, 1)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new CipherE1(param(e, 0, 0.8), intParam(e, 1, 2), param(e, 2, 1.5));
            case 2 -> new CipherE2(intParam(e, 0, 2), param(e, 1, 1.2), param(e, 2, 0.3));
            case 4 -> new CipherE4(param(e, 0, 0.5));
            case 6 -> new CipherE6(param(e, 0, 0.2), param(e, 1, 3.5), param(e, 2, 0.16));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> CipherKit::cipherSkill;
            case 3 -> CipherKit::cipherUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /** 战技: 有#6基础概率使目标陷入虚弱 (#3伤害降低), 自身攻击力提高#5持续#4回合, 造成#1/#2%伤害. */
    static void cipherSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit main = targets.getFirst();
        double mainMult = ctx.firstParam();
        double sideMult = ctx.param(1, 0.5);
        double weakRatio = ctx.param(2, 0.1);
        int turns = ctx.intParam(3, 2);
        double atkBoost = ctx.param(4, 0.3);
        double chance = ctx.param(5, 1.2);
        ctx.dealDamage(battle, user, main, mainMult);
        battle.breakToughness(user, main, ctx.stanceSingle());
        if (!main.isDeath() && battle.checkEffectHit(user, main, chance)) {
            Buff debuff = new Buff("虚弱", Buff.Category.DEBUFF, user, main, turns)
                    .stat(AttributeType.WEAKNESS_RATIO, DoubleValue.Modifier.pure(-weakRatio,
                            DoubleValue.Modifier.ModifierSource.DEBUFF));
            battle.applyBuff(main, debuff);
        }
        for (Enemy enemy : battle.getAliveEnemies()) {
            if (enemy != main) {
                ctx.dealDamage(battle, user, enemy, sideMult);
                battle.breakToughness(user, enemy, ctx.stanceSpread());
            }
        }
        user.removeBuff("空手套白银");
        Buff buff = new Buff("空手套白银", Buff.Category.BUFF, user, user, turns)
                .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atkBoost,
                        DoubleValue.Modifier.ModifierSource.BUFF));
        battle.applyBuff(user, buff);
    }

    /** 终结技: 对目标造成#1%伤害, 并对目标及其相邻目标造成#4%伤害和记录值#3的真实伤害. */
    static void cipherUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        CanHit main = targets.getFirst();
        double mainMult = ctx.firstParam();
        double recordRatio = ctx.param(1, 0.25);
        double sharedRatio = ctx.param(2, 0.75);
        double sideMult = ctx.param(3, 0.2);
        ctx.dealDamage(battle, user, main, mainMult);
        battle.breakToughness(user, main, ctx.stanceSingle());
        double record = CipherKit.recordValue(user);
        if (record > 0 && !main.isDeath()) {
            battle.applyTrueDamage(main, record * recordRatio, "记录值");
        }
        for (Enemy enemy : battle.getAliveEnemies()) {
            if (enemy != main) {
                ctx.dealDamage(battle, user, enemy, sideMult);
                battle.breakToughness(user, enemy, ctx.stanceSpread());
                if (record > 0 && !enemy.isDeath()) {
                    battle.applyTrueDamage(enemy, record * sharedRatio, "记录值");
                }
            }
        }
        CipherKit.resetRecord(user);
        IO.println("  " + user.getName() + " clears the record (" + String.format("%.0f", record) + ")");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /** 神行宝鞋: 速度≥140/170时, 暴击率提高25%/50%。 */
    static class CipherBoots implements Trace {
        private final double crit140;
        private final double crit170;
        private final double record140;
        private final double record170;

        CipherBoots(double crit140, double crit170, double record140, double record170) {
            this.crit140 = crit140;
            this.crit170 = crit170;
            this.record140 = record140;
            this.record170 = record170;
        }

        @Override
        public String getName() {
            return "神行宝鞋";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            refresh(battle, owner);
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            refresh(battle, owner);
        }

        private void refresh(Battle battle, Character owner) {
            double speed = owner.getAttribute(AttributeType.SPEED) != null
                    ? owner.getAttribute(AttributeType.SPEED).get() : 0;
            double crit = speed >= 170 ? crit170 : speed >= 140 ? crit140 : 0;
            owner.removeBuff("神行宝鞋");
            Buff buff = new Buff("神行宝鞋", Buff.Category.BUFF, owner, owner, -1)
                    .stat(AttributeType.CRIT_CHANCE, DoubleValue.Modifier.pure(crit,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
        }
    }

    /** 三百侠盗: 赛飞儿会记录我方目标对【老主顾】以外目标造成的非真实伤害的8%。 */
    static class CipherBandits implements Trace {
        private final double ratio;

        CipherBandits(double ratio) {
            this.ratio = ratio;
        }

        @Override
        public String getName() {
            return "三百侠盗";
        }
    }

    /** 偷天换日: 天赋的追加攻击暴击伤害提高100%, 敌方全体受到的伤害提高40%。 */
    static class CipherSwitch implements Trace {
        private final double enemyVuln;
        private final double cdmg;

        CipherSwitch(double enemyVuln, double cdmg) {
            this.enemyVuln = enemyVuln;
            this.cdmg = cdmg;
        }

        @Override
        public String getName() {
            return "偷天换日";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (Enemy enemy : battle.getAliveEnemies()) {
                Buff buff = new Buff("偷天换日", Buff.Category.DEBUFF, owner, enemy, -1)
                        .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(enemyVuln,
                                DoubleValue.Modifier.ModifierSource.DEBUFF));
                battle.applyBuff(enemy, buff);
            }
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /** 星魂1 察言观色看笑脸: 记录的伤害值为原记录值的150%, 追加攻击时攻击力提高80%持续2回合。 */
    static class CipherE1 implements Trace {
        private final double atkBoost;
        private final int turns;
        private final double recordRatio;

        CipherE1(double atkBoost, int turns, double recordRatio) {
            this.atkBoost = atkBoost;
            this.turns = turns;
            this.recordRatio = recordRatio;
        }

        @Override
        public String getName() {
            return "星魂1";
        }
    }

    /** 星魂2 焦头烂额顺手牵: 赛飞儿击中敌方目标时, 有120%基础概率使其受到的伤害提高30%持续2回合。 */
    static class CipherE2 implements Trace {
        private final int turns;
        private final double chance;
        private final double vuln;

        CipherE2(int turns, double chance, double vuln) {
            this.turns = turns;
            this.chance = chance;
            this.vuln = vuln;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (targets == null) {
                return;
            }
            for (CanHit target : targets) {
                if (!target.isDeath() && battle.checkEffectHit(owner, target, chance)) {
                    Buff buff = new Buff("星魂2", Buff.Category.DEBUFF, owner, target, turns)
                            .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(vuln,
                                    DoubleValue.Modifier.ModifierSource.DEBUFF));
                    battle.applyBuff(target, buff);
                }
            }
        }
    }

    /** 星魂4 东窗事发一溜烟: 【老主顾】受到我方目标攻击后, 赛飞儿对其造成50%攻击力附加伤害。 */
    static class CipherE4 implements Trace {
        private final double atkRatio;

        CipherE4(double atkRatio) {
            this.atkRatio = atkRatio;
        }

        @Override
        public String getName() {
            return "星魂4";
        }

        @Override
        public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                 List<? extends CanHit> targets) {
            if (actor == owner || targets == null) {
                return;
            }
            for (CanHit target : targets) {
                if (target.hasBuffNamed("老主顾") && !target.isDeath()) {
                    battle.dealAttackDamage(owner, target, atkRatio, 0,
                            com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                    com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP,
                                    Element.QUANTUM));
                }
            }
        }
    }

    /** 星魂6 大盗无名天地间: 追加攻击伤害提高350%, 记录时额外记录16%。 */
    static class CipherE6 implements Trace {
        private final double recordReturn;
        private final double followUpBonus;
        private final double extraRecord;

        CipherE6(double recordReturn, double followUpBonus, double extraRecord) {
            this.recordReturn = recordReturn;
            this.followUpBonus = followUpBonus;
            this.extraRecord = extraRecord;
        }

        @Override
        public String getName() {
            return "星魂6";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return type == SkillType.ULTRA ? 1 + followUpBonus : 1.0;
        }
    }

    // ─── 【记录值】/【老主顾】代理 (天赋 热情好客的多洛斯人) ────────────

    private static final java.util.Map<CanHit, Double> RECORD = new java.util.concurrent.ConcurrentHashMap<>();

    static double recordValue(CanHit owner) {
        return RECORD.getOrDefault(owner, 0.0);
    }

    static void addRecord(CanHit owner, double amount) {
        RECORD.put(owner, recordValue(owner) + amount);
    }

    static void resetRecord(CanHit owner) {
        RECORD.put(owner, 0.0);
    }

    /** 天赋代理: 敌方目标入场时, 使生命上限最高的目标成为【老主顾】。 */
    static void applyOldCustomer(Battle battle, Character owner) {
        List<Enemy> alive = battle.getAliveEnemies();
        if (alive.isEmpty()) {
            return;
        }
        boolean exists = alive.stream().anyMatch(e -> e.hasBuffNamed("老主顾"));
        if (exists) {
            return;
        }
        Enemy target = alive.stream().max(java.util.Comparator.comparingDouble(Enemy::getMaxHp)).orElse(null);
        if (target != null) {
            for (Enemy enemy : alive) {
                enemy.removeBuff("老主顾");
            }
            battle.applyBuff(target, new Buff("老主顾", Buff.Category.DEBUFF, owner, target, -1));
            IO.println("  [天赋] " + target.getName() + " becomes 老主顾");
        }
    }

    /** 天赋代理: 老主顾受到攻击后, 记录伤害值并发动追加攻击。 */
    static void recordAndFollowUp(Battle battle, Character owner, List<? extends CanHit> targets) {
        if (targets == null) {
            return;
        }
        for (CanHit target : targets) {
            if (target.isDeath() || target.getCamp() == owner.getCamp()) {
                continue;
            }
            if (target.hasBuffNamed("老主顾")) {
                double ratio = 0.12;
                com.laosun.aluminium.models.SkillData talent = KitSupport.skillData(owner, SkillType.TALENT);
                if (talent != null && talent.getSkills() != null && !talent.getSkills().isEmpty()
                        && talent.getSkills().getFirst().size() > 1) {
                    ratio = talent.getSkills().getFirst().get(1);
                }
                double mult = 0.75;
                battle.dealAttackDamage(owner, target, mult, 0,
                        com.laosun.aluminium.models.DamageCalculator.DamageContext.of(
                                com.laosun.aluminium.models.DamageCalculator.DamageType.FOLLOW_UP,
                                Element.QUANTUM));
                IO.println("  [天赋] " + owner.getName() + " follows up on 老主顾 " + target.getName());
            }
        }
    }
}
