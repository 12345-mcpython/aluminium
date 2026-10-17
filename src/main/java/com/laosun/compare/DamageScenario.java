package com.laosun.compare;


import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

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
    public static class Attacker {

        private Integer cid;

        private Integer level;

        /** 0-6 (星魂). */
        private Integer eidolon;

        /** {@code COMMON} / {@code SKILL} / {@code ULTRA} / {@code TALENT} - the slot cast. */
        private String skill;

        /** The level the skill is cast at; absent = the data's row count (the convention this repo uses). */
        private Integer skillLevel;

        /** Absolute attribute values to install on the attacker, so both sides use the same panel. */
        private Map<String, Double> attributes;
    }

    /**
     * What it hits.
     */
    @Getter
    public static class Target {

        /** A key of {@code monster_config.json}, e.g. 1002011. */
        private Integer monsterId;

        private Integer level;

        /** The stage's hard level group; defaults to 1. */
        private Integer hardLevelGroup;

        /** Absolute attribute values to install on the enemy (e.g. a smaller HEALTH so it survives one hit). */
        private Map<String, Double> attributes;
    }
}
