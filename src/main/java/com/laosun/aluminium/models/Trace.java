package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;

import java.util.List;

/**
 * A passive 行迹技能 (trace skill) of a character.
 *
 * <p>Each character has up to three trace passives (A2/A4/A6) defined by the
 * skill-tree nodes in {@code point.json}. The {@link Traces} factory builds the
 * concrete implementations from the data's {@code effect}/{@code param} values.
 *
 * <p>Hooks are invoked by {@link Battle}:
 * <ul>
 *   <li>{@link #onBattleStart} — battle start effects</li>
 *   <li>{@link #afterAction} — after the owner uses a skill / ultimate</li>
 *   <li>{@link #onTurnStart} — at the start of the owner's turn</li>
 *   <li>{@link #onKill} — when the owner defeats an enemy</li>
 *   <li>{@link #onEnemyBreak} — when any enemy's toughness is broken</li>
 *   <li>{@link #onDamaged} — when the owner takes damage</li>
 *   <li>{@link #damageMultiplier} — conditional damage bonus of the owner's attacks</li>
 *   <li>{@link #aggroMultiplier} — conditional aggro modifier (HSR.md §3.4)</li>
 * </ul>
 */
public interface Trace {

    /**
     * The trace's display name (from the game data).
     */
    String getName();

    default void onBattleStart(Battle battle, Character owner) {
    }

    default void afterAction(Battle battle, Character owner, SkillType type,
                             List<? extends CanHit> targets) {
    }

    default void onTurnStart(Battle battle, Character owner) {
    }

    default void onKill(Battle battle, Character owner, CanHit victim) {
    }

    default void onEnemyBreak(Battle battle, Character owner, Enemy enemy) {
    }

    /**
     * Invoked when the owner takes damage.
     *
     * @param attacker the entity that dealt the damage (may be null for DoT/break)
     */
    default void onDamaged(Battle battle, Character owner, CanHit attacker, double damage) {
    }

    /**
     * Invoked when a DoT owned by this character ticks on a victim.
     */
    default void onDotDamage(Battle battle, Character owner, CanHit victim, double damage) {
    }

    /**
     * Invoked when another character (or summon owner) takes an action.
     */
    default void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                              List<? extends CanHit> targets) {
    }

    /**
     * Invoked when a memosprite (忆灵) takes an action. Only the summon's owner's
     * traces are notified, with the memosprite as {@code summon}.
     */
    default void onSummonAction(Battle battle, Character owner, com.laosun.aluminium.models.Summon summon,
                                SkillType type, List<? extends CanHit> targets) {
    }

    /**
     * Invoked when 阿哈时刻 (Aha Moment) begins (HSR.md §3.1), before the
     * elation skills are cast. Used by 欢愉 light cones (e.g. 嗤笑).
     */
    default void onAhaMoment(Battle battle, Character owner) {
    }

    /**
     * Bonus crit chance for the owner's attacks against the given defender.
     */
    default double critChanceBonus(Battle battle, Character owner, CanHit defender, SkillType type) {
        return 0;
    }

    /**
     * Extra multiplier applied to the owner's outgoing damage.
     */
    default double damageMultiplier(Battle battle, Character owner, CanHit defender, SkillType type) {
        return 1.0;
    }

    /**
     * Extra multiplier applied to the owner's aggro when enemies pick targets.
     */
    default double aggroMultiplier(Battle battle, Character owner) {
        return 1.0;
    }
}
