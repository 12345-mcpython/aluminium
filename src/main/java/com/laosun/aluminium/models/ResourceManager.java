package com.laosun.aluminium.models;

import com.laosun.aluminium.enums.ResourceScope;
import lombok.Getter;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Every {@link Resource} a combatant owns (P8-8), keyed by id.
 *
 * <p>This is what lets a "stack character" work without its own class: the character holds a
 * manager, its data declares which resources exist, and the trigger table adds to or spends them.
 *
 * <h2>Scope is enforced here, not on the resource</h2>
 * {@link ResourceScope#PARTY} resources are deliberately **rejected** by {@link #register}. A party
 * resource needs one owner that outlives any single character; hanging it on each member would give
 * the team four independent counters where the game has one shared pool — and nothing would report
 * the mistake. Rejecting loudly is the honest option until a per-battle registry exists.
 *
 * <p>⚠ The scope check reads {@link Resource#getScope()}. A {@code PARTY} resource cannot be
 * registered at all, so "registered" today implies {@code SELF}.
 */
public class ResourceManager {

    /**
     * -- GETTER --
     *  The combatant these resources belong to.
     */
    @Getter
    private final CanHit owner;

    /**
     * Registered resources, in declaration order so that iteration (and therefore any UI listing) is
     * stable rather than hash-ordered.
     */
    private final Map<String, Resource> resources = new LinkedHashMap<>();

    /**
     * @param owner the combatant these resources belong to
     */
    public ResourceManager(CanHit owner) {
        this.owner = owner;
    }

    /**
     * Registers a resource.
     *
     * @param resource the resource
     * @return the registered instance, for chaining
     * @throws IllegalArgumentException if the id is already registered, or the scope is one the
     *                                  engine does not support yet
     */
    public Resource register(Resource resource) {
        if (resource == null) {
            throw new IllegalArgumentException("Cannot register a null resource");
        }
        if (!resource.getScope().isWired()) {
            throw new IllegalArgumentException(
                    "Resource '" + resource.getId() + "' declares scope " + resource.getScope().value()
                            + ", which the engine does not support yet (see ResourceScope): a party-level "
                            + "resource needs a per-battle owner, not a copy per character");
        }
        Resource previous = resources.putIfAbsent(resource.getId(), resource);
        if (previous != null) {
            throw new IllegalArgumentException(
                    "Resource '" + resource.getId() + "' is already registered on " + owner.getName());
        }
        return resource;
    }

    /**
     * Creates and registers a self-scoped resource.
     *
     * @param id      resource id
     * @param max     normal cap
     * @param initial initial value
     * @return the registered resource
     */
    public Resource register(String id, int max, int initial) {
        return register(new Resource(id, ResourceScope.SELF, max, initial));
    }

    /**
     * The resource with this id, or {@code null}.
     */
    public Resource get(String id) {
        return resources.get(id);
    }

    /**
     * Whether a resource with this id is registered.
     */
    public boolean has(String id) {
        return resources.containsKey(id);
    }

    /**
     * All registered resources, in registration order.
     */
    public Collection<Resource> all() {
        return java.util.Collections.unmodifiableCollection(resources.values());
    }

    /**
     * How many resources are registered.
     */
    public int size() {
        return resources.size();
    }

    /**
     * Adds to a resource.
     *
     * @param id    resource id
     * @param delta amount to add
     * @return the amount actually credited (0 when the resource is missing, capped, or {@code delta <= 0})
     */
    public int gain(String id, int delta) {
        Resource resource = resources.get(id);
        return resource == null ? 0 : resource.gain(delta);
    }

    /**
     * Spends from a resource, never below 0.
     *
     * @param id    resource id
     * @param delta amount to spend
     * @return the amount actually spent
     */
    public int spend(String id, int delta) {
        Resource resource = resources.get(id);
        return resource == null ? 0 : resource.spend(delta);
    }

    /**
     * Spends an exact amount, all-or-nothing.
     *
     * @param id    resource id
     * @param delta amount to spend
     * @return whether the spend succeeded (false when the resource is missing or short)
     */
    public boolean spendExactly(String id, int delta) {
        Resource resource = resources.get(id);
        return resource != null && resource.spendExactly(delta);
    }

    /**
     * Current value of a resource (0 when it does not exist).
     */
    public int value(String id) {
        Resource resource = resources.get(id);
        return resource == null ? 0 : resource.getValue();
    }

    /**
     * Whether a resource exists and has reached its normal cap.
     */
    public boolean isFull(String id) {
        Resource resource = resources.get(id);
        return resource != null && resource.isFull();
    }

    /**
     * Attaches a "became full" listener to a resource.
     *
     * <p>Convenience for the assembly point: the resource itself has the listener hook, but callers
     * usually only have the id.
     *
     * @param id       resource id
     * @param listener the listener
     * @throws IllegalArgumentException when no such resource is registered
     */
    public void onBecameFull(String id, Consumer<Resource> listener) {
        Resource resource = resources.get(id);
        if (resource == null) {
            throw new IllegalArgumentException(
                    "No resource '" + id + "' registered on " + owner.getName());
        }
        resource.setOnBecameFull(listener);
    }

    @Override
    public String toString() {
        return owner.getName() + resources.values();
    }
}
