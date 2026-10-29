package com.laosun.aluminium.test.trigger;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.PredicateSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.Path;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.Predicates;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The <b>entity predicate</b> ({@code {"type": "entity", "entity": ..., "json": {...}}}): one unit, and a map of
 * sub-predicate to value.
 *
 * <p><b>What is pinned.</b> Two halves, because the model has two failure modes. The first is <b>reading the wrong
 * thing</b> - an ally question answered about the enemy, a percentage read as an absolute, a range that is exclusive
 * where the document says inclusive. The second is <b>accepting a shape that means nothing</b> - a misspelled
 * sub-predicate, a boolean written as the string {@code "true"}, an empty range, an attribute name that does not exist.
 * Those are refused at load (this class), because a predicate that silently never matches looks exactly like a rule that
 * never fires for a good reason.
 */
public class EntityPredicateTest {

    private static final int OWNER = 1001;              // 三月七: a Character, so path / element / cid exist
    private static final int LEVEL = 80;

    /**
     * {@code is_ally} / {@code is_enemy} read the camp, not the identity: the owner is ours, the monster is not.
     */
    @Test
    public void campFlagsReadTheCamp() {
        Assertions.assertTrue(test(entity("self", "{\"is_ally\": true}"), context(true)),
                "our own character is an ally");
        Assertions.assertFalse(test(entity("self", "{\"is_enemy\": true}"), context(true)),
                "and is not an enemy");
        Assertions.assertTrue(test(entity("target", "{\"is_enemy\": true}"), context(true)),
                "the monster targeted here is an enemy");
    }

    /**
     * {@code hp_pct} is a <b>percentage</b>, and the range is inclusive at both ends.
     */
    @Test
    public void hpPctIsAPercentageWithInclusiveEnds() {
        Context scene = context(true);
        damageToHalf(scene.enemy);

        Assertions.assertTrue(test(entity("target", "{\"hp_pct\": {\"max\": 0.5}}"), scene),
                "half HP is at the top of {\"max\": 0.5}");
        Assertions.assertTrue(test(entity("target", "{\"hp_pct\": {\"min\": 0.5}}"), scene),
                "and at the bottom of {\"min\": 0.5}");
        Assertions.assertFalse(test(entity("target", "{\"hp_pct\": {\"min\": 0.6}}"), scene),
                "but not above 60%");
        Assertions.assertFalse(test(entity("target", "{\"hp\": {\"max\": 100}}"), scene),
                "the absolute reading is a different number: 15000 HP is not at most 100");
    }

    /**
     * A bare number is shorthand for "at least this".
     */
    @Test
    public void aBareNumberMeansAtLeast() {
        Context scene = context(true);
        damageTo(scene.enemy, scene.enemy.getCurrentHp() - 100);

        Assertions.assertTrue(test(entity("target", "{\"hp\": 100}"), scene), "exactly the threshold holds");
        Assertions.assertFalse(test(entity("target", "{\"hp\": 101}"), scene), "one point above does not");
    }

    /**
     * {@code attribute} names an attribute and takes a range; {@code speed} is the same question for SPEED.
     */
    @Test
    public void anAttributeIsAWindow() {
        Context scene = context(true);
        double speed = scene.owner.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get();

        Assertions.assertTrue(test(entity("self", "{\"attribute\": {\"name\": \"SPEED\", \"min\": " + speed + "}}"), scene),
                "the sheet's own Speed is at the bottom of the window");
        Assertions.assertFalse(test(entity("self", "{\"attribute\": {\"name\": \"SPEED\", \"min\": " + (speed + 1) + "}}"), scene),
                "and below a window that starts one point higher");
        Assertions.assertTrue(test(entity("self", "{\"speed\": " + speed + "}"), scene),
                "the named shorthand reads the same number");
    }

    /**
     * {@code stacks} counts a counter on the unit, the {@code self_stacks:} reading.
     */
    @Test
    public void stacksCountsTheCounter() {
        Context scene = context(true);
        for (int i = 0; i < 3; i++) {
            scene.owner.getBuffManager().addBuff(stacks());
        }

        Assertions.assertTrue(test(entity("self", "{\"stacks\": {\"name\": \"充能\", \"min\": 3}}"), scene),
                "three stacks satisfy {\"min\": 3}");
        Assertions.assertFalse(test(entity("self", "{\"stacks\": {\"name\": \"充能\", \"min\": 4}}"), scene),
                "and do not satisfy {\"min\": 4}");
        Assertions.assertFalse(test(entity("self", "{\"stacks\": {\"name\": \"别的\", \"min\": 1}}"), scene),
                "a counter that does not exist reads 0");
    }

    /**
     * The closed-set questions accept one name or a list meaning "any of".
     */
    @Test
    public void pathAndElementTakeANameOrAList() {
        Context scene = context(true);
        String path = scene.owner.getPath().name();
        String otherPath = path.equals(Path.HARMONY.name()) ? Path.DESTRUCTION.name() : Path.HARMONY.name();

        Assertions.assertTrue(test(entity("self", "{\"path\": \"" + path + "\"}"), scene), "its own path");
        Assertions.assertTrue(test(entity("self", "{\"path\": [\"" + otherPath + "\", \"" + path + "\"]}"), scene),
                "a list means any of them");
        Assertions.assertFalse(test(entity("self", "{\"path\": [\"" + otherPath + "\"]}"), scene),
                "and a list that does not hold it fails");
        Assertions.assertTrue(test(entity("self", "{\"element\": \"" + element(scene.owner) + "\"}"), scene),
                "the element is a closed name too");
    }

    /**
     * A missing subject fails: a predicate about the target cannot pass on an event with no target.
     */
    @Test
    public void aMissingSubjectFails() {
        Context scene = context(true);
        TriggerTable.TriggerContext noTarget = new TriggerTable.TriggerContext(
                scene.owner, scene.owner, null, 0, 0, null, scene.battle, SkillCategory.UNSPECIFIED);

        Assertions.assertFalse(test(entity("target", "{\"is_dead\": true}"), noTarget),
                "there is no target to be dead");
    }

    /**
     * {@code camp} and {@code team} split the sides: a camp name is absolute, {@code team} is relative to the rule
     * owner (the thing a sentence like "an ally" actually means).
     */
    @Test
    public void campAndTeamSplitTheSides() {
        Context scene = context(true);

        Assertions.assertTrue(test(entity("self", "{\"camp\": \"PLAYER\"}"), scene), "the owner's camp");
        Assertions.assertTrue(test(entity("target", "{\"camp\": [\"ENEMY\", \"NEUTRAL\"]}"), scene), "a list means any of");
        Assertions.assertTrue(test(entity("target", "{\"team\": \"other\"}"), scene), "the monster is on the other side");
        Assertions.assertTrue(test(entity("self", "{\"team\": \"own\"}"), scene), "the owner is on our side");
    }

    /**
     * {@code eidolon} and {@code skill_level} read the character's own ranks: a slot at level 1 is below level 2.
     */
    @Test
    public void eidolonAndSkillLevelReadTheCharactersRanks() {
        Context scene = context(true);
        int eidolon = scene.owner.getEidolonRank();
        int skill = scene.owner.getSkillLevel().get(com.laosun.aluminium.enums.SkillType.SKILL);

        Assertions.assertTrue(test(entity("self", "{\"eidolon\": " + eidolon + "}"), scene), "the character's rank");
        Assertions.assertFalse(test(entity("self", "{\"eidolon\": " + (eidolon + 1) + "}"), scene), "one above does not");
        Assertions.assertTrue(test(entity("self", "{\"skill_level\": {\"name\": \"SKILL\", \"min\": " + skill + "}}"), scene),
                "the Skill slot's level");
        Assertions.assertFalse(test(entity("self", "{\"skill_level\": {\"name\": \"SKILL\", \"min\": " + (skill + 1) + "}}"), scene),
                "and it is not one level higher");
    }

    /**
     * {@code state_turns} is the remaining duration; a state that is not there reads 0.
     */
    @Test
    public void stateTurnsReadsTheRemainingDuration() {
        Context scene = context(true);
        scene.owner.getBuffManager().addBuff(new com.laosun.aluminium.buff.StackBuff("充能", 2, true, 9));

        Assertions.assertTrue(test(entity("self", "{\"state_turns\": {\"name\": \"充能\", \"min\": 2}}"), scene),
                "two turns are still on it");
        Assertions.assertFalse(test(entity("self", "{\"state_turns\": {\"name\": \"充能\", \"min\": 3}}"), scene),
                "and not three");
        Assertions.assertTrue(test(entity("self", "{\"state_turns\": {\"name\": \"别的\", \"max\": 0}}"), scene),
                "a state that does not exist has 0 turns left");
    }

    /**
     * {@code shield_pct} is a percentage of Max HP, like {@code hp_pct}.
     */
    @Test
    public void shieldPctIsAPercentageOfMaxHp() {
        Context scene = context(true);
        scene.owner.getShieldState().set(scene.owner.getMaxHp() / 4);

        Assertions.assertTrue(test(entity("self", "{\"shield_pct\": {\"max\": 0.25}}"), scene),
                "a quarter of Max HP is at the top of the window");
        Assertions.assertFalse(test(entity("self", "{\"shield_pct\": {\"min\": 0.5}}"), scene), "and not a half");
        Assertions.assertTrue(test(entity("self", "{\"shield_pct\": 0.25}"), scene), "a bare number reads as at least");
    }

    /**
     * {@code same_as} is <b>identity</b>, and it is the condition the content states most: {@code actor == self} has
     * 1,238 uses, and a property cannot express "this unit is that one".
     */
    @Test
    public void sameAsComparesIdentity() {
        Context scene = context(true);

        Assertions.assertTrue(test(entity("self", "{\"same_as\": \"self\"}"), scene), "a unit is itself");
        Assertions.assertTrue(test(entity("actor", "{\"same_as\": \"self\"}"), scene), "the actor here is the owner");
        Assertions.assertFalse(test(entity("target", "{\"same_as\": \"self\"}"), scene), "the monster is not");
    }

    /**
     * The compiled condition still <b>reads as the file spelled it</b>: a log line, an error message and a test that
     * pins "this rule's guard" keep seeing {@code actor == self} rather than a JSON body.
     */
    @Test
    public void theConditionRendersBackToItsSpelling() {
        TriggerSpec rule = TriggerSpecs.rule("TAKING_HIT", null);
        var compiled = com.laosun.aluminium.trigger.Predicates.compile(entity("actor", "{\"same_as\": \"self\"}"), rule, null);
        Assertions.assertEquals("actor == self", compiled.source());

        var ally = com.laosun.aluminium.trigger.Predicates.compile(entity("target", "{\"is_ally\": true}"), rule, null);
        Assertions.assertEquals("target is_ally", ally.source());

        var stacks = com.laosun.aluminium.trigger.Predicates.compile(
                entity("self", "{\"stacks\": {\"name\": \"充能\", \"min\": 3}}"), rule, null);
        Assertions.assertEquals("self_stacks:充能 >= 3", stacks.source());
    }

    /**
     * The four counts the replay party's own rules need: a memosprite on the field, the enemies left, a counter, and the
     * two bars the condition DSL also spells ({@code self_energy_percent}, {@code target_debuff_count}).
     */
    @Test
    public void theSidesAndCountersAreAskable() {
        Context scene = context(true);
        scene.owner.getBuffManager().addBuff(stacks());

        Assertions.assertTrue(test(entity("self", "{\"summon_count\": {\"max\": 0}}"), scene),
                "no memosprite has been summoned here");
        Assertions.assertTrue(test(entity("self", "{\"enemy_count\": {\"min\": 1}}"), scene),
                "the fixture's monster is alive");
        Assertions.assertTrue(test(entity("self", "{\"stacks\": {\"name\": \"充能\", \"min\": 1}}"), scene),
                "the stack counter reads 1 after one mark");
        Assertions.assertTrue(test(entity("self", "{\"debuff_count\": {\"max\": 0}}"), scene),
                "nothing debuffed it");
        Assertions.assertTrue(test(entity("self", "{\"energy_pct\": {\"max\": 1}}"), scene),
                "its energy is inside the bar");
    }

    /**
     * A subject of {@code summon} is the rule owner's memosprite - and it fails, loudly-free, when none is out.
     */
    @Test
    public void theSummonSubjectIsTheOwnersMemosprite() {
        Context scene = context(true);
        Assertions.assertFalse(test(entity("summon", "{\"is_summon\": true}"), scene),
                "with no memosprite on the field every predicate about it is false");
    }

    /**
     * The shapes that mean nothing are refused at load.
     */
    @Test
    public void impossibleShapesAreRefusedAtLoad() {
        assertRefused(entity("self", "{\"is_alive\": true}"), "is_alive");                  // unknown key
        assertRefused(entity("self", "{\"is_ally\": \"true\"}"), "true");                    // boolean as a string
        assertRefused(entity("self", "{\"hp\": {}}"), "hp");                                 // empty range
        assertRefused(entity("self", "{\"attribute\": {\"name\": \"NOPE\"}}"), "NOPE");       // unknown attribute
        assertRefused(entity("nowhere", "{\"is_ally\": true}"), "nowhere");                  // unknown subject
        assertRefused(entity("target", "{\"shield_from\": \"nowhere\"}"), "shield_from");     // unknown provider
        assertRefused(entity("self", "{\"camp\": \"SIDEWAYS\"}"), "SIDEWAYS");              // unknown camp
        assertRefused(entity("self", "{\"team\": \"middle\"}"), "middle");                   // unknown team
        assertRefused(entity("self", "{\"skill_level\": {\"name\": \"BOGUS\"}}"), "BOGUS");     // unknown slot
    }

    // --- fixtures ---------------------------------------------------------------------------------------------------

    private record Context(Character owner, com.laosun.aluminium.entity.CanHit enemy, Battle battle) {
    }

    private static Context context(boolean ignored) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        var enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(owner), List.of(enemy), BattleFixture.seeded());
        return new Context(owner, enemy, battle);
    }

    private static TriggerTable.TriggerContext ctx(Context scene) {
        return new TriggerTable.TriggerContext(scene.owner, scene.owner, scene.enemy, 0, 0, null, scene.battle,
                SkillCategory.UNSPECIFIED);
    }

    private static boolean test(PredicateSpec spec, Context scene) {
        return test(spec, ctx(scene));
    }

    private static boolean test(PredicateSpec spec, TriggerTable.TriggerContext ctx) {
        return Predicates.compile(spec, rule(), null).test(ctx);
    }

    private static void assertRefused(PredicateSpec spec, String mentioned) {
        IllegalArgumentException error = Assertions.assertThrows(IllegalArgumentException.class,
                () -> Predicates.compile(spec, rule(), null));
        Assertions.assertTrue(error.getMessage().contains(mentioned),
                "the message names \"" + mentioned + "\": " + error.getMessage());
    }

    private static TriggerSpec rule() {
        return TriggerSpecs.rule("TAKING_HIT", null);
    }

    private static PredicateSpec entity(String subject, String json) {
        PredicateSpec spec = new PredicateSpec();
        TriggerSpecs.set(spec, "type", "entity");
        TriggerSpecs.set(spec, "entity", subject);
        TriggerSpecs.set(spec, "json", com.google.gson.JsonParser.parseString(json).getAsJsonObject());
        return spec;
    }

    private static String element(Character character) {
        DamageElement element = character.getElement();
        return element == null ? "Fire" : element.name();
    }

    /**
     * One mark of the counter: {@code addBuff} three times is how a rule's {@code ADD_STACK} grants three.
     */
    private static com.laosun.aluminium.buff.AbstractBuff stacks() {
        return new com.laosun.aluminium.buff.StackBuff("充能", 9, true, 9);
    }

    /**
     * Brings a unit to half HP by healing it to full and then taking the damage - the only HP write the engine exposes
     * is healing, and the damage path is what a real fight uses.
     */
    private static void damageToHalf(com.laosun.aluminium.entity.CanHit unit) {
        damageTo(unit, unit.getCurrentHp() - unit.getMaxHp() / 2);
    }

    private static void damageTo(com.laosun.aluminium.entity.CanHit unit, double amount) {
        unit.heal(unit.getMaxHp());
        unit.takeDamage(amount, false);
    }
}
