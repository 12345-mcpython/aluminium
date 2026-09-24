package com.laosun.aluminium.test;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.RelicSet;
import com.laosun.aluminium.data.RelicSets;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.RelicType;
import com.laosun.aluminium.exceptions.RelicException;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Relic;
import com.laosun.aluminium.models.RelicSuit;
import com.laosun.aluminium.utils.AttributeBuilder;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.LevelPromotionCalc;
import com.laosun.aluminium.utils.RelicFactory;
import com.laosun.aluminium.utils.StageFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Relic set bonuses (P10-3): {@code relic_sets.json} is loaded, the game's property vocabulary maps onto
 * {@link AttributeType}, and wearing two or four pieces of a set actually changes the computed panel.
 *
 * <p>Before this, the engine loaded the relic <em>affix</em> tables and nothing else: {@code RelicSuit}
 * summed main and sub attributes and ignored the set a relic belongs to, so every 2-piece and 4-piece bonus
 * in the game was silently worth zero.
 *
 * <p>The set used for the value assertions is 102 "Musketeer of Wild Wheat", because both of its tiers are
 * plain stats in the data (2 pieces +12% ATK, 4 pieces +6% SPD). Most 4-piece bonuses are an ability the
 * engine cannot execute yet, so they could not carry an assertion about a panel value; that gap is pinned
 * separately by {@link #everyEffectIsEitherStatsOrANamedAbility()}.
 */
public class RelicSetTest {

    /** "Musketeer of Wild Wheat" — a cavern set: HEAD / HAND / BODY / BOOT. */
    private static final int CAVERN_SET = 102;
    /** "Space Sealing Station" — a planar ornament set: NECK (sphere) / OBJECT (rope). */
    private static final int PLANAR_SET = 301;
    /** The reference star rating and level: 5-star, level 15. */
    private static final int STAR = 5;
    private static final int LEVEL = 15;
    /**
     * Set 102's 2-piece bonus as {@code relic_sets.json} spells it ({@code AttackAddedRatio: 0.12}), i.e.
     * +12% <b>attack percent</b> — not flat attack. Pinned here so the expectations below are traceable to
     * the shipped file rather than to this test's arithmetic.
     */
    private static final double MUSKETEER_2PC_ATTACK_PERCENT = 0.12;
    /** Set 102's 4-piece bonus: {@code SpeedAddedRatio: 0.06}. */
    private static final double MUSKETEER_4PC_SPEED_PERCENT = 0.06;
    /** Set 301's 2-piece bonus: {@code AttackAddedRatio: 0.12}. */
    private static final double SPACE_SEALING_2PC_ATTACK_PERCENT = 0.12;
    /** Jing Yuan: used for the "does the bonus reach a real character's sheet" case. */
    private static final int JING_YUAN = 1204;
    private static final int CHARACTER_LEVEL = 80;
    /** Base values the relic pipeline is fed with; the base is what a percentage multiplies. */
    private static final double BASE_ATTACK = 1000.0;
    private static final double BASE_SPEED = 100.0;
    private static final double TOLERANCE = 1e-9;

    // ==================================================================
    // 1. The table is loaded and bound
    // ==================================================================

    /**
     * {@code relic_sets.json} is loaded, and its rows really bind (a misspelled {@code @SerializedName}
     * leaves a field at 0/false/empty — the silent-Gson failure this project keeps hitting).
     */
    @Test
    public void relicSetsAreLoadedFromTheGeneratedTable() {
        Assertions.assertFalse(Constant.RELIC_SETS.isEmpty(),
                "relic_sets.json was not loaded — the engine cannot apply any set bonus without it");
        Assertions.assertEquals(60, Constant.RELIC_SETS.size(),
                "the shipped relic_sets.json holds 60 sets (32 cavern + 28 planar); a different number means "
                        + "the file changed and this registry line needs re-checking");
        int effects = Constant.RELIC_SETS.values().stream().mapToInt(set -> set.effects().size()).sum();
        Assertions.assertEquals(92, effects, "the shipped file holds 92 set bonuses (2- and 4-piece)");
        Assertions.assertTrue(RelicSets.loadCount() >= 1, "the file must have been read at least once");
        Assertions.assertSame(Constant.RELIC_SETS, RelicSets.table(), "the table is read once and cached");

        RelicSet musketeer = Constant.RELIC_SETS.get(CAVERN_SET);
        Assertions.assertNotNull(musketeer, "set " + CAVERN_SET + " must exist");
        Assertions.assertEquals(CAVERN_SET, musketeer.setId(), "set_id must bind, not stay 0");
        Assertions.assertEquals("Musketeer of Wild Wheat", musketeer.name().english(),
                "set 102's name is 'Musketeer of Wild Wheat'");
        Assertions.assertFalse(musketeer.isPlanar(), "set 102 is a cavern set");
        Assertions.assertEquals(
                List.of(RelicType.HEAD, RelicType.HAND, RelicType.BODY, RelicType.BOOT),
                slotsOf(musketeer),
                "a cavern set occupies the four cavern slots, in the order the data lists them");
        Assertions.assertEquals(List.of(2, 4), requiresOf(musketeer),
                "a cavern set has a 2-piece and a 4-piece bonus");
        Assertions.assertEquals(5, musketeer.parts().getFirst().rarity(),
                "the 5-star copy of each part is the one kept by the generator");

        RelicSet planar = Constant.RELIC_SETS.get(PLANAR_SET);
        Assertions.assertNotNull(planar, "set " + PLANAR_SET + " must exist");
        Assertions.assertTrue(planar.isPlanar(), "set 301 is a planar ornament set (is_planar must bind)");
        Assertions.assertEquals(List.of(RelicType.BALL, RelicType.LINE), slotsOf(planar),
                "NECK is the sphere slot and OBJECT is the rope slot");
        Assertions.assertEquals(List.of(2), requiresOf(planar), "a planar set only has a 2-piece bonus");
    }

    // ==================================================================
    // 2. The property-name mapping (the guard against a silent skip)
    // ==================================================================

    /**
     * <b>The guard rail.</b> Every property in the shipped file must resolve to an
     * {@link AttributeType}; an unmappable name throws with the offending string instead of being dropped.
     *
     * <p>This is the failure mode worth a test of its own: a dropped property does not break anything
     * visibly — the character is simply a few percent weaker than the game, forever, with nothing in the log.
     */
    @Test
    public void everyPropertyNameInTheShippedFileResolvesToAnAttributeType() {
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
        Assertions.assertTrue(properties > 0, "the loop above must actually visit properties");
        Assertions.assertEquals(19, names.size(),
                "the shipped file uses 19 distinct property names; a new one must be mapped in "
                        + "AttributeType.BY_GAME_PROPERTY before this test can pass: " + names);

        // The mapping is the generator's own inner_outer_mapping — the same dictionary that turns the
        // affix tables into attack_percent / crit_chance — so these pairings are the semantics, not
        // spelling: "AddedRatio" means a percentage, "Base" means the ratio attribute itself.
        Assertions.assertEquals(AttributeType.ATTACK_PERCENT,
                AttributeType.fromGameProperty("AttackAddedRatio"), "AttackAddedRatio is attack percent");
        Assertions.assertEquals(AttributeType.HEALTH_PERCENT,
                AttributeType.fromGameProperty("HPAddedRatio"), "HPAddedRatio is health percent");
        Assertions.assertEquals(AttributeType.DEFENCE_PERCENT,
                AttributeType.fromGameProperty("DefenceAddedRatio"), "DefenceAddedRatio is defence percent");
        Assertions.assertEquals(AttributeType.SPEED_PERCENT,
                AttributeType.fromGameProperty("SpeedAddedRatio"), "SpeedAddedRatio is speed percent");
        Assertions.assertEquals(AttributeType.CRIT_CHANCE,
                AttributeType.fromGameProperty("CriticalChanceBase"), "CriticalChanceBase is crit rate");
        Assertions.assertEquals(AttributeType.CRIT_ATTACK,
                AttributeType.fromGameProperty("CriticalDamageBase"), "CriticalDamageBase is crit damage");
        Assertions.assertEquals(AttributeType.BREAKING_EFFECT,
                AttributeType.fromGameProperty("BreakDamageAddedRatioBase"), "break effect");
        Assertions.assertEquals(AttributeType.ENERGY_REGENERATION_RATE,
                AttributeType.fromGameProperty("SPRatioBase"), "SPRatioBase is energy regeneration rate");
        Assertions.assertEquals(AttributeType.OUTGOING_HEALING_BOOST,
                AttributeType.fromGameProperty("HealRatioBase"), "HealRatioBase is outgoing healing");
        Assertions.assertEquals(AttributeType.EFFECT_HIT_RATE,
                AttributeType.fromGameProperty("StatusProbabilityBase"), "StatusProbabilityBase is effect hit");
        Assertions.assertEquals(AttributeType.EFFECT_RESISTANCE,
                AttributeType.fromGameProperty("StatusResistanceBase"), "StatusResistanceBase is effect res");
        Assertions.assertEquals(AttributeType.PHYSICAL_DAMAGE_BOOST,
                AttributeType.fromGameProperty("PhysicalAddedRatio"), "PhysicalAddedRatio is the physical boost");
        Assertions.assertEquals(AttributeType.ELATION_DAMAGE_BOOST,
                AttributeType.fromGameProperty("ElationDamageAddedRatioBase"), "ElationDamageAddedRatioBase");
    }

    /** An unknown property name is an error, not a skip — that is what makes the guard above worth having. */
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
     * {@code 0} while the engine kept looking sets up by map key — nothing would visibly break, and every
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

    // ==================================================================
    // 3. 2-piece / 4-piece, on the computed value
    // ==================================================================

    /**
     * Exactly two pieces of the same set apply the 2-piece bonus, and nothing else:
     * {@code ATTACK = 1000 × 1.12 = 1120}.
     *
     * <p>The control suit wears the <b>same two pieces with their set id cleared</b>, so the only difference
     * between the two computed values is the set bonus itself — not the affixes, which are identical.
     */
    @Test
    public void twoPieceBonusAppliesAtTwoPieces() {
        RelicSuit two = musketeer(RelicType.HEAD, RelicType.BODY);
        RelicSuit control = musketeerWithoutSetBonuses(RelicType.HEAD, RelicType.BODY);

        Assertions.assertEquals(BASE_ATTACK * (1 + MUSKETEER_2PC_ATTACK_PERCENT),
                attribute(two, AttributeType.ATTACK, BASE_ATTACK),
                TOLERANCE, "2 pieces of set 102 must give +12% ATK on top of the untouched base");
        Assertions.assertEquals(BASE_ATTACK, attribute(control, AttributeType.ATTACK, BASE_ATTACK), TOLERANCE,
                "the control (same pieces, no set id) must be exactly the base");
        Assertions.assertEquals(BASE_SPEED, attribute(two, AttributeType.SPEED, BASE_SPEED), TOLERANCE,
                "the 4-piece bonus must NOT apply at two pieces");

        Assertions.assertEquals(List.of(2), requiresOf(two),
                "only the 2-piece bonus is live at two pieces");
    }

    /**
     * Four pieces additionally apply the 4-piece bonus: {@code ATTACK = 1000 × 1.12 + flat},
     * {@code SPEED = 100 × 1.06 + flat}, and both tiers are attributable.
     *
     * <p>Ten flat points of attack (the hand piece) and the boots' flat speed are deliberately in the
     * expectation: a percentage multiplies only the base, so a test that ignored the flat parts would be
     * asserting something the engine does not do.
     */
    @Test
    public void fourPieceBonusAppliesAdditionallyAtFourPieces() {
        RelicSuit four = musketeer(RelicType.HEAD, RelicType.HAND, RelicType.BODY, RelicType.BOOT);
        RelicSuit control = musketeerWithoutSetBonuses(RelicType.HEAD, RelicType.HAND, RelicType.BODY,
                RelicType.BOOT);

        double flatAttack = four.hand.getMainAttribute().value();
        double flatSpeed = four.boot.getMainAttribute().value();

        Assertions.assertEquals(BASE_ATTACK * (1 + MUSKETEER_2PC_ATTACK_PERCENT) + flatAttack,
                attribute(four, AttributeType.ATTACK, BASE_ATTACK), TOLERANCE,
                "the 4-piece bonus keeps the 2-piece +12% ATK and adds the hand piece's flat attack");
        Assertions.assertEquals(BASE_SPEED * (1 + MUSKETEER_4PC_SPEED_PERCENT) + flatSpeed,
                attribute(four, AttributeType.SPEED, BASE_SPEED), TOLERANCE,
                "four pieces add +6% SPD on top of the boots' flat speed");

        // "Additionally" spelled out: each tier's contribution on its own, measured against the control.
        Assertions.assertEquals(BASE_ATTACK * MUSKETEER_2PC_ATTACK_PERCENT,
                attribute(four, AttributeType.ATTACK, BASE_ATTACK)
                        - attribute(control, AttributeType.ATTACK, BASE_ATTACK), TOLERANCE,
                "the 2-piece attack bonus is worth 12% of the base");
        Assertions.assertEquals(BASE_SPEED * MUSKETEER_4PC_SPEED_PERCENT,
                attribute(four, AttributeType.SPEED, BASE_SPEED)
                        - attribute(control, AttributeType.SPEED, BASE_SPEED), TOLERANCE,
                "the 4-piece speed bonus is worth 6% of the base");
        Assertions.assertEquals(List.of(2, 4), requiresOf(four),
                "both tiers are live at four pieces");
    }

    /**
     * Taking pieces off takes the bonuses with them: 4 → 3 loses the 4-piece bonus (the 2-piece stays),
     * 2 pieces keep only the 2-piece bonus, and 1 piece gets nothing at all.
     */
    @Test
    public void removingPiecesRemovesTheBonus() {
        Assertions.assertEquals(BASE_ATTACK * MUSKETEER_2PC_ATTACK_PERCENT, attackBonus(4), TOLERANCE,
                "four pieces: the 2-piece attack bonus is live");
        Assertions.assertEquals(BASE_SPEED * MUSKETEER_4PC_SPEED_PERCENT, speedBonus(4), TOLERANCE,
                "four pieces: the 4-piece speed bonus is live");

        Assertions.assertEquals(0.0, speedBonus(3), TOLERANCE,
                "three pieces: the 4-piece bonus is gone");
        Assertions.assertEquals(BASE_ATTACK * MUSKETEER_2PC_ATTACK_PERCENT, attackBonus(3), TOLERANCE,
                "three pieces: the 2-piece bonus is still there");

        Assertions.assertEquals(BASE_ATTACK * MUSKETEER_2PC_ATTACK_PERCENT, attackBonus(2), TOLERANCE,
                "two pieces: exactly at the 2-piece threshold");
        Assertions.assertEquals(0.0, speedBonus(2), TOLERANCE,
                "two pieces: still below the 4-piece threshold");

        Assertions.assertEquals(0.0, attackBonus(1), TOLERANCE, "one piece: below both thresholds");
        Assertions.assertEquals(0.0, speedBonus(1), TOLERANCE, "one piece: below both thresholds");

        RelicSuit empty = new RelicSuit();
        Assertions.assertTrue(empty.activeEffects().isEmpty(), "an empty suit satisfies no set");
        Assertions.assertEquals(BASE_ATTACK, attribute(empty, AttributeType.ATTACK, BASE_ATTACK), TOLERANCE,
                "an empty suit contributes nothing");
    }

    // ==================================================================
    // 4. The bonus reaches a real character sheet
    // ==================================================================

    /**
     * The bonus is not merely a modifier on a scratch builder: it moves a real character's computed panel.
     *
     * <p>Same character, same pieces, the only difference being whether the pieces carry their set id, so
     * everything else — traces, level scaling, base stats — cancels out of the difference.
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

        Assertions.assertEquals(baseAttack * MUSKETEER_2PC_ATTACK_PERCENT,
                withSet.getAttribute(AttributeType.ATTACK).get()
                        - withoutSet.getAttribute(AttributeType.ATTACK).get(),
                TOLERANCE, "Jing Yuan's sheet must gain 12% of his base attack from the 2-piece bonus");
        Assertions.assertEquals(baseSpeed * MUSKETEER_4PC_SPEED_PERCENT,
                withSet.getAttribute(AttributeType.SPEED).get()
                        - withoutSet.getAttribute(AttributeType.SPEED).get(),
                TOLERANCE, "…and 6% of his base speed from the 4-piece bonus");
    }

    // ==================================================================
    // 5. The deterministic factory and the reference team
    // ==================================================================

    /**
     * The reference build is 4 pieces of one set plus 2 of another, both bonuses are live, and building it
     * twice gives the same numbers (no {@code ThreadLocalRandom}, no rolling).
     */
    @Test
    public void theReferenceRelicBuildIsDeterministicAndWearsBothSetSizes() {
        RelicSuit first = StageFactory.referenceRelics();
        RelicSuit second = StageFactory.referenceRelics();
        Assertions.assertNotSame(first, second, "every call must build fresh relics, not share them");

        List<Relic> pieces = piecesOf(first);
        Assertions.assertEquals(6, pieces.size(), "a full suit is six pieces");
        Assertions.assertEquals(4, pieces.stream().filter(piece -> piece.setId == CAVERN_SET).count(),
                "four pieces of cavern set " + CAVERN_SET);
        Assertions.assertEquals(2, pieces.stream().filter(piece -> piece.setId == PLANAR_SET).count(),
                "two pieces of planar set " + PLANAR_SET);
        Assertions.assertEquals(6, pieces.stream().filter(piece -> piece.getSubAttributes().isEmpty()).count(),
                "the factory rolls no sub-stats, so the build is exactly reproducible");

        RelicSuit control = combinedWithoutSetBonuses();
        Assertions.assertEquals(BASE_ATTACK * (MUSKETEER_2PC_ATTACK_PERCENT + SPACE_SEALING_2PC_ATTACK_PERCENT),
                attribute(first, AttributeType.ATTACK, BASE_ATTACK)
                        - attribute(control, AttributeType.ATTACK, BASE_ATTACK), TOLERANCE,
                "the 4-piece cavern set's +12% ATK and the planar set's +12% ATK both apply");
        Assertions.assertEquals(BASE_SPEED * MUSKETEER_4PC_SPEED_PERCENT,
                attribute(first, AttributeType.SPEED, BASE_SPEED)
                        - attribute(control, AttributeType.SPEED, BASE_SPEED), TOLERANCE,
                "the 4-piece cavern speed bonus applies too");

        Assertions.assertEquals(attribute(first, AttributeType.ATTACK, BASE_ATTACK),
                attribute(second, AttributeType.ATTACK, BASE_ATTACK),
                TOLERANCE, "a second build must produce the same panel, or the build is not reproducible");
        Assertions.assertEquals(attribute(first, AttributeType.SPEED, BASE_SPEED),
                attribute(second, AttributeType.SPEED, BASE_SPEED),
                TOLERANCE, "a second build must produce the same panel, or the build is not reproducible");
    }

    /** The real team really wears the reference build (the wiring, not just the factory). */
    @Test
    public void theRealTeamActuallyWearsRelics() {
        for (Character character : StageFactory.realTeam()) {
            RelicSuit suit = character.getRelicSuit();
            Assertions.assertNotNull(suit, character.getName() + " must carry a relic suit");
            List<Relic> pieces = piecesOf(suit);
            Assertions.assertEquals(6, pieces.size(), character.getName() + " must wear six relics");
            Assertions.assertEquals(4, pieces.stream().filter(piece -> piece.setId == CAVERN_SET).count(),
                    character.getName() + " must wear the 4-piece cavern set");
            Assertions.assertEquals(2, pieces.stream().filter(piece -> piece.setId == PLANAR_SET).count(),
                    character.getName() + " must wear the 2-piece planar set");
        }
    }

    // ==================================================================
    // 6. Registered gaps and error paths
    // ==================================================================

    /**
     * <b>No effect is silently empty.</b> Every bonus in the file either carries stats the engine applies or
     * names an ability it cannot execute yet — nothing is both stat-less and ability-less, which would be a
     * bonus that quietly does nothing.
     *
     * <p>The counts are pinned because they <em>are</em> the registered gap: 57 stat bonuses are applied,
     * 35 ability-only bonuses (most 4-piece effects) are selected but not executed, and if regeneration
     * changes either number, this line is where somebody finds out.
     */
    @Test
    public void everyEffectIsEitherStatsOrANamedAbility() {
        int withStats = 0;
        int abilityOnly = 0;
        for (RelicSet set : Constant.RELIC_SETS.values()) {
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
        Assertions.assertEquals(57, withStats, "the shipped file's stat bonuses");
        Assertions.assertEquals(35, abilityOnly,
                "the shipped file's ability-only bonuses: selected by RelicSuit.activeEffects(), not executed "
                        + "— the engine has no ability interpreter");
        Assertions.assertEquals(Constant.RELIC_SETS.values().stream().mapToInt(s -> s.effects().size()).sum(),
                withStats + abilityOnly, "every effect is accounted for");
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

    /** The factory refuses to build a piece the set does not have (rather than inventing one). */
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

    /** A suit wearing exactly the given slots of the cavern set, built from the real data. */
    private static RelicSuit musketeer(RelicType... slots) {
        return suitOf(CAVERN_SET, slots);
    }

    /** The same pieces as {@link #musketeer}, with the set id cleared: the control that isolates the bonus. */
    private static RelicSuit musketeerWithoutSetBonuses(RelicType... slots) {
        return suitOfWithoutSetId(CAVERN_SET, slots);
    }

    /** The whole reference build (cavern + planar), with every set id cleared. */
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

    /** Runs a suit through the same pipeline a character uses and returns one computed attribute. */
    private static double attribute(RelicSuit suit, AttributeType type, double base) {
        AttributeBuilder builder = new AttributeBuilder();
        builder.setBase(type, base);
        suit.appendTo(builder);
        return builder.build()[type.ordinal()].get();
    }

    /** The attack the 4-piece cavern set contributes at the given piece count. */
    private static double attackBonus(int pieces) {
        return attribute(musketeer(firstSlots(pieces)), AttributeType.ATTACK, BASE_ATTACK)
                - attribute(musketeerWithoutSetBonuses(firstSlots(pieces)), AttributeType.ATTACK, BASE_ATTACK);
    }

    /** The speed the 4-piece cavern set contributes at the given piece count. */
    private static double speedBonus(int pieces) {
        return attribute(musketeer(firstSlots(pieces)), AttributeType.SPEED, BASE_SPEED)
                - attribute(musketeerWithoutSetBonuses(firstSlots(pieces)), AttributeType.SPEED, BASE_SPEED);
    }

    /** The first {@code pieces} cavern slots, in head → hand → body → boot order. */
    private static RelicType[] firstSlots(int pieces) {
        RelicType[] order = {RelicType.HEAD, RelicType.HAND, RelicType.BODY, RelicType.BOOT};
        RelicType[] selected = new RelicType[Math.max(pieces, 0)];
        System.arraycopy(order, 0, selected, 0, Math.max(pieces, 0));
        return selected;
    }

    /** The relics of a suit, read through the public slot fields. */
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

    /** The slots a set occupies, in data order. */
    private static List<RelicType> slotsOf(RelicSet set) {
        return set.parts().stream().map(part -> RelicType.fromSetPart(part.type())).toList();
    }

    /** The piece requirements a suit actually satisfies, in data order. */
    private static List<Integer> requiresOf(RelicSet set) {
        return set.effects().stream().map(RelicSet.Effect::require).toList();
    }

    /** The piece requirements a suit actually satisfies, in data order. */
    private static List<Integer> requiresOf(RelicSuit suit) {
        return suit.activeEffects().stream().map(RelicSet.Effect::require).toList();
    }
}
