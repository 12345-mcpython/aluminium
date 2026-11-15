package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.RelicSet;
import com.laosun.aluminium.data.RelicSets;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.RelicType;
import com.laosun.aluminium.exceptions.RelicException;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.equipment.Relic;
import com.laosun.aluminium.equipment.RelicSuit;
import com.laosun.aluminium.utils.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;

/**
 * Relic set bonuses: {@code relic_sets.json} is loaded, the game's property vocabulary maps onto
 * {@link AttributeType}, and wearing two or four pieces of a set actually changes the computed panel.
 *
 * <p>Before this, the engine loaded the relic <em>affix</em> tables and nothing else: {@code RelicSuit}
 * summed main and sub attributes and ignored the set a relic belongs to, so every 2-piece and 4-piece bonus
 * in the game was silently worth zero.
 *
 * <p>The set used for the value assertions is 102 "Musketeer of Wild Wheat", because both of its tiers are
 * plain stats in the data (2 pieces +12% ATK, 4 pieces +6% SPD). Most 4-piece bonuses are an ability the
 * engine cannot execute yet, so they could not carry an assertion about a panel value; that gap is pinned
 * by the accounting block of {@link #theShippedRelicSetTableIsLoadedBoundAndFullyAccountedFor()}.
 *
 * <p><b>L3:</b> the single-clause cases are grouped into the four scenarios below, every clause inside an
 * {@code assertAll} entry that names itself: the shipped table (loaded, bound, mapped and accounted for),
 * the piece-count thresholds, the bonus on a real character sheet, and the deterministic reference build
 * together with the team that wears it. The four rejection paths stay their own tests: an
 * {@code assertThrows} is not a positive clause and must not hide behind one.
 */
public class RelicSetTest {

    /**
     * "Musketeer of Wild Wheat" - a cavern set: HEAD / HAND / BODY / BOOT.
     */
    private static final int CAVERN_SET = 102;
    /**
     * "Space Sealing Station" - a planar ornament set: NECK (sphere) / OBJECT (rope).
     */
    private static final int PLANAR_SET = 301;
    /**
     * The reference star rating and level: 5-star, level 15.
     */
    private static final int STAR = 5;
    private static final int LEVEL = 15;
    /**
     * Set 102's 2-piece bonus as {@code relic_sets.json} spells it ({@code AttackAddedRatio: 0.12}), i.e.
     * +12% <b>attack percent</b> - not flat attack. Pinned here so the expectations below are traceable to
     * the shipped file rather than to this test's arithmetic.
     */
    private static final double MUSKETEER_2PC_ATTACK_PERCENT = 0.12;
    /**
     * Set 102's 4-piece bonus: {@code SpeedAddedRatio: 0.06}.
     */
    private static final double MUSKETEER_4PC_SPEED_PERCENT = 0.06;
    /**
     * Set 301's 2-piece bonus: {@code AttackAddedRatio: 0.12}.
     */
    private static final double SPACE_SEALING_2PC_ATTACK_PERCENT = 0.12;
    /**
     * Jing Yuan: used for the "does the bonus reach a real character's sheet" case.
     */
    private static final int JING_YUAN = 1204;
    private static final int CHARACTER_LEVEL = 80;
    /**
     * Base values the relic pipeline is fed with; the base is what a percentage multiplies.
     */
    private static final double BASE_ATTACK = 1000.0;
    private static final double BASE_SPEED = 100.0;
    private static final double TOLERANCE = 1e-9;

    // ==================================================================
    // 1. The shipped table: loaded, bound, mapped and accounted for
    // ==================================================================

    /**
     * The shipped {@code relic_sets.json}, end to end.
     *
     * <p>It is loaded and cached, its rows really bind (a misspelled {@code @SerializedName} leaves a field
     * at 0/false/empty - the silent-Gson failure this project keeps hitting), every property name in it
     * resolves to an {@link AttributeType}, and every effect is either stats the engine applies or an
     * ability it selects but cannot execute yet - nothing is both stat-less and ability-less, which would be
     * a bonus that quietly does nothing.
     *
     * <p>The property-name mapping is the guard rail: an unmappable name throws with the offending string
     * instead of being dropped, and that failure mode is worth naming because a dropped property does not
     * break anything visibly - the character is simply a few percent weaker than the game, forever, with
     * nothing in the log.
     *
     * <p><b>L3:</b> these were three single-clause cases about one fixture - the shipped
     * {@link Constant#RELIC_SETS} table - and one subject: whether every row, name and effect in it is
     * usable. They are one scenario, and every clause names itself in its {@code assertAll}.
     */
    @Test
    public void theShippedRelicSetTableIsLoadedBoundAndFullyAccountedFor() {
        // Synthetic test-side sets (release_version == "test") never ship, so no census counts them.
        List<RelicSet> shipped = Constant.RELIC_SETS.values().stream()
                .filter(s -> !"test".equals(s.releaseVersion())).toList();
        int effects = shipped.stream().mapToInt(set -> set.effects().size()).sum();

        RelicSet musketeer = Constant.RELIC_SETS.get(CAVERN_SET);
        RelicSet planar = Constant.RELIC_SETS.get(PLANAR_SET);

        // The guard rail, row by row: the call throws for an unmappable name, so a property can never be
        // skipped silently while the loop below still counts it.
        Set<String> names = new LinkedHashSet<>();
        int properties = 0;
        for (RelicSet set : Constant.RELIC_SETS.values()) {
            for (RelicSet.Effect effect : set.effects()) {
                for (RelicSet.Property property : effect.properties()) {
                    properties++;
                    names.add(property.type());
                    Assertions.assertNotNull(AttributeType.fromGameProperty(property.type()),
                            "set " + set.setId() + "'s " + effect.require() + "-piece bonus uses the property '"
                                    + property.type() + "', which must resolve to an AttributeType");
                }
            }
        }

        // No effect is silently empty: stats, or an ability that is at least selected and named.
        int withStats = 0;
        int abilityOnly = 0;
        for (RelicSet set : shipped) {
            for (RelicSet.Effect effect : set.effects()) {
                if (!effect.properties().isEmpty()) {
                    withStats++;
                    continue;
                }
                Assertions.assertTrue(effect.hasAbility(),
                        "set " + set.setId() + "'s " + effect.require() + "-piece bonus has neither "
                                + "properties nor an ability: nothing would ever apply it");
                abilityOnly++;
            }
        }
        final int propertyCount = properties;
        final int statBonuses = withStats;
        final int abilityBonuses = abilityOnly;

        Assertions.assertAll("the shipped relic_sets.json is loaded and its rows bind",
                () -> Assertions.assertFalse(Constant.RELIC_SETS.isEmpty(),
                        "relic_sets.json was not loaded — the engine cannot apply any set bonus without it"),
                // ⚠ CORRECTED (2026-10-18, data migration): 60 -> 62 sets in the current version.
                () -> Assertions.assertEquals(62, shipped.size(),
                        "the shipped relic_sets.json holds 62 sets; a different number means "
                                + "the file changed and this registry line needs re-checking"),
                // ⚠ CORRECTED (2026-10-18, data migration): 92 -> 96 bonuses (two new sets, 2 + 2).
                () -> Assertions.assertEquals(96, effects,
                        "the shipped file holds 96 set bonuses (2- and 4-piece)"),
                () -> Assertions.assertTrue(RelicSets.loadCount() >= 1,
                        "the file must have been read at least once"),
                () -> Assertions.assertSame(Constant.RELIC_SETS, RelicSets.table(),
                        "the table is read once and cached"),
                () -> Assertions.assertNotNull(musketeer, "set " + CAVERN_SET + " must exist"),
                () -> Assertions.assertEquals(CAVERN_SET, musketeer.setId(), "set_id must bind, not stay 0"),
                () -> Assertions.assertEquals("Musketeer of Wild Wheat", musketeer.name().english(),
                        "set 102's name is 'Musketeer of Wild Wheat'"),
                () -> Assertions.assertFalse(musketeer.isPlanar(), "set 102 is a cavern set"),
                () -> Assertions.assertEquals(
                        List.of(RelicType.HEAD, RelicType.HAND, RelicType.BODY, RelicType.BOOT),
                        slotsOf(musketeer),
                        "a cavern set occupies the four cavern slots, in the order the data lists them"),
                () -> Assertions.assertEquals(List.of(2, 4), requiresOf(musketeer),
                        "a cavern set has a 2-piece and a 4-piece bonus"),
                () -> Assertions.assertEquals(5, musketeer.parts().getFirst().rarity(),
                        "the 5-star copy of each part is the one kept by the generator"),
                () -> Assertions.assertNotNull(planar, "set " + PLANAR_SET + " must exist"),
                () -> Assertions.assertTrue(planar.isPlanar(),
                        "set 301 is a planar ornament set (is_planar must bind)"),
                () -> Assertions.assertEquals(List.of(RelicType.BALL, RelicType.LINE), slotsOf(planar),
                        "NECK is the sphere slot and OBJECT is the rope slot"),
                () -> Assertions.assertEquals(List.of(2), requiresOf(planar),
                        "a planar set only has a 2-piece bonus"));

        // The mapping is the generator's own inner_outer_mapping - the same dictionary that turns the
        // affix tables into attack_percent / crit_chance - so these pairings are the semantics, not
        // spelling: "AddedRatio" means a percentage, "Base" means the ratio attribute itself.
        Assertions.assertAll("the shipped property vocabulary",
                () -> Assertions.assertTrue(propertyCount > 0, "the loop above must actually visit properties"),
                () -> Assertions.assertEquals(19, names.size(),
                        "the shipped file uses 19 distinct property names; a new one must be mapped in "
                                + "AttributeType.BY_GAME_PROPERTY before this test can pass: " + names),
                () -> Assertions.assertEquals(AttributeType.ATTACK_PERCENT,
                        AttributeType.fromGameProperty("AttackAddedRatio"), "AttackAddedRatio is attack percent"),
                () -> Assertions.assertEquals(AttributeType.HEALTH_PERCENT,
                        AttributeType.fromGameProperty("HPAddedRatio"), "HPAddedRatio is health percent"),
                () -> Assertions.assertEquals(AttributeType.DEFENCE_PERCENT,
                        AttributeType.fromGameProperty("DefenceAddedRatio"), "DefenceAddedRatio is defence percent"),
                () -> Assertions.assertEquals(AttributeType.SPEED_PERCENT,
                        AttributeType.fromGameProperty("SpeedAddedRatio"), "SpeedAddedRatio is speed percent"),
                () -> Assertions.assertEquals(AttributeType.CRIT_CHANCE,
                        AttributeType.fromGameProperty("CriticalChanceBase"), "CriticalChanceBase is crit rate"),
                () -> Assertions.assertEquals(AttributeType.CRIT_ATTACK,
                        AttributeType.fromGameProperty("CriticalDamageBase"), "CriticalDamageBase is crit damage"),
                () -> Assertions.assertEquals(AttributeType.BREAKING_EFFECT,
                        AttributeType.fromGameProperty("BreakDamageAddedRatioBase"), "break effect"),
                () -> Assertions.assertEquals(AttributeType.ENERGY_REGENERATION_RATE,
                        AttributeType.fromGameProperty("SPRatioBase"), "SPRatioBase is energy regeneration rate"),
                () -> Assertions.assertEquals(AttributeType.OUTGOING_HEALING_BOOST,
                        AttributeType.fromGameProperty("HealRatioBase"), "HealRatioBase is outgoing healing"),
                () -> Assertions.assertEquals(AttributeType.EFFECT_HIT_RATE,
                        AttributeType.fromGameProperty("StatusProbabilityBase"), "StatusProbabilityBase is effect hit"),
                () -> Assertions.assertEquals(AttributeType.EFFECT_RESISTANCE,
                        AttributeType.fromGameProperty("StatusResistanceBase"), "StatusResistanceBase is effect res"),
                () -> Assertions.assertEquals(AttributeType.PHYSICAL_DAMAGE_BOOST,
                        AttributeType.fromGameProperty("PhysicalAddedRatio"), "PhysicalAddedRatio is the physical boost"),
                () -> Assertions.assertEquals(AttributeType.ELATION_DAMAGE_BOOST,
                        AttributeType.fromGameProperty("ElationDamageAddedRatioBase"), "ElationDamageAddedRatioBase"));

        // The counts are pinned because they are the registered gap: stat bonuses are applied, ability-only
        // bonuses (most 4-piece effects) are selected but not executed, and if regeneration changes either
        // number, this line is where somebody finds out.
        Assertions.assertAll("the registered gap: stats or a named ability",
                // ⚠ CORRECTED (2026-10-18, data migration): 57 -> 59 stat bonuses (the two new sets ship one each).
                () -> Assertions.assertEquals(59, statBonuses, "the shipped file's stat bonuses"),
                // ⚠ CORRECTED (2026-10-18, data migration): 35 -> 37 ability-only bonuses.
                () -> Assertions.assertEquals(37, abilityBonuses,
                        "the shipped file's ability-only bonuses: selected by RelicSuit.activeEffects(), not executed "
                                + "— the engine has no ability interpreter"),
                () -> Assertions.assertEquals(shipped.stream().mapToInt(s -> s.effects().size()).sum(),
                        statBonuses + abilityBonuses, "every effect is accounted for"));
    }

    // ==================================================================
    // 2. 2-piece / 4-piece, on the computed value
    // ==================================================================

    /**
     * The piece-count thresholds of set 102, on the computed value.
     *
     * <p>Exactly two pieces apply the 2-piece bonus and nothing else: {@code ATTACK = 1000 x 1.12 = 1120}.
     * Four pieces additionally apply the 4-piece bonus - {@code ATTACK = 1000 x 1.12 + flat},
     * {@code SPEED = 100 x 1.06 + flat} - and both tiers are attributable. Taking pieces off takes the
     * bonuses with them: 4 to 3 loses the 4-piece bonus (the 2-piece stays), 2 pieces keep only the
     * 2-piece bonus, and 1 piece gets nothing at all, as does an empty suit.
     *
     * <p>Every control wears the <b>same pieces with their set id cleared</b>, so the only difference
     * between the two computed values is the set bonus itself - not the affixes, which are identical. Ten
     * flat points of attack (the hand piece) and the boots' flat speed are deliberately in the four-piece
     * expectation: a percentage multiplies only the base, so a test that ignored the flat parts would be
     * asserting something the engine does not do.
     *
     * <p><b>L3:</b> the three single-clause cases use the same fixture ({@code musketeer(...)} and its
     * set-id-cleared control) and the same observable (the computed {@code ATTACK} / {@code SPEED} of that
     * suit), so they are one scenario and each threshold names itself in its {@code assertAll}.
     */
    @Test
    public void theMusketeerSetAppliesItsBonusesExactlyAtTheThresholds() {
        RelicSuit two = musketeer(RelicType.HEAD, RelicType.BODY);
        RelicSuit twoControl = musketeerWithoutSetBonuses(RelicType.HEAD, RelicType.BODY);
        RelicSuit four = musketeer(RelicType.HEAD, RelicType.HAND, RelicType.BODY, RelicType.BOOT);
        RelicSuit fourControl = musketeerWithoutSetBonuses(RelicType.HEAD, RelicType.HAND, RelicType.BODY,
                RelicType.BOOT);
        double flatAttack = four.hand.getMainAttribute().value();
        double flatSpeed = four.boot.getMainAttribute().value();
        RelicSuit empty = new RelicSuit();

        Assertions.assertAll("two pieces of set 102",
                () -> Assertions.assertEquals(BASE_ATTACK * (1 + MUSKETEER_2PC_ATTACK_PERCENT),
                        attribute(two, AttributeType.ATTACK, BASE_ATTACK), TOLERANCE,
                        "2 pieces of set 102 must give +12% ATK on top of the untouched base"),
                () -> Assertions.assertEquals(BASE_ATTACK,
                        attribute(twoControl, AttributeType.ATTACK, BASE_ATTACK), TOLERANCE,
                        "the control (same pieces, no set id) must be exactly the base"),
                () -> Assertions.assertEquals(BASE_SPEED, attribute(two, AttributeType.SPEED, BASE_SPEED),
                        TOLERANCE, "the 4-piece bonus must NOT apply at two pieces"),
                () -> Assertions.assertEquals(List.of(2), requiresOf(two),
                        "only the 2-piece bonus is live at two pieces"));

        Assertions.assertAll("four pieces of set 102",
                () -> Assertions.assertEquals(BASE_ATTACK * (1 + MUSKETEER_2PC_ATTACK_PERCENT) + flatAttack,
                        attribute(four, AttributeType.ATTACK, BASE_ATTACK), TOLERANCE,
                        "the 4-piece bonus keeps the 2-piece +12% ATK and adds the hand piece's flat attack"),
                () -> Assertions.assertEquals(BASE_SPEED * (1 + MUSKETEER_4PC_SPEED_PERCENT) + flatSpeed,
                        attribute(four, AttributeType.SPEED, BASE_SPEED), TOLERANCE,
                        "four pieces add +6% SPD on top of the boots' flat speed"),
                // "Additionally" spelled out: each tier's contribution on its own, measured against the control.
                () -> Assertions.assertEquals(BASE_ATTACK * MUSKETEER_2PC_ATTACK_PERCENT,
                        attribute(four, AttributeType.ATTACK, BASE_ATTACK)
                                - attribute(fourControl, AttributeType.ATTACK, BASE_ATTACK), TOLERANCE,
                        "the 2-piece attack bonus is worth 12% of the base"),
                () -> Assertions.assertEquals(BASE_SPEED * MUSKETEER_4PC_SPEED_PERCENT,
                        attribute(four, AttributeType.SPEED, BASE_SPEED)
                                - attribute(fourControl, AttributeType.SPEED, BASE_SPEED), TOLERANCE,
                        "the 4-piece speed bonus is worth 6% of the base"),
                () -> Assertions.assertEquals(List.of(2, 4), requiresOf(four),
                        "both tiers are live at four pieces"));

        Assertions.assertAll("taking pieces off takes the bonuses with them",
                () -> Assertions.assertEquals(BASE_ATTACK * MUSKETEER_2PC_ATTACK_PERCENT, attackBonus(4),
                        TOLERANCE, "four pieces: the 2-piece attack bonus is live"),
                () -> Assertions.assertEquals(BASE_SPEED * MUSKETEER_4PC_SPEED_PERCENT, speedBonus(4),
                        TOLERANCE, "four pieces: the 4-piece speed bonus is live"),
                () -> Assertions.assertEquals(0.0, speedBonus(3), TOLERANCE,
                        "three pieces: the 4-piece bonus is gone"),
                () -> Assertions.assertEquals(BASE_ATTACK * MUSKETEER_2PC_ATTACK_PERCENT, attackBonus(3),
                        TOLERANCE, "three pieces: the 2-piece bonus is still there"),
                () -> Assertions.assertEquals(BASE_ATTACK * MUSKETEER_2PC_ATTACK_PERCENT, attackBonus(2),
                        TOLERANCE, "two pieces: exactly at the 2-piece threshold"),
                () -> Assertions.assertEquals(0.0, speedBonus(2), TOLERANCE,
                        "two pieces: still below the 4-piece threshold"),
                () -> Assertions.assertEquals(0.0, attackBonus(1), TOLERANCE,
                        "one piece: below both thresholds"),
                () -> Assertions.assertEquals(0.0, speedBonus(1), TOLERANCE,
                        "one piece: below both thresholds"),
                () -> Assertions.assertTrue(empty.activeEffects().isEmpty(), "an empty suit satisfies no set"),
                () -> Assertions.assertEquals(BASE_ATTACK, attribute(empty, AttributeType.ATTACK, BASE_ATTACK),
                        TOLERANCE, "an empty suit contributes nothing"));
    }

    // ==================================================================
    // 3. The bonus reaches a real character sheet
    // ==================================================================

    /**
     * The bonus is not merely a modifier on a scratch builder: it moves a real character's computed panel.
     *
     * <p>Same character, same pieces, the only difference being whether the pieces carry their set id, so
     * everything else - traces, level scaling, base stats - cancels out of the difference.
     */
    @Test
    public void setBonusReachesTheCharacterSheet() {
        Character withSet = CharacterFactory.create(JING_YUAN, CHARACTER_LEVEL, true, null,
                RelicFactory.suit(CAVERN_SET, STAR, LEVEL));
        Character withoutSet = CharacterFactory.create(JING_YUAN, CHARACTER_LEVEL, true, null,
                musketeerWithoutSetBonuses(RelicType.HEAD, RelicType.HAND, RelicType.BODY, RelicType.BOOT));

        double baseAttack = Constant.CHARACTERS.get(JING_YUAN).attack()
                * LevelPromotionCalc.calcCharacterRate(CHARACTER_LEVEL, true);
        double baseSpeed = Constant.CHARACTERS.get(JING_YUAN).speed();

        Assertions.assertAll("Jing Yuan's computed sheet",
                () -> Assertions.assertEquals(baseAttack * MUSKETEER_2PC_ATTACK_PERCENT,
                        withSet.getAttribute(AttributeType.ATTACK).get()
                                - withoutSet.getAttribute(AttributeType.ATTACK).get(),
                        TOLERANCE,
                        "Jing Yuan's sheet must gain 12% of his base attack from the 2-piece bonus"),
                () -> Assertions.assertEquals(baseSpeed * MUSKETEER_4PC_SPEED_PERCENT,
                        withSet.getAttribute(AttributeType.SPEED).get()
                                - withoutSet.getAttribute(AttributeType.SPEED).get(),
                        TOLERANCE, "…and 6% of his base speed from the 4-piece bonus"));
    }

    // ==================================================================
    // 4. The deterministic factory and the reference team
    // ==================================================================

    /**
     * The reference build, and the team that wears it.
     *
     * <p>The build is 4 pieces of one set plus 2 of another, both bonuses are live, and building it twice
     * gives the same numbers (no {@code ThreadLocalRandom}, no rolling). The real team really wears that
     * build - the wiring, not just the factory.
     *
     * <p><b>L3:</b> the two single-clause cases share this one fixture ({@code StageFactory}'s reference
     * relics) and this one observable (the pieces of the build and its computed panel), so they are one
     * scenario and each clause names itself in its {@code assertAll}.
     */
    @Test
    public void theReferenceRelicBuildIsDeterministicAndTheRealTeamWearsIt() {
        RelicSuit first = StageFactory.referenceRelics();
        RelicSuit second = StageFactory.referenceRelics();
        List<Relic> pieces = piecesOf(first);
        RelicSuit control = combinedWithoutSetBonuses();

        Assertions.assertAll("the reference relic build",
                () -> Assertions.assertNotSame(first, second, "every call must build fresh relics, not share them"),
                () -> Assertions.assertEquals(6, pieces.size(), "a full suit is six pieces"),
                () -> Assertions.assertEquals(4,
                        pieces.stream().filter(piece -> piece.setId == CAVERN_SET).count(),
                        "four pieces of cavern set " + CAVERN_SET),
                () -> Assertions.assertEquals(2,
                        pieces.stream().filter(piece -> piece.setId == PLANAR_SET).count(),
                        "two pieces of planar set " + PLANAR_SET),
                () -> Assertions.assertEquals(6,
                        pieces.stream().filter(piece -> piece.getSubAttributes().isEmpty()).count(),
                        "the factory rolls no sub-stats, so the build is exactly reproducible"),
                () -> Assertions.assertEquals(
                        BASE_ATTACK * (MUSKETEER_2PC_ATTACK_PERCENT + SPACE_SEALING_2PC_ATTACK_PERCENT),
                        attribute(first, AttributeType.ATTACK, BASE_ATTACK)
                                - attribute(control, AttributeType.ATTACK, BASE_ATTACK), TOLERANCE,
                        "the 4-piece cavern set's +12% ATK and the planar set's +12% ATK both apply"),
                () -> Assertions.assertEquals(BASE_SPEED * MUSKETEER_4PC_SPEED_PERCENT,
                        attribute(first, AttributeType.SPEED, BASE_SPEED)
                                - attribute(control, AttributeType.SPEED, BASE_SPEED), TOLERANCE,
                        "the 4-piece cavern speed bonus applies too"),
                () -> Assertions.assertEquals(attribute(first, AttributeType.ATTACK, BASE_ATTACK),
                        attribute(second, AttributeType.ATTACK, BASE_ATTACK),
                        TOLERANCE, "a second build must produce the same panel, or the build is not reproducible"),
                () -> Assertions.assertEquals(attribute(first, AttributeType.SPEED, BASE_SPEED),
                        attribute(second, AttributeType.SPEED, BASE_SPEED),
                        TOLERANCE, "a second build must produce the same panel, or the build is not reproducible"));

        for (Character character : StageFactory.realTeam()) {
            RelicSuit suit = character.getRelicSuit();
            Assertions.assertNotNull(suit, character.getName() + " must carry a relic suit");
            List<Relic> worn = piecesOf(suit);
            Assertions.assertAll(character.getName() + " wears the reference build",
                    () -> Assertions.assertEquals(6, worn.size(), character.getName() + " must wear six relics"),
                    () -> Assertions.assertEquals(4,
                            worn.stream().filter(piece -> piece.setId == CAVERN_SET).count(),
                            character.getName() + " must wear the 4-piece cavern set"),
                    () -> Assertions.assertEquals(2,
                            worn.stream().filter(piece -> piece.setId == PLANAR_SET).count(),
                            character.getName() + " must wear the 2-piece planar set"));
        }
    }

    // ==================================================================
    // 5. Rejected input: one test per error path
    // ==================================================================

    /**
     * An unknown property name is an error, not a skip - that is what makes the guard above worth having.
     */
    @Test
    public void anUnmappablePropertyNameThrowsInsteadOfBeingSkipped() {
        IllegalArgumentException unknown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> AttributeType.fromGameProperty("AttackAddedRation"),
                "a typo in a property name must not resolve to anything");
        Assertions.assertTrue(unknown.getMessage().contains("AttackAddedRation"),
                "the message must name the offending property so it can be fixed: " + unknown.getMessage());

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> AttributeType.fromGameProperty(null), "null is not a property name");
    }

    /**
     * The loader's two row checks, exercised on hand-made maps (a generated data file cannot be doctored
     * from a test).
     *
     * <p>The {@code set_id} check is the one that matters: if that field stops binding, every row would say
     * {@code 0} while the engine kept looking sets up by map key - nothing would visibly break, and every
     * set bonus in the game would quietly become zero.
     */
    @Test
    public void theLoaderRejectsRowsItCannotTrust() {
        RelicSet misfiled = new RelicSet(999, null, "1.0", List.of(), List.of(), false);
        IllegalStateException mismatch = Assertions.assertThrows(IllegalStateException.class,
                () -> RelicSets.index(Map.of(String.valueOf(CAVERN_SET), misfiled)),
                "a row whose set_id disagrees with its map key must be rejected");
        Assertions.assertTrue(mismatch.getMessage().contains("999"), mismatch.getMessage());

        RelicSet fine = new RelicSet(CAVERN_SET, null, "1.0", null, null, false);
        Assertions.assertEquals(CAVERN_SET, RelicSets.index(Map.of(String.valueOf(CAVERN_SET), fine))
                .get(CAVERN_SET).setId(), "a consistent row is accepted");
        Assertions.assertTrue(RelicSets.index(Map.of(String.valueOf(CAVERN_SET), fine))
                .get(CAVERN_SET).parts().isEmpty(), "absent lists are normalised to empty, not left null");
        Assertions.assertTrue(RelicSets.index(null).isEmpty(), "an empty file is an empty table");
        IllegalStateException badKey = Assertions.assertThrows(IllegalStateException.class,
                () -> RelicSets.index(Map.of("not-a-number", fine)),
                "the keys of the file are set ids; anything else is a broken file");
        Assertions.assertTrue(badKey.getMessage().contains("not a number"),
                "a non-numeric key must be reported as such, not silently coerced: " + badKey.getMessage());
    }

    /**
     * A relic that names a set which does not exist is an error, not "no bonus".
     *
     * <p>Hand-built relics carry {@link Constant#RELIC_SET_NONE} and are simply not counted; a non-zero id
     * that the table does not contain can only be a bug in whoever built the relic, and treating it as
     * "nothing to apply" would hide it exactly the way an unmapped property would.
     */
    @Test
    public void anUnknownSetIdIsRejectedInsteadOfIgnored() {
        RelicSuit suit = new RelicSuit();
        suit.addToSuit(Relic.builder()
                .type(RelicType.HEAD)
                .star(STAR)
                .level(LEVEL)
                .mainAttribute(AttributeType.HEALTH)
                .setId(999_999)
                .build());

        IllegalArgumentException unknown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> suit.appendTo(new AttributeBuilder()),
                "an unknown set id must fail loudly");
        Assertions.assertTrue(unknown.getMessage().contains("999999"), unknown.getMessage());

        // A relic that belongs to no set at all (the default for hand-built pieces) is skipped, not an error.
        RelicSuit noSet = new RelicSuit();
        noSet.addToSuit(Relic.builder()
                .type(RelicType.HEAD)
                .star(STAR)
                .level(LEVEL)
                .mainAttribute(AttributeType.HEALTH)
                .build());
        AttributeBuilder builder = new AttributeBuilder();
        builder.setBase(AttributeType.HEALTH, BASE_ATTACK);
        noSet.appendTo(builder);
        Assertions.assertEquals(BASE_ATTACK + noSet.head.getMainAttribute().value(),
                builder.build()[AttributeType.HEALTH.ordinal()].get(), TOLERANCE,
                "a relic with no set contributes its own affix and no bonus");
    }

    /**
     * The factory refuses to build a piece the set does not have (rather than inventing one).
     */
    @Test
    public void theFactoryRejectsAPartTheSetDoesNotHave() {
        Assertions.assertThrows(RelicException.class,
                () -> RelicFactory.piece(CAVERN_SET, RelicType.BALL, STAR, LEVEL),
                "a cavern set has no sphere; asking for one must fail, not silently build something else");
        Assertions.assertThrows(RelicException.class,
                () -> RelicFactory.piece(PLANAR_SET, RelicType.BODY, STAR, LEVEL),
                "a planar set has no body piece");
        Assertions.assertThrows(RelicException.class,
                () -> RelicFactory.piece(CAVERN_SET, RelicType.HEAD, STAR, LEVEL + 1),
                "level 16 does not exist for a 5-star piece");

        // Two sets that want the same slot would leave the suit wearing more than six relics and counting
        // both sets as satisfied, so the combination is refused.
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> RelicFactory.combinedSuit(STAR, LEVEL, CAVERN_SET, 103),
                "two cavern sets cannot both be worn: they want the same four slots");
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> RelicFactory.combinedSuit(STAR, LEVEL, PLANAR_SET, 301),
                "the same planar set twice is the same slot clash");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * A suit wearing exactly the given slots of the cavern set, built from the real data.
     */
    private static RelicSuit musketeer(RelicType... slots) {
        return suitOf(CAVERN_SET, slots);
    }

    /**
     * The same pieces as {@link #musketeer}, with the set id cleared: the control that isolates the bonus.
     */
    private static RelicSuit musketeerWithoutSetBonuses(RelicType... slots) {
        return suitOfWithoutSetId(CAVERN_SET, slots);
    }

    /**
     * The whole reference build (cavern + planar), with every set id cleared.
     */
    private static RelicSuit combinedWithoutSetBonuses() {
        RelicSuit suit = suitOfWithoutSetId(CAVERN_SET, RelicType.HEAD, RelicType.HAND, RelicType.BODY,
                RelicType.BOOT);
        for (Relic piece : piecesOf(suitOfWithoutSetId(PLANAR_SET, RelicType.BALL, RelicType.LINE))) {
            suit.addToSuit(piece);
        }
        return suit;
    }

    private static RelicSuit suitOf(int setId, RelicType... slots) {
        RelicSuit suit = new RelicSuit();
        for (RelicType slot : slots) {
            suit.addToSuit(RelicFactory.piece(setId, slot, STAR, LEVEL));
        }
        return suit;
    }

    private static RelicSuit suitOfWithoutSetId(int setId, RelicType... slots) {
        RelicSuit suit = new RelicSuit();
        for (RelicType slot : slots) {
            Relic piece = RelicFactory.piece(setId, slot, STAR, LEVEL);
            // The fields are public (Relic is a plain data holder), so the control is the same piece
            // minus the one thing under test: which set it belongs to.
            piece.setId = Constant.RELIC_SET_NONE;
            suit.addToSuit(piece);
        }
        return suit;
    }

    /**
     * Runs a suit through the same pipeline a character uses and returns one computed attribute.
     */
    private static double attribute(RelicSuit suit, AttributeType type, double base) {
        AttributeBuilder builder = new AttributeBuilder();
        builder.setBase(type, base);
        suit.appendTo(builder);
        return builder.build()[type.ordinal()].get();
    }

    /**
     * The attack the 4-piece cavern set contributes at the given piece count.
     */
    private static double attackBonus(int pieces) {
        return attribute(musketeer(firstSlots(pieces)), AttributeType.ATTACK, BASE_ATTACK)
                - attribute(musketeerWithoutSetBonuses(firstSlots(pieces)), AttributeType.ATTACK, BASE_ATTACK);
    }

    /**
     * The speed the 4-piece cavern set contributes at the given piece count.
     */
    private static double speedBonus(int pieces) {
        return attribute(musketeer(firstSlots(pieces)), AttributeType.SPEED, BASE_SPEED)
                - attribute(musketeerWithoutSetBonuses(firstSlots(pieces)), AttributeType.SPEED, BASE_SPEED);
    }

    /**
     * The first {@code pieces} cavern slots, in head to hand to body to boot order.
     */
    private static RelicType[] firstSlots(int pieces) {
        RelicType[] order = {RelicType.HEAD, RelicType.HAND, RelicType.BODY, RelicType.BOOT};
        RelicType[] selected = new RelicType[Math.max(pieces, 0)];
        System.arraycopy(order, 0, selected, 0, Math.max(pieces, 0));
        return selected;
    }

    /**
     * The relics of a suit, read through the public slot fields.
     */
    private static List<Relic> piecesOf(RelicSuit suit) {
        Relic[] slots = {suit.hand, suit.head, suit.body, suit.boot, suit.ball, suit.line};
        List<Relic> pieces = new ArrayList<>();
        for (Relic piece : slots) {
            if (piece != null) {
                pieces.add(piece);
            }
        }
        return pieces;
    }

    /**
     * The slots a set occupies, in data order.
     */
    private static List<RelicType> slotsOf(RelicSet set) {
        return set.parts().stream().map(part -> RelicType.fromSetPart(part.type())).toList();
    }

    /**
     * The piece requirements a suit actually satisfies, in data order.
     */
    private static List<Integer> requiresOf(RelicSet set) {
        return set.effects().stream().map(RelicSet.Effect::require).toList();
    }

    /**
     * The piece requirements a suit actually satisfies, in data order.
     */
    private static List<Integer> requiresOf(RelicSuit suit) {
        return suit.activeEffects().stream().map(RelicSet.Effect::require).toList();
    }
}
