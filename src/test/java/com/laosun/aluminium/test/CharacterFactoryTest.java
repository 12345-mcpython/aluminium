package com.laosun.aluminium.test;

import com.laosun.aluminium.beans.CharacterData;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.Path;
import com.laosun.aluminium.exceptions.CharacterException;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.SkillPoint;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.LevelPromotionCalc;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

/**
 * P8-1 acceptance: {@code CharacterFactory} + completion of the character identity fields.
 *
 * <p>This item **does not do skill assembly** (that is P8-2), so what is asserted here is:
 * element / path / aggro / energy cap / level / panel scaling — that is, "a character's identity
 * and numbers", without mechanics.
 *
 * <p>Character selection principle: the 5 are the P8-5 target team + Preservation, covering 5 of
 * the 8 elements, 5 paths, 4 aggro tiers, 4 energy tiers; plus 3 **data boundary** characters
 * (null / 12 / 9 energy) that only get energy assertions.
 */
public class CharacterFactoryTest {

    // ==================================================================
    // Main acceptance: Jing Yuan
    // ==================================================================

    @Test
    public void createBuildsARealCharacter() {
        Character jingYuan = CharacterFactory.create(1204, 80);

        Assertions.assertEquals("Jing Yuan", jingYuan.getName());
        Assertions.assertEquals(80, jingYuan.getLevel());
        Assertions.assertEquals(DamageElement.THUNDER, jingYuan.getElement());
        Assertions.assertEquals(Path.ERUDITION, jingYuan.getPath());
        Assertions.assertEquals(130, jingYuan.getMaxEnergy(), 1e-9);
        Assertions.assertEquals(75, jingYuan.getAggro());
    }

    /**
     * Every character's star rating can be read, and the only values are 4 / 5 (a prerequisite of P8-2).
     *
     * <p>{@code rarity} is obtained by the generator from the last digit of
     * {@code AvatarConfig.Rarity} (of the form {@code CombatPowerAvatarRarityType5}); in the data it
     * is **23 four-stars + 70 five-stars** (consistent with the docs' index table).
     *
     * <p>Why pin it down now: **in P8-2 the skill level cap differs by star rating** (it must be
     * readable before skill assembly), and if the {@code CharacterData} record is missing a field,
     * Gson silently gives 0 — no error, just everything wrong.
     */
    @Test
    public void everyCharacterHasAStarRating() {
        Map<Integer, Long> distribution = com.laosun.aluminium.Constant.CHARACTERS.values().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        CharacterData::rarity, java.util.stream.Collectors.counting()));

        Assertions.assertEquals(Set.of(4, 5), distribution.keySet(),
                "the star rating should only be 4 and 5, actually " + distribution);
        Assertions.assertEquals(23L, distribution.get(4), "number of 4★");
        Assertions.assertEquals(70L, distribution.get(5), "number of 5★");

        Assertions.assertEquals(5, CharacterFactory.data(1204).rarity(), "Jing Yuan is 5★");
        Assertions.assertEquals(4, CharacterFactory.data(1001).rarity(), "March 7th is 4★");
    }

    /**
     * Panel scaling check: {@code final panel = data base value × calcCharacterRate(80, true)},
     * **then plus the trace bonuses from {@code point.json}** (the latter applied unconditionally in
     * {@code build()}).
     *
     * <pre>
     *   Jing Yuan's traces: attack 4+4+6+6+8 = 28%, defence 5+7.5 = 12.5%, no HP trace
     *   attack  95.04  × 7.35 × 1.28   = 894.13632
     *   defence 66     × 7.35 × 1.125  = 545.7375
     *   HP      158.4  × 7.35          = 1164.24
     * </pre>
     *
     * <p>⚠ My first version asserted attack / defence directly as {@code data × multiplier} and
     * **missed the traces**; the failure values 894.136 / 545.7375 looked like "attribute array
     * index misalignment", which sent me down a wrong diagnosis for one round.
     * Here it was changed to explicitly include the traces via {@code SkillPoint.sumAttributes} —
     * the test explains that 28% / 12.5% by itself.
     */
    @Test
    public void panelIsScaledFromTheData() {
        CharacterData data = CharacterFactory.data(1204);
        Character jingYuan = CharacterFactory.create(1204, 80);
        double rate = LevelPromotionCalc.calcCharacterRate(80, true);
        Map<AttributeType, Double> traces = SkillPoint.sumAttributes(SkillPoint.init(1204));

        double traceAttack = traces.getOrDefault(AttributeType.ATTACK_PERCENT, 0.0);
        double traceDefence = traces.getOrDefault(AttributeType.DEFENCE_PERCENT, 0.0);
        Assertions.assertEquals(0.28, traceAttack, 1e-9, "Jing Yuan's attack traces total 28%");
        Assertions.assertEquals(0.125, traceDefence, 1e-9, "Jing Yuan's defence traces total 12.5%");

        Assertions.assertEquals(data.health() * rate,
                jingYuan.getAttribute(AttributeType.HEALTH).get(), 1e-3,
                "he has no HP trace");
        Assertions.assertEquals(data.attack() * rate * (1 + traceAttack),
                jingYuan.getAttribute(AttributeType.ATTACK).get(), 1e-3);
        Assertions.assertEquals(data.defence() * rate * (1 + traceDefence),
                jingYuan.getAttribute(AttributeType.DEFENCE).get(), 1e-3);
        // speed does not take level scaling, and there is no speed trace
        Assertions.assertEquals(data.speed(),
                jingYuan.getAttribute(AttributeType.SPEED).get(), 1e-9);
        Assertions.assertEquals(1164.24, jingYuan.getAttribute(AttributeType.HEALTH).get(), 1e-3,
                "Jing Yuan Lv80 fully ascended HP (in-game value)");
    }

    /**
     * The ascension multiplier only guarantees **the two anchors Lv1 and Lv80**; the intermediate
     * tiers are a **linear approximation**, not game values.
     *
     * <p>Measured {@code calcCharacterRate}: Lv20 → 2.35, Lv40 → 4.15, Lv70 → **6.85**;
     * whereas in the docs' "panel growth" table, ascension 2/40 is
     * {@code 285.12 / 158.4 = 1.80} and ascension 5/70 is {@code 475.2 / 158.4 = 3.00}
     * (that is, 1 + level tier × 0.4).
     * So **do not** use the intermediate tiers to cross-check against the game panel — the formula
     * only lines up at the two ends (this is consistent with the "simulator multiplier formula"
     * recorded in ROADMAP P1-4: it was an approximation to begin with).
     *
     * <p>This one writes "which anchors are trustworthy" into the test, so that next time nobody
     * (including me) asserts 475.2 at Lv70 again.
     */
    @Test
    public void onlyTheLevel1AndLevel80AnchorsMatchTheGameTable() {
        // Trustworthy anchor 1: Lv1 unascended = base value
        Assertions.assertEquals(1.0, LevelPromotionCalc.calcCharacterRate(1, false), 1e-9);
        Assertions.assertEquals(158.4,
                158.4 * LevelPromotionCalc.calcCharacterRate(1, false), 1e-9);

        // Trustworthy anchor 2: Lv80 ascended = ×7.35 (Jing Yuan max level 1164.24 / March 7th 1058.4)
        Assertions.assertEquals(7.35, LevelPromotionCalc.calcCharacterRate(80, true), 1e-9);
        Assertions.assertEquals(1164.24,
                158.4 * LevelPromotionCalc.calcCharacterRate(80, true), 1e-3);
        Assertions.assertEquals(1058.4,
                144.0 * LevelPromotionCalc.calcCharacterRate(80, true), 1e-3);

        // Intermediate tiers: record the current formula value, **and explicitly record its difference from the game table**
        Assertions.assertEquals(6.85, LevelPromotionCalc.calcCharacterRate(70, true), 1e-9);
        Assertions.assertNotEquals(475.2 / 158.4,
                LevelPromotionCalc.calcCharacterRate(70, true), 1e-6,
                "Lv70 is a deviating tier of the linear approximation, not a game value (the game table is 3.00×)");
    }

    /**
     * A latent bug fixed in P8-1: a low level combined with "ascended" once produced a
     * **negative ascension**.
     *
     * <p>Originally Lv1 + {@code promotion=true} gave {@code promoteCount = 1/10 - 1 = -1}, a
     * multiplier of 0.6 — so "ascended" actually pushed the level-1 panel down to 60% (Jing Yuan's
     * HP 158.4 → 95.04).
     * And 95.04 happens to be his **attack** value, so this bug looked like "attribute array index
     * misalignment" and was extremely easy to misdiagnose (I misdiagnosed it at first myself).
     *
     * <p>A level-1 character cannot have a negative ascension, so the lower bound is 0, and the Lv1
     * multiplier must be exactly 1.0.
     */
    @Test
    public void lowLevelsNeverGetNegativePromotion() {
        Assertions.assertEquals(1.0, LevelPromotionCalc.calcCharacterRate(1, true), 1e-9);
        Assertions.assertEquals(1.0, LevelPromotionCalc.calcCharacterRate(1, false), 1e-9);
        for (int level = 1; level <= 80; level++) {
            Assertions.assertTrue(LevelPromotionCalc.calcCharacterRate(level, true) >= 1.0,
                    "Lv" + level + " ascended multiplier should not be less than 1");
            Assertions.assertTrue(LevelPromotionCalc.calcCharacterRate(level, true)
                            >= LevelPromotionCalc.calcCharacterRate(level, false),
                    "Lv" + level + ": the ascended panel should not be lower than the unascended one");
        }
    }

    /**
     * The unascended panel is **never higher than** the ascended one (at Lv70 it really is lower —
     * at Lv80 the two are the same, see the test above).
     */
    @Test
    public void unpromotedPanelIsNeverHigher() {
        Character promoted = CharacterFactory.create(1204, 70, true);
        Character unpromoted = CharacterFactory.create(1204, 70, false);
        double promotedRate = LevelPromotionCalc.calcCharacterRate(70, true);
        double unpromotedRate = LevelPromotionCalc.calcCharacterRate(70, false);

        Assertions.assertTrue(promoted.getAttribute(AttributeType.HEALTH).get()
                        > unpromoted.getAttribute(AttributeType.HEALTH).get(),
                "the Lv70 ascended panel should be higher");
        Assertions.assertEquals(CharacterFactory.data(1204).health() * promotedRate,
                promoted.getAttribute(AttributeType.HEALTH).get(), 1e-3);
        Assertions.assertEquals(CharacterFactory.data(1204).health() * unpromotedRate,
                unpromoted.getAttribute(AttributeType.HEALTH).get(), 1e-3);
    }

    // ==================================================================
    // Field coverage: element / path / aggro / energy
    // ==================================================================

    /**
     * Full-table check of the identity fields of 5 real characters (values taken directly from
     * {@code character_data.json}).
     *
     * <p>The element, path, aggro and energy of these 5 are deliberately made **pairwise different**,
     * so one test covers several tiers.
     */
    @Test
    public void identityFieldsMatchTheDataForTheTargetTeam() {
        assertIdentity(1204, "Jing Yuan", DamageElement.THUNDER, Path.ERUDITION, 130, 75);
        assertIdentity(1102, "Seele", DamageElement.QUANTUM, Path.HUNT, 120, 75);
        assertIdentity(1107, "Clara", DamageElement.PHYSICAL, Path.DESTRUCTION, 110, 125);
        assertIdentity(1105, "Natasha", DamageElement.PHYSICAL, Path.ABUNDANCE, 90, 100);
        assertIdentity(1001, "March 7th", DamageElement.ICE, Path.PRESERVATION, 120, 150);
    }

    /**
     * Element parsing must be **case-insensitive**: {@code character_data.attribute} is all
     * lowercase ({@code "thunder"}), whereas the {@code element} of {@code skills.json} is
     * capitalized ({@code "Thunder"}). Both spellings exist in the same data set.
     *
     * <p>Before the fix {@code fromString} was an exact match, so all-lowercase input
     * **silently returned null** — the element field became null instead of raising an error.
     */
    @Test
    public void elementParsingIsCaseInsensitive() {
        Assertions.assertEquals(DamageElement.THUNDER, DamageElement.fromString("Thunder"));
        Assertions.assertEquals(DamageElement.THUNDER, DamageElement.fromString("thunder"));
        Assertions.assertEquals(DamageElement.THUNDER, DamageElement.fromString("THUNDER"));
        Assertions.assertEquals(DamageElement.THUNDER, DamageElement.fromString("  thunder  "),
                "leading and trailing whitespace should be ignored");
        Assertions.assertEquals(DamageElement.QUANTUM, DamageElement.fromString("Quantum"));
        Assertions.assertEquals(DamageElement.QUANTUM, DamageElement.fromString("quantum"));

        // Non-damaging skills are written "Unknown" in the data → must still be null, not become some element
        Assertions.assertNull(DamageElement.fromString("Unknown"));
        Assertions.assertNull(DamageElement.fromString("unknown"));
        Assertions.assertNull(DamageElement.fromString(""));
        Assertions.assertNull(DamageElement.fromString(null));
    }

    @Test
    public void identityFieldsAreCaseInsensitivelyParsedForEveryCharacter() {
        // All 93 characters' attribute should parse to an element (only 7 elements exist in the data, all present)
        com.laosun.aluminium.Constant.CHARACTERS.forEach((cid, data) -> {
            DamageElement element = DamageElement.fromString(data.attribute());
            Assertions.assertNotNull(element,
                    "character " + cid + "'s attribute=" + data.attribute() + " does not parse to an element");
        });
    }

    // ==================================================================
    // Energy boundaries (the three named by the P3-0 A table)
    // ==================================================================

    /**
     * The three data boundaries of the energy cap:
     * <ul>
     *   <li>1407 遐蝶 — the only {@code null} in the whole data set. **Must stay 0 (no energy bar)**;
     *       falling back to 100 would conjure an energy bar for her out of nothing;</li>
     *   <li>1220 Feixiao — 12 (the ultimate only costs 6);</li>
     *   <li>1308 Acheron — 9 (actually uses "stacks in place of an energy bar", see P8-8).</li>
     * </ul>
     */
    @Test
    public void energyEdgeCasesAreKeptFaithfully() {
        Assertions.assertNull(CharacterFactory.data(1407).maxEnergy(), "Castorice's energy really is null");
        Assertions.assertEquals(0, CharacterFactory.create(1407, 80).getMaxEnergy(), 1e-9,
                "null must land as 0 = no energy bar; it must not fall back to 100");
        Assertions.assertFalse(CharacterFactory.create(1407, 80).hasEnergyBar(),
                "Castorice has no energy bar");

        Assertions.assertEquals(12, CharacterFactory.create(1220, 80).getMaxEnergy(), 1e-9);
        Assertions.assertEquals(9, CharacterFactory.create(1308, 80).getMaxEnergy(), 1e-9);
    }

    /**
     * Characters that have an energy bar: {@code hasEnergyBar()} is true and the starting energy is 0.
     */
    @Test
    public void charactersWithEnergyHaveAnEnergyBar() {
        Character jingYuan = CharacterFactory.create(1204, 80);

        Assertions.assertTrue(jingYuan.hasEnergyBar());
        Assertions.assertEquals(0, jingYuan.getCurrentEnergy(), 1e-9);
        Assertions.assertFalse(jingYuan.isEnergyFull(), "should not start at full energy");
    }

    // ==================================================================
    // Mid-battle entry / legacy entry point
    // ==================================================================

    /**
     * Different level → different panel (level 1 and level 80).
     */
    @Test
    public void levelChangesThePanel() {
        Character low = CharacterFactory.create(1204, 1);
        Character high = CharacterFactory.create(1204, 80);

        Assertions.assertEquals(1, low.getLevel());
        Assertions.assertTrue(high.getAttribute(AttributeType.HEALTH).get()
                > low.getAttribute(AttributeType.HEALTH).get());
        Assertions.assertEquals(CharacterFactory.data(1204).health(),
                low.getAttribute(AttributeType.HEALTH).get(), 1e-3,
                "Lv1 is just the data base value (multiplier 1.0)");
    }

    /**
     * The legacy placeholder entry point still works, but has **no** element (faithfully reflecting
     * "this is not a character").
     *
     * <p>This also explains why new code after P8 should not use it any more.
     */
    @Test
    public void placeholderEntryStillWorksButHasNoIdentity() {
        Character placeholder = Character.fromAttributes("hero", 10_000, 100, 100, 100);

        Assertions.assertEquals("hero", placeholder.getName());
        Assertions.assertNull(placeholder.getElement(), "the placeholder character has no element");
        Assertions.assertEquals(Path.OTHER, placeholder.getPath(), "the placeholder character's path is the fallback value");
        Assertions.assertEquals(0, placeholder.getMaxEnergy(), 1e-9, "the placeholder character has no energy bar");
    }

    /**
     * Unknown cid → a self-explanatory exception; {@code exists} can be asked in advance.
     */
    @Test
    public void unknownCharacterIsRejected() {
        Assertions.assertThrows(CharacterException.class, () -> CharacterFactory.create(99999, 80));
        Assertions.assertThrows(CharacterException.class, () -> CharacterFactory.data(99999));

        Assertions.assertTrue(CharacterFactory.exists(1204));
        Assertions.assertFalse(CharacterFactory.exists(99999));
    }

    /**
     * A character produced by the factory can join a battle directly (panel / element / path all present).
     */
    @Test
    public void factoryCharactersCanJoinABattle() {
        Character jingYuan = CharacterFactory.create(1204, 80);
        Character seele = CharacterFactory.create(1102, 80);
        com.laosun.aluminium.models.Enemy enemy =
                com.laosun.aluminium.models.EnemyFactory.create(1002011, 90, 1);

        com.laosun.aluminium.Battle battle = new com.laosun.aluminium.Battle(
                java.util.List.of(jingYuan, seele), java.util.List.of(enemy), new java.util.Random(0));
        battle.startBattle();

        Assertions.assertEquals(3, battle.queue.size(), "2 characters + 1 monster");
        Assertions.assertEquals(com.laosun.aluminium.Battle.Status.RUNNING, battle.getStatus());
        // aggro comes from the data (Jing Yuan 75 / Seele 75), so the chance of being hit is even
        Assertions.assertEquals(75, battle.aggroOf(jingYuan));
        Assertions.assertEquals(75, battle.aggroOf(seele));
    }

    // ==================================================================

    private static void assertIdentity(int cid, String name, DamageElement element,
                                       Path path, double energy, int aggro) {
        Character character = CharacterFactory.create(cid, 80);

        Assertions.assertEquals(name, character.getName(), "cid " + cid);
        Assertions.assertEquals(element, character.getElement(), "cid " + cid);
        Assertions.assertEquals(path, character.getPath(), "cid " + cid);
        Assertions.assertEquals(energy, character.getMaxEnergy(), 1e-9, "cid " + cid);
        Assertions.assertEquals(aggro, character.getAggro(), "cid " + cid);
    }
}
