package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.MemospriteSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
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
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * {@code COMMAND_SUMMON} - "make the memosprite deal damage equal to X% of the memosprite's Max HP to all enemies".
 *
 * <p><b>What the op is.</b> The rule's owner orders its summon to attack <b>now</b>, with the numbers of a skill
 * the rule names. 长夜月's ultimate is the first user: "summon the memosprite '长夜', then make the memosprite '长夜' deal Ice damage equal to
 * '长夜''s #1[i]% Max HP to all enemies".
 *
 * <p><b>Where each number comes from, because that is the whole design.</b>
 * <ul>
 *   <li>the <b>multiplier</b> (2.0 at Lv10), the <b>element</b> (Ice) and the <b>shape</b> (AoEAttack) are read
 *       from the named skill's own data - {@code skill: "ULTRA"} + {@code damage_param: 0} - so they cannot
 *       drift from {@code skills.json};</li>
 *   <li>Note: the <b>row</b> has to be stated ({@code damage_level: 10}) because a character's skills are all at
 *       level 1 in this engine while the document quotes the Lv10 row. Reading "the skill's level" would deal
 *       half the damage with nothing to report - the case below pins the row by measuring the ratio between
 *       three levels of the same skill;</li>
 *   <li>the <b>base attribute</b> is the one thing the skill's row does not say: "equal to the <b>memosprite</b>'s Max HP",
 *       not 长夜月's attack - so the rule states it, and the case that doubles the summon's Max HP (and then the
 *       owner's) is what tells the two apart;</li>
 *   <li>the <b>toughness</b> (90) is the same kind of fact and is therefore <b>not</b> a field of this op either:
 *       it is 141303's own {@code stance_list}, and the engine's ordinary damage path removes it during the cast.
 *       Note: Measured through a real cast, because the cases here fire {@code ULT_CAST} by hand and that path removes
 *       no toughness - a hand-fired event would "show" a gap that does not exist.</li>
 * </ul>
 *
 * <p><b>Whose action it is.</b> The owner's: the summon swings without spending its turn, exactly like a
 * follow-up, and its own turn keeps using its own skill. The damage is attributed to the <b>summon</b>, which is
 * pinned through the event vocabulary the previous change added ({@code SUMMON_ATTACK} + {@code actor == summon}).
 */
public class SummonCommandTest {
    private static final double EPS = 1e-6;

    /** 长夜月 - her ultimate is the first user of {@code COMMAND_SUMMON}. */
    private static final int OWNER = 1413;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int OTHER_MONSTER = 8002040;

    // ==================================================================
    // 1. It is the summon that attacks
    // ==================================================================

    /**
     * The commanded attack is the <b>summon's</b>, not the owner's.
     *
     * <p>Pinned with the vocabulary from the previous change rather than by inspecting internals: a rule that
     * answers {@code SUMMON_ATTACK} when {@code actor == summon} must fire. If the engine settled the damage
     * with the owner as the attacker, `actor == summon` would be false and this case would not.
     */
    @Test
    public void theSummonIsTheAttacker() {
        Character owner = characterWith(
                rule("ULT_CAST", command(0, 10)),
                rule("SUMMON_ATTACK", List.of("actor == summon"), grantCritDamage(0.5)));
        Battle battle = new Battle(List.of(owner), List.of(dummy()), new Random(0));
        battle.summonMemosprite(owner);
        double before = critDamageOf(owner);

        int fired = battle.fireTriggers(TriggerEvent.ULT_CAST, owner, null, 0, 0);

        Assertions.assertEquals(1, fired, "the command ran");
        Assertions.assertEquals(before + 0.5, critDamageOf(owner), EPS,
                "and the attack was announced as the summons's own");
    }

    /**
     * The share is read from the <b>summon's</b> attribute.
     *
     * <p>Two runs, changing one unit's Max HP each time: doubling the summon's doubles the damage, doubling the
     * owner's leaves it untouched. Nothing else about the battle differs, so the two numbers say exactly which
     * unit the base came off.
     */
    @Test
    public void theBaseIsTheSummonsOwnAttribute() {
        double plain = commandedDamage(summon -> { });
        double summonFatter = commandedDamage(summon -> summon.setAttribute(AttributeType.HEALTH,
                new DoubleValue(summon.getMaxHp() * 2)));
        double ownerFatter = commandedDamage(summon -> { }, owner -> owner.setAttribute(AttributeType.HEALTH,
                new DoubleValue(owner.getMaxHp() * 2)));

        Assertions.assertTrue(plain > 0, "precondition: the commanded attack dealt damage");
        Assertions.assertEquals(2.0, summonFatter / plain, 1e-6,
                "twice the summon's Max HP, twice the damage (" + plain + " -> " + summonFatter + ")");
        Assertions.assertEquals(plain, ownerFatter, 1e-6,
                "…and the owner's Max HP is not what it reads");
    }

    /** The shape comes from the named skill, so an AOE ultimate reaches every enemy. */
    @Test
    public void itHitsEveryEnemyBecauseTheSkillIsAnAoe() {
        Character owner = characterWith(rule("ULT_CAST", command(0, 10)));
        Enemy first = dummy();
        Enemy second = otherDummy();
        Battle battle = new Battle(List.of(owner), List.of(first, second), new Random(0));
        battle.summonMemosprite(owner);
        double firstBefore = first.getCurrentHp();
        double secondBefore = second.getCurrentHp();
        double ourSideBefore = ourSideHp(battle);

        battle.fireTriggers(TriggerEvent.ULT_CAST, owner, null, 0, 0);

        Assertions.assertTrue(first.getCurrentHp() < firstBefore, "the aimed-at enemy was hit");
        Assertions.assertTrue(second.getCurrentHp() < secondBefore, "and so was the other one");
        Assertions.assertEquals(ourSideBefore, ourSideHp(battle), EPS, "and nothing on our own side");
    }

    /**
     * The multiplier is read from the <b>row the rule states</b>.
     *
     * <p>141303's parameter rows are {@code 1.0} (Lv1), {@code 2.0} (Lv10) and {@code 2.5} (Lv15), so three runs
     * that differ only in {@code damage_level} must come out in that ratio. Without this, "the skill's own
     * level" (which is 1 for every character today) would silently deal half the text's damage.
     */
    @Test
    public void theMultiplierComesFromTheStatedRow() {
        double atLevel1 = commandedDamageWith(1);
        double atLevel10 = commandedDamageWith(10);
        double atLevel15 = commandedDamageWith(15);

        Assertions.assertTrue(atLevel1 > 0, "precondition: damage was dealt");
        Assertions.assertEquals(2.0, atLevel10 / atLevel1, 1e-6, "Lv10 is twice Lv1 (1.0 -> 2.0)");
        Assertions.assertEquals(2.5, atLevel15 / atLevel1, 1e-6, "and Lv15 is 2.5x (the skill's cap)");
    }

    /**
     * The command does not spend the summon's turn, and does not touch its own skill.
     *
     * <p>"make the memosprite ... deal damage" is the owner's action: the memosprite attacks without acting. Its place in the action
     * bar and its own skill (50% of its Max HP, single target) are both still there afterwards.
     */
    @Test
    public void theSummonsTurnIsNotSpent() {
        Character owner = characterWith(rule("ULT_CAST", command(0, 10)));
        Battle battle = new Battle(List.of(owner), List.of(dummy()), new Random(0));
        Summon evey = battle.summonMemosprite(owner);
        battle.processRequests();
        double queueBefore = timeRemaining(battle, evey);

        battle.fireTriggers(TriggerEvent.ULT_CAST, owner, null, 0, 0);

        Assertions.assertEquals(queueBefore, timeRemaining(battle, evey), EPS,
                "the summon did not take an action");
        Assertions.assertNotNull(evey.getSkills().get(SkillType.COMMON), "…and its own skill is untouched");
    }

    // ==================================================================
    // 2. Refusals
    // ==================================================================

    /** Every argument is required, and a stray one is refused rather than ignored. */
    @Test
    public void theArgumentsAreCheckedAtLoadTime() {
        assertRejected(commandWithout("skill"), "skill");
        assertRejected(commandWithout("damageParam"), "damage_param");
        assertRejected(commandWithout("attribute"), "attribute");
        assertRejected(withField(command(0, 10), "damageLevel", 0), "damage_level");
        assertRejected(withField(command(0, 10), "turns", 2), "duration");
        assertRejected(withField(command(0, 10), "target", "self"), "target");
        assertRejected(withField(command(0, 10), "skill", "NONSENSE"), "slot");
    }

    /**
     * A summon that is not on the field is a loud error, naming the condition that would have gated the rule.
     *
     * <p>Same discipline as the {@code "summon"} target selector: silence would be a wrong state (the rule fires
     * and nothing happens), while the message is a one-line fix in the rule file.
     */
    @Test
    public void commandingASummonThatIsNotThereIsLoud() {
        Character owner = characterWith(rule("ULT_CAST", command(0, 10)));
        Battle battle = new Battle(List.of(owner), List.of(dummy()), new Random(0));

        IllegalStateException refused = Assertions.assertThrows(IllegalStateException.class,
                () -> battle.fireTriggers(TriggerEvent.ULT_CAST, owner, null, 0, 0));

        Assertions.assertTrue(refused.getMessage().contains("self_summon_count >= 1"), refused.getMessage());
    }

    /** Naming a slot whose skill does not deal damage is refused when the effect fires. */
    @Test
    public void aNonDamagingSkillIsRefused() {
        Character owner = characterWith(rule("ULT_CAST",
                withField(command(0, 10), "skill", "TALENT")));
        Battle battle = new Battle(List.of(owner), List.of(dummy()), new Random(0));
        battle.summonMemosprite(owner);

        IllegalStateException refused = Assertions.assertThrows(IllegalStateException.class,
                () -> battle.fireTriggers(TriggerEvent.ULT_CAST, owner, null, 0, 0));

        Assertions.assertTrue(refused.getMessage().contains("damaging"), refused.getMessage());
    }

    /**
     * A level the skill's table does not have is refused <b>loudly when it fires</b>, not read as something else.
     *
     * <p>The check cannot be at load time - a rule does not know its owner's cid while it is being read - so the
     * row bounds are checked where the table is finally in hand. The alternative (clamping, or falling back to
     * the skill's own level) would be a wrong number with nothing to report.
     */
    @Test
    public void aLevelOutsideTheTableIsRefusedWhenItFires() {
        Character owner = characterWith(rule("ULT_CAST", command(0, 99)));
        Battle battle = new Battle(List.of(owner), List.of(dummy()), new Random(0));
        battle.summonMemosprite(owner);

        IllegalStateException refused = Assertions.assertThrows(IllegalStateException.class,
                () -> battle.fireTriggers(TriggerEvent.ULT_CAST, owner, null, 0, 0));

        Assertions.assertTrue(refused.getMessage().contains("damage_level"), refused.getMessage());
        Assertions.assertTrue(refused.getMessage().contains("99"), refused.getMessage());
    }

    /**
     * The attack is aimed at a <b>living</b> enemy: a corpse at the head of the camp is skipped.
     *
     * <p>{@code EnemySkill} takes the first entry of the list it is handed as its main target, so a command that
     * passed the opposing camp unfiltered would swing at a dead unit and settle nothing - a rule that fires and
     * does nothing, which is the symptom this project keeps closing.
     */
    @Test
    public void aDeadEnemyAtTheHeadOfTheCampIsSkipped() {
        Character owner = characterWith(rule("ULT_CAST", command(0, 10)));
        Enemy first = dummy();
        Enemy second = otherDummy();
        Battle battle = new Battle(List.of(owner), List.of(first, second), new Random(0));
        battle.summonMemosprite(owner);
        first.takeDamage(9_999_999);
        battle.processRequests();
        double secondBefore = second.getCurrentHp();

        battle.fireTriggers(TriggerEvent.ULT_CAST, owner, null, 0, 0);

        Assertions.assertTrue(first.isDeath(), "precondition: the first enemy is down");
        Assertions.assertTrue(second.getCurrentHp() < secondBefore,
                "the command still hit the one that is alive");
    }

    // ==================================================================
    // 3. The shipped content: 长夜月's ultimate
    // ==================================================================

    /**
     * Her authored ultimate summons and then commands, in that order, and the command lands on every enemy.
     *
     * <p>Nothing is summoned before the rule fires, so the order of the two effects is what makes this work: a
     * file with the command first would raise "no summon on the field". End-to-end through the real loader and
     * the real battle.
     */
    @Test
    public void theAuthoredUltimateSummonsThenCommands() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Enemy first = dummy();
        Enemy second = otherDummy();
        Battle battle = new Battle(List.of(owner), List.of(first, second), new Random(0));
        Assertions.assertEquals(0, battle.summonCountOf(owner), "precondition: nothing is out yet");
        double firstBefore = first.getCurrentHp();
        double secondBefore = second.getCurrentHp();

        battle.fireTriggers(TriggerEvent.ULT_CAST, owner, null, 0, 0);

        Assertions.assertEquals(1, battle.summonCountOf(owner), "「召唤忆灵「长夜」」 came first");
        Assertions.assertTrue(first.getCurrentHp() < firstBefore, "…and then the command hit the enemies");
        Assertions.assertTrue(second.getCurrentHp() < secondBefore, "every one of them");
    }

    /** The authored rule states the skill, the column, the row and the base - and nothing it does not need. */
    @Test
    public void theAuthoredRuleNamesItsSkillAndRow() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Battle battle = new Battle(List.of(owner), List.of(dummy()), new Random(0));
        // A real battlefield is needed because `matching` evaluates the conditions, and this rule's condition
        // asks about the owner (`actor == self`) -- the same reason every condition test builds a battle.
        List<TriggerTable.CompiledRule> rules = TriggerTables.of(OWNER).matching(TriggerEvent.ULT_CAST,
                new TriggerTable.TriggerContext(owner, owner, null, 0, 0, null, battle));
        Assertions.assertEquals(1, rules.size(), "her ultimate is one rule");
        Assertions.assertEquals(List.of("actor == self"), rules.getFirst().conditions().stream()
                .map(TriggerTable.Condition::source).toList());

        List<EffectSpec> effects = rules.getFirst().effects();
        Assertions.assertEquals(2, effects.size(), "summon, then command");
        Assertions.assertEquals("SUMMON", effects.get(0).getOp());

        EffectSpec command = effects.get(1);
        Assertions.assertEquals("COMMAND_SUMMON", command.getOp());
        Assertions.assertEquals("ULTRA", command.getSkill());
        Assertions.assertEquals(0, command.getDamageParam(),
                "column 0 is `#1[i]`, the Max HP share (the placeholders are 1-based, the column is not)");
        Assertions.assertNull(command.getDamageLevel(),
                "⚠ no `damage_level` any more (M-32): the row is not pinned per effect but stated as the skill's own "
                        + "level, so an Eidolon's \"Ultimate Lv. +2\" composes with it instead of being ignored");
        // …and the file really does state that level, as a BATTLE_START raise of the ULTRA slot (10 - 1).
        TriggerTable table = TriggerTables.of(OWNER);
        Assertions.assertTrue(table.matching(TriggerEvent.BATTLE_START,
                        new TriggerTable.TriggerContext(owner, owner, null, 0, 0, null, battle)).stream()
                        .flatMap(rule -> rule.effects().stream())
                        .anyMatch(effect -> "RAISE_SKILL_LEVEL".equals(effect.getOp())
                                && "ULTRA".equals(effect.getSkill())
                                && effect.getAmount() != null && effect.getAmount() == 9.0),
                "her file states \"quoted at Lv10\" as a ULTRA +9 battle-start raise");
        Assertions.assertEquals("HEALTH", command.getAttribute());
        Assertions.assertNull(command.getPercent(), "the multiplier is the skill's, not a second copy here");
        Assertions.assertNull(command.getTarget(), "the victims come from the skill's shape");
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    /**
     * A {@code COMMAND_SUMMON} effect reading {@code column} of the ULTRA skill's row at the given level.
     *
     * <p>Note: The column is a <b>0-based array index</b> while the document writes its placeholders 1-based:
     * 141303's {@code #1[i]} (the Max HP share) is column <b>0</b>. Writing 1 there reads {@code #2} - the
     * [至暗之谜] charge count, which is 2 at <em>every</em> level - and at Lv10 that happens to equal the right
     * answer, so the rule looks correct until the level changes. {@link #theMultiplierComesFromTheStatedRow} is
     * the case that caught it.
     */
    private static EffectSpec command(int column, int level) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "COMMAND_SUMMON");
        TriggerSpecs.set(effect, "skill", "ULTRA");
        TriggerSpecs.set(effect, "damageParam", column);
        TriggerSpecs.set(effect, "damageLevel", level);
        TriggerSpecs.set(effect, "attribute", "HEALTH");
        return effect;
    }

    /** The same, with one field removed or replaced (for the refusal cases). */
    private static EffectSpec commandWithout(String field) {
        EffectSpec effect = command(0, 10);
        TriggerSpecs.set(effect, field, null);
        return effect;
    }

    private static EffectSpec withField(EffectSpec effect, String field, Object value) {
        TriggerSpecs.set(effect, field, value);
        return effect;
    }

    private static TriggerSpec rule(String event, EffectSpec... effects) {
        return TriggerSpecs.rule(event, null, effects);
    }

    private static TriggerSpec rule(String event, List<String> when, EffectSpec... effects) {
        return TriggerSpecs.rule(event, when, effects);
    }

    private static EffectSpec grantCritDamage(double percent) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "CRIT_ATTACK");
        TriggerSpecs.set(effect, "percent", percent);
        TriggerSpecs.set(effect, "permanent", true);
        return effect;
    }

    private static Character characterWith(TriggerSpec... rules) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(rules)));
        return owner;
    }

    /** One battle, one command: how much HP the enemy camp lost. */
    private static double commandedDamage(java.util.function.Consumer<Summon> tweak) {
        return commandedDamage(tweak, owner -> { });
    }

    private static double commandedDamage(java.util.function.Consumer<Summon> tweakSummon,
                                          java.util.function.Consumer<Character> tweakOwner) {
        Character owner = characterWith(rule("ULT_CAST", command(0, 10)));
        Enemy target = dummy();
        Battle battle = new Battle(List.of(owner), List.of(target), new Random(0));
        Summon evey = battle.summonMemosprite(owner);
        tweakSummon.accept(evey);
        tweakOwner.accept(owner);
        double before = target.getCurrentHp();

        battle.fireTriggers(TriggerEvent.ULT_CAST, owner, null, 0, 0);

        return before - target.getCurrentHp();
    }

    /** The same, at a stated level, for the row case. */
    private static double commandedDamageWith(int level) {
        Character owner = characterWith(rule("ULT_CAST", command(0, level)));
        Enemy target = dummy();
        Battle battle = new Battle(List.of(owner), List.of(target), new Random(0));
        battle.summonMemosprite(owner);
        double before = target.getCurrentHp();

        battle.fireTriggers(TriggerEvent.ULT_CAST, owner, null, 0, 0);

        return before - target.getCurrentHp();
    }

    private static void assertRejected(EffectSpec effect, String expectedInMessage) {
        TriggerSpec spec = rule("ULT_CAST", effect);
        IllegalArgumentException rejected = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(spec)));
        Assertions.assertTrue(rejected.getMessage().contains(expectedInMessage),
                "expected the message to mention '" + expectedInMessage + "': " + rejected.getMessage());
    }

    private static double critDamageOf(CanHit unit) {
        return unit.getAttribute(AttributeType.CRIT_ATTACK).get();
    }

    private static double ourSideHp(Battle battle) {
        return battle.allies.stream().mapToDouble(CanHit::getCurrentHp).sum();
    }

    /** How much action value the unit still has - the number an action would reset. */
    private static double timeRemaining(Battle battle, CanHit target) {
        for (Signal signal : battle.queue.snapshot()) {
            if (signal.getCanHit() == target) {
                return battle.queue.getTimeRemaining(signal);
            }
        }
        return Double.NaN;
    }

    private static Enemy dummy() {
        return EnemyFactory.create(MONSTER, 90, 1);
    }

    /**
     * Casting the ultimate removes the toughness <b>its own skill states</b> - and after M-40 it is the
     * <b>commanded</b> attack that removes it.
     *
     * <p>141303's {@code stance_list} is {@code single 0 / all 90}, so one real cast takes a 90-point bar to 0; the
     * bar is widened to 300 here so that "90 removed" and "180 removed" cannot read the same. Note: The 90 travels with
     * the <b>swing</b>: since her cast delegates its own damage ({@code DELEGATE_DAMAGE} on {@code CAST_SETUP}),
     * the executor expands no damage and removes no toughness of its own, so this op reads the column off the
     * named skill and hands it to the {@code EnemySkill} the memosprite swings with. That is the same "one source"
     * rule the element, the shape and the multiplier already follow - which is also why the op still takes no
     * stance <b>argument</b>: writing the 90 in the rule would be a second copy of the number, and getting it
     * wrong would be a second helping of toughness.
     *
     * <p>Driven through the real cast ({@code Battle.castImmediate}) rather than by firing {@code ULT_CAST} by hand,
     * which is what the other cases here do: the cast is what runs {@code CAST_SETUP} and therefore what makes the
     * delegation happen at all.
     */
    @Test
    public void castingTheUltimateRemovesTheToughnessItsOwnSkillStates() {
        Enemy enemy = otherDummy();                                   // its template bar is exactly the skill's `all`
        enemy.setStanceWeak(java.util.Set.of(DamageElement.ICE));     // its element must be a weakness to move
        // A bar WIDER than 90, so the number removed is observable rather than hidden by the cap: the template's own
        // 90-point bar would read 0 for "90 removed" and for "180 removed" alike, which would let a second helping
        // of toughness pass this case unnoticed.
        enemy.setMaxStance(300);
        enemy.setStance(300);
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Battle battle = new Battle(List.of(owner), List.of(enemy), new Random(0));

        battle.castImmediate(new DefaultSkill(OWNER, 3, 1), owner, List.of(enemy));

        Assertions.assertEquals(210, enemy.getStance(), EPS,
                "\"make the memosprite '长夜' deal ... Ice damage to all enemies\" -- exactly the 90 of 141303's own stance_list, once");
    }

    private static Enemy otherDummy() {
        return EnemyFactory.create(OTHER_MONSTER, 90, 1);
    }
}
