package com.laosun.aluminium.models.event;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.CanHit;

import java.util.List;

/**
 * Attack-level event: fired once per attack, after every damage segment of that attack has
 * been settled.
 *
 * <p>Unlike {@link DamageEvent} (per damage instance), this describes "this one attack of ours" and is
 * broadcast to <b>every ally</b> ({@code battle.characters}) — which is exactly where third-party kits land:
 * Robin's (知更鸟) 【协奏】 and Tribbie's (缇宝) field are both hung on the characters themselves, so they only act
 * when the main DPS attacks ("after our target casts an attack each time").
 *
 * <p>It is fired only from {@code SkillExecutor.execute}. Damage that is not part of a skill
 * activation (additional damage / true damage / DOT / break) goes straight through {@code Battle.applyDamage}
 * and therefore never fires it — in the official definition additional damage "does not count as having caused
 * 1 attack", which also naturally avoids the recursion "additional damage kills → triggers additional damage".
 *
 * @param battle      the running battle
 * @param attacker    the ally that performed the attack
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
