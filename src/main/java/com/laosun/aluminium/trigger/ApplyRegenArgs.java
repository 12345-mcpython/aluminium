package com.laosun.aluminium.trigger;

/**
 * What {@link ApplyRegenOp} parsed out of its effect.
 *
 * @param buffName  the state's name, or the empty string when it states none
 * @param magnitude the heal per tick; a Max-HP share is worked out per carrier, so it is not a constant
 * @param turns     the state's lifetime
 */
record ApplyRegenArgs(String buffName, Magnitude magnitude, Integer turns) implements OpArgs {
}
