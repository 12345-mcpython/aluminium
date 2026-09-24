package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Path;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.StageFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The real team assembly (P8-5).
 *
 * <p>Until P8-5, {@code StageFactory.load} fought with a placeholder team built from
 * {@code Character.fromAttributes}: no element, no path, no real skills, and stat magnitudes chosen by
 * hand. This class pins what replaced it — a 4-character team from {@link CharacterFactory}, each
 * carrying a light cone of its own path.
 *
 * <p>⚠ The point of these assertions is that the team is **real**, not merely that it is non-empty:
 * the placeholder team satisfied "size == 3 and everyone has HP", so a test that only checks that
 * would pass against the old code too.
 */
public class RealTeamTest {

    /** A stage with wave data, used by the acceptance case. */
    private static final int STAGE = 103201;

    // ==================================================================
    // The team itself
    // ==================================================================

    /** The acceptance criterion: the team is 4 characters, every one with a real element. */
    @Test
    public void theTeamIsFourRealCharacters() {
        List<Character> team = StageFactory.realTeam();

        Assertions.assertEquals(4, team.size());
        for (Character character : team) {
            Assertions.assertNotNull(character.getElement(),
                    character.getName() + " must have a real element (the placeholder team had none)");
            Assertions.assertNotEquals(0, character.getCid(),
                    character.getName() + " must know its own cid (placeholders had none)");
            Assertions.assertTrue(character.getMaxHp() > 0);
        }
    }

    /**
     * Every member has a **distinct identity**: different cid, different element/path combination.
     *
     * <p>Guards against the assembly accidentally producing four copies of the same character, which
     * "size == 4" alone would not catch.
     */
    @Test
    public void theMembersAreDistinctCharacters() {
        List<Character> team = StageFactory.realTeam();

        long distinctCids = team.stream().map(Character::getCid).distinct().count();
        Assertions.assertEquals(4, distinctCids, "four different characters");

        long distinctPaths = team.stream().map(Character::getPath).distinct().count();
        Assertions.assertEquals(4, distinctPaths,
                "the roster covers four different paths, so more than one damage shape gets exercised");
    }

    /**
     * Every member carries a light cone whose type matches its path.
     *
     * <p>Picked by path from {@code weapons.json}; see {@code StageFactory.samePathWeapon}.
     */
    @Test
    public void everyMemberCarriesAWeaponOfItsOwnPath() {
        List<Character> team = StageFactory.realTeam();

        for (Character character : team) {
            Weapon weapon = character.getWeapon();
            Assertions.assertNotNull(weapon, character.getName() + " should carry a light cone");

            // The weapon's `type` uses the same vocabulary as the character's `mt`, which is exactly
            // why Path is the join key.
            Assertions.assertEquals(character.getPath(), Path.fromMt(weapon.getType()),
                    character.getName() + "'s cone should be of its own path (type=" + weapon.getType() + ")");
        }
    }

    /** The cone's panel actually reaches the character's sheet. */
    @Test
    public void theWeaponContributesToTheStatSheet() {
        Character withCone = StageFactory.realTeam().getFirst();

        // Rebuild the same character without a cone and compare: the cone must have added attack.
        Character bare = CharacterFactory.create(withCone.getCid(), 80);
        double withConeAttack = withCone.getAttribute(AttributeType.ATTACK).get();
        double bareAttack = bare.getAttribute(AttributeType.ATTACK).get();

        Assertions.assertTrue(withConeAttack > bareAttack,
                "equipping a light cone must raise the sheet (" + bareAttack + " -> " + withConeAttack + ")");
    }

    /**
     * ⚠ {@code WeaponData.rarity} really binds. It was missing from the bean until P8-5 even though
     * {@code weapons.json} has the field, which is the classic silent-Gson failure this project keeps
     * hitting: the pick would then always see {@code rarity == 0} and quietly fall back to sorting by
     * id alone (i.e. 3-star starter cones).
     */
    @Test
    public void weaponRarityIsBound() {
        var highest = Constant.WEAPONS.values().stream()
                .mapToInt(com.laosun.aluminium.beans.WeaponData::rarity)
                .max().orElse(0);
        Assertions.assertEquals(5, highest,
                "weapons.json has 5-star cones; a max of 0 means rarity failed to bind");
    }

    /** The team therefore carries the strongest available cone per path, not a starter one. */
    @Test
    public void teamCarriesBestAvailableConePerPath() {
        for (Character character : StageFactory.realTeam()) {
            int rarity = Constant.WEAPONS.values().stream()
                    .filter(w -> Path.fromMt(w.type()) == character.getPath())
                    .mapToInt(com.laosun.aluminium.beans.WeaponData::rarity)
                    .max().orElse(0);
            Assertions.assertTrue(rarity > 0, "the path should have at least one cone");
            // The chosen cone's panel sits at the rarity's value: check it is not a 3-star's numbers.
            Assertions.assertTrue(character.getWeapon().getAttack() > 400,
                    character.getName() + " should carry a high-rarity cone, got atk="
                            + character.getWeapon().getAttack());
        }
    }

    /** The team is built fresh each call, so two teams never share mutable state. */
    @Test
    public void eachCallBuildsAFreshTeam() {
        List<Character> first = StageFactory.realTeam();
        List<Character> second = StageFactory.realTeam();

        Assertions.assertNotSame(first.getFirst(), second.getFirst(),
                "a second call must not hand back the same instances");
        first.getFirst().takeDamage(1);
        Assertions.assertEquals(second.getFirst().getMaxHp(), second.getFirst().getCurrentHp(), 1e-9,
                "damaging one team must not affect the other");
    }

    /** Speeds differ, so the action bar order is meaningful rather than a tie. */
    @Test
    public void speedsAreDistinct() {
        List<Character> team = StageFactory.realTeam();
        long distinct = team.stream()
                .map(c -> c.getAttribute(AttributeType.SPEED).get())
                .distinct().count();
        Assertions.assertEquals(4, distinct, "distinct speeds keep the action bar deterministic");
    }

    // ==================================================================
    // Through a real stage
    // ==================================================================

    /** The acceptance criterion: {@code load(103201)} fights with this team and runs without blowing up. */
    @Test
    public void stageDoesNotGetBuiltWithPlaceholders() {
        assumeStageData();

        Battle battle = StageFactory.load(STAGE, StageFactory.realTeam(), new Random(1));
        List<Character> team = battle.characters;

        Assertions.assertEquals(4, team.size());
        Assertions.assertEquals(4, team.stream().map(Character::getCid).distinct().count(),
                "the battle's characters should be four distinct real characters");
        for (Character character : team) {
            Assertions.assertNotNull(character.getElement());
        }

        // A full round without exceptions.
        for (int i = 0; i < 8 && !battle.isOver(); i++) {
            battle.stepForward();
            if (battle.currentMove == null) {
                break;
            }
            battle.beforeMove();
            battle.afterMove();
        }
        Assertions.assertNotEquals(Battle.Status.NOT_STARTED, battle.getStatus());
        Assertions.assertTrue(battle.queue.getElapsed() > 0);
    }

    /** The no-argument {@code load} uses the real team too (that is the whole point of P8-5). */
    @Test
    public void defaultLoadUsesTheRealTeam() {
        assumeStageData();

        Battle battle = StageFactory.load(STAGE);
        Assertions.assertEquals(4, battle.characters.size());
        for (Character character : battle.characters) {
            Assertions.assertNotNull(character.getElement(),
                    character.getName() + " came from the placeholder path (no element)");
            Assertions.assertNotEquals(0, character.getCid(), character.getName() + " must be a real cid");
        }
    }

    /** The old placeholder entry point is gone, so nothing can silently go back to it. */
    @Test
    public void placeholderTeamNoLongerExists() {
        boolean present = false;
        for (var method : StageFactory.class.getDeclaredMethods()) {
            if (method.getName().toLowerCase().contains("temporary")) {
                present = true;
            }
        }
        Assertions.assertFalse(present,
                "StageFactory must not still expose a temporary/placeholder team");
    }

    /** Stage data is generator output and is not in the repository. */
    private static void assumeStageData() {
        Assumptions.assumeFalse(Constant.stages().isEmpty(),
                "missing stage.json (generator output), skipping the stage-level assertions");
    }
}
