package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.ServantConfig;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.enums.SkillType;
import lombok.Getter;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.laosun.aluminium.enums.AttributeType.*;

/**
 * A 忆灵 (memosprite): an independent combat unit summoned by its owner (HSR.md §2).
 *
 * <p>Stats come from the real data (HSR.md §2.1: 生命和速度由天赋单独决定):
 * <ul>
 *   <li>面板继承: summoner's out-of-battle panel (ATK/DEF/crit/...) at summon time</li>
 *   <li>HP/SPD: resolved from the summoner's talent params via the placeholder
 *   formulas in servant_config.json (e.g. 衣匠: 44% 阿格莱雅生命上限 + 180)</li>
 *   <li>Skills: the real 忆灵技能 from servant_skills.json (Skill01 忆灵普攻,
 *   Skill02 忆灵技, passives ...)</li>
 *   <li>No energy bar: the data has no 终结技 energy (BPNeed = -1)</li>
 * </ul>
 *
 * <p>Damage type is 忆灵伤害 (HSR.md §4.2). The memosprite can be targeted by
 * allies and enemies.
 */
@Getter
public class Summon extends CanHit {

    /** The character that summoned this memosprite. */
    private final CanHit owner;
    /** The servant config from servant_config.json. */
    private final ServantConfig config;
    /** The 忆灵技 (secondary skill, Skill02) if the data provides one. */
    private final Skill secondarySkill;

    /**
     * Returns the character that summoned this memosprite.
     */
    public CanHit getOwner() {
        return owner;
    }

    /**
     * Creates a memosprite from its owner.
     *
     * @param owner      the summoner (a Memory-path character)
     * @param servantId  the servant ID (e.g. 11402)
     */
    public Summon(CanHit owner, int servantId) {
        super(resolveName(servantId), Camp.PLAYER, buildAttributes(owner, servantId));
        this.owner = owner;
        this.config = Constant.SERVANT_CONFIGS.get(servantId);
        setLevel(owner.getLevel());
        setElement(owner.getElement());
        setAggro(config != null ? config.aggro() : 100);
        setMaxEnergy(0); // 忆灵无能量条 (data: BPNeed = -1)
        setSkill(SkillType.SUMMON_SKILL, new ServantSkill(servantId, 1, 1));
        // 忆灵技 (Skill02, e.g. 德谬歌 此诗献予一切生命).
        if (hasSkillIndex(servantId, 2)) {
            this.secondarySkill = servantId == 11415
                    ? new CyreneServantSkill(servantId, 2, 1)
                    : new ServantSkill(servantId, 2, 1);
            setSkill(SkillType.SUMMON_TALENT, this.secondarySkill);
        } else {
            this.secondarySkill = null;
        }
    }

    private static String resolveName(int servantId) {
        ServantConfig config = Constant.SERVANT_CONFIGS.get(servantId);
        return config != null && config.name() != null && config.name().english() != null
                ? config.name().english() : "Memosprite";
    }

    private static boolean hasSkillIndex(int servantId, int index) {
        return Constant.SERVANT_SKILLS.get(servantId) != null
                && Constant.SERVANT_SKILLS.get(servantId).containsKey(index);
    }

    // ─── 面板: HP/SPD from the summoner's talent (HSR.md §2.1) ─────────

    private static final Pattern PLACEHOLDER = Pattern.compile("#(\\d+)");

    /**
     * Snapshot-inherits the summoner's panel and resolves HP/SPD from the
     * summoner's talent params using the servant config's placeholder formulas.
     */
    private static DoubleValue[] buildAttributes(CanHit owner, int servantId) {
        ServantConfig config = Constant.SERVANT_CONFIGS.get(servantId);
        DoubleValue[] inherited = owner.getAttributes();
        DoubleValue[] attributes = new DoubleValue[inherited.length];
        for (int i = 0; i < inherited.length; i++) {
            attributes[i] = inherited[i] != null ? inherited[i].clone() : null;
        }
        // The placeholder formulas resolve against the params of the skill
        // referenced by HPSkill / SpeedSkill (usually the talent or summon skill).
        List<Double> hpParams = skillParams(owner, config != null ? config.hpSkill() : null);
        List<Double> speedParams = skillParams(owner, config != null ? config.speedSkill() : null);

        double hpInherit = resolve(config != null ? config.hpInherit() : null, hpParams, 1.0);
        double hpBase = resolve(config != null ? config.hpBase() : null, hpParams, 0);
        double hp = owner.getMaxHp() * hpInherit + hpBase;
        if (hp <= 0) {
            // 死龙等无面板公式的忆灵 (HP 由特殊机制决定): 以召唤者生命兜底.
            hp = owner.getMaxHp();
        }
        attributes[HEALTH.ordinal()] = new DoubleValue(hp);

        double speedBase = resolve(config != null ? config.speedBase() : null, speedParams, 0);
        if (speedBase > 0) {
            attributes[SPEED.ordinal()] = new DoubleValue(speedBase);
        } else {
            double speedInherit = resolve(config != null ? config.speedInherit() : null, speedParams, 1.0);
            double speed = owner.getAttribute(AttributeType.SPEED).get() * speedInherit;
            if (speed <= 0) {
                // Some servant configs carry no speed formula (小伊卡/德谬歌);
                // fall back to the summoner's speed so the unit can act.
                speed = owner.getAttribute(AttributeType.SPEED).get();
            }
            attributes[SPEED.ordinal()] = new DoubleValue(speed);
        }
        return attributes;
    }

    /** Params of the skill referenced by a config skill ID (e.g. 140703 → 遐蝶终结技). */
    private static List<Double> skillParams(CanHit owner, Integer skillId) {
        if (skillId == null || !(owner instanceof Character character)) {
            return List.of();
        }
        SkillType type = switch (skillId % 100) {
            case 1 -> SkillType.COMMON;
            case 2 -> SkillType.SKILL;
            case 3 -> SkillType.ULTRA;
            default -> SkillType.TALENT;
        };
        Skill skill = character.getSkills().get(type);
        if (skill != null && skill.getData() != null && skill.getData().getSkills() != null
                && !skill.getData().getSkills().isEmpty()) {
            return skill.getData().getSkills().get(0);
        }
        return List.of();
    }

    /**
     * Resolves a "#N" placeholder against the talent params; plain numbers pass
     * through; missing values use the fallback.
     */
    private static double resolve(String placeholder, List<Double> talentParams, double fallback) {
        if (placeholder == null || placeholder.isEmpty()) {
            return fallback;
        }
        try {
            return Double.parseDouble(placeholder.trim());
        } catch (NumberFormatException ignored) {
        }
        Matcher matcher = PLACEHOLDER.matcher(placeholder);
        if (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1)) - 1;
            if (index >= 0 && index < talentParams.size()) {
                return talentParams.get(index);
            }
        }
        return fallback;
    }

    @Override
    public String toString() {
        return "Summon[" + getName() + "]";
    }
}
