package com.laosun.aluminium.trigger;

/**
 * What {@link ApplyControlOp} parsed out of its effect.
 *
 * @param control   the control state's name
 * @param element   the element of the state's own per-turn damage, or {@code null} when it deals none
 * @param magnitude that per-turn damage, as the effect states it
 * @param turns     how long the control lasts
 */
record ApplyControlArgs(String control, String element, Magnitude magnitude, Integer turns) implements OpArgs {
}
