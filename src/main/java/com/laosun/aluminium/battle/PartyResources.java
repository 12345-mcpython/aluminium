package com.laosun.aluminium.battle;

import com.laosun.aluminium.beans.ResourceSpec;
import com.laosun.aluminium.enums.ResourceScope;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.state.Resource;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The party-scoped state a battle owns: shared counters such as [笑点] and the per-holder
 * 【好活当赏】 snapshots that read them.
 *
 * <p><b>Why this is a separate object.</b> These are not facts about one combatant, so they cannot live
 * on a {@code Character} or a {@code Buff}: the counter is shared by the party, and the snapshot belongs
 * to whoever holds the state while its value was frozen from the shared pool. Keeping them next to
 * {@code Battle}'s turn logic made both harder to read; this object owns only the storage and the
 * arithmetic, and {@code Battle} keeps the event plumbing.
 */
public final class PartyResources {

    /**
     * The counters, in declaration order.
     */
    private final Map<String, Resource> resources = new LinkedHashMap<>();

    /**
     * One record per holder, replaced when the state is granted again.
     */
    private final Map<CanHit, Snapshot> laughSnapshots = new IdentityHashMap<>();

    /**
     * Registers the PARTY-scoped declarations of the given units.
     *
     * <p>Only a character carries a trigger table, and only a {@code scope: PARTY} declaration belongs
     * here; a character's own resource stays on the character.
     *
     * @param allies the units whose declarations are read
     */
    public void registerFrom(Iterable<? extends CanHit> allies) {
        for (CanHit ally : allies) {
            if (!(ally instanceof Character character) || character.getTriggerTable() == null) {
                continue;
            }
            for (ResourceSpec spec : character.getTriggerTable().resources()) {
                if (!"PARTY".equalsIgnoreCase(spec.scope() == null ? "" : spec.scope().trim())) {
                    continue;
                }
                resources.putIfAbsent(spec.id(), new Resource(spec.id(), ResourceScope.PARTY, spec.max(),
                        spec.initial() == null ? 0 : spec.initial()));
                if (spec.overflow() != null) {
                    resources.get(spec.id()).setMaxOverflow(spec.overflow());
                }
            }
        }
    }

    /**
     * @param id the counter name
     * @return the counter's current value, or {@code 0} when the battle has no such counter
     */
    public int value(String id) {
        Resource resource = resources.get(id);
        return resource == null ? 0 : resource.value();
    }

    /**
     * @param id the counter name
     * @return the counter, or {@code null} when the battle has no such counter
     */
    public Resource resource(String id) {
        return resources.get(id);
    }

    /**
     * A unit received 【好活当赏】: freeze the party's 笑点 as ITS count, to run down over {@code turns} of
     * its own turns.
     *
     * @param holder the unit receiving the state
     * @param turns  how many of the holder's turns it lasts (the document states 2)
     */
    public void noteElationGift(CanHit holder, int turns) {
        if (holder == null || holder.isDeath()) {
            return;
        }
        laughSnapshots.put(holder, new Snapshot(value("笑点"), Math.max(1, turns)));
    }

    /**
     * @return the merged, unexpired laugh count of every holder
     */
    public int countedLaughPoints() {
        int total = 0;
        for (Snapshot snapshot : laughSnapshots.values()) {
            if (!snapshot.hasExpired()) {
                total += snapshot.points();
            }
        }
        return total;
    }

    /**
     * @param holder the holder to ask
     * @return its frozen count, or {@code 0} when it holds none
     */
    public int laughSnapshotOf(CanHit holder) {
        Snapshot snapshot = laughSnapshots.get(holder);
        return snapshot == null || snapshot.hasExpired() ? 0 : snapshot.points();
    }

    /**
     * @return how many holders are counted right now
     */
    public int laughSnapshotHolders() {
        return (int) laughSnapshots.values().stream().filter(snapshot -> !snapshot.hasExpired()).count();
    }

    /**
     * Counts one of the unit's own turns against its snapshot, dropping it when the last one is gone.
     *
     * @param holder the unit whose turn ended
     */
    public void tick(CanHit holder) {
        Snapshot snapshot = laughSnapshots.get(holder);
        if (snapshot == null) {
            return;
        }
        int left = snapshot.turnsLeft() - 1;
        if (left <= 0) {
            laughSnapshots.remove(holder);
        } else {
            laughSnapshots.put(holder, new Snapshot(snapshot.points(), left));
        }
    }

    /**
     * One holder's frozen laugh count and its own remaining turns.
     *
     * <p>「各个【好活当赏】的持续时间各自独立计算」: each instance runs down on its OWN holder's turns,
     * so the holder is part of the record rather than one shared deadline.
     */
    private record Snapshot(int points, int turnsLeft) {
        boolean hasExpired() {
            return turnsLeft <= 0;
        }
    }
}
