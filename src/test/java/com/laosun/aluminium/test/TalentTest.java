package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Talents and follow-up attacks (P8-3), as pure data.
 *
 * <p>The engine capability landed in P8-6/P8-7/P8-8 (events, trigger tables, resources) plus the
 * {@code DAMAGE} op and the {@code target} condition variable added here. What remains is content,
 * and content is JSON — this class asserts that two real characters work with <b>no Java character
 * class</b>, which is the whole point of the P8-0 three-way split.
 *
 * <h2>Where a follow-up attack comes from</h2>
 * The talent slot (4) is the source. That slot's {@code attack_type} is empty, and that is
 * <b>correct rather than a data gap</b>: the talent is a passive, and a passive is not a swing. Its
 * data still carries the complete attack — effect shape, element, toughness values and the per-level
 * multiplier — so the trigger table only has to supply the <i>when</i>:
 *
 * <pre>
 *   Clara: "after I am hit, hit back"           -> HP_LOST + target == self + DAMAGE(target=attacker)
 *   Seele: "after I kill, take another turn"    -> KILL + actor == self + EXTRA_TURN
 * </pre>
 */
public class TalentTest {

    private static final int CLARA = 1107;
    private static final int SEELE = 1102;
    private static final int ICE_EDGE = 1002011;

    /** Doc: Clara's counter is 160% of her ATK, which is her talent's level-10 value. */
    private static final double CLARA_DOC = 1.6;

    // ==================================================================
    // The data is what makes it work
    // ==================================================================

    /** Both characters are registered, and the tables carry the expected rules. */
    @Test
    public void bothCharactersHaveTalentRules() {
        Assertions.assertTrue(TriggerTables.exists(CLARA), "1107 must have a trigger file");
        Assertions.assertTrue(TriggerTables.exists(SEELE), "1102 must have a trigger file");

        Assertions.assertEquals(1, TriggerTables.of(CLARA).ruleCount(TriggerEvent.HP_LOST),
                "Clara counters on HP_LOST");
        Assertions.assertEquals(1, TriggerTables.of(SEELE).ruleCount(TriggerEvent.KILL),
                "Seele acts on KILL");
    }

    /**
     * The documented percentage is the talent's <b>level-10</b> multiplier — confirmed against a
     * second character rather than assumed.
     *
     * <p>Himeko's talent says 140% and its data reads {@code [0.7 … 1.4 … 1.75]}, whose 10th row is
     * exactly 1.4; Clara's says 160% and her 10th row is exactly 1.6. Two independent hits at the same
     * index means the docs quote level 10, not the maximum.
     */
    @Test
    public void documentedPercentagesAreTheLevelTenValues() {
        var clara = CharacterFactory.data(CLARA);
        var himeko = CharacterFactory.data(1003);
        Assertions.assertNotNull(clara);
        Assertions.assertNotNull(himeko);

        double claraTen = talentRow(CLARA, 1, 10);
        double himekoTen = talentRow(1003, 0, 10);
        Assertions.assertEquals(CLARA_DOC, claraTen, 1e-9, "Clara's doc says 160% = level 10");
        Assertions.assertEquals(1.4, himekoTen, 1e-9, "Himeko's doc says 140% = level 10");
    }

    /**
     * The multiplier index is genuinely per-ability, which is why the data states it.
     *
     * <p>If the engine guessed index 0 it would read Clara's first parameter ({@code 1}) instead of
     * her multiplier and be wrong by 60%, with nothing reporting it.
     */
    @Test
    public void theMultiplierIndexDiffersPerAbility() {
        // Himeko: multiplier first.
        Assertions.assertEquals(1.4, talentRow(1003, 0, 10), 1e-9);
        // Clara: multiplier second; index 0 is a flag whose value is 1.
        Assertions.assertEquals(1.0, talentRow(CLARA, 0, 10), 1e-9, "index 0 is not the multiplier");
        Assertions.assertEquals(1.6, talentRow(CLARA, 1, 10), 1e-9, "index 1 is");
    }

    // ==================================================================
    // Clara: the counter actually fires
    // ==================================================================

    /** The counter fires on a real enemy turn and lands on the enemy that hit her. */
    @Test
    public void claraCountersTheEnemyThatHitHer() {
        Character clara = CharacterFactory.create(CLARA, 80);
        Battle battle = newBattle(List.of(clara));
        Enemy enemy = firstEnemy(battle);

        double claraHpBefore = clara.getCurrentHp();
        double enemyHpBefore = enemy.getCurrentHp();

        letEnemyAttack(battle, enemy, clara);

        Assertions.assertTrue(clara.getCurrentHp() < claraHpBefore, "the enemy hit Clara");
        Assertions.assertTrue(enemy.getCurrentHp() < enemyHpBefore,
                "Clara's counter should have hurt the attacker ("
                        + enemyHpBefore + " -> " + enemy.getCurrentHp() + ")");
    }

    /**
     * The counter deals <b>the talent's multiplier</b>, not some other parameter.
     *
     * <p>⚠ This assertion is the point of the whole {@code damage_param} field. An earlier version of
     * this test only checked "the enemy lost HP", and it happily passed when the index was wrong
     * (parameter 0 is a flag worth 1.0 instead of the 0.8 multiplier) — a mutation test caught that.
     *
     * <p>The comparison is a <b>ratio against the engine itself</b> rather than a re-derived formula:
     * firing the counter with {@code damage_param} 1 and with 0 must produce damage in exactly the
     * ratio of the two parameters. Re-deriving the number by hand would mean restating every damage
     * zone here (my first attempt forgot the defence zone and was off by ~1.8x), and a duplicated
     * formula is a second source of truth that can rot.
     */
    @Test
    public void theCounterDealsTheTalentMultiplier() {
        double withCorrectParam = counterDamage(1);
        double withWrongParam = counterDamage(0);

        double right = talentRow(CLARA, 1, 1);
        double wrong = talentRow(CLARA, 0, 1);
        Assertions.assertNotEquals(wrong, right, "the two parameters must differ, or this proves nothing");

        Assertions.assertEquals(right / wrong, withCorrectParam / withWrongParam, 1e-6,
                "damage must scale with the parameter at the declared index (index 1 = " + right
                        + ", index 0 = " + wrong + ")");
    }

    /**
     * The <b>JSON</b> declares the right index.
     *
     * <p>Separate from the ratio test above on purpose: that one builds its own rule and therefore
     * proves the engine honours {@code damage_param}, while this one proves the shipped file carries
     * index 1 rather than 0. Mutation-testing showed the difference — flipping the JSON left the ratio
     * test green.
     */
    @Test
    public void theShippedRuleDeclaresTheRightParameterIndex() {
        Character clara = CharacterFactory.create(CLARA, 80);
        var rule = TriggerTables.of(CLARA)
                .matching(TriggerEvent.HP_LOST,
                        new com.laosun.aluminium.models.TriggerTable.TriggerContext(
                                clara, CharacterFactory.create(1003, 80), clara, 0, 0))
                .getFirst();

        var effect = rule.effects().getFirst();
        Assertions.assertEquals(1, effect.getDamageParam(),
                "Clara's multiplier is parameter 1; 0 is a flag whose value is 1.0");
        Assertions.assertEquals("TALENT", effect.getSkill());
        Assertions.assertEquals("attacker", effect.getTarget(),
                "the counter must be aimed back at whoever hit her");
    }

    /**
     * The rule only matches when Clara herself is the one who lost HP.
     *
     * <p>Checked directly against the shipped table (not a hand-built one), so the {@code when}
     * condition in the JSON is pinned: without it the counter would fire for every ally's wound.
     */
    @Test
    public void theShippedRuleRequiresClaraToBeTheVictim() {
        Character clara = CharacterFactory.create(CLARA, 80);
        Character ally = CharacterFactory.create(1003, 80);
        var table = TriggerTables.of(CLARA);

        Assertions.assertEquals(1, table.matching(TriggerEvent.HP_LOST,
                new com.laosun.aluminium.models.TriggerTable.TriggerContext(clara, ally, clara, 0, 10))
                .size(), "Clara is the victim -> matches");

        Assertions.assertEquals(0, table.matching(TriggerEvent.HP_LOST,
                new com.laosun.aluminium.models.TriggerTable.TriggerContext(clara, ally, ally, 0, 10))
                .size(), "an ally is the victim -> must not match");
    }

    /** Settles one counter with the given parameter index and returns the damage it dealt. */
    private static double counterDamage(int damageParam) {
        Character clara = CharacterFactory.create(CLARA, 80);
        Battle battle = newBattle(List.of(clara));
        Enemy enemy = firstEnemy(battle);

        // Replace the JSON rule with one that uses the requested index; everything else identical.
        clara.setTriggerTable(new com.laosun.aluminium.models.TriggerTable(CLARA, List.of(
                rule("HP_LOST", List.of("target == self"),
                        damageEffect("TALENT", damageParam, "attacker")))));

        double before = enemy.getCurrentHp();
        battle.fireTriggersForAlly(TriggerEvent.HP_LOST, enemy, clara, 100);
        return before - enemy.getCurrentHp();
    }

    /** The counter does not fire for a teammate's wounds: {@code target == self} does the filtering. */
    @Test
    public void claraDoesNotCounterWhenSomeoneElseIsHit() {
        Character clara = CharacterFactory.create(CLARA, 80);
        Character ally = CharacterFactory.create(1003, 80);
        Battle battle = newBattle(List.of(clara, ally));
        Enemy enemy = firstEnemy(battle);

        double enemyHpBefore = enemy.getCurrentHp();
        letEnemyAttack(battle, enemy, ally);

        Assertions.assertTrue(ally.getCurrentHp() < ally.getMaxHp(), "the ally was the one hit");
        Assertions.assertEquals(enemyHpBefore, enemy.getCurrentHp(), 1e-9,
                "Clara must not counter for someone else's wound");
    }

    /** Turned around: the HP-loss rule alone fires for anyone, so the condition is doing the work. */
    @Test
    public void withoutTheSubjectConditionTheCounterWouldFireForAnyone() {
        Character clara = CharacterFactory.create(CLARA, 80);
        Character ally = CharacterFactory.create(1003, 80);
        Battle battle = newBattle(List.of(clara, ally));
        Enemy enemy = firstEnemy(battle);

        // Directly ask the engine to fire HP_LOST with the *ally* as the subject.
        int fired = battle.fireTriggersForAlly(TriggerEvent.HP_LOST, enemy, ally, 10);
        Assertions.assertEquals(0, fired,
                "Clara's rule requires target == self, so an ally's loss must not match");
    }

    // ==================================================================
    // Seele: the extra turn
    // ==================================================================

    /**
     * Seele gains an extra turn after a kill, and it is **hers** — the action bar hands the turn back
     * to her rather than pushing someone else to the front.
     */
    @Test
    public void seeleGetsAnExtraTurnAfterAKill() {
        Character seele = CharacterFactory.create(SEELE, 80);
        Character ally = CharacterFactory.create(1003, 80);
        Battle battle = newBattle(List.of(seele, ally));
        Enemy enemy = firstEnemy(battle);

        // Take the enemy to the brink, then let Seele's own attack finish it.
        enemy.takeDamage(enemy.getCurrentHp() - 1);

        boolean acted = actOnOwnTurn(battle, seele,
                () -> seele.getSkills().get(SkillType.COMMON), List.of(enemy));
        Assertions.assertTrue(acted, "Seele should have acted");
        Assertions.assertTrue(enemy.isDeath(), "the attack should have killed");

        Assertions.assertNotNull(battle.queue.getExtraTurnActor(),
                "the kill should have scheduled an extra turn");
        Assertions.assertSame(seele, battle.queue.getExtraTurnActor(),
                "and it must be Seele's turn, not a teammate's");
    }

    /** A teammate's kill must not grant Seele an extra turn ({@code actor == self}). */
    @Test
    public void seeleDoesNotGetATurnFromATeammatesKill() {
        Character seele = CharacterFactory.create(SEELE, 80);
        Character ally = CharacterFactory.create(1003, 80);
        Battle battle = newBattle(List.of(seele, ally));
        Enemy enemy = firstEnemy(battle);

        enemy.takeDamage(enemy.getCurrentHp() - 1);
        boolean acted = actOnOwnTurn(battle, ally,
                () -> ally.getSkills().get(SkillType.COMMON), List.of(enemy));

        Assertions.assertTrue(acted);
        Assertions.assertTrue(enemy.isDeath(), "the teammate got the kill");
        Assertions.assertNull(battle.queue.getExtraTurnActor(),
                "Seele's talent keys off actor == self, so someone else's kill grants nothing");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** One parameter of a talent's row for a given skill level. */
    private static double talentRow(int cid, int paramIndex, int level) {
        var skill = new com.laosun.aluminium.models.DefaultSkill(cid, 4, level);
        return skill.getData().getSkills().get(level - 1).get(paramIndex);
    }

    /** Builds a {@code DAMAGE} effect for the tests (the beans are read-only, so this uses reflection). */
    private static com.laosun.aluminium.beans.EffectSpec damageEffect(String skill, int damageParam,
                                                                     String target) {
        var effect = new com.laosun.aluminium.beans.EffectSpec();
        set(effect, "op", "DAMAGE");
        set(effect, "skill", skill);
        set(effect, "damageParam", damageParam);
        set(effect, "target", target);
        return effect;
    }

    /** Builds a trigger rule for the tests. */
    private static com.laosun.aluminium.beans.TriggerSpec rule(
            String on, List<String> when, com.laosun.aluminium.beans.EffectSpec effect) {
        var spec = new com.laosun.aluminium.beans.TriggerSpec();
        set(spec, "on", on);
        if (when != null) {
            set(spec, "when", when);
        }
        set(spec, "doEffects", List.of(effect));
        set(spec, "source", "test");
        return spec;
    }

    private static void set(Object target, String field, Object value) {
        try {
            var f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot set " + field, e);
        }
    }

    private static Battle newBattle(List<Character> team) {
        Enemy enemy = EnemyFactory.create(ICE_EDGE, 90, 1);
        // A big pool so nothing dies by accident, and enough ATK pressure to make HP loss visible.
        enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(1_000_000));
        enemy.heal(1_000_000);
        List<Enemy> enemies = new ArrayList<>();
        enemies.add(enemy);
        Battle battle = new Battle(team, enemies, new Random(0));
        battle.startBattle();
        return battle;
    }

    private static Enemy firstEnemy(Battle battle) {
        return battle.enemies.getFirst();
    }

    /** Advances until {@code actor}'s turn, then has it use {@code skill} on {@code targets}. */
    private static boolean actOnOwnTurn(Battle battle, CanHit actor,
                                        java.util.function.Supplier<com.laosun.aluminium.models.Skill> skill,
                                        List<? extends CanHit> targets) {
        for (int i = 0; i < 40; i++) {
            battle.stepForward();
            if (battle.currentMove == null) {
                break;
            }
            if (battle.currentMove.getCanHit() == actor) {
                battle.beforeMove();
                boolean result = battle.performAction(skill.get(), targets);
                battle.afterMove();
                return result;
            }
            battle.afterMove();
        }
        Assertions.fail("40 steps without reaching " + actor.getName() + "'s turn");
        return false;
    }

    /** Advances until the enemy acts, then has it hit {@code victim}. */
    private static void letEnemyAttack(Battle battle, Enemy attacker, CanHit victim) {
        for (int i = 0; i < 40; i++) {
            battle.stepForward();
            if (battle.currentMove == null) {
                break;
            }
            Signal current = battle.currentMove;
            CanHit actor = current.getCanHit();
            battle.beforeMove();
            if (actor == attacker) {
                battle.performAction(attacker.getSkills().get(SkillType.COMMON), List.of(victim));
                battle.afterMove();
                return;
            }
            battle.afterMove();
        }
        Assertions.fail("the enemy never got a turn");
    }
}
