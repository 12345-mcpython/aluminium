package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Element;
import com.laosun.aluminium.enums.SkillType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A generic, data-driven interpreter for 行迹技能 and 星魂 descriptions.
 *
 * <p>The game descriptions follow a small set of patterns (stat buffs, energy
 * restoration, action advance, conditional damage bonuses, cleansing, healing,
 * shields, extra hits, aggro modifiers...). This engine reads the Chinese
 * description plus the {@code param} values and produces a concrete
 * {@link Trace}. Complex kit-specific mechanics (summons, enhanced states,
 * stack systems, 结界...) fall back to their closest generic equivalent.
 */
public final class GenericPassives {

    /**
     * Marker for interpreted stat-buff traces, exposing the buffed attribute
     * (used to avoid double-applying set properties).
     */
    public interface IsStatBuff {
        AttributeType getAttribute();
    }

    private GenericPassives() {
    }

    // ─── Trigger points ────────────────────────────────────────────────

    private enum Trigger {
        BATTLE_START, TURN_START, SKILL, ULTRA, COMMON, ANY_ACTION, KILL, DAMAGED, BREAK, DOT_TICK
    }

    /** Conditional damage-bonus conditions. */
    private enum Condition {
        ALWAYS, DEBUFFED, BROKEN, BURNING, WIND_SHEAR, SHOCKED, BLEEDING, FROZEN,
        LOW_HP_TARGET, SELF_LOW_HP, SELF_HIGH_HP,
        WEAK_FIRE, WEAK_ICE, WEAK_QUANTUM, WEAK_WIND, WEAK_THUNDER, WEAK_IMAGINARY, WEAK_PHYSICAL
    }

    // ─── Interpretation entry points ───────────────────────────────────

    /**
     * Interprets one trace node description.
     *
     * @return all implementable behaviors of the description (may be empty)
     */
    public static List<Trace> interpretTrace(String desc, List<Double> params) {
        if (desc == null || desc.isEmpty()) {
            return List.of();
        }
        return interpretAll(desc, params);
    }

    /**
     * Interprets one eidolon rank description (ranks 3/5 are pure skill levels
     * and need no behavior).
     */
    public static List<Trace> interpretEidolon(String desc, List<Double> params) {
        if (desc == null || desc.isEmpty()) {
            return List.of();
        }
        return interpretAll(desc, params);
    }

    /**
     * Interprets a light cone (光锥) passive description. Stat buffs already
     * granted permanently by the weapon's ability properties are skipped; only
     * the remaining conditional / triggered effects are kept.
     *
     * @param abilityProperties the weapon's permanent ability property attribute strings
     */
    public static Trace interpretWeaponPassive(String desc, List<Double> params, List<String> abilityProperties) {
        if (desc == null || desc.isEmpty()) {
            return null;
        }
        java.util.Set<AttributeType> propertyBases = new java.util.HashSet<>();
        for (String property : abilityProperties) {
            try {
                AttributeType type = AttributeType.fromString(property);
                propertyBases.add(com.laosun.aluminium.Constant.PERCENT_TO_BASE.getOrDefault(type, type));
            } catch (IllegalArgumentException ignored) {
            }
        }
        for (Trace trace : interpretAll(desc, params)) {
            if (trace instanceof GenericBuffOnTrigger buff) {
                AttributeType attr = buff.getAttribute();
                AttributeType base = com.laosun.aluminium.Constant.PERCENT_TO_BASE.getOrDefault(attr, attr);
                if (propertyBases.contains(base)) {
                    continue; // covered by the weapon's ability properties
                }
            }
            return trace;
        }
        return null;
    }

    // ─── The main interpreter ──────────────────────────────────────────

    /**
     * Mechanism markers that the generic engine cannot faithfully implement.
     * Descriptions containing any of these are left uninterpreted rather than
     * misapplied unconditionally (conservative: 不懂的不要瞎编).
     */
    private static final String[] GATE_MARKERS = {
            // threshold conditions
            "大于", "小于", "高于", "低于", "不足", "每超过", "每拥有", "每持有", "每触发",
            "最多叠加", "层数", "叠加", "以上", "以下", "或以上", "或以下",
            // state / stack / summon mechanics
            "状态", "结界", "标记", "召唤", "忆灵", "额外回合", "回合结束",
            "充能", "能量上限", "行动值", "攻击次数", "伤害次数", "触发次数",
            "当前生命值", "生命值降低", "生命值百分比", "消耗", "降低至", "提升至",
            "计入", "固定概率", "基础概率", "可触发", "最多触发", "触发1次",
            "【"
    };

    private static boolean isGated(String desc) {
        for (String marker : GATE_MARKERS) {
            if (desc.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static List<Trace> interpretAll(String desc, List<Double> params) {
        List<Trace> result = new ArrayList<>();
        // Aggro modifiers.
        if (desc.contains("被敌方目标攻击的概率提高")) {
            result.add(new GenericAggro(1.5));
            return result;
        }
        if (desc.contains("被敌方目标攻击的概率降低")) {
            result.add(new GenericAggro(0.5));
            return result;
        }

        // Conditional damage multipliers with a KNOWN condition bypass the gate.
        Condition knownCondition = null;
        if (desc.contains("造成的伤害提高")) {
            knownCondition = detectCondition(desc, params);
            if (knownCondition != null) {
                result.add(new GenericDamageMultiplier(value(desc, params, "#1", 0.1), knownCondition));
            }
        }
        if (knownCondition == null) {
            knownCondition = detectCondition(desc, params);
        }
        // Unknown-condition / state / stack mechanics are left uninterpreted.
        if (isGated(desc) && knownCondition == null) {
            return result;
        }

        // Battle-start / turn-start energy.
        Trigger trigger = detectTrigger(desc);
        if (trigger == null) {
            // Effects without an explicit trigger are battle-start passives.
            trigger = Trigger.BATTLE_START;
        }

        // Energy restoration.
        if (desc.contains("恢复") && desc.contains("点能量")) {
            double amount = value(desc, params, "#1", 5);
            result.add(new GenericEnergy(amount, trigger));
            return result;
        }

        // Skill point restoration.
        if (desc.contains("恢复") && desc.contains("战技点")) {
            double amount = value(desc, params, "#1", 1);
            result.add(new GenericSkillPoint(amount, trigger));
            return result;
        }

        // Action advance.
        if (desc.contains("行动提前")) {
            double ratio = value(desc, params, "#1", 0.2);
            result.add(new GenericAdvance(ratio, trigger));
            return result;
        }

        boolean team = desc.contains("我方全体");

        // Stat buffs — collectable: one description may contain several.
        if (desc.contains("速度提高")) {
            result.add(new GenericBuffOnTrigger(AttributeType.SPEED, value(desc, params, "#1", 0.1),
                    turns(desc, params), trigger, false, team));
        }
        if (desc.contains("攻击力提高")) {
            result.add(new GenericBuffOnTrigger(AttributeType.ATTACK, value(desc, params, "#1", 0.1),
                    turns(desc, params), trigger, false, team));
        }
        if (desc.contains("防御力提高")) {
            result.add(new GenericBuffOnTrigger(AttributeType.DEFENCE, value(desc, params, "#1", 0.1),
                    turns(desc, params), trigger, false, team));
        }
        if (desc.contains("暴击率提高至100%")) {
            result.add(new GenericBasicCrit100());
        } else if (desc.contains("暴击率提高")) {
            result.add(new GenericBuffOnTrigger(AttributeType.CRIT_CHANCE, value(desc, params, "#1", 0.1),
                    turns(desc, params), trigger, true, team));
        }
        if (desc.contains("暴击伤害提高")) {
            result.add(new GenericBuffOnTrigger(AttributeType.CRIT_ATTACK, value(desc, params, "#1", 0.1),
                    turns(desc, params), trigger, true, team));
        }
        if (desc.contains("效果抵抗提高")) {
            result.add(new GenericBuffOnTrigger(AttributeType.EFFECT_RESISTANCE, value(desc, params, "#1", 0.1),
                    -1, trigger, true, team));
        }
        if (desc.contains("效果命中提高")) {
            result.add(new GenericBuffOnTrigger(AttributeType.EFFECT_HIT_RATE, value(desc, params, "#1", 0.1),
                    -1, trigger, true, team));
        }
        if (desc.contains("击破特攻提高")) {
            result.add(new GenericBuffOnTrigger(AttributeType.BREAKING_EFFECT, value(desc, params, "#1", 0.1),
                    turns(desc, params), trigger, true, team));
        }
        if (desc.contains("弱点击破效率提高")) {
            result.add(new GenericBuffOnTrigger(AttributeType.WEAKNESS_BREAK_EFFICIENCY,
                    value(desc, params, "#1", 0.1), turns(desc, params), trigger, true, team));
        }
        if (desc.contains("能量恢复效率提高")) {
            result.add(new GenericBuffOnTrigger(AttributeType.ENERGY_REGENERATION_RATE,
                    value(desc, params, "#1", 0.1), -1, trigger, true, team));
        }
        if (desc.contains("抗性穿透提高")) {
            result.add(new GenericBuffOnTrigger(AttributeType.RESISTANCE_PENETRATION,
                    value(desc, params, "#1", 0.1), turns(desc, params), trigger, true, team));
        }

        // Team-wide damage boost ("我方全体...造成的伤害提高").
        if (team && desc.contains("造成的伤害提高")) {
            result.add(new GenericBuffOnTrigger(AttributeType.ALL_DAMAGE_TYPE_BOOST,
                    value(desc, params, "#1", 0.1), -1, Trigger.BATTLE_START, true, true));
        }

        // Defense ignore ("无视目标X%的防御力").
        if (desc.contains("无视") && desc.contains("防御力")) {
            result.add(new GenericBuffOnTrigger(AttributeType.DEFENCE_IGNORE, value(desc, params, "#1", 0.1),
                    -1, Trigger.BATTLE_START, true, team));
        }

        // Damage taken reduction ("受到的伤害降低X%").
        if (desc.contains("受到的伤害降低")) {
            result.add(new GenericReductionOnTrigger(value(desc, params, "#1", 0.15), turns(desc, params),
                    trigger, team));
        }

        // Always-on damage multiplier ("造成的伤害为原伤害的X%").
        if (desc.contains("造成的伤害为原伤害的")) {
            result.add(new GenericAlwaysMultiplier(value(desc, params, "#1", 1.0)));
        }

        // Fixed crit on a specific action type ("X的暴击率固定为100%").
        if (desc.contains("暴击率固定为100%")) {
            if (desc.contains("终结技")) {
                result.add(new GenericTypeCrit100(SkillType.ULTRA));
            } else if (desc.contains("普攻")) {
                result.add(new GenericTypeCrit100(SkillType.COMMON));
            }
        }

        // Action-type damage multiplier ("普攻/战技/终结技的伤害倍率提高X%").
        if (desc.contains("的伤害倍率提高") || desc.contains("的伤害提高")) {
            SkillType type = null;
            if (desc.contains("普攻")) type = SkillType.COMMON;
            else if (desc.contains("战技")) type = SkillType.SKILL;
            else if (desc.contains("终结技")) type = SkillType.ULTRA;
            if (type != null) {
                result.add(new GenericTypeMultiplier(type, value(desc, params, "#1", 0.1)));
            }
        }

        // Enemy all-res down ("全属性抗性降低X%").
        if (desc.contains("全属性抗性降低")) {
            result.add(new GenericEnemyVulnOnStart(value(desc, params, "#1", 0.1)));
        }

        // Damage taken (vulnerability) debuff on targets.
        if (desc.contains("受到的伤害提高") && desc.contains("使")) {
            result.add(new GenericVulnerabilityOnAction(value(desc, params, "#1", 0.1),
                    turns(desc, params), trigger));
        }

        // Cleansing.
        if (desc.contains("解除") && desc.contains("负面效果")) {
            result.add(new GenericCleanse(trigger));
        }

        // HoT / heal.
        if (desc.contains("回复") && desc.contains("生命值")) {
            result.add(new GenericHealOnTrigger(value(desc, params, "#1", 0.05),
                    value(desc, params, "#2", 0), trigger));
        }

        // Shield.
        if (desc.contains("护盾")) {
            result.add(new GenericShieldOnTrigger(trigger));
        }

        return result;
    }

    // ─── Parsing helpers ───────────────────────────────────────────────

    private static final Pattern PARAM_PATTERN = Pattern.compile("#(\\d+)\\[[a-z0-9]+\\]");

    /**
     * Reads the parameter referenced by the given token (e.g. "#1") from the
     * description's param list.
     */
    private static double value(String desc, List<Double> params, String token, double fallback) {
        Matcher m = PARAM_PATTERN.matcher(desc);
        while (m.find()) {
            if (m.group(0).startsWith(token + "[")) {
                int index = Integer.parseInt(m.group(1)) - 1;
                if (params != null && index >= 0 && index < params.size()) {
                    return params.get(index);
                }
            }
        }
        return fallback;
    }

    /** The duration in turns, if the description mentions "持续#N[i]回合". */
    private static int turns(String desc, List<Double> params) {
        Matcher m = PARAM_PATTERN.matcher(desc);
        int lastIndex = -1;
        while (m.find()) {
            String full = m.group(0);
            int idx = desc.indexOf("持续");
            if (idx >= 0 && desc.indexOf(full, idx) >= 0) {
                lastIndex = Integer.parseInt(m.group(1)) - 1;
            }
        }
        if (lastIndex >= 0 && params != null && lastIndex < params.size()) {
            return (int) Math.round(params.get(lastIndex));
        }
        return -1; // permanent
    }

    private static Trigger detectTrigger(String desc) {
        if (desc.contains("战斗开始时") || desc.contains("进入战斗时")) {
            return Trigger.BATTLE_START;
        }
        if (desc.contains("回合开始时") || desc.contains("回合开始")) {
            return Trigger.TURN_START;
        }
        if (desc.contains("施放终结技")) {
            return Trigger.ULTRA;
        }
        if (desc.contains("施放战技")) {
            return Trigger.SKILL;
        }
        if (desc.contains("施放普攻") || desc.contains("施放攻击")) {
            return Trigger.COMMON;
        }
        if (desc.contains("消灭敌方目标")) {
            return Trigger.KILL;
        }
        if (desc.contains("受到攻击")) {
            return Trigger.DAMAGED;
        }
        if (desc.contains("被击破") || desc.contains("弱点击破")) {
            return Trigger.BREAK;
        }
        if (desc.contains("施放")) {
            return Trigger.ANY_ACTION;
        }
        return null;
    }

    private static Condition detectCondition(String desc, List<Double> params) {
        if (desc.contains("弱点击破状态")) {
            return Condition.BROKEN;
        }
        if (desc.contains("负面效果")) {
            return Condition.DEBUFFED;
        }
        if (desc.contains("灼烧状态")) {
            return Condition.BURNING;
        }
        if (desc.contains("风化状态")) {
            return Condition.WIND_SHEAR;
        }
        if (desc.contains("触电状态")) {
            return Condition.SHOCKED;
        }
        if (desc.contains("裂伤状态")) {
            return Condition.BLEEDING;
        }
        if (desc.contains("冻结状态")) {
            return Condition.FROZEN;
        }
        if (desc.contains("生命值百分比小于等于")) {
            return Condition.LOW_HP_TARGET;
        }
        if (desc.contains("当前生命值百分比大于等于")) {
            return Condition.SELF_HIGH_HP;
        }
        if (desc.contains("当前生命值百分比小于等于")) {
            return Condition.SELF_LOW_HP;
        }
        for (Element element : Element.values()) {
            String weakName = switch (element) {
                case FIRE -> "火属性弱点";
                case ICE -> "冰属性弱点";
                case WIND -> "风属性弱点";
                case THUNDER -> "雷属性弱点";
                case QUANTUM -> "量子属性弱点";
                case IMAGINARY -> "虚数属性弱点";
                case PHYSICAL -> "物理属性弱点";
            };
            if (desc.contains(weakName)) {
                return switch (element) {
                    case FIRE -> Condition.WEAK_FIRE;
                    case ICE -> Condition.WEAK_ICE;
                    case WIND -> Condition.WEAK_WIND;
                    case THUNDER -> Condition.WEAK_THUNDER;
                    case QUANTUM -> Condition.WEAK_QUANTUM;
                    case IMAGINARY -> Condition.WEAK_IMAGINARY;
                    case PHYSICAL -> Condition.WEAK_PHYSICAL;
                };
            }
        }
        return null;
    }

    // ─── Generic implementations ───────────────────────────────────────

    /** Self or team stat buff applied at a trigger point. */
    private static class GenericBuffOnTrigger implements Trace, IsStatBuff {
        private final AttributeType attribute;
        private final double percent;
        private final int turns;
        private final Trigger trigger;
        private final boolean percentagePoint;
        private final boolean team;

        GenericBuffOnTrigger(AttributeType attribute, double percent, int turns, Trigger trigger,
                             boolean percentagePoint, boolean team) {
            this.attribute = attribute;
            this.percent = percent;
            this.turns = turns;
            this.trigger = trigger;
            this.percentagePoint = percentagePoint;
            this.team = team;
        }

        @Override
        public String getName() {
            return (team ? "team " : "") + attribute.attributeString + " buff";
        }

        @Override
        public AttributeType getAttribute() {
            return attribute;
        }

        private void apply(Battle battle, Character owner) {
            List<? extends CanHit> targets = team
                    ? battle.getAlivePlayerUnits()
                    : List.of(owner);
            for (CanHit target : targets) {
                if (target instanceof Character character) {
                    character.removeBuff(getName());
                }
                DoubleValue.Modifier modifier = percentagePoint
                        ? DoubleValue.Modifier.pure(percent, DoubleValue.Modifier.ModifierSource.BUFF)
                        : DoubleValue.Modifier.addPercent(percent, DoubleValue.Modifier.ModifierSource.BUFF);
                Buff buff = new Buff(getName(), Buff.Category.BUFF, owner, target, turns).stat(attribute, modifier);
                battle.applyBuff(target, buff);
            }
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            if (trigger == Trigger.BATTLE_START) {
                apply(battle, owner);
            }
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            if (trigger == Trigger.TURN_START) {
                apply(battle, owner);
            }
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (trigger == Trigger.ANY_ACTION || trigger == Trigger.SKILL && type == SkillType.SKILL
                    || trigger == Trigger.ULTRA && type == SkillType.ULTRA
                    || trigger == Trigger.COMMON && type == SkillType.COMMON) {
                apply(battle, owner);
            }
        }
    }

    /** Energy restoration at a trigger point. */
    private static class GenericEnergy implements Trace {
        private final double amount;
        private final Trigger trigger;

        GenericEnergy(double amount, Trigger trigger) {
            this.amount = amount;
            this.trigger = trigger;
        }

        @Override
        public String getName() {
            return "energy+" + (int) amount;
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            if (trigger == Trigger.BATTLE_START) {
                owner.gainEnergy(amount);
            }
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            if (trigger == Trigger.TURN_START) {
                owner.gainEnergy(amount);
            }
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (trigger == Trigger.ANY_ACTION || trigger == Trigger.SKILL && type == SkillType.SKILL
                    || trigger == Trigger.ULTRA && type == SkillType.ULTRA
                    || trigger == Trigger.COMMON && type == SkillType.COMMON) {
                owner.gainEnergy(amount);
            }
        }

        @Override
        public void onKill(Battle battle, Character owner, CanHit victim) {
            if (trigger == Trigger.KILL && victim instanceof Enemy) {
                owner.gainEnergy(amount);
            }
        }

        @Override
        public void onEnemyBreak(Battle battle, Character owner, Enemy enemy) {
            if (trigger == Trigger.BREAK) {
                owner.gainEnergy(amount);
            }
        }

        @Override
        public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
            if (trigger == Trigger.DAMAGED) {
                owner.gainEnergy(amount);
            }
        }

        @Override
        public void onDotDamage(Battle battle, Character owner, CanHit victim, double damage) {
            if (trigger == Trigger.DOT_TICK) {
                owner.gainEnergy(amount);
            }
        }
    }

    /** Action advance at a trigger point. */
    private static class GenericAdvance implements Trace {
        private final double ratio;
        private final Trigger trigger;

        GenericAdvance(double ratio, Trigger trigger) {
            this.ratio = ratio;
            this.trigger = trigger;
        }

        @Override
        public String getName() {
            return "advance+" + (int) (ratio * 100) + "%";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            if (trigger == Trigger.BATTLE_START) {
                battle.advanceByPercent(owner, ratio);
            }
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (trigger == Trigger.ANY_ACTION || trigger == Trigger.SKILL && type == SkillType.SKILL
                    || trigger == Trigger.ULTRA && type == SkillType.ULTRA
                    || trigger == Trigger.COMMON && type == SkillType.COMMON) {
                battle.advanceByPercent(owner, ratio);
            }
        }

        @Override
        public void onKill(Battle battle, Character owner, CanHit victim) {
            if (trigger == Trigger.KILL && victim instanceof Enemy) {
                battle.advanceByPercent(owner, ratio);
            }
        }
    }

    /** Conditional damage multiplier. */
    private static class GenericDamageMultiplier implements Trace {
        private final double bonus;
        private final Condition condition;

        GenericDamageMultiplier(double bonus, Condition condition) {
            this.bonus = bonus;
            this.condition = condition;
        }

        @Override
        public String getName() {
            return "dmg+" + (int) (bonus * 100) + "%";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return matches(defender, owner) ? 1 + bonus : 1.0;
        }

        private boolean matches(CanHit defender, Character owner) {
            return switch (condition) {
                case ALWAYS -> true;
                case DEBUFFED -> defender.hasDebuff();
                case BROKEN -> defender instanceof Enemy enemy && enemy.isBroken();
                case BURNING -> defender.hasDotOfElement(Element.FIRE);
                case WIND_SHEAR -> defender.hasDotOfElement(Element.WIND);
                case SHOCKED -> defender.hasDotOfElement(Element.THUNDER);
                case BLEEDING -> defender.hasDotOfElement(Element.PHYSICAL);
                case FROZEN -> defender.getControlState() == Buff.ControlType.FROZEN;
                case LOW_HP_TARGET -> defender.getHpPercent() <= 0.5;
                case SELF_LOW_HP -> owner.getHpPercent() <= 0.5;
                case SELF_HIGH_HP -> owner.getHpPercent() >= 0.8;
                case WEAK_FIRE -> defender instanceof Enemy e && e.isWeakTo(Element.FIRE);
                case WEAK_ICE -> defender instanceof Enemy e && e.isWeakTo(Element.ICE);
                case WEAK_WIND -> defender instanceof Enemy e && e.isWeakTo(Element.WIND);
                case WEAK_THUNDER -> defender instanceof Enemy e && e.isWeakTo(Element.THUNDER);
                case WEAK_QUANTUM -> defender instanceof Enemy e && e.isWeakTo(Element.QUANTUM);
                case WEAK_IMAGINARY -> defender instanceof Enemy e && e.isWeakTo(Element.IMAGINARY);
                case WEAK_PHYSICAL -> defender instanceof Enemy e && e.isWeakTo(Element.PHYSICAL);
            };
        }
    }

    /** Aggro modifier (被攻击概率). */
    private static class GenericAggro implements Trace {
        private final double multiplier;

        GenericAggro(double multiplier) {
            this.multiplier = multiplier;
        }

        @Override
        public String getName() {
            return "aggro x" + multiplier;
        }

        @Override
        public double aggroMultiplier(Battle battle, Character owner) {
            return multiplier;
        }
    }

    /** Cleanses debuffs at a trigger point. */
    private static class GenericCleanse implements Trace {
        private final Trigger trigger;

        GenericCleanse(Trigger trigger) {
            this.trigger = trigger;
        }

        @Override
        public String getName() {
            return "cleanse";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (trigger == Trigger.SKILL && type == SkillType.SKILL
                    || trigger == Trigger.ULTRA && type == SkillType.ULTRA
                    || trigger == Trigger.ANY_ACTION) {
                owner.getBuffs().stream()
                        .filter(b -> b.getCategory() == Buff.Category.DEBUFF)
                        .findFirst()
                        .ifPresent(buff -> battle.removeBuff(owner, buff));
            }
        }

        @Override
        public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
            if (trigger == Trigger.DAMAGED) {
                owner.getBuffs().stream()
                        .filter(b -> b.getCategory() == Buff.Category.DEBUFF)
                        .findFirst()
                        .ifPresent(buff -> battle.removeBuff(owner, buff));
            }
        }
    }

    /** Heal at a trigger point. */
    private static class GenericHealOnTrigger implements Trace {
        private final double ratio;
        private final double flat;
        private final Trigger trigger;

        GenericHealOnTrigger(double ratio, double flat, Trigger trigger) {
            this.ratio = ratio;
            this.flat = flat;
            this.trigger = trigger;
        }

        @Override
        public String getName() {
            return "heal";
        }

        @Override
        public void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
            if (trigger == Trigger.DAMAGED) {
                owner.heal(owner.getMaxHp() * ratio + flat);
            }
        }

        @Override
        public void onKill(Battle battle, Character owner, CanHit victim) {
            if (trigger == Trigger.KILL && victim instanceof Enemy) {
                owner.heal(owner.getMaxHp() * ratio + flat);
            }
        }
    }

    /** Shield at a trigger point. */
    private static class GenericShieldOnTrigger implements Trace {
        private final Trigger trigger;

        GenericShieldOnTrigger(Trigger trigger) {
            this.trigger = trigger;
        }

        @Override
        public String getName() {
            return "shield";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            if (trigger == Trigger.BATTLE_START) {
                battle.applyShield(owner, owner.getMaxHp() * 0.2, owner);
            }
        }
    }

    /** Basic attack always crits. */
    private static class GenericBasicCrit100 implements Trace {
        @Override
        public String getName() {
            return "basic crit 100%";
        }

        @Override
        public double critChanceBonus(Battle battle, Character owner, CanHit defender, SkillType type) {
            return type == SkillType.COMMON ? 1.0 : 0;
        }
    }

    /** Applies vulnerability to targets when acting. */
    private static class GenericVulnerabilityOnAction implements Trace {
        private final double percent;
        private final int turns;
        private final Trigger trigger;

        GenericVulnerabilityOnAction(double percent, int turns, Trigger trigger) {
            this.percent = percent;
            this.turns = turns;
            this.trigger = trigger;
        }

        @Override
        public String getName() {
            return "vuln+" + (int) (percent * 100) + "%";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (trigger != Trigger.SKILL || type != SkillType.SKILL) {
                return;
            }
            for (CanHit target : targets) {
                Buff buff = new Buff(getName(), Buff.Category.DEBUFF, owner, target, turns)
                        .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(percent,
                                DoubleValue.Modifier.ModifierSource.DEBUFF));
                battle.applyBuff(target, buff);
            }
        }
    }

    /** Skill point restoration at a trigger point. */
    private static class GenericSkillPoint implements Trace {
        private final double amount;
        private final Trigger trigger;

        GenericSkillPoint(double amount, Trigger trigger) {
            this.amount = amount;
            this.trigger = trigger;
        }

        @Override
        public String getName() {
            return "SP+" + (int) amount;
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (trigger == Trigger.SKILL && type == SkillType.SKILL
                    || trigger == Trigger.ULTRA && type == SkillType.ULTRA
                    || trigger == Trigger.ANY_ACTION) {
                battle.addSkillPoints((int) amount);
            }
        }
    }

    /** Damage reduction (减伤) buff on a trigger point. */
    private static class GenericReductionOnTrigger implements Trace {
        private final double percent;
        private final int turns;
        private final Trigger trigger;
        private final boolean team;

        GenericReductionOnTrigger(double percent, int turns, Trigger trigger, boolean team) {
            this.percent = percent;
            this.turns = turns;
            this.trigger = trigger;
            this.team = team;
        }

        @Override
        public String getName() {
            return (team ? "team " : "") + "damage reduction";
        }

        private void apply(Battle battle, Character owner) {
            List<? extends CanHit> targets = team ? battle.getAlivePlayerUnits() : List.of(owner);
            for (CanHit target : targets) {
                if (target instanceof Character character) {
                    character.removeBuff(getName());
                }
                Buff buff = new Buff(getName(), Buff.Category.BUFF, owner, target, turns)
                        .stat(AttributeType.DAMAGE_REDUCTION, DoubleValue.Modifier.multiplyPercent(-percent,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                battle.applyBuff(target, buff);
            }
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            if (trigger == Trigger.BATTLE_START) {
                apply(battle, owner);
            }
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (trigger == Trigger.SKILL && type == SkillType.SKILL
                    || trigger == Trigger.ULTRA && type == SkillType.ULTRA
                    || trigger == Trigger.ANY_ACTION) {
                apply(battle, owner);
            }
        }
    }

    /** Always-on damage multiplier ("造成的伤害为原伤害的X%"). */
    private static class GenericAlwaysMultiplier implements Trace {
        private final double multiplier;

        GenericAlwaysMultiplier(double multiplier) {
            this.multiplier = multiplier;
        }

        @Override
        public String getName() {
            return "dmg x" + String.format("%.2f", multiplier);
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return multiplier;
        }
    }

    /** Fixed 100% crit chance for one action type. */
    private static class GenericTypeCrit100 implements Trace {
        private final SkillType type;

        GenericTypeCrit100(SkillType type) {
            this.type = type;
        }

        @Override
        public String getName() {
            return type + " crit 100%";
        }

        @Override
        public double critChanceBonus(Battle battle, Character owner, CanHit defender, SkillType type) {
            return type == this.type ? 1.0 : 0;
        }
    }

    /** Damage multiplier for a specific action type. */
    private static class GenericTypeMultiplier implements Trace {
        private final SkillType type;
        private final double bonus;

        GenericTypeMultiplier(SkillType type, double bonus) {
            this.type = type;
            this.bonus = bonus;
        }

        @Override
        public String getName() {
            return type + " dmg+" + (int) (bonus * 100) + "%";
        }

        @Override
        public double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
            return type == this.type ? 1 + bonus : 1.0;
        }
    }

    /** Enemy all-res down at battle start (approximated as vulnerability). */
    private static class GenericEnemyVulnOnStart implements Trace {
        private final double percent;

        GenericEnemyVulnOnStart(double percent) {
            this.percent = percent;
        }

        @Override
        public String getName() {
            return "enemy vuln+" + (int) (percent * 100) + "%";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            for (Enemy enemy : battle.getAliveEnemies()) {
                Buff buff = new Buff(getName(), Buff.Category.DEBUFF, owner, enemy, -1)
                        .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(percent,
                                DoubleValue.Modifier.ModifierSource.DEBUFF));
                battle.applyBuff(enemy, buff);
            }
        }
    }
}
