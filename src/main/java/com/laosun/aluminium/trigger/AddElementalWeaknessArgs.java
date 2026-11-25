package com.laosun.aluminium.trigger;

/**
 * What {@link AddElementalWeaknessOp} parsed out of its effect.
 *
 * @param element the element name as written - a {@code DamageElement}, or one of the two named sources
 *                ({@code party_first}, {@code random_absent}) that resolve to an element at fire time
 * @param turns   how long the weakness lasts, or {@code null} for the rest of the battle
 */
record AddElementalWeaknessArgs(String element, Integer turns) implements OpArgs {
}
