package com.laosun.aluminium.models.event;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.CanHit;

/**
 * Energy credit event (P8-6): fired once after someone **actually** gains energy.
 *
 * <p>{@code actuallyAdded} is the **return value** of {@code gainEnergy} (the value after being truncated
 * by the cap), not the theoretical energy gain — this is deliberate: cases like 知更鸟 / 缇宝's
 * "after each ally attack, +N energy" care about "how much was really credited", and the amount clipped
 * by the cap must not be counted as another "energy gain".
 *
 * <p><b>Not fired for characters with no energy bar</b> ({@code maxEnergy == 0}, e.g. 遐蝶 1407, plus the
 * stack-resource 飞霄/黄泉/白厄/昔涟/银狼LV.999) — their {@code EnergyGain} is always {@code null}, so
 * execution never reaches here at all.
 * In other words: **this event = "the energy bar moved"**, not "someone gained a resource".
 *
 * <p>Emission point: {@code Battle.applyEnergyGain} (the **only** energy-gain entry point inside battle).
 *
 * @param battle        the running battle
 * @param target        the one credited
 * @param actuallyAdded the actually credited value ({@code > 0}; 0 when clipped by the cap, in which case
 *                      it is **not fired**)
 */
public interface EnergyEvent {
    default void onEnergyGain(Battle battle, CanHit target, double actuallyAdded) {
    }
}
