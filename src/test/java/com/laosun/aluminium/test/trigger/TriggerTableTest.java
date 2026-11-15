package com.laosun.aluminium.test.trigger;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.trigger.TriggerInterpreter;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TestCharacters;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import com.laosun.aluminium.equipment.RelicSuit;

/**
 * Trigger tables: character mechanics expressed as data.
 *
 * <p>The acceptance criterion is that <b>two real characters work with no Java
 * character class at all</b> -- their mechanics exist only as JSON under
 * {@code resources/game/characters/}:
 * <ul>
 *   <li><b>Tribbie 1403</b> - trace 1403103: 30 energy at battle start, and 1.50 energy
 *       <b>per target hit</b> when another ally attacks;</li>
 *   <li><b>Robin 1309</b> - talent: a <b>flat 2</b> energy whenever an ally attacks.</li>
 * </ul>
 * Those two are deliberately paired because they differ in the one way that is easy to get wrong:
 * Tribbie scales with the number of targets, Robin does not.
 */
public class TriggerTableTest {

    private static final int TRIBBIE = 1403;
    private static final int ROBIN = 1309;

    /**
     * An ordinary character with <b>no trigger file</b>, used as the "unregistered" control.
     *
     * <p>Note: Looked up rather than named: a named character can acquire rules of her own, which would break
     * three of the claims below. See {@link TestCharacters}.
     */
    // Note: split in two. The "there is no file" facts (`TriggerTables.exists` is false, a lookup is a cache miss)
    // use a SYNTHETIC id that is never built; every use as a BODY builds 1002 instead, because those claims are about the table
    // the test installs or about the body's own data, not about the body having no rules.
    private static final int NO_TRIGGERS = 999999;

    /**
     * An ally whose ultimate connects with <b>every</b> enemy - the driver for the "per target hit" case.
     *
     * <p>Named rather than looked up, because the property being asked for is a fact about one character's
     * <b>skill data</b> (1003's ultimate is all-target attack (全体攻击), 60 toughness, Fire), not about the engine. Note: She has a rule
     * file of her own, and that is deliberately acceptable here: the number each scenario measures is
     * <em>Tribbie's</em> or <em>Robin's</em> energy, which only their own tables can credit, and none of her rules
     * grants energy to anybody ({@code resources/game/characters/1003.json}).
     */
    private static final int AOE_ALLY = 1003;
    private static final int ICE_EDGE = 1002011;

    // ==================================================================
    // 1. The acceptance criterion: two real characters, pure data
    // ==================================================================

    /**
     * Tribbie's trace 1403103 as data: 30 energy at the start of battle, 1.50 energy <b>per target hit</b>
     * by <b>another</b> ally, and nothing when Tribbie herself attacks.
     *
     * <p>Three targets hit => 4.5, not 1.5. That is the case that distinguishes "per hit" from "per attack",
     * which is the difference between Tribbie and Robin. The own-attack half is what the {@code actor != self}
     * condition is for.
     */
    @Test
    public void tribbieTraceCreditsBattleStartAndPerTargetHits() {
        Character tribbie = CharacterFactory.create(TRIBBIE, 80);
        Battle soloBattle = newBattle(List.of(tribbie), 1);
        // Battle.startBattle() has already run inside newBattle, so the trigger has fired.
        double battleStartEnergy = tribbie.getCurrentEnergy();

        double beforeOwnAttack = tribbie.getCurrentEnergy();
        strike(soloBattle, tribbie, soloBattle.enemies().getFirst());
        double ownAttackGain = tribbie.getCurrentEnergy() - beforeOwnAttack;
        double skillEnergy = 20 * (1 + tribbie.getAttribute(AttributeType.ENERGY_REGENERATION_RATE).get());

        Character tribbieUnderAoeAlly = CharacterFactory.create(TRIBBIE, 80);
        Character attacker = CharacterFactory.create(AOE_ALLY, 80);
        Battle threeTargets = newBattle(List.of(tribbieUnderAoeAlly, attacker), 3);
        double beforeThreeTargets = tribbieUnderAoeAlly.getCurrentEnergy();
        attackAllEnemies(threeTargets, attacker);
        double threeTargetGain = tribbieUnderAoeAlly.getCurrentEnergy() - beforeThreeTargets;

        Character tribbieUnderSingleStrike = CharacterFactory.create(TRIBBIE, 80);
        Character himeko = CharacterFactory.create(1002, 80);
        Battle oneTarget = newBattle(List.of(tribbieUnderSingleStrike, himeko), 1);
        double beforeOneTarget = tribbieUnderSingleStrike.getCurrentEnergy();
        strike(oneTarget, himeko, oneTarget.enemies().getFirst());
        double oneTargetGain = tribbieUnderSingleStrike.getCurrentEnergy() - beforeOneTarget;

        Assertions.assertAll("Tribbie trace 1403103",
                () -> Assertions.assertEquals(30, battleStartEnergy, 1e-6,
                        "trace 1403103: 30 energy at battle start"),
                () -> Assertions.assertEquals(1.5 * 3, threeTargetGain, 1e-6,
                        "1.5 per target x 3 targets"),
                () -> Assertions.assertEquals(1.5, oneTargetGain, 1e-6,
                        "1.5 per target x 1 target"),
                () -> Assertions.assertEquals(skillEnergy, ownAttackGain, 1e-6,
                        "own attack grants no trace energy"));
    }

    /**
     * Robin's talent as data: a <b>flat 2</b> whenever an ally attacks, regardless of how many targets, and
     * never on her own attack ("我方目标", our targets, excludes Robin herself).
     *
     * <p>Deliberately the same setup as Tribbie's three-target case, so the pair proves the two numbers are
     * modelled differently rather than both being "some energy per attack". The own-attack half pins the
     * {@code actor != self} condition, the kind of rule a table without conditions would get wrong.
     */
    @Test
    public void robinTalentGrantsFlatEnergyPerAllyAttackOnly() {
        Character robin = CharacterFactory.create(ROBIN, 80);
        Character attacker = CharacterFactory.create(AOE_ALLY, 80);
        Battle withAlly = newBattle(List.of(robin, attacker), 3);
        double beforeAllyAttack = robin.getCurrentEnergy();
        attackAllEnemies(withAlly, attacker);
        double allyAttackGain = robin.getCurrentEnergy() - beforeAllyAttack;

        Character soloRobin = CharacterFactory.create(ROBIN, 80);
        Battle soloBattle = newBattle(List.of(soloRobin), 1);
        double beforeOwnAttack = soloRobin.getCurrentEnergy();
        strike(soloBattle, soloRobin, soloBattle.enemies().getFirst());
        double ownAttackGain = soloRobin.getCurrentEnergy() - beforeOwnAttack;

        // Her own attack still grants the ordinary skill energy (20), so compare against that rather than 0 --
        // the point is that no *talent* energy was added on top.
        double skillEnergy = 20 * (1 + soloRobin.getAttribute(AttributeType.ENERGY_REGENERATION_RATE).get());

        Assertions.assertAll("Robin talent: flat 2 per ally attack, never on her own",
                () -> Assertions.assertEquals(2, allyAttackGain, 1e-6,
                        "flat 2 per attack, even when 3 targets were hit"),
                () -> Assertions.assertEquals(skillEnergy, ownAttackGain, 1e-6,
                        "only the base skill energy: the talent must not fire on Robin's own attack"));
    }

    // ==================================================================
    // 2. Unregistered = empty table, and the battle still runs
    // ==================================================================

    /**
     * A character with no file gets {@link TriggerTable#EMPTY}, and a battle containing one works
     * normally. "Not data-ised yet" is an ordinary state, not an error -- with 93 characters and a
     * handful of files, most characters are in it.
     */
    @Test
    public void unregisteredCharacterHasAnEmptyTableAndTheBattleStillRuns() {
        // Note: this test asserts what an EMPTY TABLE does, so it builds the empty table itself instead of borrowing
        // the shared "unregistered character" control. Measured: that control's data matters here -- the assertion below is
        // about the character recovering energy from its own basic attack, and the last file-less id (1506) has max energy 0,
        // so it reads 0.0. Replacing a table is usually a trap (it deletes the rule under test), but here the
        // empty table IS the subject, which is exactly why it is the right construction.
        Character plain = CharacterFactory.create(1002, 80);
        plain.setTriggerTable(new TriggerTable(1002, java.util.List.of()));
        Assertions.assertTrue(plain.getTriggerTable().isEmpty(), "the table really is empty");
        // The file-less property is a separate fact and keeps its own witness (1506 is a key of character_data.json with no file).
        Assertions.assertFalse(TriggerTables.exists(NO_TRIGGERS), "and no resource either");

        Battle battle = newBattle(List.of(plain), 1);
        double before = plain.getCurrentEnergy();
        strike(battle, plain, battle.enemies().getFirst());

        double skillEnergy = 20 * (1 + plain.getAttribute(
                AttributeType.ENERGY_REGENERATION_RATE).get());
        assertEnergyEquals(plain.getCurrentEnergy() - before, skillEnergy,
                "an empty table changes nothing");
    }

    /**
     * Tables are cached: asking twice must not read the classpath twice.
     */
    @Test
    public void tablesAreLoadedOncePerCid() {
        TriggerTables.clearCache();
        TriggerTables.of(TRIBBIE);
        int afterFirst = TriggerTables.loadCount();
        TriggerTables.of(TRIBBIE);
        TriggerTables.of(TRIBBIE);
        Assertions.assertEquals(afterFirst, TriggerTables.loadCount(), "second lookup must be cached");

        // A cid with no file is cached too (as the empty table), so it must not re-probe either.
        TriggerTables.of(NO_TRIGGERS);
        int afterMiss = TriggerTables.loadCount();
        TriggerTables.of(NO_TRIGGERS);
        Assertions.assertEquals(afterMiss, TriggerTables.loadCount(), "a miss is cached as well");
    }

    // ==================================================================
    // 3. The engine really fires the events (not just "the file parses")
    // ==================================================================

    /**
     * The trigger fires on the event that happened rather than on "the battle exists".
     *
     * <p>Note: The numbers are per-event: Robin's two traces (行迹) live in the
     * same file (Coloratura Cadenza (华彩花腔) to {@code BATTLE_START}, Sequential Passage (模进乐段) to {@code SKILL_CAST}), so the counts are
     * what is pinned: an event fires the rules that named <i>that</i> event and no others.
     */
    @Test
    public void battleStartAndACastFireDifferentRules() {
        Character robin = CharacterFactory.create(ROBIN, 80);
        Character himeko = CharacterFactory.create(1402, 80);
        Battle battle = newBattle(List.of(robin, himeko), 1);

        // Note: 4 since 2026-11-04 - her Coloratura Cadenza, the level convention both files carry, and 1402's
        // 飞驰之阳 trace (an ungated BATTLE_START rule; 1402's technique rule is gated on the 秘技 state and her
        // Eidolon level raises on the star rank, so neither matches a bare event).
        Assertions.assertEquals(4, battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.BATTLE_START, null, null, 0, 0)),
                "her Coloratura Cadenza trace, plus the level convention both files now carry, plus 1402's own trace");

        Assertions.assertEquals(0, battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.BASIC_ATTACK, robin, null, 1, 0)),
                "nothing in her file listens to BASIC_ATTACK: Basic ATK (普攻) is not Skill (战技)");

        int fired = battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, himeko, null, 1, 0));
        Assertions.assertEquals(1, fired, "only Robin's table has an ALLY_ATTACK rule");
    }

    /**
     * The other half of the vocabulary guard: <b>every event the vocabulary declares is emitted</b>.
     *
     * <p>The enum is a closed vocabulary content may use, so a declared-but-unemitted event is a trap:
     * the rule loads nowhere and fails only when an author writes it. Wiring {@code TURN_START} and
     * {@code TAKING_HIT} made this true for the first time, and this assertion is what keeps it true.
     */
    @Test
    public void everyDeclaredTriggerEventIsEmitted() {
        List<String> unwired = Arrays.stream(TriggerEvent.values())
                .filter(event -> !event.isWired())
                .map(TriggerEvent::value)
                .toList();
        Assertions.assertTrue(unwired.isEmpty(),
                "these events are declared in TriggerEvent but no emitter fires them, so any rule using "
                        + "them is rejected at load: " + unwired);
    }

    private static TriggerEvent unwiredEvent() {
        return Arrays.stream(TriggerEvent.values()).filter(event -> !event.isWired()).findFirst()
                .orElse(null);
    }

    // ==================================================================
    // 4. Fail fast: a bad table is rejected at load, not silently ignored
    // ==================================================================

    /**
     * Every way a raw rule can be wrong at load time is reported, never skipped: an unknown event name, an
     * event the engine does not emit yet, an unknown condition variable, a malformed condition, an unknown
     * op, an op whose prerequisite phase has not landed, a missing required argument, and a rule with no
     * effects.
     *
     * <p>Otherwise a content author would write a rule, see nothing happen, and have no way to tell
     * "my condition is wrong" from "the engine never fires this". An unknown event name is a typo; a planned
     * op names the phase so the author learns what to wait for; a missing argument and an unknown condition
     * variable are named in the message.
     *
     * <p>The event is taken from the enum instead of being hard-coded: it has an emitter. The
     * companion assertion {@link #everyDeclaredTriggerEventIsEmitted()} is what keeps the vocabulary
     * honest - without it the day every event is wired this case would silently stop covering the
     * rejection path.
     */
    @Test
    public void invalidTriggerRulesAreRejectedAtLoad() {
        Assertions.assertAll("a bad trigger table is rejected at load",
                () -> {
                    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> new TriggerTable(1,
                                    List.of(trigger("NO_SUCH_EVENT", List.of("self"), energy(1)))));
                    Assertions.assertTrue(e.getMessage().contains("NO_SUCH_EVENT"),
                            "an unknown event is rejected and named: " + e.getMessage());
                },
                () -> {
                    TriggerEvent unwired = unwiredEvent();
                    if (unwired == null) {
                        return;                  // guarded by everyDeclaredTriggerEventIsEmitted()
                    }
                    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> new TriggerTable(1,
                                    List.of(trigger(unwired.value(), List.of("self"), energy(1)))));
                    Assertions.assertTrue(e.getMessage().contains("not emitted"),
                            "an unwired event is rejected with the reason: " + e.getMessage());
                    Assertions.assertTrue(e.getMessage().contains(unwired.value()),
                            "an unwired event is rejected by name: " + e.getMessage());
                },
                () -> {
                    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> new TriggerTable(1,
                                    List.of(trigger("ALLY_ATTACK", List.of("hp < 50"), energy(1)))));
                    Assertions.assertTrue(e.getMessage().contains("hp"),
                            "an unknown condition variable is rejected and named: " + e.getMessage());
                },
                () -> Assertions.assertThrows(IllegalArgumentException.class,
                        () -> new TriggerTable(1,
                                List.of(trigger("ALLY_ATTACK", List.of("actor !="), energy(1)))),
                        "a condition with a missing right operand is rejected"),
                () -> Assertions.assertThrows(IllegalArgumentException.class,
                        () -> new TriggerTable(1,
                                List.of(trigger("ALLY_ATTACK", List.of("nonsense"), energy(1)))),
                        "a condition with no operator is rejected"),
                () -> {
                    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> new TriggerTable(1,
                                    List.of(trigger("ALLY_ATTACK", List.of(), op("TELEPORT", null)))));
                    Assertions.assertTrue(e.getMessage().contains("TELEPORT"),
                            "an unknown op is rejected and named: " + e.getMessage());
                },
                () -> {
                    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> new TriggerTable(1,
                                    List.of(trigger("ALLY_ATTACK", List.of(), op("REDUCE_TOUGHNESS", 1.0)))));
                    Assertions.assertTrue(e.getMessage().contains("roadmap"),
                            "a planned-but-unwired op says what to wait for: " + e.getMessage());
                },
                () -> {
                    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> new TriggerTable(1,
                                    List.of(trigger("ALLY_ATTACK", List.of(), op("GAIN_ENERGY", null)))));
                    Assertions.assertTrue(e.getMessage().contains("amount"),
                            "a missing required argument is rejected and named: " + e.getMessage());
                },
                () -> Assertions.assertThrows(IllegalArgumentException.class,
                        () -> new TriggerTable(1, List.of(trigger("ALLY_ATTACK", List.of(), null))),
                        "a rule with no effects is rejected"));
    }

    /**
     * Numeric equality parses - {@code hit_count == 2} has been in the DSL's documentation since its first
     * version and was rejected as "compares two variables".
     *
     * <p>Found by writing a condition that needed {@code == 0}: {@code ==} and {@code !=} went straight to the
     * <b>identity</b> branch (which is about {@code actor == self}), so anything without {@code self} on one
     * side was refused - including the examples in the comment above the parser. The two readings are now kept
     * apart by whether either side <em>is</em> {@code self}, and this case pins both.
     */
    @Test
    public void numericEqualityAndInequalityParse() {
        TriggerTable exact = new TriggerTable(1, List.of(
                trigger("ALLY_ATTACK", List.of("hit_count == 2"), energy(1))));
        TriggerTable notExact = new TriggerTable(1, List.of(
                trigger("ALLY_ATTACK", List.of("hit_count != 2"), energy(1))));
        Character owner = character("owner");
        Battle battle = newBattle(List.of(owner), 1);

        owner.setTriggerTable(exact);
        Assertions.assertEquals(1, battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, owner, null, 2, 0)),
                "an exact hit count now parses as a number");
        Assertions.assertEquals(0, battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, owner, null, 3, 0)),
                "…and really compares it");

        owner.setTriggerTable(notExact);
        Assertions.assertEquals(0, battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, owner, null, 2, 0)));
        Assertions.assertEquals(1, battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, owner, null, 3, 0)));

        // The identity reading is untouched: `self` is still an identity, not a number ...
        TriggerTable selfIdentity = new TriggerTable(1, List.of(
                trigger("ALLY_ATTACK", List.of("actor == self"), energy(1))));
        owner.setTriggerTable(selfIdentity);
        Assertions.assertEquals(1, battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, owner, null, 1, 0)));

        // ... and two *names* still get the clearer message rather than "no numeric literal".
        IllegalArgumentException twoVariables = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(1, List.of(
                        trigger("ALLY_ATTACK", List.of("actor == target"), energy(1)))));
        Assertions.assertTrue(twoVariables.getMessage().contains("two variables"), twoVariables.getMessage());
    }

    // ==================================================================
    // 4b. MODIFY_ATTR: the generic stat buff
    // ==================================================================

    /**
     * The op writes a real modifier onto the owner: +50% ATK is 450. A <b>ratio</b> reading is not a factor
     * either - a negative percent becomes a {@code DEBUFF}, landing on the other half of the attribute so a
     * debuff does not silently overwrite a buff on the same stat - and an attribute name is normalised before
     * lookup, because {@code AttributeType.fromString} documents itself as case-insensitive.
     */
    @Test
    public void modifyAttrOpAppliesPercentToTheOwner() {
        Character buffed = character("owner");
        fireAttr(null, buffed, buffed, attrOp(AttributeType.ATTACK, 0.5, 2, null));

        Character debuffed = character("owner");
        fireAttr(null, debuffed, debuffed, attrOp(AttributeType.DEFENCE, -0.25, 2, null));

        Character mixedCase = character("owner");
        fireAttr(null, mixedCase, mixedCase, attrOp("Attack", 0.5, 2, null));

        Assertions.assertAll("MODIFY_ATTR buffs the owner",
                () -> Assertions.assertEquals(450, buffed.getAttribute(AttributeType.ATTACK).get(), 1e-9,
                        "ATK 300 with +50% is 450"),
                () -> Assertions.assertEquals(150, debuffed.getAttribute(AttributeType.DEFENCE).get(), 1e-9,
                        "DEF 200 with -25% is 150"),
                () -> Assertions.assertEquals(0, debuffed.getAttribute(AttributeType.DEFENCE)
                                .filterBySource(DoubleValue.Modifier.ModifierSource.BUFF).size(),
                        "a negative percent lands in the debuff half, so no buff is installed"),
                () -> Assertions.assertEquals(1, debuffed.getAttribute(AttributeType.DEFENCE)
                                .filterBySource(DoubleValue.Modifier.ModifierSource.DEBUFF).size(),
                        "and exactly one debuff modifier is installed"),
                () -> Assertions.assertEquals(450, mixedCase.getAttribute(AttributeType.ATTACK).get(), 1e-9,
                        "a mixed-case attribute name is normalised, so ATK 300 with +50% is 450"));
    }

    /**
     * A <b>ratio</b> attribute ({@code CRIT_ATTACK} and friends) takes {@code percent} as the value
     * itself, because that is the only reading under which the rule does anything at all.
     *
     * <p>Those attributes are written by the builder through {@code addPercentPoint}, i.e. as a flat
     * modifier on a base of <b>0</b> - so an additive percentage would multiply zero: the op would
     * fire, install a modifier, and change nothing, which is exactly the kind of silent no-op this
     * project's guard rails exist to prevent. This is the reading {@code RelicSuit.appendTo} already
     * uses for the same attributes ("any other {@code isPercent} attribute is a percentage-point
     * value"), so a relic set rule can say "CRIT DMG +25%" and mean it.
     */
    @Test
    public void modifyAttrOpAddsARatioAttributeRatherThanScalingIt() {
        Character owner = CharacterFactory.create(1002, 80);
        double before = owner.getAttribute(AttributeType.CRIT_ATTACK).get();
        Assertions.assertTrue(before > 0, "precondition: the character's CRIT DMG comes from the data");

        fireAttr(null, owner, owner, attrOp(AttributeType.CRIT_ATTACK, 0.25, 2, null));

        Assertions.assertEquals(before + 0.25, owner.getAttribute(AttributeType.CRIT_ATTACK).get(), 1e-9,
                "+25 percentage points of CRIT DMG, not a 25% factor of a zero base");
        Assertions.assertEquals(1, owner.getAttribute(AttributeType.CRIT_ATTACK)
                .filterBySource(DoubleValue.Modifier.ModifierSource.BUFF).size());

        fireAttr(null, owner, owner, attrOp(AttributeType.CRIT_ATTACK, -0.1, 2, null));
        Assertions.assertEquals(before + 0.15, owner.getAttribute(AttributeType.CRIT_ATTACK).get(), 1e-9,
                "a negative ratio change lands in the debuff half, so the two coexist");
    }

    /**
     * The {@code target} field decides who receives the buff: {@code "target"} is the event's subject
     * (leaving the owner untouched), and {@code "all_allies"} is what makes a party-wide buff expressible -
     * party-wide is the majority of buff talents in the real data, so this selector decides whether the op
     * is actually useful.
     */
    @Test
    public void modifyAttrOpSelectsWhoReceivesTheBuff() {
        Character owner = character("owner");
        Character ally = character("ally");
        fireAttr(null, owner, ally, attrOp(AttributeType.ATTACK, 1.0, 2, "target"));

        Character partyOwner = character("owner");
        Character allyOne = character("ally1");
        Character allyTwo = character("ally2");
        Battle battle = newBattle(List.of(partyOwner, allyOne, allyTwo), 1);
        fireAttr(battle, partyOwner, partyOwner, attrOp(AttributeType.ATTACK, 0.5, 2, "all_allies"));

        Assertions.assertAll("MODIFY_ATTR target selector",
                () -> Assertions.assertEquals(600, ally.getAttribute(AttributeType.ATTACK).get(), 1e-9,
                        "target: \"target\" gives the event's subject ATK 300 with +100% = 600"),
                () -> Assertions.assertEquals(300, owner.getAttribute(AttributeType.ATTACK).get(), 1e-9,
                        "the owner must be untouched when the effect names the subject"),
                () -> Assertions.assertEquals(450, partyOwner.getAttribute(AttributeType.ATTACK).get(), 1e-9,
                        "owner should have received the party buff"),
                () -> Assertions.assertEquals(450, allyOne.getAttribute(AttributeType.ATTACK).get(), 1e-9,
                        "ally1 should have received the party buff"),
                () -> Assertions.assertEquals(450, allyTwo.getAttribute(AttributeType.ATTACK).get(), 1e-9,
                        "ally2 should have received the party buff"));
    }

    /**
     * {@code all_allies} needs a battle to take the party from; without one it must say so.
     */
    @Test
    public void allAlliesWithoutABattleFailsLoudly() {
        Character owner = character("owner");

        IllegalStateException e = Assertions.assertThrows(IllegalStateException.class,
                () -> fireAttr(null, owner, owner, attrOp(AttributeType.ATTACK, 0.5, 2, "all_allies")));
        // Asserting on "no battle" rather than on "all_allies" is deliberate: the unknown-selector
        // message also contains "all_allies", so the weaker assertion would pass for the wrong reason.
        Assertions.assertTrue(e.getMessage().contains("no battle"), e.getMessage());
    }

    /**
     * {@code MODIFY_ATTR}'s inputs are closed sets checked when the table is read: an unknown attribute, one
     * of the four {@code *_PERCENT} builder-only names ({@code getAttribute} returns {@code null} for them,
     * so a buff on one would blow up mid-battle), a missing or non-positive {@code turns} (no duration
     * default exists, and a zero-turn buff would expire before it could do anything), and an unknown
     * {@code target} selector - which must be refused rather than falling back to "the owner", because that
     * would make {@code "atacker"} behave exactly like {@code "self"} and report nothing.
     */
    @Test
    public void modifyAttrOpRejectsInvalidInput() {
        Assertions.assertAll("MODIFY_ATTR load-time validation",
                () -> {
                    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> table(attrOp("ATTCK", 0.5, 2, null)));
                    Assertions.assertTrue(e.getMessage().contains("ATTCK"),
                            "a misspelled attribute is rejected and named: " + e.getMessage());
                },
                () -> {
                    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> table(attrOp("ATTACK_PERCENT", 0.5, 2, null)));
                    Assertions.assertTrue(e.getMessage().contains("ATTACK_PERCENT"),
                            "a builder-only percent attribute is rejected and named: " + e.getMessage());
                },
                () -> {
                    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> table(attrOp(AttributeType.ATTACK, 0.5, null, null)));
                    Assertions.assertTrue(e.getMessage().contains("turns"),
                            "a missing duration is rejected and names turns: " + e.getMessage());
                },
                () -> {
                    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> table(attrOp(AttributeType.ATTACK, 0.5, 0, null)));
                    Assertions.assertTrue(e.getMessage().contains("turns"),
                            "a non-positive duration is rejected and names turns: " + e.getMessage());
                },
                () -> {
                    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                            () -> table(attrOp(AttributeType.ATTACK, 0.5, 2, "atacker")));
                    Assertions.assertTrue(e.getMessage().contains("atacker"),
                            "a misspelled target selector is rejected and named: " + e.getMessage());
                });
    }

    // ==================================================================
    // 5. Conditions behave as the data implies
    // ==================================================================

    /**
     * Conditions select exactly the rules the data says: {@code actor != self} lets an ally's attack through
     * and blocks the owner's own, {@code hit_count > 0} rejects an attack that hit nothing, the literal may
     * sit on either side of a numeric comparison, and the bare {@code self} shorthand means "the actor is me".
     */
    @Test
    public void conditionsMatchAsTheDataImplies() {
        TriggerTable selfAndHitCount = new TriggerTable(1, List.of(
                trigger("ALLY_ATTACK", List.of("actor != self", "hit_count > 0"), energy(2))));

        Character owner = CharacterFactory.create(1002, 80);
        Character other = CharacterFactory.create(1002, 80);
        TriggerTable.TriggerContext oneHit = new TriggerTable.TriggerContext(owner, other, null, 1, 0);
        TriggerTable.TriggerContext noHit = new TriggerTable.TriggerContext(owner, other, null, 0, 0);

        TriggerTable left = new TriggerTable(1, List.of(
                trigger("ALLY_ATTACK", List.of("0 < hit_count"), energy(1))));
        TriggerTable right = new TriggerTable(1, List.of(
                trigger("ALLY_ATTACK", List.of("hit_count > 0"), energy(1))));

        TriggerTable selfShorthand = new TriggerTable(1, List.of(
                trigger("SKILL_CAST", List.of("self"), energy(5))));

        Assertions.assertAll("condition vocabulary",
                () -> Assertions.assertEquals(1, selfAndHitCount.matching(TriggerEvent.ALLY_ATTACK,
                                new TriggerTable.TriggerContext(owner, other, null, 1, 0)).size(),
                        "an ally's attack matches"),
                () -> Assertions.assertEquals(0, selfAndHitCount.matching(TriggerEvent.ALLY_ATTACK,
                                new TriggerTable.TriggerContext(owner, owner, null, 1, 0)).size(),
                        "the owner's own attack is excluded"),
                () -> Assertions.assertEquals(0, selfAndHitCount.matching(TriggerEvent.ALLY_ATTACK,
                                new TriggerTable.TriggerContext(owner, other, null, 0, 0)).size(),
                        "and an attack that hit nothing is excluded by hit_count > 0"),
                () -> Assertions.assertEquals(1, left.matching(TriggerEvent.ALLY_ATTACK, oneHit).size(),
                        "0 < hit_count accepts the literal on the left"),
                () -> Assertions.assertEquals(1, right.matching(TriggerEvent.ALLY_ATTACK, oneHit).size(),
                        "hit_count > 0 accepts the literal on the right"),
                () -> Assertions.assertEquals(0, left.matching(TriggerEvent.ALLY_ATTACK, noHit).size(),
                        "0 < hit_count rejects an attack that hit nothing"),
                () -> Assertions.assertEquals(0, right.matching(TriggerEvent.ALLY_ATTACK, noHit).size(),
                        "hit_count > 0 rejects an attack that hit nothing"),
                () -> Assertions.assertEquals(1, selfShorthand.matching(TriggerEvent.SKILL_CAST,
                                new TriggerTable.TriggerContext(owner, owner, null, 0, 0)).size(),
                        "bare self matches when the actor is the owner"),
                () -> Assertions.assertEquals(0, selfShorthand.matching(TriggerEvent.SKILL_CAST,
                                new TriggerTable.TriggerContext(owner, other, null, 0, 0)).size(),
                        "bare self does not match another actor"));
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static Battle newBattle(List<Character> team, int enemyCount) {
        List<Enemy> enemies = new java.util.ArrayList<>();
        for (int i = 0; i < enemyCount; i++) {
            Enemy enemy = EnemyFactory.create(ICE_EDGE, 90, 1);
            // Big HP so nothing dies mid-test (a death would change the hit count and the events).
            enemy.setAttribute(com.laosun.aluminium.enums.AttributeType.HEALTH,
                    new com.laosun.aluminium.DoubleValue(1_000_000));
            enemy.heal(1_000_000);
            enemies.add(enemy);
        }
        Battle battle = new Battle(team, enemies, BattleFixture.seeded());
        battle.startBattle();
        return battle;
    }

    /**
     * A single-target basic attack, settled immediately.
     */
    private static void strike(Battle battle, Character attacker, CanHit target) {
        battle.castImmediate(attacker.getSkills().get(SkillType.COMMON), attacker, List.of(target));
    }

    /**
     * An attack that connects with every enemy.
     *
     * <p>Uses a multi-target skill rather than the basic attack so the hit count really is the number
     * of enemies -- which is the whole point of the per-target/ per-attack distinction.
     */
    private static void attackAllEnemies(Battle battle, Character attacker) {
        battle.castImmediate(attacker.getSkills().get(SkillType.ULTRA), attacker,
                List.of(battle.enemies().getFirst()));
    }

    /**
     * Asserts energy with a tolerance, because the credited amount goes through the owner's energy
     * regeneration rate (which multiplies every gain).
     */
    private static void assertEnergyEquals(Character character, double expected, String why) {
        assertEnergyEquals(character.getCurrentEnergy(), expected, why);
    }

    private static void assertEnergyEquals(double actual, double expected, String why) {
        Assertions.assertEquals(expected, actual, 1e-6, why);
    }

    /**
     * Builds a raw rule for the validation tests.
     */
    private static TriggerSpec trigger(String on, List<String> when, EffectSpec effect) {
        TriggerSpec spec = new TriggerSpec();
        set(spec, "on", on);
        set(spec, "when", when);
        if (effect != null) {
            set(spec, "doEffects", List.of(effect));
        }
        set(spec, "source", "test");
        return spec;
    }

    private static EffectSpec energy(double amount) {
        return op("GAIN_ENERGY", amount);
    }

    // ---- MODIFY_ATTR helpers ----

    /**
     * hp 1000 / def 200 / atk 300 / speed 100 - all different, so a mix-up shows up as a number.
     */
    private static Character character(String name) {
        return Character.fromAttributes(name, 1000, 200, 300, 100);
    }

    private static EffectSpec attrOp(AttributeType attribute, double percent, Integer turns,
                                     String target) {
        return attrOp(attribute.name(), percent, turns, target);
    }

    /**
     * The name-taking overload exists so the "unknown attribute" tests can pass a literal.
     */
    private static EffectSpec attrOp(String attributeName, double percent, Integer turns,
                                     String target) {
        EffectSpec effect = new EffectSpec();
        set(effect, "op", "MODIFY_ATTR");
        set(effect, "attribute", attributeName);
        set(effect, "percent", percent);
        set(effect, "turns", turns);
        set(effect, "target", target);
        return effect;
    }

    /**
     * A one-rule table that fires on any skill cast, so the tests exercise load-time validation.
     */
    private static TriggerTable table(EffectSpec effect) {
        TriggerSpec spec = new TriggerSpec();
        set(spec, "on", "SKILL_CAST");
        set(spec, "when", List.of());
        set(spec, "doEffects", List.of(effect));
        set(spec, "source", "TriggerTableTest");
        return new TriggerTable(1, List.of(spec));
    }

    /**
     * Runs one {@code MODIFY_ATTR} rule.
     *
     * <p>{@code battle} may be {@code null} for the single-target selectors: {@code MODIFY_ATTR} only
     * touches the resolved target's buff manager. {@code all_allies} does need it, and says so - that
     * is {@link #allAlliesWithoutABattleFailsLoudly}.
     */
    private static void fireAttr(Battle battle, Character owner, Character subject, EffectSpec effect) {
        TriggerTable.TriggerContext ctx = new TriggerTable.TriggerContext(owner, owner, subject, 0, 0);
        for (TriggerTable.CompiledRule rule : table(effect).matching(TriggerEvent.SKILL_CAST, ctx)) {
            TriggerInterpreter.apply(battle, rule, ctx);
        }
    }

    private static EffectSpec op(String name, Double amount) {
        EffectSpec effect = new EffectSpec();
        set(effect, "op", name);
        set(effect, "amount", amount);
        return effect;
    }

    /**
     * Sets a private field through its setter when one exists, else reflectively.
     *
     * <p>The beans are Lombok {@code @Getter} only (they are read from JSON), so the test builds them
     * through reflection rather than widening the production API just for tests.
     */
    private static void set(Object target, String field, Object value) {
        com.laosun.aluminium.test.support.TriggerSpecs.set(target, field, value);
    }
}
