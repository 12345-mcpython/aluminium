package com.laosun.aluminium.trigger;

import java.util.List;

/**
 * What {@link DamageOp} parsed out of its effect.
 *
 * <p><b>The settlement cluster.</b> Every field here answers "how is this instance settled", and none of them is a
 * number to read off the battlefield - which is why they are all fixed at load time. The quantity itself stays in the
 * {@link Magnitude} handed beside this record: a base is a <b>quantity</b>, and mixing quantities into a description is
 * what made {@code EffectSpec} one fat bag in the first place.
 *
 * <p><b>Nothing here is unused.</b> Fields the loader validates but the settlement never reads (the {@code parts} sum,
 * {@code amount_from_event}, the true-damage flag) stay on the bean: they are read through the {@code Magnitude} view,
 * which is where the base lives. A record component that no line reads would be the {@code as_attack} defect again -
 * validated, declared, and never consulted.
 *
 * @param attacker        who deals it ("actor" / "holder_of:&lt;state&gt;" / "summon"), or {@code null} for the rule owner
 * @param blast           the share the neighbours of a BLAST centre take, or {@code null}
 * @param skill           the slot that lends the row, or {@code null} for a literal ratio
 * @param element         the element, required when no skill row can lend one
 * @param elationFrom     the state whose holder the un-underlined Elation terms come from, or {@code null}
 * @param damageType      the instance's own type, or {@code null} to take the skill's / TALENT's
 * @param percentPerAllyState the extra share per ally holding a state, or {@code null}
 * @param allyState       the state counted by that share, or {@code null}
 * @param allyStates      several such states, or {@code null}
 * @param ordinary        whether this is an ORDINARY instance (an attack, not additional damage)
 * @param stance          a toughness reduction, or {@code null}
 * @param ignoreWeakness  whether that reduction ignores the weakness gate
 * @param critRate        a fixed crit rate override, or {@code null}
 * @param critDamage      a fixed crit damage override, or {@code null}
 * @param castCategory    the category the instance is stamped with, or {@code null}
 * @param unboosted       whether the instance skips the boost zone
 * @param countedLaughs   a fixed Elation laugh count, or {@code null}
 * @param split           whether the magnitude is the TOTAL, divided among the victims
 * @param perTarget       whether the event's hit count multiplies the magnitude
 * @param times           how many independent settlements, or {@code null}
 */
record DamageArgs(String attacker,
                  Double blast,
                  String skill,
                  String element,
                  String elationFrom,
                  String damageType,
                  Double percentPerAllyState,
                  String allyState,
                  List<String> allyStates,
                  boolean ordinary,
                  Double stance,
                  boolean ignoreWeakness,
                  Double critRate,
                  Double critDamage,
                  String castCategory,
                  boolean unboosted,
                  Double countedLaughs,
                  boolean split,
                  boolean perTarget,
                  Integer times) implements OpArgs {
}
