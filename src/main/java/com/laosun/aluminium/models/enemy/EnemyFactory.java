package com.laosun.aluminium.models.enemy;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.EnemySkillData;
import com.laosun.aluminium.beans.HardLevelGroup;
import com.laosun.aluminium.beans.MonsterConfig;
import com.laosun.aluminium.beans.MonsterTemplate;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillEffectType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.utils.AttributeBuilder;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.laosun.aluminium.enums.AttributeType.*;

/**
 * Builds an {@link Enemy} with a correct stat sheet from real monster data (P2-4).
 *
 * <pre>
 * EnemyFactory.create(1002011, 90, 1)
 *   → monster_config[1002011] × monster_template_config[1002011] × hard_level_group[1][90]
 *   → Enemy (attributes / level / resistance / weaknesses / toughness)
 * </pre>
 *
 * <p>This stage does **numbers** only: weaknesses and toughness merely move data onto the
 * {@code Enemy}; toughness reduction and weakness break are in P4. Debuff immunity
 * ({@code debuff_resistance}) is wired up in P6-1, the phase table in P9-5, and the summon roster in P9-4.
 *
 * <p>Both the level and the level group come from the **stage** ({@code StageConfig}), so they are
 * passed in by the caller; the stage-driven part is left to P7-4.
 *
 * <p><b>Why the lookup is a separate step.</b> Since P9-4 the same monster data also builds
 * {@link com.laosun.aluminium.models.Summon}s ({@code SummonFactory}), which need exactly "which config,
 * which template, what does it scale to" and none of the {@link Enemy}-only fields. {@link #resolve} is
 * that shared half, so the two factories cannot drift apart on the scaling rules or the error messages.
 */
public final class EnemyFactory {

    private EnemyFactory() {
    }

    /**
     * A monster id resolved to everything that can be known about it before a combatant exists.
     *
     * @param monsterId the instance id (kept so error messages and skills can name it)
     * @param config    the per-instance data
     * @param template  the template
     * @param stats     the scaled stat sheet
     */
    record Resolved(int monsterId, MonsterConfig config, MonsterTemplate template, EnemyStats stats) {
    }

    /**
     * Looks the monster up and scales it, without deciding what kind of combatant to build.
     *
     * @param monsterId      a key of {@code monster_config.json}, e.g. 1002011 (Ice Edge 冰锋)
     * @param level          the stage level
     * @param hardLevelGroup the stage's hard level group (a monster's own {@code hard_level_group}
     *                       is usually just 1; what really decides difficulty is the stage)
     * @return the resolved data
     * @throws IllegalArgumentException if the instance / template / level-group level does not exist
     */
    static Resolved resolve(int monsterId, int level, int hardLevelGroup) {
        MonsterConfig config = Constant.MONSTER_CONFIGS.get(monsterId);
        if (config == null) {
            throw new IllegalArgumentException("Unknown monster: " + monsterId);
        }
        MonsterTemplate template = Constant.MONSTER_TEMPLATES.get(config.templateId());
        if (template == null) {
            throw new IllegalArgumentException("Unknown monster template: " + config.templateId()
                    + " (monster " + monsterId + ")");
        }
        HardLevelGroup group = Constant.HARD_LEVEL_GROUPS.getOrDefault(hardLevelGroup, Map.of()).get(level);
        if (group == null) {
            throw new IllegalArgumentException("No hard level group " + hardLevelGroup + " at level " + level);
        }
        return new Resolved(monsterId, config, template, EnemyScaler.scale(template, config, group));
    }

    /**
     * Builds an enemy from "instance id + level + level group".
     *
     * @param monsterId      a key of {@code monster_config.json}, e.g. 1002011 (Ice Edge 冰锋)
     * @param level          the stage level
     * @param hardLevelGroup the stage's hard level group (a monster's own {@code hard_level_group}
     *                       is usually just 1; what really decides difficulty is the stage)
     * @return an enemy with a correct stat sheet (HP is the final max HP)
     * @throws IllegalArgumentException if the instance / template / level-group level does not exist
     */
    public static Enemy create(int monsterId, int level, int hardLevelGroup) {
        Resolved resolved = resolve(monsterId, level, hardLevelGroup);
        MonsterConfig config = resolved.config();
        MonsterTemplate template = resolved.template();

        Enemy enemy = new Enemy(displayName(config, monsterId), statSheet(resolved.stats()).build());
        enemy.setLevel(level);                                  // P1-4: level enters the defence zone
        enemy.setDamageResist(config.damageResistance());       // P1-6: the resistance zone takes effect directly
        enemy.setDebuffResist(config.debuffResistance());        // P6-1: specific debuff resistance
        enemy.setStanceWeak(Set.copyOf(config.stanceWeak()));   // P2-2: weaknesses (used by P4's toughness-reduction check)
        enemy.setStance(resolved.stats().stance());
        enemy.setMaxStance(resolved.stats().stance());
        enemy.setStanceCount(template.stanceCount());
        enemy.setStanceType(template.stanceType());
        enemy.setSummonIds(List.copyOf(config.summonIds()));    // P9-4: what this monster may bring in
        enemy.setSkill(SkillType.COMMON, enemySkillFor(monsterId, template));
        return enemy;
    }

    /**
     * The base attribute sheet every monster-derived combatant shares.
     *
     * <p>Shared with {@code SummonFactory} so that "which attributes a monster's data fills in" has one
     * answer: a summon has its own HP/ATK/DEF/SPD and effect hit/resistance like any other unit, and only
     * the monster-<em>only</em> columns (resistances, weaknesses, toughness, phase table) are left out.
     */
    static AttributeBuilder statSheet(EnemyStats stats) {
        return new AttributeBuilder()
                .setBase(HEALTH, stats.hp())
                .setBase(ATTACK, stats.attack())
                .setBase(DEFENCE, stats.defence())
                .setBase(SPEED, stats.speed())
                .setBase(EFFECT_RESISTANCE, stats.effectResistance())
                // P6-1: effect hit rate MUST land in the stat sheet, otherwise the enemy's hit rate is always 0 (EnemyStats computes it but nobody used it)
                .setBase(EFFECT_HIT_RATE, stats.effectHitRate());
    }

    /**
     * Builds the skill an enemy acts with (P5-3).
     *
     * <p>The skill comes from {@code enemy_skills.json} ({@link Constant#ENEMY_SKILLS}).
     * That table **covers only the few monsters used for the demo**; other monsters have no entry —
     * in that case it falls back to a default basic attack: multiplier 1.0, single hit, element
     * taken from the monster's own {@code stance_type}. That way "any monster can hit people" and
     * it will not just stand there doing nothing because there is no data.
     *
     * <p>Element resolution order: the table's {@code element} → the monster's own
     * {@code stance_type} → physical ({@code stance_type} may also be null; the data really does
     * contain such entries).
     *
     * <p>⚠ The multipliers are guesses, see {@link com.laosun.aluminium.beans.EnemySkillData}.
     *
     * @param monsterId the monster the data is keyed by (a summon passes its own id, not its master's)
     * @param template  the monster's template, for the element fallback
     * @return the skill, never {@code null}
     */
    static Skill enemySkillFor(int monsterId, MonsterTemplate template) {
        EnemySkillData data = Constant.ENEMY_SKILLS.get(monsterId);
        DamageElement element = data == null ? null : data.element();
        if (element == null) {
            element = template.stanceType() == null ? null : template.stanceType();
        }
        if (element == null) {
            element = DamageElement.PHYSICAL;
        }
        DamageType type = data == null || data.damageType() == null
                ? DamageType.NORMAL
                : DamageType.fromString(data.damageType());
        double multiplier = data == null ? 1.0 : data.multiplier();
        int hits = data == null ? 1 : data.hits();
        SkillEffectType effect = data == null || data.effect() == null
                ? SkillEffectType.SINGLE_ATTACK
                : SkillEffectType.fromString(data.effect());
        return new EnemySkill(element, multiplier, hits, type, effect);
    }

    private static String displayName(MonsterConfig config, int monsterId) {
        return config.name() == null ? "Monster#" + monsterId : config.name().chinese();
    }
}
