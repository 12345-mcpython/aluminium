package com.laosun.aluminium.entity;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Camp;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import com.laosun.aluminium.DoubleValue;

/**
 * A summoned entity that participates in combat.
 *
 * <p>Summons are aligned with whoever called them: an enemy boss's minions fight alongside the monsters in
 * {@code Battle.enemies}, and a player-side summon would join our own side.
 *
 * <p><b>Where they come from.</b> {@code SummonFactory} builds one from the same monster data
 * ({@code monster_config.json}) an {@link com.laosun.aluminium.enemy.Enemy} is built from, and
 * {@code Battle.summon(master, summonId, group)} is what puts it on the field - into <b>the master's own
 * camp</b>: an enemy's minion joins {@code Battle.enemies}, ours joins {@code Battle.allies}. The roster of
 * "which monsters may be summoned" lives on the master
 * ({@link com.laosun.aluminium.enemy.Enemy#getSummonIds()}).
 *
 * <p><b>What a summon is not (yet).</b> It is a {@code CanHit}, not an {@code Enemy}, so it has a stat
 * sheet, a level, a skill and buffs, but <b>none of the monster-only mechanics</b>: no toughness bar
 * (nothing to break), no weakness list, no per-element damage resistance, no specific debuff resistance
 * and no phase table. That split is the camp split - the enemy camp holds any {@code CanHit} while
 * {@code Battle.enemyUnits()} is "the monsters in it" - and it is why those mechanics say
 * {@code instanceof Enemy} at their call sites instead of assuming every enemy-camp unit has them.
 *
 * <p><b>Still open</b> (all of it content or mechanism rather than placement):
 * memosprites (忆灵) as they are actually described - a stat <b>snapshot</b> of the summoner and joint
 * attacks - and whatever decides <em>when</em> a roster entry is used, since an enemy skill's {@code SUMMON}
 * effect needs a "what to summon" column this data does not have (see {@code SkillEffectType.SUMMON}).
 */
@Getter
@Setter
@ToString(callSuper = true)
public class Summon extends CanHit {

    /**
     * This memosprite's own skills, keyed by their <b>data slot</b> -- the number `SkillEffects` and `DefaultSkill` use.
     *
     * <p>Note: Not {@code CanHit.skills}: that map is keyed by {@link com.laosun.aluminium.enums.SkillType}, which has exactly two
     * summon entries (亿灵技能 / 亿灵天赋; memosprite skill / memosprite talent), and a memosprite's own skill list runs to eighteen.
     */
    private final java.util.Map<Integer, com.laosun.aluminium.skill.Skill> skillsByDataSlot =
            new java.util.LinkedHashMap<>();

    /**
     * Attaches one of this memosprite's own skills, addressed the way the data addresses it.
     */
    public void setSkillAt(int dataSlot, com.laosun.aluminium.skill.Skill skill) {
        if (skill != null) {
            skillsByDataSlot.put(dataSlot, skill);
        }
    }

    /**
     * This memosprite's own skill at that data slot, or {@code null}.
     */
    public com.laosun.aluminium.skill.Skill skillAt(int dataSlot) {
        return skillsByDataSlot.get(dataSlot);
    }

    /**
     * Every own skill of this memosprite, by data slot.
     */
    public java.util.Map<Integer, com.laosun.aluminium.skill.Skill> skillsByDataSlot() {
        return java.util.Map.copyOf(skillsByDataSlot);
    }

    /**
     * Who called this summon, or {@code null} for a hand-built one.
     *
     * <p>The link is one-way and lives here rather than as a list on the master because its only job is the
     * lifecycle: <b>a summon leaves when its master does</b> ({@code Battle.removeDeadCombatants}). Keeping
     * the pointer on the summon means "who owns this" is answerable from the summon alone, with no registry
     * to keep in sync - and no way for a summon to outlive its owner because a list somewhere was not
     * cleaned up.
     */
    private CanHit master;

    /**
     * A summon's 「%生命上限」 damage reads the <b>summoner's</b> Max HP, not its own.
     *
     * <p><b>Why this is not the servant's own sheet.</b> Three sources agree, and they were checked in this order:
     * <ol>
     *   <li>the recorded fight: every memosprite cast's {@code raw_damage_before_modifiers} is exactly
     *       {@code row share × the summoner's 生命上限} - 长夜 0.06 × 2960.0287, 晴空乐手 1.5 × 5249.7811 - including
     *       three different panels in one window ({@code scenarios/veritas-085418.replay.json});</li>
     *   <li>{@code ConfigEntity}'s {@code ServantConfig.SyncPropertyExceptList} names the properties that are
     *       <b>not</b> synced from the summoner: for 长夜 it is speed alone, so {@code MaxHP} follows the summoner's;
     *       the two servants that keep their own HP (死龙 {@code Castorice}, 德谬歌 {@code Cyrene}) list {@code MaxHP};</li>
     *   <li>and the ability files follow the same split: every servant uses {@code "FormulaType": "ByMaxHP"} except
     *       死龙, the one whose HP is its own - it is the only user of {@code "BySummonerMaxHP"}.</li>
     * </ol>
     * The {@code memosprites/<cid>.json} share (0.5 for 长夜, 0.7 for 晴空乐手) is what the servant's <b>own HP bar</b>
     * starts at, and the engine still uses it there; it is not what the damage row multiplies.
     */
    @Override
    public double damageBase(AttributeType attribute) {
        if (attribute == AttributeType.HEALTH && master != null) {
            return master.getAttribute(AttributeType.HEALTH).get();
        }
        return super.damageBase(attribute);
    }

    /**
     * Constructs a summoned entity.
     *
     * @param name       display name
     * @param camp       faction alignment (typically the summoner's own)
     * @param attributes pre-computed attribute array
     */
    public Summon(String name, Camp camp, DoubleValue[] attributes) {
        super(name, camp, attributes);
    }
}
