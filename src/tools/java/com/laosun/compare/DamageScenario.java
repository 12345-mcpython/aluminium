package com.laosun.compare;


import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.laosun.aluminium.damage.Damage;

/**
 * One comparison scenario: <b>what the original game computed, next to what this engine computes</b>.
 *
 * <p>Read from a JSON file by {@link DamageComparator}; see {@code scenarios/README.md} for the shape and for the
 * zone-name mapping between the game's own terms and this engine's {@code Damage.breakdown()} keys.
 *
 * <p>The scenario states the <b>inputs</b> (who casts what, against which enemy, with which panel) and the
 * <b>expected numbers</b> per zone. Everything the engine needs is derived from those; nothing is read from the test
 * sources, so a scenario file can be handed over as-is.
 */
@Getter
@Setter
public class DamageScenario {

    /** A short label, printed as the table's title. */
    private String name;

    /** Free text: where the numbers came from (a video, a spreadsheet, a hand calculation). */
    private String note;

    private Attacker attacker;

    private Target target;

    /**
     * Force the crit roll: {@code true} = this hit crits, {@code false} = it does not, absent = leave the dice alone.
     *
     * <p>The game's number already includes its own crit decision, so a comparison that leaves the roll to chance
     * compares two different hits.
     */
    private Boolean crit;

    /** Which settled instance to report, 0-based. Defaults to 0 (the first). */
    private Integer instance;

    /** The number of damage instances the cast is expected to settle (1002's Skill settles four). */
    private Integer instances;

    /** Relative tolerance for a row to count as matching; defaults to {@link DamageComparator#DEFAULT_TOLERANCE}. */
    private Double tolerance;

    /** Zone name (or {@code base} / {@code final}) to the number the original game shows. */
    private Map<String, Double> expect = new LinkedHashMap<>();

    /**
     * Who casts.
     */
    @Getter
    @Setter
    public static class Attacker {

        private Integer cid;

        private Integer level;

        /** 0-6 (星魂). */
        private Integer eidolon;

        /** {@code COMMON} / {@code SKILL} / {@code ULTRA} / {@code TALENT} - the slot cast. */
        private String skill;

        /** The level the skill is cast at; absent = the data's row count (the convention this repo uses). */
        private Integer skillLevel;

        /* Absolute attribute values to install on the attacker, so both sides use the same panel. */
        /**
         * When {@code true} the stand-in is built with an EMPTY trigger table, so its own kit contributes nothing and
         * only {@link #attributes} describes the hit.
         *
         * <p>Needed by the capture replay: a capture states every modifier of a hit, and a stand-in character's own
         * traces would otherwise add boosts the capture does not have (measured: 1002 carries 0.224 wind boost, 1003
         * carries 0.374 fire boost plus another 0.15 that does not come from any attribute this scenario can reach).
         */
        private Boolean suppressContent;

        private Map<String, Double> attributes;

        /**
         * The base layer itself ({@code damage_value} in a captured game event: the skill multiplier times ATK).
         *
         * <p>When stated, the attacker's ATK is solved from it ({@code ATK = base / multiplier}), so the comparison starts
         * from <b>the game's own base</b> instead of depending on this engine's 4-star panel - which is what makes a
         * capture comparable at all, since the game's number came from a panel with light cone, relics and buffs.
         */
        private Double base;
    }

    /**
     * What it hits.
     */
    @Getter
    @Setter
    public static class Target {

        /** A key of {@code monster_config.json}, e.g. 1002011. */
        private Integer monsterId;

        private Integer level;

        /** The stage's hard level group; defaults to 1. */
        private Integer hardLevelGroup;

        /** Absolute attribute values to install on the enemy (e.g. a smaller HEALTH so it survives one hit). */
        private Map<String, Double> attributes;

        /**
         * Whether the target is in the <b>broken</b> state.
         *
         * <p>It matters to the numbers: an unbroken enemy takes the extra 10% toughness reduction inside the reduction
         * zone, so a capture whose {@code all_damage_reduce} is the total must state this or the engine will apply
         * {@code 0.75 x 0.9} where the game applied {@code 0.75}.
         */
        private Boolean broken;

        /** Damage-side state on the target: vulnerability and/or reduction, exactly as the capture reports them. */
        private List<Modifier> buffs;

        /**
         * One damage-side modifier on the target.
         */
        @Getter
        @Setter
        public static class Modifier {

            /** {@code vulnerable} ({@code all_damage_type_taken_ratio}) or {@code reduction} ({@code all_damage_reduce}). */
            private String kind;

            private Double value;

            /** Turns; absent = 1. */
            private Integer turns;
        }
    }
}
