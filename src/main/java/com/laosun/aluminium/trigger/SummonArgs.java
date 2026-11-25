package com.laosun.aluminium.trigger;

/**
 * What {@link SummonOp} parsed out of its effect.
 *
 * <p>Three shapes, decided once here rather than re-derived on every firing: another instance of a memosprite already
 * out, a {@code panel_less} entity placed with the summoner's own panel (神君 / 阿哈 / 浮元), or the ordinary memosprite -
 * which may be named, because a kit can own several.
 *
 * @param anotherInstance whether a second copy of one already on the field is wanted
 * @param panelLess       whether the entity has no spec and takes the summoner's panel
 * @param name            the memosprite's name, or {@code null} for "the owner's own"
 * @param speed           the panel-less entity's Speed, when one is stated
 */
record SummonArgs(boolean anotherInstance, boolean panelLess, String name, Double speed) implements OpArgs {
}
