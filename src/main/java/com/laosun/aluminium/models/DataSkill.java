package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Element;
import com.laosun.aluminium.models.DamageCalculator.DamageContext;
import com.laosun.aluminium.models.DamageCalculator.DamageType;
import com.laosun.aluminium.models.kit.KitRegistry;
import com.laosun.aluminium.models.kit.SkillBehavior;
import com.laosun.aluminium.models.kit.SkillContext;

import java.util.List;

/**
 * A generic skill driven entirely by game data (skills.json or servant data).
 *
 * <p>The execution behavior is selected by the data's {@code skill_effect}:
 * <ul>
 *   <li>{@code SingleAttack} / {@code AoEAttack} / {@code Blast} / {@code Bounce} —
 *   damage with multiplier {@code param[0]} (HSR.md §2.1)</li>
 *   <li>{@code Restore} — heal ({@code param[0]} × MAX_HP + flat {@code param[1]})</li>
 *   <li>{@code Support} — ATK/DMG buff to allied targets</li>
 *   <li>{@code Defence} — shield ({@code param[0]} × MAX_HP)</li>
 *   <li>{@code Impair} — debuff with base chance (HSR.md §3.5)</li>
 *   <li>{@code Enhance} — self buff</li>
 *   <li>{@code Summon} — summons the user's 忆灵</li>
 * </ul>
 *
 * <p>Toughness damage comes from the skill's {@code stance_list} (HSR.md §3.2):
 * the {@code single} value is applied to the main target, {@code spread} to
 * adjacent targets, and {@code all} to every enemy.
 */
public class DataSkill extends Skill {
    protected final int cid;
    protected final int skillId;
    protected final int level;
    protected SkillData data;

    public DataSkill(int cid, int skillId, int level) {
        this.cid = cid;
        this.skillId = skillId;
        this.level = level;
        this.data = SkillData.init(cid, skillId);
    }

    /**
     * Constructor for subclasses that resolve their data from another source
     * (e.g. 忆灵 skills from servant_skills.json).
     */
    protected DataSkill(int cid, int skillId, int level, SkillData data) {
        this.cid = cid;
        this.skillId = skillId;
        this.level = level;
        this.data = data;
    }

    @Override
    public int getLevel() {
        return level;
    }

    @Override
    public SkillData getData() {
        return data;
    }

    @Override
    public void execute(Battle battle, CanHit user, List<? extends CanHit> targets) {
        if (data == null || targets == null || targets.isEmpty()) {
            return;
        }
        // 欢愉技 (HSR.md §3.4): attack_type "ElationDamage" — damage is level-based
        // and ignores ATK / elemental boosts.
        if ("ElationDamage".equals(data.getSkillType())) {
            elationDamage(battle, user, targets);
            return;
        }
        List<Double> params = params();
        if (params.isEmpty()) {
            return;
        }
        // 特殊技能行为 (策略模式): 由角色的 CharacterKit 注册, 处理通用引擎
        // 无法表达的机制 (冻结/禁锢/引爆/消耗生命/加速...).
        SkillBehavior behavior = KitRegistry.skillBehavior(cid, skillId);
        if (behavior != null) {
            behavior.execute(battle, skillContext(), user, targets);
            return;
        }
        String effect = data.getSkillEffect();
        switch (effect == null ? "" : effect) {
            case "AoEAttack" -> aoeAttack(battle, user, params);
            case "Blast" -> blast(battle, user, targets, params);
            case "Bounce" -> bounce(battle, user, targets, params);
            case "Restore" -> restore(battle, user, targets, params);
            case "Support" -> support(battle, user, targets, params);
            case "Defence" -> defence(battle, user, targets, params);
            case "Impair" -> impair(battle, user, targets, params);
            case "Enhance" -> enhance(battle, user, params);
            case "Summon" -> summon(battle, user);
            case "MazeAttack", "Maze" -> { /* out-of-battle skills do nothing */ }
            default -> singleAttack(battle, user, targets, params);
        }
    }

    // ─── Elation damage (HSR.md §3.4) ──────────────────────────────────

    /**
     * Casts the 欢愉技: deals 欢愉伤害 and accumulates 笑点.
     */
    protected void elationDamage(Battle battle, CanHit user, List<? extends CanHit> targets) {
        List<Double> params = params();
        // Param semantics vary per skill in the dump data; clamp to a sane
        // multiplier range for the demo (HSR.md §3.4: 欢愉倍率).
        double multiplier = params.isEmpty() ? 1.0 : Math.min(1.0, Math.max(0.05, params.getFirst()));
        switch (data.getSkillEffect() == null ? "" : data.getSkillEffect()) {
            case "AoEAttack" -> {
                for (Enemy enemy : battle.getAliveEnemies()) {
                    battle.dealElationDamage(user, enemy, multiplier);
                }
            }
            case "Bounce" -> {
                List<Enemy> alive = battle.getAliveEnemies();
                for (int i = 0; i < 5; i++) {
                    if (alive.isEmpty()) {
                        return;
                    }
                    CanHit target = i == 0 ? targets.getFirst()
                            : alive.get((int) (Math.random() * alive.size()));
                    battle.dealElationDamage(user, target, multiplier);
                }
            }
            default -> battle.dealElationDamage(user, targets.getFirst(), multiplier);
        }
    }

    // ─── Damage handlers ───────────────────────────────────────────────

    protected void singleAttack(Battle battle, CanHit user, List<? extends CanHit> targets,
                                List<Double> params) {
        CanHit target = targets.getFirst();
        dealDamage(battle, user, target, params.getFirst());
        battle.breakToughness(user, target, stanceSingle());
        applyDescDebuff(battle, user, target, params, 1.0);
    }

    protected void aoeAttack(Battle battle, CanHit user, List<Double> params) {
        for (Enemy enemy : battle.getAliveEnemies()) {
            dealDamage(battle, user, enemy, params.getFirst());
            battle.breakToughness(user, enemy, stanceAll());
            applyDescDebuff(battle, user, enemy, params, 1.0);
        }
    }

    protected void blast(Battle battle, CanHit user, List<? extends CanHit> targets, List<Double> params) {
        double sideMultiplier = params.size() > 1 ? params.get(1) : params.getFirst() * 0.5;
        CanHit main = targets.getFirst();
        dealDamage(battle, user, main, params.getFirst());
        battle.breakToughness(user, main, stanceSingle());
        applyDescDebuff(battle, user, main, params, 1.0);
        for (CanHit enemy : battle.getAliveEnemies()) {
            if (enemy != main) {
                dealDamage(battle, user, enemy, sideMultiplier);
                battle.breakToughness(user, enemy, stanceSpread());
            }
        }
    }

    protected void bounce(Battle battle, CanHit user, List<? extends CanHit> targets, List<Double> params) {
        // Data only provides the per-hit multiplier; HSR bounce attacks hit 5 times total.
        int hits = 5;
        List<Enemy> alive = battle.getAliveEnemies();
        for (int i = 0; i < hits; i++) {
            if (alive.isEmpty()) {
                return;
            }
            CanHit target = i == 0 ? targets.getFirst()
                    : alive.get((int) (Math.random() * alive.size()));
            dealDamage(battle, user, target, params.getFirst());
            battle.breakToughness(user, target, stanceSingle() / hits);
        }
    }

    /**
     * Deals damage with the skill's multiplier, using HP as the base stat for
     * 生命上限-scaling skills (e.g. 死龙).
     */
    protected void dealDamage(Battle battle, CanHit user, CanHit target, double multiplier) {
        DamageContext context = DamageContext.of(DamageType.NORMAL, user.getElement());
        if (skillDesc().contains("生命上限")) {
            battle.dealAttackDamageBase(user, target, user.getMaxHp() * multiplier, context);
        } else {
            battle.dealAttackDamage(user, target, multiplier, 1.0, context);
        }
    }

    // ─── Utility handlers ──────────────────────────────────────────────

    protected void restore(Battle battle, CanHit user, List<? extends CanHit> targets, List<Double> params) {
        double hpMultiplier = params.getFirst();
        double flat = params.size() > 1 ? params.get(1) : 0;
        double baseHeal = user.getMaxHp() * hpMultiplier + flat;
        for (CanHit target : friendlyTargets(battle, user)) {
            battle.healTarget(user, target, baseHeal);
        }
    }

    protected void support(Battle battle, CanHit user, List<? extends CanHit> targets, List<Double> params) {
        // 开拓者·同谐 (8005/8006) 终结技: 【舞梦】 — party attacks on broken
        // enemies convert toughness into 超击破伤害 (HSR.md 超击破).
        if ((cid == 8005 || cid == 8006) && "Ultra".equals(data.getSkillType())) {
            int duration = params.size() > 0 ? params.get(0).intValue() : 3;
            double atkPercent = params.size() > 2 ? params.get(2) : 0.15;
            for (CanHit ally : battle.getAlivePlayerUnits()) {
                if (ally instanceof Character character && !character.hasBuffNamed("舞梦")) {
                    Buff buff = new Buff("舞梦", Buff.Category.BUFF, user, ally, duration)
                            .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atkPercent,
                                    DoubleValue.Modifier.ModifierSource.BUFF));
                    battle.applyBuff(ally, buff);
                }
            }
            IO.println("  【舞梦】activated for " + duration
                    + " turns: attacks on broken enemies deal super break damage!");
            return;
        }
        // Generic support: raises the target's ATK by param[1] for param[2] turns.
        double atkPercent = params.size() > 1 ? params.get(1) : 0.2;
        int duration = params.size() > 2 ? params.get(2).intValue() : 2;
        for (CanHit target : friendlyTargets(battle, user)) {
            if (target == null || target.isDeath()) {
                continue;
            }
            Buff buff = new Buff(dataSkillName(), Buff.Category.BUFF, user, target, duration)
                    .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atkPercent,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(target, buff);
        }
    }

    protected void defence(Battle battle, CanHit user, List<? extends CanHit> targets, List<Double> params) {
        double shieldRatio = params.getFirst();
        double shield = user.getMaxHp() * shieldRatio;
        for (CanHit target : friendlyTargets(battle, user)) {
            battle.applyShield(target, shield, user);
        }
    }

    protected void impair(Battle battle, CanHit user, List<? extends CanHit> targets, List<Double> params) {
        // Generic impair: debuff with base chance (HSR.md §3.5). The attribute is
        // chosen by the skill's description keywords.
        double baseChance = params.size() > 0 ? Math.min(1.0, params.getFirst()) : 0.6;
        for (CanHit target : targets) {
            applyDescDebuff(battle, user, target, params, baseChance);
        }
    }

    /**
     * Applies a debuff chosen by the skill's description keywords:
     * 速度降低 → Slow, 受到的伤害提高 → vulnerability, 防御力降低 → DEF down,
     * 攻击力降低 → ATK down, 弱点 → weakness implant, 裂伤 → bleed DoT.
     * Falls back to DEF down (the original generic behavior).
     *
     * @param baseChance base chance of the debuff (HSR.md §3.5)
     */
    protected void applyDescDebuff(Battle battle, CanHit user, CanHit target,
                                   List<Double> params, double baseChance) {
        if (target == null || target.isDeath()) {
            return;
        }
        String desc = skillDesc();
        double value = params.size() > 1 ? params.get(1) : 0.1;
        int duration = params.size() > 2 ? params.get(2).intValue() : 2;

        if (desc.contains("弱点")) {
            // Weakness implant (e.g. Silver Wolf): target gains the user's element.
            if (target instanceof Enemy enemy && battle.checkEffectHit(user, target, baseChance)) {
                enemy.addTemporaryWeakness(user.getElement(), Math.max(1, duration));
                IO.println("  " + enemy.getName() + " gains " + user.getElement().string + " weakness!");
            }
            return;
        }
        if (desc.contains("裂伤")) {
            // 裂伤 (bleed): physical DoT of 8% max HP per turn.
            if (battle.checkEffectHit(user, target, baseChance)) {
                double dotDamage = target.getMaxHp() * 0.08;
                target.applyDot(new Buff.Dot("Bleed (裂伤)", user, target, dotDamage, Element.PHYSICAL, 2));
                IO.println("  " + target.getName() + " is bleeding (" + String.format("%.0f", dotDamage)
                        + " physical DoT x2)");
            }
            return;
        }
        Buff debuff;
        if (desc.contains("速度降低")) {
            debuff = new Buff("Slow", Buff.Category.DEBUFF, user, target, duration)
                    .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(-value,
                            DoubleValue.Modifier.ModifierSource.DEBUFF));
        } else if (desc.contains("受到的伤害提高")) {
            debuff = new Buff("Damage Taken", Buff.Category.DEBUFF, user, target, duration)
                    .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(value,
                            DoubleValue.Modifier.ModifierSource.DEBUFF));
        } else if (desc.contains("攻击力降低")) {
            debuff = new Buff("ATK Down", Buff.Category.DEBUFF, user, target, duration)
                    .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(-value,
                            DoubleValue.Modifier.ModifierSource.DEBUFF));
        } else if (desc.contains("防御力降低")) {
            debuff = new Buff("DEF Down", Buff.Category.DEBUFF, user, target, duration)
                    .stat(AttributeType.DEFENCE, DoubleValue.Modifier.addPercent(-value,
                            DoubleValue.Modifier.ModifierSource.DEBUFF));
        } else {
            debuff = new Buff("DEF Down", Buff.Category.DEBUFF, user, target, duration)
                    .stat(AttributeType.DEFENCE, DoubleValue.Modifier.addPercent(-value,
                            DoubleValue.Modifier.ModifierSource.DEBUFF));
        }
        if (battle.checkEffectHit(user, target, baseChance)) {
            battle.applyBuff(target, debuff);
        }
    }

    protected void enhance(Battle battle, CanHit user, List<Double> params) {
        // Generic self-buff: +speed / +all damage for a few turns.
        double boost = params.size() > 2 ? params.get(2) : 0.1;
        int duration = params.size() > 3 ? params.get(3).intValue() : 2;
        Buff buff = new Buff(dataSkillName(), Buff.Category.BUFF, user, user, duration)
                .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(boost,
                        DoubleValue.Modifier.ModifierSource.BUFF));
        battle.applyBuff(user, buff);
    }

    protected void summon(Battle battle, CanHit user) {
        // 忆灵 (HSR.md §2): summon an independent combat unit that joins the queue.
        battle.summonRequest(user);
    }

    /**
     * Heal/shield/support skills always target the user's own camp (HSR.md §4),
     * including 忆灵 (HSR.md §2.1: 可被我方单体技能选中).
     */
    protected List<? extends CanHit> friendlyTargets(Battle battle, CanHit user) {
        List<? extends CanHit> allies = user.getCamp() == com.laosun.aluminium.enums.Camp.PLAYER
                ? battle.getAlivePlayerUnits() : battle.getAliveEnemies();
        return allies.isEmpty() ? List.of(user) : allies;
    }


    // ─── Helpers ───────────────────────────────────────────────────────

    protected List<Double> params() {
        return data.getSkills() != null && level >= 1 && level <= data.getSkills().size()
                ? data.getSkills().get(level - 1) : List.of();
    }

    /**
     * Builds the {@link SkillContext} passed to special skill behaviors.
     */
    protected SkillContext skillContext() {
        List<Double> params = params();
        return new SkillContext(params, stanceSingle(), stanceSpread(), stanceAll(), skillDesc());
    }

    /**
     * The base stat of a damage skill: HP for 生命上限-scaling skills
     * (e.g. 死龙), otherwise ATK.
     */
    protected double baseStat(CanHit user) {
        if (skillDesc().contains("生命上限")) {
            return user.getMaxHp();
        }
        return user.getAttribute(AttributeType.ATTACK) != null
                ? user.getAttribute(AttributeType.ATTACK).get() : 0;
    }

    protected String skillDesc() {
        return data.getSkillIntroduction() != null && data.getSkillIntroduction().chinese() != null
                ? data.getSkillIntroduction().chinese() : "";
    }

    protected String dataSkillName() {
        return "Skill#" + skillId;
    }

    protected double stanceSingle() {
        return data.getStanceList() != null ? Enemy.stanceToToughness(data.getStanceList().single()) : 0;
    }

    protected double stanceSpread() {
        return data.getStanceList() != null ? Enemy.stanceToToughness(data.getStanceList().spread()) : 0;
    }

    protected double stanceAll() {
        return data.getStanceList() != null ? Enemy.stanceToToughness(data.getStanceList().all()) : 0;
    }

    protected int getCid() {
        return cid;
    }

    protected int getSkillId() {
        return skillId;
    }

    @Override
    public String toString() {
        return "DataSkill[" + cid + "_" + skillId + "@" + level + "]";
    }
}
