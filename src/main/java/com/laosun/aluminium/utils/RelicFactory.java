package com.laosun.aluminium.utils;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.RelicMainAttribute;
import com.laosun.aluminium.beans.RelicSet;
import com.laosun.aluminium.data.RelicSets;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.RelicType;
import com.laosun.aluminium.exceptions.RelicException;
import com.laosun.aluminium.models.Relic;
import com.laosun.aluminium.models.RelicSuit;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Deterministic relics from the real set data (P10-3).
 *
 * <pre>{@code
 * RelicSuit musketeer = RelicFactory.suit(102, 5, 15);        // 4 pieces of "Musketeer of Wild Wheat"
 * RelicSuit reference = RelicFactory.combinedSuit(            // + a 2-piece planar ornament set
 *         5, 15, 102, 301);
 * }</pre>
 *
 * <h2>Why this exists next to {@link Relic#createRandomLevelZero}</h2>
 * That method rolls its main attribute and its 3-4 sub-attributes with {@code ThreadLocalRandom}, so two runs
 * of the same battle disagree about the team's stats and nothing about a relic build can be reproduced or
 * asserted. This factory is the opposite: every piece it returns is a pure function of
 * {@code (set id, slot, star, level)}, taken from {@code relic_sets.json} / {@code main_attribute.json}.
 *
 * <h2>What it chooses, and why</h2>
 * <ul>
 *   <li><b>Slot</b>: the parts of the named set, mapped through {@link RelicType#fromSetPart(String)} — so a
 *       cavern set produces HEAD/HAND/BODY/BOOT and a planar set produces the sphere and the rope. A slot the
 *       set does not come in is rejected rather than silently built.</li>
 *   <li><b>Main attribute</b>: the first entry of a stated preference list that the star level's table
 *       actually has (see {@link #PREFERRED_MAIN_ATTRIBUTE}). The slots whose main stat is fixed by the game
 *       (head, hand) have exactly one candidate. The rest are picked for a <b>generic damage dealer</b>, and
 *       deliberately <b>not</b> for a character: the sphere's real main stat is an element damage boost, and
 *       "which element" is a property of the wearer, which this factory does not know and must not guess.
 *       Nothing is deducted from a name — every candidate is checked against the generated table, and if a
 *       star level has none of them the call throws.</li>
 *   <li><b>Sub-attributes</b>: <b>none</b>. Sub-stats are the random part of an in-game relic, so "deriving"
 *       them here would mean inventing numbers that no data file contains. A relic with only a main attribute
 *       keeps the reference team's sheet exactly reproducible, which is what the set-bonus tests need.</li>
 * </ul>
 */
public final class RelicFactory {

    /**
     * The lowest star rating {@code main_attribute.json} has a table for (and the lowest a relic comes in).
     */
    private static final int MIN_STAR = 2;

    /**
     * The highest star rating {@code main_attribute.json} has a table for.
     */
    private static final int MAX_STAR = 5;

    /**
     * Main attribute preference per slot, best first; the first candidate present in the star level's table
     * wins.
     *
     * <p>Head and hand are not choices: the game gives them flat HP and flat ATK and nothing else. Body, boots
     * and rope are picked for a generic damage dealer (crit rate, speed, energy regeneration). The sphere is
     * the one genuine compromise: its real main stat is an element damage boost, and an element belongs to the
     * <em>wearer</em>, so a character-agnostic factory takes the ATK% that every element's sphere also offers
     * rather than pretending to know which element is coming.
     */
    private static final Map<RelicType, List<AttributeType>> PREFERRED_MAIN_ATTRIBUTE = Map.of(
            RelicType.HEAD, List.of(AttributeType.HEALTH),
            RelicType.HAND, List.of(AttributeType.ATTACK),
            RelicType.BODY, List.of(AttributeType.CRIT_CHANCE, AttributeType.ATTACK_PERCENT,
                    AttributeType.HEALTH_PERCENT, AttributeType.DEFENCE_PERCENT),
            RelicType.BOOT, List.of(AttributeType.SPEED, AttributeType.ATTACK_PERCENT,
                    AttributeType.HEALTH_PERCENT, AttributeType.DEFENCE_PERCENT),
            RelicType.BALL, List.of(AttributeType.ATTACK_PERCENT, AttributeType.HEALTH_PERCENT,
                    AttributeType.DEFENCE_PERCENT),
            RelicType.LINE, List.of(AttributeType.ENERGY_REGENERATION_RATE, AttributeType.ATTACK_PERCENT,
                    AttributeType.BREAKING_EFFECT, AttributeType.HEALTH_PERCENT, AttributeType.DEFENCE_PERCENT)
    );

    private RelicFactory() {
    }

    /**
     * Builds one piece of a set.
     *
     * @param setId the set id from {@code relic_sets.json}
     * @param slot  the slot the piece occupies
     * @param star  star rating (2-5)
     * @param level upgrade level (0 to the part's {@code max_level})
     * @return a relic of that set, with the slot's preferred main attribute and no sub-attributes
     * @throws RelicException           when the set does not come in that slot, at that star, or up to that
     *                                  level
     * @throws IllegalArgumentException when the set id is unknown or the star level has no table
     */
    public static Relic piece(int setId, RelicType slot, int star, int level) {
        RelicSet set = RelicSets.require(setId);
        RelicSet.Part part = set.part(slot);
        if (part == null) {
            throw new RelicException("Relic set " + setId + " (" + nameOf(set) + ") has no " + slot
                    + " piece; it has " + partTypes(set));
        }
        if (part.rarity() != null && star > part.rarity()) {
            throw new RelicException("Relic set " + setId + " " + part.type() + " comes in up to "
                    + part.rarity() + " stars, not " + star);
        }
        if (part.maxLevel() != null && level > part.maxLevel()) {
            throw new RelicException("Relic set " + setId + " " + part.type() + " goes up to level "
                    + part.maxLevel() + ", not " + level);
        }
        if (level < 0) {
            throw new RelicException("Relic level must not be negative: " + level);
        }
        return Relic.builder()
                .type(slot)
                .setId(setId)
                .star(star)
                .level(level)
                .mainAttribute(preferredMainAttribute(slot, star))
                .build();
    }

    /**
     * Builds every piece of one set — four for a cavern set, two for a planar ornament set.
     *
     * @param setId the set id
     * @param star  star rating (2-5)
     * @param level upgrade level
     * @return a suit holding that set's pieces
     * @throws RelicException when the set does not exist at that star/level (see {@link #piece})
     */
    public static RelicSuit suit(int setId, int star, int level) {
        RelicSuit suit = new RelicSuit();
        for (RelicSet.Part part : RelicSets.require(setId).parts()) {
            suit.addToSuit(piece(setId, RelicType.fromSetPart(part.type()), star, level));
        }
        return suit;
    }

    /**
     * Builds one suit out of several sets — the shape a real build has, since a 4-piece cavern set and a
     * 2-piece planar ornament set always come from two different set ids and together fill all six slots.
     *
     * <p>Two sets that want the same slot are rejected: a character has one relic per slot, and adding both
     * would silently leave the suit holding more pieces than it can wear (and both sets' bonuses counting as
     * satisfied).
     *
     * @param star   star rating (2-5)
     * @param level  upgrade level
     * @param setIds the sets to wear, in order
     * @return the combined suit
     * @throws IllegalArgumentException when no set id is given, or two of them occupy the same slot
     * @throws RelicException           when a set cannot supply the pieces asked for
     */
    public static RelicSuit combinedSuit(int star, int level, int... setIds) {
        if (setIds == null || setIds.length == 0) {
            throw new IllegalArgumentException("at least one relic set id is required");
        }
        RelicSuit suit = new RelicSuit();
        for (int setId : setIds) {
            for (RelicSet.Part part : RelicSets.require(setId).parts()) {
                RelicType slot = RelicType.fromSetPart(part.type());
                if (alreadyWearing(suit, slot)) {
                    throw new IllegalArgumentException("relic set " + setId + " wants the " + slot
                            + " slot, which an earlier set in this build already occupies — a suit wears at"
                            + " most one relic per slot (set ids: " + describe(setIds) + ")");
                }
                suit.addToSuit(piece(setId, slot, star, level));
            }
        }
        return suit;
    }

    /**
     * Whether a slot of the suit is already taken. See {@link #combinedSuit}.
     */
    private static boolean alreadyWearing(RelicSuit suit, RelicType slot) {
        return switch (slot) {
            case HEAD -> suit.head != null;
            case HAND -> suit.hand != null;
            case BODY -> suit.body != null;
            case BOOT -> suit.boot != null;
            case BALL -> suit.ball != null;
            case LINE -> suit.line != null;
        };
    }

    private static String describe(int... setIds) {
        StringJoiner joiner = new StringJoiner(", ");
        for (int setId : setIds) {
            joiner.add(String.valueOf(setId));
        }
        return joiner.toString();
    }

    /**
     * The main attribute this factory gives a slot at a star level: the first entry of the slot's preference
     * list that the generated table contains.
     *
     * @param slot the equipment slot
     * @param star star rating (2-5)
     * @return the chosen main attribute
     * @throws IllegalArgumentException when the star level has no table at all
     * @throws RelicException           when the table has none of the preferred attributes
     */
    public static AttributeType preferredMainAttribute(RelicType slot, int star) {
        if (star < MIN_STAR || star > MAX_STAR) {
            throw new IllegalArgumentException("Relic star must be " + MIN_STAR + "-" + MAX_STAR
                    + " but was " + star);
        }
        Map<AttributeType, RelicMainAttribute.Attribute> available =
                Constant.RELIC_MAIN_ATTRIBUTES.getAttributeByStar(star).get(slot);
        for (AttributeType candidate : PREFERRED_MAIN_ATTRIBUTE.get(slot)) {
            if (available != null && available.containsKey(candidate)) {
                return candidate;
            }
        }
        throw new RelicException("None of the preferred main attributes " + PREFERRED_MAIN_ATTRIBUTE.get(slot)
                + " exists for " + slot + " at " + star + " stars (table holds "
                + (available == null ? "nothing" : available.keySet()) + ")");
    }

    /**
     * The set's English name, for error messages; the id is used when the data has no name.
     */
    private static String nameOf(RelicSet set) {
        return set.name() == null || set.name().english() == null ? "set " + set.setId() : set.name().english();
    }

    /**
     * The part names a set actually has, for error messages.
     */
    private static String partTypes(RelicSet set) {
        StringJoiner joiner = new StringJoiner(", ");
        for (RelicSet.Part part : set.parts()) {
            joiner.add(part.type());
        }
        return joiner.toString();
    }
}
