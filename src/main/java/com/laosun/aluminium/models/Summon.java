package com.laosun.aluminium.models;

import com.laosun.aluminium.enums.Camp;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * A summoned entity that participates in combat.
 *
 * <p>Summons are aligned with whoever called them: an enemy boss's minions fight alongside the monsters in
 * {@code Battle.enemies} (P9-4), and a player-side summon would join our own side.
 *
 * <p><b>Where they come from.</b> {@code SummonFactory} builds one from the same monster data
 * ({@code monster_config.json}) an {@link com.laosun.aluminium.models.enemy.Enemy} is built from, and
 * {@code Battle.summon(master, summonId, group)} is what puts it on the field — into <b>the master's own
 * camp</b>: an enemy's minion joins {@code Battle.enemies}, ours joins {@code Battle.allies}. The roster of
 * "which monsters may be summoned" lives on the master
 * ({@link com.laosun.aluminium.models.enemy.Enemy#getSummonIds()}).
 *
 * <p><b>What a summon is not (yet).</b> It is a {@code CanHit}, not an {@code Enemy}, so it has a stat
 * sheet, a level, a skill and buffs, but <b>none of the monster-only mechanics</b>: no toughness bar
 * (nothing to break), no weakness list, no per-element damage resistance, no specific debuff resistance
 * and no phase table. That split is the L-8 one — the enemy camp holds any {@code CanHit} while
 * {@code Battle.enemyUnits()} is "the monsters in it" — and it is why those mechanics say
 * {@code instanceof Enemy} at their call sites instead of assuming every enemy-camp unit has them.
 *
 * <p>🚧 <b>Still open</b> (the P9-4 remainder, all of it content or mechanism rather than placement):
 * memosprites (忆灵) as they are actually described — a stat <b>snapshot</b> of the summoner and joint
 * attacks — and whatever decides <em>when</em> a roster entry is used, since an enemy skill's {@code SUMMON}
 * effect needs a "what to summon" column this data does not have (see {@code SkillEffectType.SUMMON}).
 */
@Getter
@Setter
@ToString(callSuper = true)
public class Summon extends CanHit {

    /**
     * Who called this summon, or {@code null} for a hand-built one.
     *
     * <p>The link is one-way and lives here rather than as a list on the master because its only job is the
     * lifecycle: <b>a summon leaves when its master does</b> ({@code Battle.removeDeadCombatants}). Keeping
     * the pointer on the summon means "who owns this" is answerable from the summon alone, with no registry
     * to keep in sync — and no way for a summon to outlive its owner because a list somewhere was not
     * cleaned up.
     */
    private CanHit master;

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
