package com.laosun.aluminium.data;

import com.google.gson.Gson;
import com.laosun.aluminium.beans.MemospriteSpec;
import com.laosun.aluminium.enums.AttributeType;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads memosprite panels from {@code resources/memosprites/<ownerCid>.json} (P9-4, the 忆灵 half).
 *
 * <p>Same contract as {@link TriggerTables}, and for the same reasons: it is <b>hand-written</b> content
 * rather than generated data (so it does not belong in {@code Constant}), it lives outside the gitignored
 * {@code data/}, it is <b>optional per character</b> — most characters have no memosprite — and therefore a
 * <b>missing file is an ordinary state</b> while a file that exists and is wrong throws.
 *
 * <p><b>Keyed by the owner, not by the memosprite.</b> The file is named after the character who summons it
 * ({@code 1402.json} = 阿格莱雅's 衣匠) because the panel is <em>stated against that character</em>: the
 * percentages mean "of the summoner's sheet". A file named after the memosprite would have to repeat the
 * owner inside it, and the two could then disagree.
 */
public final class Memosprites {

    /**
     * Directory holding the per-owner files, relative to the classpath.
     */
    public static final String DIR = "memosprites";

    /**
     * Cached because an owner asked for twice must not hit the classpath twice.
     */
    private static final Map<Integer, MemospriteSpec> CACHE = new HashMap<>();

    private static final Gson GSON = new Gson();

    /**
     * How many times a file has actually been read (not merely asked for) — so a test can assert the lazy
     * and cached properties without reflection, the same way {@code TriggerTables.loadCount} does.
     */
    private static int loadCount;

    private Memosprites() {
    }

    /**
     * The memosprite of the given character, or {@code null} when this character has none.
     *
     * <p>{@code null} is the honest answer to "does this character summon a memosprite", and it is the same
     * shape {@code Constant.MONSTER_CONFIGS.get(...)} uses for "no such monster": the lookup answers
     * absence, and the <b>caller</b> decides whether absence is acceptable. Here it is not — a rule or a
     * call that expects a memosprite is wrong if there is none — so
     * {@code SummonFactory.memosprite} turns it into a loud failure that names the file to write.
     *
     * @param ownerCid the summoning character's id
     * @return the validated spec, or {@code null} when there is no file for this character
     * @throws IllegalStateException when a file exists but cannot be parsed into a valid spec
     */
    public static MemospriteSpec of(int ownerCid) {
        synchronized (CACHE) {
            if (CACHE.containsKey(ownerCid)) {
                return CACHE.get(ownerCid);
            }
            MemospriteSpec loaded = load(ownerCid);
            CACHE.put(ownerCid, loaded);
            return loaded;
        }
    }

    /**
     * Whether this character has a memosprite file at all (one classpath lookup, then cached via {@link #of}).
     */
    public static boolean exists(int ownerCid) {
        return of(ownerCid) != null;
    }

    /**
     * How many memosprite files have been read from the classpath. See {@link #loadCount}.
     */
    public static int loadCount() {
        synchronized (CACHE) {
            return loadCount;
        }
    }

    /**
     * Drops the cache, for tests that need to re-read a file. Not used by the engine.
     */
    public static void clearCache() {
        synchronized (CACHE) {
            CACHE.clear();
            loadCount = 0;
        }
    }

    /**
     * Validates a spec <b>at load time</b>.
     *
     * <p>Every rejection here is a wrong panel that would otherwise be discovered by playing: a memosprite
     * with no name is unnamed in every log; a panel that states neither a share nor a flat value describes
     * nothing; a typo in an attribute name silently produces a 0; a duplicate attribute makes "which wins"
     * an accident of iteration order; and a missing {@code HEALTH} or {@code SPEED} produces a unit that
     * dies to a tick or that the action bar refuses to schedule.
     *
     * @param spec   the parsed spec (may be {@code null}, which is not an error — it means "no file")
     * @param source a label for error messages (normally the resource path)
     * @return the same spec, for chaining
     * @throws IllegalArgumentException when the spec cannot describe a real unit
     */
    public static MemospriteSpec validate(MemospriteSpec spec, String source) {
        if (spec == null) {
            return null;
        }
        if (spec.name() == null || spec.name().isBlank()) {
            throw new IllegalArgumentException("Memosprite spec has no \"name\" (" + source + ")");
        }
        if (spec.panel() == null || spec.panel().isEmpty()) {
            throw new IllegalArgumentException(
                    "Memosprite '" + spec.name() + "' has an empty \"panel\": there is nothing to derive "
                            + "from the summoner (" + source + ")");
        }
        Set<AttributeType> seen = new HashSet<>();
        for (MemospriteSpec.Panel entry : spec.panel()) {
            AttributeType attribute = requireAttribute(entry, spec, source);
            if (!seen.add(attribute)) {
                throw new IllegalArgumentException(
                        "Memosprite '" + spec.name() + "' states \"" + entry.attribute() + "\" twice; which "
                                + "entry wins would be an accident of order (" + source + ")");
            }
            requireUsableValue(entry, attribute, spec, source);
        }
        requirePresent(seen, AttributeType.HEALTH, spec, source);
        requirePresent(seen, AttributeType.SPEED, spec, source);
        return spec;
    }

    private static AttributeType requireAttribute(MemospriteSpec.Panel entry, MemospriteSpec spec,
                                                 String source) {
        if (entry == null || entry.attribute() == null || entry.attribute().isBlank()) {
            throw new IllegalArgumentException(
                    "Memosprite '" + spec.name() + "' has a panel entry with no \"attribute\" (" + source + ")");
        }
        AttributeType attribute;
        try {
            attribute = AttributeType.fromString(entry.attribute());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Memosprite '" + spec.name() + "' names unknown attribute '" + entry.attribute()
                            + "'; use a name from AttributeType, e.g. HEALTH / SPEED (" + source + ")");
        }
        if (attribute.isPercentVariant()) {
            throw new IllegalArgumentException(
                    "Memosprite '" + spec.name() + "' names '" + entry.attribute() + "', which is a "
                            + "builder-only input key: the runtime slot is null, so deriving it would fail "
                            + "the moment the unit is built. Name the base attribute instead (" + source + ")");
        }
        return attribute;
    }

    private static void requireUsableValue(MemospriteSpec.Panel entry, AttributeType attribute,
                                           MemospriteSpec spec, String source) {
        boolean hasPercent = entry.percent() != null;
        boolean hasFlat = entry.flat() != null;
        if (!hasPercent && !hasFlat) {
            throw new IllegalArgumentException(
                    "Memosprite '" + spec.name() + "' entry for " + attribute.name() + " states neither "
                            + "\"percent\" (a share of the summoner) nor \"flat\": there is no value to "
                            + "derive (" + source + ")");
        }
        if (hasPercent && (!isFinite(entry.percent()) || entry.percent() <= 0)) {
            throw new IllegalArgumentException(
                    "Memosprite '" + spec.name() + "' gives " + attribute.name() + " \"percent\": "
                            + entry.percent() + ", but a share of the summoner must be a positive number "
                            + "(0.35 = 35%) (" + source + ")");
        }
        if (hasFlat && (!isFinite(entry.flat()) || entry.flat() < 0)) {
            throw new IllegalArgumentException(
                    "Memosprite '" + spec.name() + "' gives " + attribute.name() + " \"flat\": "
                            + entry.flat() + ", but a flat addition must be finite and not negative "
                            + "(" + source + ")");
        }
        if ((attribute == AttributeType.HEALTH || attribute == AttributeType.SPEED)
                && !(positive(entry.percent()) || positive(entry.flat()))) {
            throw new IllegalArgumentException(
                    "Memosprite '" + spec.name() + "' would give " + attribute.name() + " a value of zero "
                            + "(a zero share and no flat addition): " + zeroConsequence(attribute)
                            + " (" + source + ")");
        }
    }

    private static String zeroConsequence(AttributeType attribute) {
        return attribute == AttributeType.HEALTH
                ? "a unit with 0 Max HP dies the moment anything touches it"
                : "the action bar cannot schedule a unit with 0 speed";
    }

    private static void requirePresent(Set<AttributeType> seen, AttributeType required, MemospriteSpec spec,
                                       String source) {
        if (!seen.contains(required)) {
            throw new IllegalArgumentException(
                    "Memosprite '" + spec.name() + "' does not state " + required.name()
                            + ": a panel entry replaces the attribute, so leaving it out would give the "
                            + "unit 0 — " + zeroConsequence(required) + " (" + source + ")");
        }
    }

    private static boolean positive(Double value) {
        return value != null && isFinite(value) && value > 0;
    }

    private static boolean isFinite(Double value) {
        return value != null && !value.isNaN() && !value.isInfinite();
    }

    private static MemospriteSpec load(int ownerCid) {
        String path = resourceFor(ownerCid);
        if (path == null) {
            return null;                        // no memosprite: an ordinary state
        }
        try (InputStream stream = Memosprites.class.getResourceAsStream(path)) {
            if (stream == null) {
                return null;
            }
            synchronized (CACHE) {
                loadCount++;
            }
            MemospriteSpec spec;
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                spec = GSON.fromJson(reader, MemospriteSpec.class);
            }
            if (spec == null) {
                throw new IllegalStateException(
                        "Memosprite file " + path + " parsed to null (an empty file?): a file that exists "
                                + "must describe a memosprite");
            }
            return validate(spec, path);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to read memosprite spec for cid " + ownerCid, e);
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "Invalid memosprite spec for cid " + ownerCid + " (" + path + "): " + e.getMessage(), e);
        }
    }

    private static String resourceFor(int ownerCid) {
        String path = "/" + DIR + "/" + ownerCid + ".json";
        return Memosprites.class.getResource(path) == null ? null : path;
    }

    /**
     * The attributes a panel may mention, for error messages and tests.
     */
    public static List<AttributeType> mentionedBy(MemospriteSpec spec) {
        return spec.panel().stream().map(entry -> AttributeType.fromString(entry.attribute())).toList();
    }
}
