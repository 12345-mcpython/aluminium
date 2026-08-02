package com.laosun.aluminium.models.kit;

import com.laosun.aluminium.models.Trace;

import java.util.List;

/**
 * Hand-written 光锥被动 (light cone passives) facade.
 *
 * <p>Dispatches to the per-series implementations:
 * <ul>
 *   <li>{@link ThreeStarLightCones} — 20 系列三星光锥</li>
 *   <li>{@link FourStarLightCones} — 21 系列四星光锥</li>
 *   <li>{@link FiveStarLightCones} — 22 与 23 系列五星光锥</li>
 * </ul>
 */
public final class LightConePassives {

    private LightConePassives() {
    }

    /**
     * The hand-written passive for the given light cone ID, or {@code null}
     * if the cone falls back to the generic interpreter.
     */
    public static Trace forWeapon(int wid, List<Double> params) {
        if (wid < 21000) {
            return ThreeStarLightCones.forWeapon(wid, params);
        }
        if (wid < 22000) {
            return FourStarLightCones.forWeapon(wid, params);
        }
        return FiveStarLightCones.forWeapon(wid, params);
    }
}
