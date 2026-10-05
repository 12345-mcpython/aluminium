package com.laosun.aluminium.models.buff;

/**
 * A state that <b>defers a lethal blow</b> -- "temporarily defer entering the unable-to-fight state, and one can act normally" (2026-10-02).
 *
 * <p><b>The reader.</b> {@code 140} 遐蝶's warehouse ability "月茧之庇": a lethal blow gives the victim [月茧] instead of killing it,
 * and the victim then either recovers (the state is removed by a heal or a shield) or falls at its next turn.
 *
 * <p><b>Why a class rather than a field on the state's name.</b> The deferral has to be readable at the moment the
 * engine is about to commit a death, and it must end exactly when the state ends -- a subclass gives both for free:
 * {@code BuffManager.defersDeath()} asks by type, and removal needs no bookkeeping. Note: A name-based spelling would make
 * every state whose name happens to match defer death, which is a silent over-application.
 *
 * <p><b>What it does NOT do.</b> It does not prevent damage, and it does not restore HP: the victim really is at zero HP
 * while it carries this (that is what "defer" means). The engine holds the death, and commits it at the carrier's next
 * turn if the state is still there -- see {@code Battle.applyDamage} and {@code Battle.beforeMove}.
 */
public class DeferredDeathBuff extends StateBuff {

    public DeferredDeathBuff(String state, int turns, boolean permanent) {
        super(state, turns, permanent);
    }
}
