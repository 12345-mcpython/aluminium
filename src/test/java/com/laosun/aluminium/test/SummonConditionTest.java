package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.MemospriteSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.RelicTriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.TriggerTable;
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
 * {@code actor == summon} / {@code target == summon} and the {@code SUMMON_ATTACK} event - "装备者的忆灵攻击时".
 *
 * <p><b>Why the vocabulary needed both halves.</b> A memosprite's attack became a real attack (it deals its own
 * damage and the engine tells our side about it), but <em>data</em> could not act on it:
 *
 * <ul>
 *   <li>the condition DSL could compare {@code actor}/{@code target} against {@code self} only, so "MY summon"
 *       was unwritable - and without it a rule would also fire for a <b>teammate's</b> memosprite, which is an
 *       over-trigger, not a near miss;</li>
 *   <li>{@code ALLY_ATTACK} is deliberately <b>not</b> widened to cover summons: three shipped rules mean
 *       "我方其他目标攻击后" / "after an ally attacks" by it ({@code characters/1309.json},
 *       {@code characters/1403.json}, relic set 105), and whether a memosprite counts as one of those "目标" is
 *       not something the documents settle. Widening it would silently change what they fire on, so a summon's
 *       attack has an event of its own.</li>
 * </ul>
 *
 * <p><b>What is really being tested.</b> Four things that would each leave a rule which loads fine and quietly
 * does the wrong thing:
 * <ol>
 *   <li><b>whose</b> summon - the rule owner's, not a teammate's, and not an enemy's minion either;</li>
 *   <li><b>which side</b> is read - {@code actor} for "it attacked", {@code target} for "it was hit";</li>
 *   <li>a context with <b>no battlefield</b> cannot answer "is this my summon", so the condition fails for
 *       <em>both</em> polarities ({@code actor != summon} must not silently become true for everyone);</li>
 *   <li>the event fires <b>once per attack</b> and carries the hit count, so an author can tell one target from
 *       three.</li>
 * </ol>
 *
 * <p>The shipped content is relic set 123 (Hero of Triumphant Song (凯歌祝捷的英豪)), whose 4-piece is the first rule in the game's data
 * that says "my memosprite attacked" - checked through the real loader, with the numbers read from the effect's
 * {@code param} rather than from its sentence.
 */
public class SummonConditionTest {
    private static final double EPS = 1e-6;

    /** Cyrene (长夜月) - has a memosprite spec, and a hand-built table replaces her own rules cleanly. */
    private static final int OWNER = 1413;
    /** Aglaea (阿格莱雅) - the second memosprite owner, for "a teammate's summon does not count". */
    private static final int TEAMMATE = 1402;
    /**
     * Himeko (姬子) - a plain character with no memosprite spec at all, i.e. the "owns nothing" side of
     * {@code target_summon_count}.
     */
    private static final int PLAIN = 1003;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int OTHER_MONSTER = 8002040;
    /** Hero of Triumphant Song (凯歌祝捷的英豪): ATK +12%; a memosprite on the field -> SPD +6%, when the memosprite attacks -> both sides' CRIT DMG +30% / 2 turns. */
    private static final int HERO_OF_TRIUMPHANT_SONG = 123;
    /** The magnitude the fixture rules grant - a ratio attribute, so the delta is exact. */
    private static final double GRANT = 0.5;

    // ==================================================================
    // 1. Whose summon, and which side
    // ==================================================================

    /** The rule fires for the owner's own memosprite and <b>not</b> for a teammate's. */
    @Test
    public void onlyTheOwnersSummonCounts() {
        Character owner = characterWith(rule("SUMMON_ATTACK", List.of("actor == summon")));
        Character teammate = CharacterFactory.create(TEAMMATE, LEVEL);
        Battle battle = new Battle(List.of(owner, teammate), List.of(dummy()), new Random(0));

        Summon theirs = battle.summonMemosprite(teammate);
        Summon mine = battle.summonMemosprite(owner);

        Assertions.assertEquals(0, battle.fireTriggers(TriggerEvent.SUMMON_ATTACK, theirs, null, 1, 0),
                "a teammate's memosprite is not mine");
        Assertions.assertEquals(1, battle.fireTriggers(TriggerEvent.SUMMON_ATTACK, mine, null, 1, 0),
                "my own is");
    }

    /** An enemy's minion is a summon too, and it is nobody's summon of ours. */
    @Test
    public void anEnemyMinionIsNotMySummon() {
        Character owner = characterWith(rule("SUMMON_ATTACK", List.of("actor == summon")));
        Enemy boss = dummy();
        Battle battle = new Battle(List.of(owner), List.of(boss), new Random(0));
        Summon minion = battle.summon(boss, OTHER_MONSTER, 1);

        Assertions.assertEquals(Camp.ENEMY, minion.getCamp(), "precondition: it really is a summon");
        Assertions.assertEquals(0, battle.fireTriggers(TriggerEvent.SUMMON_ATTACK, minion, null, 1, 0),
                "…but it belongs to the other camp, so `summon` is not it");
    }

    /** {@code actor == self} on this event is never true: a summon is not its owner. */
    @Test
    public void theSummonIsNotItsOwner() {
        Character owner = characterWith(rule("SUMMON_ATTACK", List.of("actor == self")));
        Battle battle = new Battle(List.of(owner), List.of(dummy()), new Random(0));
        Summon mine = battle.summonMemosprite(owner);

        Assertions.assertEquals(0, battle.fireTriggers(TriggerEvent.SUMMON_ATTACK, mine, null, 1, 0));
        Assertions.assertEquals(1, battle.fireTriggers(TriggerEvent.SUMMON_ATTACK, owner, null, 1, 0),
                "…while the owner acting is exactly what it means");
    }

    /** {@code actor != summon} is the other polarity: the owner acting counts, the summon does not. */
    @Test
    public void theNegatedSpellingExcludesOnlyTheSummon() {
        Character owner = characterWith(rule("SUMMON_ATTACK", List.of("actor != summon")));
        Battle battle = new Battle(List.of(owner), List.of(dummy()), new Random(0));
        Summon mine = battle.summonMemosprite(owner);

        Assertions.assertEquals(1, battle.fireTriggers(TriggerEvent.SUMMON_ATTACK, owner, null, 1, 0));
        Assertions.assertEquals(0, battle.fireTriggers(TriggerEvent.SUMMON_ATTACK, mine, null, 1, 0));
    }

    /** Written the other way round, {@code summon == actor} is the same question. */
    @Test
    public void theMirrorSpellingMeansTheSameThing() {
        Character owner = characterWith(rule("SUMMON_ATTACK", List.of("summon == actor")));
        Battle battle = new Battle(List.of(owner), List.of(dummy()), new Random(0));
        Summon mine = battle.summonMemosprite(owner);

        Assertions.assertEquals(1, battle.fireTriggers(TriggerEvent.SUMMON_ATTACK, mine, null, 1, 0));
    }

    /**
     * "My summon" means <b>any</b> of them, not just the first.
     *
     * <p>Nothing in the shipped data owns more than one yet - Robin - Summer Songbird (知更鸟-晴歌)'s Skyward Musician (晴空乐手) is a trio, and the documents
     * do describe multi-summon characters - so a condition that resolved {@code summon} to the first match would
     * look right today and be wrong the moment one lands. Two fixture memosprites for one owner is how that shows
     * up: the <em>second</em> one attacking is still "my summon".
     */
    @Test
    public void anyOfMySummonsCountsNotJustTheFirst() {
        Character owner = characterWith(rule("SUMMON_ATTACK", List.of("actor == summon")));
        Battle battle = new Battle(List.of(owner), List.of(dummy()), new Random(0));
        Summon first = place(battle, owner, aoeSpec());
        Summon second = place(battle, owner, aoeSpec());

        Assertions.assertEquals(2, battle.summonCountOf(owner), "precondition: the owner has two out");
        Assertions.assertEquals(1, battle.fireTriggers(TriggerEvent.SUMMON_ATTACK, first, null, 1, 0));
        Assertions.assertEquals(1, battle.fireTriggers(TriggerEvent.SUMMON_ATTACK, second, null, 1, 0),
                "the second one is mine too");
    }

    /** {@code target == summon} is the same vocabulary on the receiving side: "it happened to my summon". */
    @Test
    public void theTargetSideAsksTheSameQuestionAboutTheSubject() {
        Character owner = characterWith(rule("TAKING_HIT", List.of("target == summon")));
        Battle battle = new Battle(List.of(owner), List.of(dummy()), new Random(0));
        Summon mine = battle.summonMemosprite(owner);

        Assertions.assertEquals(1, battle.fireTriggers(TriggerEvent.TAKING_HIT, dummy(), mine, 1, 100),
                "my memosprite was hit");
        Assertions.assertEquals(0, battle.fireTriggers(TriggerEvent.TAKING_HIT, dummy(), owner, 1, 100),
                "I was hit, and I am not my memosprite");
    }

    /**
     * A context with <b>no battlefield</b> cannot answer the question, so the condition fails either way.
     *
     * <p>Answering "no battle, therefore not my summon" would make {@code actor != summon} silently true for
     * every event - a rule meaning "anyone but my summon attacked" would fire on <em>everything</em>, with
     * nothing in the log to say so. That is the rule {@code self_summon_count} already follows by answering
     * {@code NaN}, and it is why the guard comes before the negation is applied.
     */
    @Test
    public void aContextWithNoBattleFailsEitherWay() {
        Character owner = characterWith(rule("SUMMON_ATTACK", List.of("actor == summon")));
        Battle battle = new Battle(List.of(owner), List.of(dummy()), new Random(0));
        Summon mine = battle.summonMemosprite(owner);
        TriggerTable.TriggerContext noBattle = new TriggerTable.TriggerContext(owner, owner, null, 1, 0);

        Assertions.assertFalse(compiledCondition(battle, "actor == summon", mine).test(noBattle));
        Assertions.assertFalse(compiledCondition(battle, "actor != summon", owner).test(noBattle),
                "not 'therefore everything else counts'");
    }

    // ==================================================================
    // 1b. `target_summon_count` - the same question about the other unit (M-41)
    // ==================================================================

    /**
     * {@code target_summon_count} counts the <b>subject's</b> summons, where {@code self_summon_count} counts the
     * owner's - the two are one word apart and both are read from the field.
     *
     * <p>Its first user is Sunday (星期日)'s Skill: "若目标拥有召唤物，则造成的伤害提高效果额外提高…", i.e. a question about
     * the ally the skill was cast on, not about the caster. The two rules are put on <b>one</b> table and the event
     * carries an ally who owns no summon while the owner owns one: exactly one of them may fire, which is the only
     * arrangement that tells the two variables apart.
     */
    @Test
    public void theTargetSummonCountAsksAboutTheSubject() {
        Character owner = characterWith(
                rule("SUMMON_ATTACK", List.of("self_summon_count >= 1")),
                rule("SUMMON_ATTACK", List.of("target_summon_count >= 1")));
        Character ally = CharacterFactory.create(PLAIN, LEVEL);
        Battle battle = new Battle(List.of(owner, ally), List.of(dummy()), new Random(0));
        battle.summonMemosprite(owner);
        battle.processRequests();

        Assertions.assertEquals(1, battle.fireTriggers(TriggerEvent.SUMMON_ATTACK, owner, ally, 1, 0),
                "my own summon is out; the ALLY has none -- so only one of the two questions answers yes");
    }

    /** …and when the subject does own one, the answer flips. */
    @Test
    public void theTargetSideReadsTheFieldToo() {
        Character owner = characterWith(
                rule("SUMMON_ATTACK", List.of("self_summon_count >= 1")),
                rule("SUMMON_ATTACK", List.of("target_summon_count >= 1")));
        Character ally = CharacterFactory.create(TEAMMATE, LEVEL);
        Battle battle = new Battle(List.of(owner, ally), List.of(dummy()), new Random(0));
        battle.summonMemosprite(ally);
        battle.processRequests();

        Assertions.assertEquals(1, battle.fireTriggers(TriggerEvent.SUMMON_ATTACK, owner, ally, 1, 0),
                "the ALLY's memosprite is what this counts, and the owner still has none");
    }

    /**
     * With no subject at all the variable is unreadable, and <b>every</b> comparison fails.
     *
     * <p>Pinned because {@code == 0} is the tempting way to write "the target has no summon", and it would be
     * silently true for every event without a target - a rule that fires on the wrong events with no symptom.
     * The same table carries both polarities, and the event with a real (summon-less) subject is the contrast that
     * shows the {@code == 0} rule works when there is something to read.
     */
    @Test
    public void anUnreadableTargetSummonCountFailsBothWays() {
        Character owner = characterWith(
                rule("SUMMON_ATTACK", List.of("target_summon_count == 0")),
                rule("SUMMON_ATTACK", List.of("target_summon_count >= 1")));
        Character ally = CharacterFactory.create(PLAIN, LEVEL);
        Battle battle = new Battle(List.of(owner, ally), List.of(dummy()), new Random(0));
        battle.summonMemosprite(owner);
        battle.processRequests();

        Assertions.assertEquals(1, battle.fireTriggers(TriggerEvent.SUMMON_ATTACK, owner, ally, 1, 0),
                "contrast: an ally with no summon really is 'zero'");
        Assertions.assertEquals(0, battle.fireTriggers(TriggerEvent.SUMMON_ATTACK, owner, null, 1, 0),
                "…but an event with no subject is not zero either: 'cannot read it' is a third fact");
    }

    // ==================================================================
    // 2. Refusals
    // ==================================================================

    /** A spelling that can never mean anything is refused where the file is read. */
    @Test
    public void unusableIdentityComparisonsAreRefused() {
        assertConditionRejected("actor == all_allies", "all_allies");
        assertConditionRejected("actor == attacker", "attacker");
        assertConditionRejected("foo == summon", "foo");
        assertConditionRejected("self == summon", "summon",
                "both sides are parties: there is no variable to ask about");
    }

    // ==================================================================
    // 3. The event itself
    // ==================================================================

    /**
     * A real memosprite attack raises {@code SUMMON_ATTACK} once, carrying the number of targets it reached.
     *
     * <p>Driven through the action bar and the summon's own skill, so this pins the emitter and not just the
     * condition: the rule demands two targets, and an AOE memosprite satisfies it exactly once per attack.
     */
    @Test
    public void aRealMemospriteAttackRaisesItOnceWithTheHitCount() {
        Character owner = characterWith(rule("SUMMON_ATTACK", List.of("actor == summon", "hit_count >= 2")));
        Enemy first = dummy();
        Enemy second = otherDummy();
        Battle battle = new Battle(List.of(owner), List.of(first, second), new Random(0));
        battle.startBattle();
        Summon evey = place(battle, owner, aoeSpec());
        double before = critDamageOf(owner);

        double dealt = letItAttack(battle, evey, List.of(first, second));

        Assertions.assertTrue(dealt > 0, "precondition: the memosprite really attacked");
        Assertions.assertEquals(before + GRANT, critDamageOf(owner), EPS,
                "one event for the attack, with hit_count 2 -- not one event per target");
    }

    /** The count is what the attack reached, so a single-target attack does not satisfy {@code >= 2}. */
    @Test
    public void theHitCountIsTheNumberOfTargetsReached() {
        Character owner = characterWith(rule("SUMMON_ATTACK", List.of("actor == summon", "hit_count >= 2")));
        Battle battle = new Battle(List.of(owner), List.of(dummy()), new Random(0));
        Summon evey = battle.summonMemosprite(owner);          // 忆灵技能1 is a single-target attack
        battle.processRequests();
        double before = critDamageOf(owner);

        letItAttack(battle, evey, List.of(battle.enemyUnits().getFirst()));

        Assertions.assertEquals(before, critDamageOf(owner), EPS, "hit_count is 1, so the rule stays out");
    }

    // ==================================================================
    // 4. The shipped content: relic set 123's 4-piece
    // ==================================================================

    /** Both authored rules state what the effect's {@code param} states - including the duration. */
    @Test
    public void theAuthoredRulesStateTheirOwnNumbers() {
        Character wearer = wearer();
        Battle battle = new Battle(List.of(wearer), List.of(dummy()), new Random(0));
        battle.startBattle();
        Summon evey = battle.memospriteOf(wearer);
        Assertions.assertNotNull(evey, "precondition: 长夜月's own rule brought 「长夜」 out");

        TriggerTable table = RelicTriggerTables.of(HERO_OF_TRIUMPHANT_SONG).at(4);
        List<TriggerTable.CompiledRule> speedRules = table.matching(TriggerEvent.TURN_START,
                new TriggerTable.TriggerContext(wearer, wearer, null, 0, 0, null, battle));
        Assertions.assertEquals(1, speedRules.size());
        EffectSpec speed = speedRules.getFirst().effects().getFirst();
        Assertions.assertEquals("SPEED", speed.getAttribute());
        Assertions.assertEquals(0.06, speed.getPercent(), EPS, "param #1");
        Assertions.assertEquals(1, speed.getTurns(),
                "「在场时」 is a WHILE clause re-evaluated each turn, not `permanent`");

        List<TriggerTable.CompiledRule> attackRules = table.matching(TriggerEvent.SUMMON_ATTACK,
                new TriggerTable.TriggerContext(wearer, evey, null, 1, 0, null, battle));
        Assertions.assertEquals(1, attackRules.size());
        Assertions.assertEquals(2, attackRules.getFirst().effects().size(), "the wearer AND the memosprite");

        EffectSpec onWearer = attackRules.getFirst().effects().get(0);
        EffectSpec onSummon = attackRules.getFirst().effects().get(1);
        Assertions.assertEquals("CRIT_ATTACK", onWearer.getAttribute());
        Assertions.assertEquals(0.3, onWearer.getPercent(), EPS, "param #2");
        Assertions.assertEquals(2, onWearer.getTurns(),
                "param #3 is 2 — the registered reason said '3 turn(s)', read off the English sentence");
        Assertions.assertNull(onWearer.getTarget(), "「装备者」 is the rule's owner: no target selector");
        Assertions.assertEquals("summon", onSummon.getTarget(), "「忆灵」 is the second effect");
    }

    /** Both units gain the CRIT DMG from a real attack, measured as a delta. */
    @Test
    public void theAuthoredSetBuffsBothUnitsWhenTheMemospriteAttacks() {
        Character wearer = wearer();
        Battle battle = new Battle(List.of(wearer), List.of(dummy()), new Random(0));
        battle.startBattle();
        Summon evey = battle.memospriteOf(wearer);
        Assertions.assertNotNull(evey, "precondition: 「长夜」 is out");

        double wearerBefore = critDamageOf(wearer);
        double summonBefore = critDamageOf(evey);

        letItAttack(battle, evey, List.of(battle.enemyUnits().getFirst()));

        Assertions.assertEquals(wearerBefore + 0.3, critDamageOf(wearer), EPS,
                "「装备者和忆灵的暴击伤害提高 30%」 — the wearer's half");
        Assertions.assertEquals(summonBefore + 0.3, critDamageOf(evey), EPS,
                "…and the memosprite's half");
    }

    /** A teammate's memosprite attacking grants nothing: the condition is what keeps the set to its wearer. */
    @Test
    public void theAuthoredSetIgnoresATeammatesMemosprite() {
        Character wearer = wearer();
        Character teammate = CharacterFactory.create(TEAMMATE, LEVEL);
        Battle battle = new Battle(List.of(wearer, teammate), List.of(dummy()), new Random(0));
        battle.startBattle();
        // A fixture summon for the teammate: Aglaea's own spec states a panel but no attack yet (her document
        // has not been read for one), and this case needs one that swings.
        Summon theirs = place(battle, teammate, aoeSpec());
        double before = critDamageOf(wearer);

        letItAttack(battle, theirs, List.of(battle.enemyUnits().getFirst()));

        Assertions.assertEquals(before, critDamageOf(wearer), EPS,
                "an over-trigger would be a wrong number with nothing to report");
    }

    /** The SPD clause needs the memosprite out: it is a WHILE condition, re-read at the wearer's turn. */
    @Test
    public void theSpeedClauseNeedsTheMemospriteOut() {
        // Aglaea, not Cyrene: her rule summons on her ULTIMATE, so the battlefield really starts without one
        // (Cyrene's "进入战斗时召唤" would make the "before" half of this case impossible to reach).
        Character wearer = CharacterFactory.create(TEAMMATE, LEVEL, true, null,
                RelicFactory.suit(HERO_OF_TRIUMPHANT_SONG, 5, 15));
        wearer.setAttribute(AttributeType.SPEED, new DoubleValue(100));   // a clean base: +6% is +6
        Battle battle = new Battle(List.of(wearer), List.of(dummy()), new Random(0));
        battle.startBattle();
        Assertions.assertEquals(0, battle.summonCountOf(wearer), "precondition: nothing summoned yet");

        startItsTurn(battle, wearer);
        Assertions.assertEquals(100, speedOf(wearer), EPS, "no memosprite, no SPD");
        battle.afterMove();

        battle.summonMemosprite(wearer);
        battle.processRequests();
        startItsTurn(battle, wearer);
        Assertions.assertEquals(106, speedOf(wearer), EPS, "…and 6% of 100 once it is out");
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    private static Character characterWith(TriggerSpec... rules) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(rules)));
        return owner;
    }

    /** The 4-piece on Cyrene, whose own rule (BATTLE_START to SUMMON) is left in place. */
    private static Character wearer() {
        return CharacterFactory.create(OWNER, LEVEL, true, null,
                RelicFactory.suit(HERO_OF_TRIUMPHANT_SONG, 5, 15));
    }

    /** One rule that grants {@link #GRANT} CRIT DMG, so "did it fire" is a readable number. */
    private static TriggerSpec rule(String event, List<String> when) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "CRIT_ATTACK");
        TriggerSpecs.set(effect, "percent", GRANT);
        TriggerSpecs.set(effect, "permanent", true);
        return TriggerSpecs.rule(event, when, effect);
    }

    /**
     * The compiled condition behind one spelling, taken from a table whose rule matches the given subject.
     *
     * <p>Needed because {@link TriggerTable#matching} only hands back rules whose conditions already hold - the
     * "no battlefield" case below is precisely the one where they do not, so the condition object has to be
     * obtained from a context in which it does.
     */
    private static TriggerTable.Condition compiledCondition(Battle battle, String text, CanHit subject) {
        TriggerTable table = new TriggerTable(OWNER, List.of(rule("SUMMON_ATTACK", List.of(text))));
        List<TriggerTable.CompiledRule> hits = table.matching(TriggerEvent.SUMMON_ATTACK,
                new TriggerTable.TriggerContext(battle.characters.getFirst(), subject, null, 0, 0, null, battle));
        Assertions.assertEquals(1, hits.size(), "'" + text + "' was expected to match this subject");
        return hits.getFirst().conditions().getFirst();
    }

    private static void assertConditionRejected(String text, String expectedInMessage) {
        assertConditionRejected(text, expectedInMessage, null);
    }

    private static void assertConditionRejected(String text, String expectedInMessage, String why) {
        TriggerSpec spec = rule("SUMMON_ATTACK", List.of(text));
        IllegalArgumentException rejected = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(spec)), why);
        Assertions.assertTrue(rejected.getMessage().contains(expectedInMessage),
                "expected the message to mention '" + expectedInMessage + "': " + rejected.getMessage());
    }

    private static double critDamageOf(CanHit unit) {
        return unit.getAttribute(AttributeType.CRIT_ATTACK).get();
    }

    private static double speedOf(Character character) {
        return character.getAttribute(AttributeType.SPEED).get();
    }

    /** Drives to the given unit's turn and lets {@code beforeMove} run - where TURN_START is fired. */
    private static void startItsTurn(Battle battle, CanHit actor) {
        for (int action = 0; action < 20; action++) {
            battle.stepForward();
            if (battle.isOver()) {
                break;
            }
            Signal current = battle.queue.getCurrentActor();
            battle.beforeMove();
            if (current != null && current.getCanHit() == actor) {
                return;                                  // its turn has started; the buff is up
            }
            battle.afterMove();
        }
        Assertions.fail("the unit never reached a turn within 20 actions");
    }

    /** Advances to the summon's turn, attacks, and returns the damage dealt. */
    private static double letItAttack(Battle battle, Summon summon, List<CanHit> targets) {
        Assertions.assertNotNull(summon.getSkills().get(SkillType.COMMON),
                "the summon has no attack to take a turn with");
        double before = targets.stream().mapToDouble(CanHit::getCurrentHp).sum();

        for (int action = 0; action < 20; action++) {
            battle.stepForward();
            if (battle.isOver()) {
                break;
            }
            Signal current = battle.queue.getCurrentActor();
            battle.beforeMove();
            if (current != null && current.getCanHit() == summon) {
                Assertions.assertTrue(battle.performAction(summon.getSkills().get(SkillType.COMMON), targets),
                        "the summon's action was refused");
                battle.processRequests();
                battle.afterMove();
                return before - targets.stream().mapToDouble(CanHit::getCurrentHp).sum();
            }
            battle.afterMove();
        }
        Assertions.fail("the summon never reached its turn within 20 actions");
        return 0;
    }

    /** Puts a fixture memosprite on the field exactly the way {@code Battle.summonMemosprite} does. */
    private static Summon place(Battle battle, Character master, MemospriteSpec spec) {
        Summon summon = SummonFactory.memosprite(master, spec);
        summon.setMaster(master);
        battle.allies.add(summon);
        battle.addRequestItems.add(summon);
        battle.processRequests();
        return summon;
    }

    /** A memosprite that hits every enemy for a share of its own Max HP - only reachable through the seam. */
    private static MemospriteSpec aoeSpec() {
        return new MemospriteSpec("fixture", "SummonConditionTest", null,
                List.of(new MemospriteSpec.Panel("HEALTH", 0.5, null),
                        new MemospriteSpec.Panel("SPEED", null, 999.0)),
                new MemospriteSpec.Attack("Ice", "HEALTH", 0.3, 1, "AoEAttack"));
    }

    private static Enemy dummy() {
        return EnemyFactory.create(MONSTER, 90, 1);
    }

    private static Enemy otherDummy() {
        return EnemyFactory.create(OTHER_MONSTER, 90, 1);
    }
}
