package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;
import com.laosun.aluminium.enums.RelicType;

import java.util.List;

/**
 * One relic set as defined by {@code relic_sets.json} — the table of 2-piece / 4-piece bonuses
 * the engine needs in order to make a worn relic suit mean anything.
 *
 * <p>The top level of the file is a map keyed by the set id as a string, which is why it is read through
 * {@link com.laosun.aluminium.data.RelicSets} (a {@code TypeToken<Map<...>>}) rather than through
 * {@link com.laosun.aluminium.utils.JSONReader#fromJSON(String, Class)}; this record is one value of that
 * map.
 *
 * <h2>Two kinds of effect, and only one of them is modellable</h2>
 * An {@link Effect} carries both stats ({@code properties}) and, for many sets, an {@code ability} — a
 * named behaviour such as "at the start of the battle, immediately regenerates 1 Skill Point"
 * ({@code Ability51011}). The engine applies {@code properties} as modifiers; it has <b>no ability
 * interpreter</b>, so an ability-only effect cannot be applied at all. That is a registered limitation,
 * not a silent skip: {@link Effect#hasAbility()} makes it visible, {@code RelicSetTest} pins the invariant
 * that every effect in the shipped file is <em>either</em> a stat effect <em>or</em> a named ability, and
 * {@code RelicSuit}'s Javadoc says plainly what a suit does and does not contribute.
 *
 * @param setId          the set id, the same key the JSON map uses (e.g. {@code 102})
 * @param name           the set's bilingual name
 * @param releaseVersion the game version the set was released in (informational)
 * @param parts          the pieces the set has: four for a cavern set, two for a planar ornament set
 * @param effects        the bonuses, each with the piece count it requires ({@code 2} or {@code 4})
 * @param isPlanar       {@code true} for a planar ornament set, i.e. one that occupies the NECK/OBJECT
 *                       (sphere / rope) slots instead of the four cavern slots
 */
public record RelicSet(@SerializedName("set_id") int setId,
                       @SerializedName("name") Translate name,
                       @SerializedName("release_version") String releaseVersion,
                       @SerializedName("parts") List<Part> parts,
                       @SerializedName("effects") List<Effect> effects,
                       @SerializedName("is_planar") boolean isPlanar) {

    /**
     * The part of this set that occupies a given slot, or {@code null} when the set has no such piece.
     *
     * <p>Used by {@code RelicFactory} to check "this set really comes in this slot, at this rarity and
     * level cap" before it builds anything — the point being that a mixed-up part is caught with a message
     * instead of producing a relic the game does not have.
     *
     * @param slot the equipment slot
     * @return the matching part, or {@code null}
     */
    public Part part(RelicType slot) {
        for (Part part : parts) {
            if (RelicType.fromSetPart(part.type()) == slot) {
                return part;
            }
        }
        return null;
    }

    /**
     * One piece of a set.
     *
     * @param type     the client's part name: {@code HEAD}, {@code HAND}, {@code BODY}, {@code FOOT},
     *                 {@code NECK} or {@code OBJECT} (see {@link RelicType#fromSetPart(String)})
     * @param typeCn   the client's Chinese label for the part (informational; the engine displays nothing)
     * @param rarity   the highest star rating this piece comes in
     * @param maxLevel the highest upgrade level this piece comes in
     */
    public record Part(@SerializedName("type") String type,
                       @SerializedName("type_cn") String typeCn,
                       @SerializedName("rarity") Integer rarity,
                       @SerializedName("max_level") Integer maxLevel) {
    }

    /**
     * One set bonus: the number of pieces it needs plus what it grants.
     *
     * @param require    how many pieces of the set must be worn: {@code 2} or {@code 4}
     * @param desc       the bonus's bilingual description
     * @param param      the raw ability parameters the description interpolates ({@code #1}, {@code #2});
     *                   informational for stat effects, since the numbers are already resolved into
     *                   {@code properties}
     * @param properties the stats the bonus grants; empty for an ability-only bonus
     * @param ability    the client's ability name, or {@code null}/blank when the bonus has no behaviour
     *                   beyond its stats — <b>the engine cannot execute this</b>
     */
    public record Effect(@SerializedName("require") int require,
                         @SerializedName("desc") Translate desc,
                         @SerializedName("param") List<Double> param,
                         @SerializedName("properties") List<Property> properties,
                         @SerializedName("ability") String ability) {

        /**
         * Whether this bonus is (also) a named ability the engine cannot execute.
         *
         * <p>Exists so that "the engine applied nothing here" is a fact a caller — or a test — can look at,
         * rather than something that has to be guessed from an empty property list.
         */
        public boolean hasAbility() {
            return ability != null && !ability.isBlank();
        }
    }

    /**
     * One stat a set bonus grants.
     *
     * <p>{@code type} is in the <b>game's</b> vocabulary ({@code AttackAddedRatio}, {@code CriticalChanceBase},
     * …), not in {@link com.laosun.aluminium.enums.AttributeType}'s; {@code RelicSuit} resolves it with
     * {@link com.laosun.aluminium.enums.AttributeType#fromGameProperty(String)}, which throws rather than
     * dropping an unknown name.
     *
     * @param type  the game property name
     * @param value the amount: a ratio for percentage attributes ({@code 0.12} = 12%), a flat amount
     *              otherwise. Negative values are legitimate (set 124's 4-piece trades speed away)
     */
    public record Property(@SerializedName("type") String type,
                           @SerializedName("value") double value) {
    }
}
