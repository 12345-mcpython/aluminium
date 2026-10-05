package com.laosun.aluminium.models.event;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.CanHit;

import java.util.List;

/**
 * Attack-level event: fired once per attack, after every damage segment of that attack has
 * been settled.
 *
 * <p>Unlike {@link DamageEvent} (per damage instance), this describes "one attack" and is broadcast to
 * <b>every ally</b> ({@code Battle.fireAfterAttack} walks {@code battle.allies}) - which is exactly where
 * third-party kits land: Robin's (知更鸟) [协奏] and Tribbie's (缇宝) field are both hung on the characters
 * themselves, so they only act when the main DPS attacks ("after our target casts an attack each time").
 * Every listener answers with its own question, so being told about an attack one does not care about is
 * harmless: {@code AbstractBuff.afterAttack} compares {@code attacker == owner} exactly for that reason.
 *
 * <p><b>Which attacks fire it.</b> A character's skill activation ({@code SkillExecutor.execute}) and, since
 * a summon's own attack ({@code EnemySkill.execute}) - both are attacks the engine drives from the
 * first segment to the last. Derived hits never do: additional damage / true damage / DOT / break go straight
 * through {@code Battle.applyDamage} and are <em>part of</em> somebody else's attack - in the official
 * definition additional damage "does not count as having caused 1 attack", which also naturally avoids the
 * recursion "additional damage kills to triggers additional damage". A follow-up attack is therefore not
 * announced either, so it does not consume an {@code "until": "next_attack"} buff (registered as M-2).
 *
 * <p>An attack that <b>hits nothing</b> is not announced: {@code Battle.fireAfterAttack} returns early when
 * no target was hit, so a swing at a battlefield with nothing alive does not count as an attack.
 *
 * @param battle      the running battle
 * @param attacker    the unit that performed the attack (a character or a summon; may be either camp)
 * @param mainTarget  the target the attacker selected (with AOE it may not be the first in hit order)
 * @param hitTargets  targets the attack actually connected with (including those that died on the spot, deduped
 *                    in hit order)
 * @param totalDamage the sum of the settled values of all segments of this attack (TODO data (D2): whether
 *                    overflow counts is not settled yet)
 */
public interface AttackEvent {
    default void afterAttack(Battle battle, CanHit attacker, CanHit mainTarget,
                             List<? extends CanHit> hitTargets, double totalDamage) {
    }
}
