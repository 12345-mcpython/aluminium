package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.MemospriteSpec;
import com.laosun.aluminium.data.Memosprites;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.enemy.SummonFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.RelicFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 忆灵 (memosprite) panels: a summon whose stat block is <b>derived from its summoner</b> (P9-4).
 *
 * <p><b>Why the panel is data, and why it is stated as an inheritance.</b> Every memosprite in the documents
 * is described that way — 「等同于阿格莱雅 35% 速度的速度以及等同于阿格莱雅 66% 生命上限 + 720 的生命上限」,
 * 「初始拥有 160 点速度，生命上限为长夜月的 50%」 — and the ratios differ per character. Absolute numbers here
 * would be wrong at every level and would go stale the moment the summoner changed a light cone or a relic,
 * so the spec says <em>how to derive</em> each attribute and the engine reads the summoner's resolved sheet.
 *
 * <p><b>What these cases are guarding.</b>
 * <ol>
 *   <li>the share is taken from the summoner's <b>current</b> sheet, not from a snapshot or from monster
 *       data — the case changes her speed and HP and expects the memosprite to follow;</li>
 *   <li>the flat term is added after the share (阿格莱雅's 720), and a flat-only entry is
 *       <b>not</b> a share of anything (长夜月's fixed 160 speed);</li>
 *   <li>a panel entry <b>replaces</b> an attribute rather than adding to it, so a spec that forgets HEALTH
 *       or SPEED describes a unit that cannot exist — rejected at load, not at battle time;</li>
 *   <li>the memosprite is our side's, so it lands in {@code allies} and never in {@code characters}
 *       (the camp split its placement depends on).</li>
 * </ol>
 */
public class MemospriteTest {
    private static final double EPS = 1e-6;

    /** 阿格莱雅 — 衣匠's panel is two shares; her id is used by no other test. */
    private static final int AGLAEA = 1402;
    /** 长夜月 — 「长夜」's panel is one share and one flat value. */
    private static final int CASTORICE_LIKE = 1413;
    /** 姬子 — a character with no memosprite, used for the "absent is an ordinary state" cases. */
    private static final int NO_MEMOSPRITE = 1003;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    // ==================================================================
    // 1. The panel is derived from the summoner's own sheet
    // ==================================================================

    /**
     * 衣匠's panel is 阿格莱雅's, per her own text: 35% of her speed, 66% of her Max HP plus 720.
     *
     * <p>The flat term is the point of the second assertion: a share alone would be a different number at
     * every level, and the game's own figure has a constant in it.
     */
    @Test
    public void thePanelIsTheSummonersSharePlusItsFlatTerm() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        Summon tailor = SummonFactory.memosprite(aglaea);

        Assertions.assertEquals("衣匠", tailor.getName());
        Assertions.assertEquals(0.66 * aglaea.getMaxHp() + 720, tailor.getMaxHp(), EPS,
                "66% of her Max HP + 720");
        Assertions.assertEquals(0.35 * aglaea.getAttribute(AttributeType.SPEED).get(),
                tailor.getAttribute(AttributeType.SPEED).get(), EPS, "35% of her SPD");
        Assertions.assertEquals(LEVEL, tailor.getLevel(),
                "the level enters the defence zone, so it is the summoner's");
        Assertions.assertEquals(Camp.PLAYER, tailor.getCamp(), "a memosprite fights for its summoner");
    }

    /**
     * It follows the summoner's <b>current</b> sheet — equipment included.
     *
     * <p>Two readings of the same character with different stats must give two different memsprites. This is
     * what makes "derive it" different from "write the numbers down", and it is the reason the spec holds
     * ratios rather than values.
     */
    @Test
    public void thePanelFollowsWhateverTheSummonerIsWearing() {
        Character bare = CharacterFactory.create(AGLAEA, LEVEL);
        // Set 302 (不老者的仙舟) is the one whose 2-piece grants HPAddedRatio; 301's grants ATK.
        Character equipped = CharacterFactory.create(AGLAEA, LEVEL, true, null,
                RelicFactory.suit(302, 5, 15));
        // Also differ in speed, which relics alone might not move.
        equipped.setAttribute(AttributeType.SPEED, new DoubleValue(
                bare.getAttribute(AttributeType.SPEED).get() * 2));

        double bareHp = SummonFactory.memosprite(bare).getMaxHp();
        double equippedHp = SummonFactory.memosprite(equipped).getMaxHp();

        Assertions.assertTrue(equipped.getMaxHp() > bare.getMaxHp(),
                "precondition: the relic suit really raised her Max HP");
        Assertions.assertEquals(0.66 * equipped.getMaxHp() + 720, equippedHp, EPS);
        Assertions.assertTrue(equippedHp > bareHp,
                "so the memosprite's panel moved with her: " + equippedHp + " vs " + bareHp);
    }

    /** 长夜月: a flat speed is <b>not</b> a share of hers, while her HP share is. */
    @Test
    public void aFlatEntryIsNotAShareOfAnything() {
        Character castorice = CharacterFactory.create(CASTORICE_LIKE, LEVEL);
        Summon nightfall = SummonFactory.memosprite(castorice);

        Assertions.assertEquals("长夜", nightfall.getName());
        Assertions.assertEquals(160, nightfall.getAttribute(AttributeType.SPEED).get(), EPS,
                "「初始拥有 160 点速度」 is a flat number, and her own speed is not part of it");
        Assertions.assertNotEquals(castorice.getAttribute(AttributeType.SPEED).get(), 160,
                "…and the two really are different numbers, so the assertion above is not vacuous");
        Assertions.assertEquals(0.5 * castorice.getMaxHp(), nightfall.getMaxHp(), EPS, "50% of her Max HP");

        // Moving HER speed must not move the memosprite's.
        castorice.setAttribute(AttributeType.SPEED, new DoubleValue(999));
        Assertions.assertEquals(160, SummonFactory.memosprite(castorice)
                .getAttribute(AttributeType.SPEED).get(), EPS);
    }

    /** A ratio attribute is derived with the builder's percentage-point convention, not as a base value. */
    @Test
    public void aRatioAttributeIsInheritedAsAPercentagePointValue() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        aglaea.setAttribute(AttributeType.CRIT_CHANCE, new DoubleValue(0.42));

        // No shipped memosprite states a ratio yet, so this spelling is exercised through the spec-taking
        // seam -- inventing a memosprite in the shipped data just to reach it is the one thing worse.
        Summon withCrit = SummonFactory.memosprite(aglaea, new MemospriteSpec("spy", "test", null, List.of(
                new MemospriteSpec.Panel("HEALTH", null, 1_000.0),
                new MemospriteSpec.Panel("SPEED", null, 100.0),
                new MemospriteSpec.Panel("CRIT_CHANCE", 1.0, null))));

        Assertions.assertEquals(0.42, withCrit.getAttribute(AttributeType.CRIT_CHANCE).get(), EPS,
                "100% of her crit rate, as a fraction -- the units every ratio attribute uses");
    }

    // ==================================================================
    // 2. Getting it onto the field
    // ==================================================================

    /** It joins our camp, not our characters, and it takes its turn. */
    @Test
    public void theMemospriteJoinsOurCampAndTakesATurn() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        Battle battle = new Battle(List.of(aglaea), List.of(monster()), new Random(0));

        Summon tailor = battle.summonMemosprite(aglaea);
        battle.processRequests();

        Assertions.assertTrue(battle.allies.contains(tailor));
        Assertions.assertFalse(battle.characters.contains(tailor),
                "it is on our side but it is not one of our characters");
        Assertions.assertTrue(battle.queue.snapshot().stream().anyMatch(s -> s.getCanHit() == tailor),
                "a positive speed is why SPEED is required in the spec: the action bar can schedule it");
        Assertions.assertSame(tailor, battle.memospriteOf(aglaea), "and it can be found by its summoner");
    }

    /**
     * Summoning twice keeps <b>one</b> memosprite, and a dead one can be replaced.
     *
     * <p>Two copies of the same memosprite would be a wrong state with nothing to see — two units where the
     * player sees one. The documents say "if it is already present, restore it to full HP"; that refresh is
     * <em>not</em> modelled, and doing nothing is the honest stand-in until it is.
     */
    @Test
    public void summoningTwiceKeepsOneAndADeadOneCanBeReplaced() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        Battle battle = new Battle(List.of(aglaea), List.of(monster()), new Random(0));
        battle.startBattle();

        Summon first = battle.summonMemosprite(aglaea);
        Assertions.assertSame(first, battle.summonMemosprite(aglaea), "still just the one");
        Assertions.assertEquals(2, battle.allies.size(), "the hero and the one memosprite");

        first.takeDamage(9_999_999);
        battle.processRequests();
        Assertions.assertTrue(first.isDeath());
        Assertions.assertNull(battle.memospriteOf(aglaea), "a corpse is not 'the memosprite on the field'");

        Summon second = battle.summonMemosprite(aglaea);
        Assertions.assertNotSame(first, second, "so a new one can be summoned");
        Assertions.assertEquals(3, battle.allies.size(), "and the dead one stays in the roster, as corpses do");
    }

    /** The summoner falling takes it along — the same lifecycle the monster-side summons have. */
    @Test
    public void theSummonersDeathTakesTheMemospriteAlong() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        Battle battle = new Battle(List.of(aglaea), List.of(monster()), new Random(0));
        battle.startBattle();
        Summon tailor = battle.summonMemosprite(aglaea);
        battle.processRequests();

        aglaea.takeDamage(9_999_999);
        battle.processRequests();

        Assertions.assertTrue(aglaea.isDeath());
        Assertions.assertTrue(tailor.isDeath(), "a memosprite does not outlive its summoner");
    }

    // ==================================================================
    // 3. Refusals
    // ==================================================================

    /** A character with no memosprite is refused <b>loudly</b>, naming the file to write. */
    @Test
    public void aCharacterWithNoMemospriteIsRefusedWithThePathToWrite() {
        Character himeko = CharacterFactory.create(NO_MEMOSPRITE, LEVEL);

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> SummonFactory.memosprite(himeko));

        Assertions.assertTrue(refused.getMessage().contains(String.valueOf(NO_MEMOSPRITE)),
                refused.getMessage());
        Assertions.assertTrue(refused.getMessage().contains(Memosprites.DIR), refused.getMessage());
        Assertions.assertNull(Memosprites.of(NO_MEMOSPRITE),
                "…and the loader's own answer for that character is simply 'none'");
    }

    /** A dead summoner cannot summon: the memosprite would be orphaned in the same breath. */
    @Test
    public void aDeadSummonerIsRefused() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        Battle battle = new Battle(List.of(aglaea), List.of(monster()), new Random(0));
        battle.startBattle();
        aglaea.takeDamage(9_999_999);

        Assertions.assertThrows(IllegalArgumentException.class, () -> battle.summonMemosprite(aglaea));
    }

    // ==================================================================
    // 4. Load-time validation
    // ==================================================================

    /**
     * A panel that cannot describe a unit is rejected where the file is read.
     *
     * <p>Each rejection is a wrong panel that would otherwise only show up by playing: an unnamed unit, an
     * empty panel, a typo, a builder-only key, an entry with no value, a duplicated attribute, a share that
     * is not a share, and the two attributes a unit cannot do without.
     */
    @Test
    public void anUnusablePanelIsRejectedAtLoadTime() {
        assertRejected(spec(null, List.of(panel("HEALTH", 0.5, null))), "name");
        assertRejected(spec("ok", List.of()), "panel");
        assertRejected(spec("ok", null), "panel");
        assertRejected(spec("ok", List.of(panel("HEALHT", 0.5, null), panel("SPEED", null, 100.0))),
                "HEALHT");
        assertRejected(spec("ok", List.of(panel("HEALTH_PERCENT", 0.5, null), panel("SPEED", null, 100.0))),
                "HEALTH_PERCENT");
        assertRejected(spec("ok", List.of(panel("HEALTH", null, null), panel("SPEED", null, 100.0))),
                "neither");
        assertRejected(spec("ok", List.of(panel("HEALTH", 0.5, null), panel("HEALTH", 0.5, null),
                panel("SPEED", null, 100.0))), "twice");
        assertRejected(spec("ok", List.of(panel("HEALTH", -0.5, null), panel("SPEED", null, 100.0))),
                "positive");
        assertRejected(spec("ok", List.of(panel("HEALTH", 0.5, null), panel("SPEED", null, -1.0))),
                "not negative");
    }

    /** The two attributes a memosprite cannot do without, each with its own reason in the message. */
    @Test
    public void aPanelWithoutHealthOrSpeedIsRejectedWithTheConsequence() {
        IllegalArgumentException noHealth = Assertions.assertThrows(IllegalArgumentException.class,
                () -> Memosprites.validate(spec("ok", List.of(panel("SPEED", null, 100.0))), "test"));
        Assertions.assertTrue(noHealth.getMessage().contains("HEALTH"), noHealth.getMessage());
        Assertions.assertTrue(noHealth.getMessage().contains("0 Max HP"), noHealth.getMessage());

        IllegalArgumentException noSpeed = Assertions.assertThrows(IllegalArgumentException.class,
                () -> Memosprites.validate(spec("ok", List.of(panel("HEALTH", 0.5, null))), "test"));
        Assertions.assertTrue(noSpeed.getMessage().contains("SPEED"), noSpeed.getMessage());
        Assertions.assertTrue(noSpeed.getMessage().contains("schedule"), noSpeed.getMessage());
    }

    /** A share of zero with no flat term would give 0, which for these two attributes is not a unit. */
    @Test
    public void aZeroValueForHealthOrSpeedIsRejected() {
        IllegalArgumentException zeroSpeed = Assertions.assertThrows(IllegalArgumentException.class,
                () -> Memosprites.validate(
                        spec("ok", List.of(panel("HEALTH", 0.5, null), panel("SPEED", null, 0.0))), "test"));
        Assertions.assertTrue(zeroSpeed.getMessage().contains("zero"), zeroSpeed.getMessage());
    }

    // ==================================================================
    // 5. The loader's contract: lazy, cached, absent is not an error
    // ==================================================================

    /** Absent is an ordinary state; a file is read once and cached. */
    @Test
    public void theLoaderIsLazyCachedAndTolerantOfAbsence() {
        Memosprites.clearCache();
        Assertions.assertEquals(0, Memosprites.loadCount());

        Assertions.assertNull(Memosprites.of(NO_MEMOSPRITE), "no file, no memosprite, no exception");
        Assertions.assertEquals(0, Memosprites.loadCount(), "and asking did not count as a read");

        Assertions.assertNotNull(Memosprites.of(AGLAEA));
        Assertions.assertEquals(1, Memosprites.loadCount());
        Assertions.assertSame(Memosprites.of(AGLAEA), Memosprites.of(AGLAEA), "cached, not re-parsed");
        Assertions.assertEquals(1, Memosprites.loadCount());
        Assertions.assertTrue(Memosprites.exists(AGLAEA));
        Assertions.assertFalse(Memosprites.exists(NO_MEMOSPRITE));
    }

    /** The shipped specs are valid and state what their documents state. */
    @Test
    public void theShippedSpecsAreValidAndGroundedInTheirDocuments() {
        MemospriteSpec aglaea = Memosprites.of(AGLAEA);
        Assertions.assertEquals(List.of(AttributeType.HEALTH, AttributeType.SPEED),
                Memosprites.mentionedBy(aglaea));
        Assertions.assertTrue(aglaea.source().contains("1402"), aglaea.source());
        Assertions.assertTrue(aglaea.source().contains("0.66"), "the parameters are quoted: " + aglaea.source());

        MemospriteSpec castorice = Memosprites.of(CASTORICE_LIKE);
        Assertions.assertTrue(castorice.source().contains("1413"), castorice.source());
        Assertions.assertNotNull(castorice.note(), "a spec carries the reasoning for the next reader");
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    private static MemospriteSpec spec(String name, List<MemospriteSpec.Panel> panel) {
        return new MemospriteSpec(name, "MemospriteTest", null, panel);
    }

    private static MemospriteSpec.Panel panel(String attribute, Double percent, Double flat) {
        return new MemospriteSpec.Panel(attribute, percent, flat);
    }

    private static void assertRejected(MemospriteSpec spec, String expectedInMessage) {
        IllegalArgumentException rejected = Assertions.assertThrows(IllegalArgumentException.class,
                () -> Memosprites.validate(spec, "test"));
        Assertions.assertTrue(rejected.getMessage().contains(expectedInMessage),
                "expected the message to mention '" + expectedInMessage + "': " + rejected.getMessage());
    }

    private static Enemy monster() {
        return EnemyFactory.create(MONSTER, 90, 1);
    }
}
