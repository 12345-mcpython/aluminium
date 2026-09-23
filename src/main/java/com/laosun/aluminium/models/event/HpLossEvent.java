package com.laosun.aluminium.models.event;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.CanHit;

/**
 * HP loss event (P8-6): fired once after someone's HP **really decreased**.
 *
 * <p>{@code amount = before - after}, i.e. the **HP actually lost**:
 * <ul>
 *   <li>What the shield absorbed **does not count** (when the shield does not break {@code amount == 0},
 *       and then it is **not fired**) — because "losing HP" and "taking damage" are two different things,
 *       and 遐蝶【新蕊】/ 万敌【血仇】/ 刃【充能】 convert by **amount of HP lost**, so the amount blocked by
 *       the shield must not be included;</li>
 *   <li>Overkill damage does not count ({@code before} is already the current value).</li>
 * </ul>
 *
 * <p>If you want "how much damage was taken (including what the shield absorbed)", look at the return
 * value of {@code Battle.applyDamage}; if you want "how much this instance dealt", look at
 * {@code Battle.assemble}. The three definitions differ, do not confuse them.
 *
 * <p>Emission point: {@code Battle.applyDamage} (the internal settlement overload), between the HP
 * deduction and the energy settlement.
 *
 * @param battle the running battle
 * @param target the one losing HP
 * @param before HP before the deduction
 * @param after  HP after the deduction
 * @param source the one who caused this instance (may be {@code null}, e.g. the source of a DOT has died)
 * @param amount the actual HP lost ({@code before - after}, always {@code > 0})
 */
public interface HpLossEvent {
    default void onHpLoss(Battle battle, CanHit target, double before, double after,
                          CanHit source, double amount) {
    }
}
