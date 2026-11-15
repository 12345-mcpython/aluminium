package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Path;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.StageFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The real team assembly.
 *
 * <p>Formerly, {@code StageFactory.load} fought with a placeholder team built from
 * {@code Character.fromAttributes}: no element, no path, no real skills, and stat magnitudes chosen by
 * hand. This class pins what replaced it - a 4-character team from {@link CharacterFactory}, each
 * carrying a light cone of its own path.
 *
 * <p>Note: The point of these assertions is that the team is real, not merely that it is non-empty:
 * the placeholder team satisfied "size == 3 and everyone has HP", so a test that only checks that
 * would pass against the old code too.
 *
 * <p>The cases are grouped by fixture: each scenario test reads one fixture (a real team, a battle
 * loaded through {@code StageFactory.load}, the weapons catalog, two separately built teams) and
 * asserts every clause that fixture supports through {@link Assertions#assertAll}.
 */
public class RealTeamTest {

    /**
     * A stage with wave data, used by the acceptance case.
     */
    private static final int STAGE = 103201;

    // ==================================================================
    // The team itself
    // ==================================================================

    /**
     * Scenario: the roster is four real, distinct characters.
     *
     * <p>Merged from three single-clause cases that all observe the same {@code StageFactory.realTeam()}
     * fixture: the size/element/cid/HP clause, the identity clause (distinct cids, four different
     * paths), and the speed clause (distinct speeds keep the action bar deterministic).
     *
     * <p>Guards against the assembly accidentally producing four copies of the same character, which
     * "size == 4" alone would not catch.
     */
    @Test
    public void theRosterIsFourDistinctRealCharacters() {
        List<Character> team = StageFactory.realTeam();

        long distinctCids = team.stream().map(Character::getCid).distinct().count();
        long distinctPaths = team.stream().map(Character::getPath).distinct().count();
        long distinctSpeeds = team.stream()
                .map(c -> c.getAttribute(AttributeType.SPEED).get())
                .distinct().count();

        Assertions.assertAll("the roster is four distinct real characters",
                () -> Assertions.assertEquals(4, team.size(), "the roster is four characters"),
                () -> {
                    for (Character character : team) {
                        Assertions.assertAll(character.getName() + " is a real character",
                                () -> Assertions.assertNotNull(character.getElement(),
                                        character.getName()
                                                + " must have a real element (the placeholder team had none)"),
                                () -> Assertions.assertNotEquals(0, character.getCid(),
                                        character.getName()
                                                + " must know its own cid (placeholders had none)"),
                                () -> Assertions.assertTrue(character.getMaxHp() > 0,
                                        character.getName() + " must have positive max HP"));
                    }
                },
                () -> Assertions.assertEquals(4, distinctCids, "four different characters"),
                () -> Assertions.assertEquals(4, distinctPaths,
                        "the roster covers four different paths, so more than one damage shape gets exercised"),
                () -> Assertions.assertEquals(4, distinctSpeeds,
                        "distinct speeds keep the action bar deterministic"));
    }

    /**
     * Scenario: every member carries the best light cone its path offers, and it is of its own path.
     *
     * <p>Merged from three cases that all observe the same fixture - the team's cones and the
     * {@code Constant.WEAPONS} catalog they are picked from: the cone's path matching the member's path,
     * the pick being the strongest cone per path rather than a 3-star starter, and the
     * {@code WeaponData.rarity} binding both of those depend on.
     *
     * <p>Note: {@code WeaponData.rarity} really binds. It was missing from the bean until recently even
     * though {@code weapons.json} has the field, which is the classic silent-Gson failure this project
     * keeps hitting: the pick would then always see {@code rarity == 0} and quietly fall back to sorting
     * by id alone (i.e. 3-star starter cones).
     */
    @Test
    public void everyMemberCarriesTheBestConeOfItsOwnPath() {
        List<Character> team = StageFactory.realTeam();

        var highestRarity = Constant.WEAPONS.values().stream()
                .mapToInt(com.laosun.aluminium.beans.WeaponData::rarity)
                .max().orElse(0);

        Assertions.assertAll("every member carries the best cone of its own path",
                () -> Assertions.assertEquals(5, highestRarity,
                        "weapons.json has 5-star cones; a max of 0 means rarity failed to bind"),
                () -> {
                    for (Character character : team) {
                        Weapon weapon = character.getWeapon();

                        // The weapon's `type` uses the same vocabulary as the character's `mt`, which is
                        // exactly why Path is the join key.
                        int pathRarity = Constant.WEAPONS.values().stream()
                                .filter(w -> Path.fromMt(w.type()) == character.getPath())
                                .mapToInt(com.laosun.aluminium.beans.WeaponData::rarity)
                                .max().orElse(0);

                        Assertions.assertAll(character.getName() + "'s light cone",
                                () -> Assertions.assertNotNull(weapon,
                                        character.getName() + " should carry a light cone"),
                                () -> Assertions.assertEquals(character.getPath(), Path.fromMt(weapon.getType()),
                                        character.getName() + "'s cone should be of its own path (type="
                                                + weapon.getType() + ")"),
                                () -> Assertions.assertTrue(pathRarity > 0,
                                        "the path should have at least one cone"),
                                // The chosen cone's panel sits at the rarity's value: check it is not a
                                // 3-star's numbers.
                                () -> Assertions.assertTrue(weapon.getAttack() > 400,
                                        character.getName() + " should carry a high-rarity cone, got atk="
                                                + character.getWeapon().getAttack()));
                    }
                });
    }

    /**
     * The cone's panel actually reaches the character's sheet.
     *
     * <p>Kept apart from the cone scenario: this compares two different fixtures - a member of the real
     * team and the same cid rebuilt by {@link CharacterFactory} without a cone.
     */
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
     * The team is built fresh each call, so two teams never share mutable state.
     */
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

    // ==================================================================
    // Through a real stage
    // ==================================================================

    /**
     * Scenario: a real stage is fought by the real team.
     *
     * <p>Merged from the two stage-level acceptance cases that share the {@code stage.json} fixture and
     * the {@code StageFactory.load} entry point: the explicit-team battle that must run a round without
     * blowing up, and the no-argument load that must default to the same real team.
     */
    @Test
    public void theStageRunsWithTheRealTeam() {
        assumeStageData();

        Battle battle = StageFactory.load(STAGE, StageFactory.realTeam(), new Random(1));
        List<Character> team = battle.characters();

        Assertions.assertAll("load(103201, real team): the battle is fought by the real team",
                () -> Assertions.assertEquals(4, team.size(), "the battle fields four characters"),
                () -> Assertions.assertEquals(4, team.stream().map(Character::getCid).distinct().count(),
                        "the battle's characters should be four distinct real characters"),
                () -> {
                    for (Character character : team) {
                        Assertions.assertNotNull(character.getElement(),
                                character.getName() + " kept its real element in battle");
                    }
                });

        // A full round without exceptions.
        for (int i = 0; i < 8 && !battle.isOver(); i++) {
            battle.stepForward();
            if (battle.currentMove() == null) {
                break;
            }
            battle.beforeMove();
            battle.afterMove();
        }

        Battle defaultBattle = StageFactory.load(STAGE);

        Assertions.assertAll("a round runs, and the no-argument load uses the real team",
                () -> Assertions.assertNotEquals(Battle.Status.NOT_STARTED, battle.getStatus(),
                        "the battle has started and keeps a running status"),
                () -> Assertions.assertTrue(battle.queue().getElapsed() > 0,
                        "one round moved the action bar's clock forward"),
                () -> Assertions.assertEquals(4, defaultBattle.characters().size(),
                        "the no-argument load fields four characters"),
                () -> {
                    for (Character character : defaultBattle.characters()) {
                        Assertions.assertAll(character.getName() + " from the no-argument load",
                                () -> Assertions.assertNotNull(character.getElement(),
                                        character.getName()
                                                + " came from the placeholder path (no element)"),
                                () -> Assertions.assertNotEquals(0, character.getCid(),
                                        character.getName() + " must be a real cid"));
                    }
                });
    }

    /**
     * The old placeholder entry point is gone, so nothing can silently go back to it.
     */
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

    /**
     * Stage data is generator output and is not in the repository.
     */
    private static void assumeStageData() {
        Assumptions.assumeFalse(Constant.stages().isEmpty(),
                "missing stage.json (generator output), skipping the stage-level assertions");
    }
}
