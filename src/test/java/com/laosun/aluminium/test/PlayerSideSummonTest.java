package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillEffectType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.enemy.EnemySkill;
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The <b>friendly half of L-8</b>: our side's camp holds any {@code CanHit}, and {@code characters} is the
 * subset view.
 *
 * <p><b>Why this is not the enemy-side change, done twice.</b> On the enemy side the roster's <em>type</em>
 * was wrong ({@code List<Enemy>}), so widening it made a summon placeable and the compiler found every site
 * that had assumed "enemy camp == monsters". Here the type was never the problem — the roster simply did not
 * exist: our side had one list, {@code characters}, and it means "our characters". The chosen shape is
 * therefore {@code allies} = the camp and {@code characters} = our characters, which is
 * {@code enemies}/{@code enemyUnits()} with the names swapped round, because {@code characters} is read in
 * <b>179 places</b> and nearly all of them mean the characters.
 *
 * <p>⚠ <b>The cost of that choice, and how it is paid here.</b> A camp-level call site left reading
 * {@code characters} will not fail to compile — it will silently ignore friendly summons: an enemy AOE would
 * miss one, a party-wide buff would skip it, and the victory check could ignore it. So each camp-level site
 * gets its own case below, with a summon on the field, and that set of cases <em>is</em> the type safety the
 * compiler is not providing. If a new camp-level site is added later, this class is where it should be
 * pinned.
 */
public class PlayerSideSummonTest {
    private static final double EPS = 1e-6;

    /** Our hero, and the master of the summon. */
    private static final int HERO = 1003;
    private static final int LEVEL = 80;
    /** 银鬃近卫 — a monster id used as the friendly summon's data. */
    private static final int MINION = 1002040;
    private static final int GROUP = 1;

    // ==================================================================
    // 1. The roster split
    // ==================================================================

    /** It joins our camp and the action bar, and it is NOT one of our characters. */
    @Test
    public void aFriendlySummonJoinsOurCampAndNotOurCharacters() {
        Fixture f = fixture();

        Assertions.assertEquals(2, f.battle.allies.size(), "the camp holds the hero and the summon");
        Assertions.assertSame(f.summon, f.battle.allies.get(1));
        Assertions.assertEquals(1, f.battle.characters.size(),
                "characters is the subset view: 'on our side' and 'is a character' are two questions");
        Assertions.assertSame(f.hero, f.battle.characters.getFirst());
        Assertions.assertEquals(1, f.battle.enemies.size(), "and nothing leaked into the enemy camp");
    }

    /** Our own attacks look at the enemy camp, so a friendly summon is not a legal target for us. */
    @Test
    public void ourOwnAttacksCannotTargetIt() {
        Fixture f = fixture();

        Assertions.assertEquals(1, f.battle.targetableEnemies().size(),
                "the enemy list is what our skills expand over");
        Assertions.assertFalse(f.battle.targetableEnemies().contains(f.summon));
    }

    /** It takes its turn: it is on the action bar with a signal of its own. */
    @Test
    public void itTakesItsTurnOnTheActionBar() {
        Fixture f = fixture();

        Assertions.assertEquals(3, f.battle.queue.size(), "hero + monster + summon");
        Assertions.assertTrue(f.battle.queue.snapshot().stream().anyMatch(s -> s.getCanHit() == f.summon),
                "the summon has a signal, so it will be handed turns like any other unit");
    }

    // ==================================================================
    // 2. The camp-level sites, one each (this is the compiler's stand-in)
    // ==================================================================

    /** {@code getOpponents}: an enemy asking "who is on the other side" must see the whole camp. */
    @Test
    public void anEnemySeesTheSummonAsAnOpponent() {
        Fixture f = fixture();

        Assertions.assertEquals(2, f.battle.getOpponents(f.monster).size(),
                "hero + summon: an enemy-targeting rule that only saw the characters would ignore half our "
                        + "side, which is exactly how a summon would end up untouchable");
        Assertions.assertTrue(f.battle.getOpponents(f.monster).contains(f.summon));
    }

    /**
     * {@code EnemySkill.struckBy}: an enemy AOE sweeps our whole camp.
     *
     * <p>The silent failure this rules out is a summon standing untouched in the middle of the blast.
     */
    @Test
    public void anEnemyAoeReachesTheSummonToo() {
        Fixture f = fixture();
        f.battle.startBattle();
        double heroBefore = f.hero.getCurrentHp();
        double summonBefore = f.summon.getCurrentHp();

        EnemySkill aoe = new EnemySkill(DamageElement.PHYSICAL, 1.0, 1,
                com.laosun.aluminium.enums.DamageType.NORMAL, SkillEffectType.AOE_ATTACK);
        f.battle.castImmediate(aoe, f.monster, List.of(f.hero));

        Assertions.assertTrue(f.hero.getCurrentHp() < heroBefore, "the named target took it");
        Assertions.assertTrue(f.summon.getCurrentHp() < summonBefore,
                "and so did the summon: an AOE iterates the camp, not the characters");
    }

    /** {@code all_allies}: a party-wide buff reaches the summon. */
    @Test
    public void aPartyWideBuffReachesTheSummon() {
        Fixture f = fixture();
        f.hero.setTriggerTable(new TriggerTable(HERO, List.of(partyAttackRule())));
        double heroAttackBefore = f.hero.getAttribute(AttributeType.ATTACK).get();
        double summonAttackBefore = f.summon.getAttribute(AttributeType.ATTACK).get();

        f.battle.startBattle();

        Assertions.assertTrue(f.hero.getAttribute(AttributeType.ATTACK).get() > heroAttackBefore,
                "precondition: the hero's own rule fired and buffed itself");
        // ⚠ The summon's expectation is exact and the hero's is not, and the reason is worth stating: an
        // `add_percent` modifier multiplies the BASE attack, so a character whose traces already contribute
        // percentages (姬子 does) does not move by exactly 10% of its resolved value -- only the increase is
        // 10% of base. A summon built from monster data has no traces, so its resolved attack IS its base.
        Assertions.assertEquals(summonAttackBefore * 1.1,
                f.summon.getAttribute(AttributeType.ATTACK).get(), 1e-6,
                "「我方全体」 is the camp, so the summon is one of them: a rule resolved against "
                        + "`characters` would have buffed the hero alone");
    }

    /**
     * The event fan-out walks the camp: a summon on our side hears about our side's events.
     *
     * <p>Uses a recording {@code Summon} because the default {@code CanHit} hooks are no-ops — with a plain
     * summon this case could not tell the two implementations apart. Both halves are covered: the hero is the
     * <b>subject</b> of the HP loss, so that notification can only arrive through the "and our entire side"
     * part of the broadcast policy, and the hero is the <b>caster</b> of the skill, which is
     * {@code SkillExecutor}'s own fan-out. Both changed with the camp split, and each is a separate line of
     * code — the mutation run caught the first and walked straight through the second until this case was
     * added.
     */
    @Test
    public void ourSideEventsReachTheSummon() {
        Fixture f = fixture();
        RecordingSummon spy = new RecordingSummon();
        spy.setMaster(f.hero);
        f.battle.allies.add(spy);                       // a hand-built summon, as EnemyCampSummonTest does
        f.battle.startBattle();

        f.battle.applyDamage(f.hero, new Damage(f.monster, f.hero, DamageElement.PHYSICAL,
                com.laosun.aluminium.enums.DamageType.NORMAL, 100));
        Assertions.assertTrue(spy.hpLosses > 0,
                "the hero was the subject, so this can only have come from the camp fan-out");

        int castsBefore = spy.skillCasts;
        f.battle.castImmediate(new DefaultSkill(HERO, 1, 1), f.hero, List.of(f.monster));
        Assertions.assertTrue(spy.skillCasts > castsBefore,
                "a friendly summon is one of 'every one of our members' for the cast broadcast too");
    }

    // ==================================================================
    // 3. Lifecycle and outcome
    // ==================================================================

    /** The master falling takes the friendly summon with it — the orphan sweep must cover our camp too. */
    @Test
    public void theMasterFallingTakesTheFriendlySummonAlong() {
        Fixture f = fixture();
        f.battle.startBattle();
        f.battle.processRequests();

        f.hero.takeDamage(9_999_999);
        f.battle.processRequests();

        Assertions.assertTrue(f.hero.isDeath(), "precondition: the master is down");
        Assertions.assertTrue(f.summon.isDeath(),
                "a sweep that only walked `enemies` would leave our own minion standing after its master");
        Assertions.assertFalse(f.battle.queue.snapshot().stream().anyMatch(s -> s.getCanHit() == f.summon));
    }

    /** Our side being wiped out is a loss, and the summon does not keep the battle alive. */
    @Test
    public void losingAllOurCharactersLosesTheBattle() {
        Fixture f = fixture();
        f.battle.startBattle();
        f.battle.processRequests();

        f.hero.takeDamage(9_999_999);
        f.battle.processRequests();

        Assertions.assertEquals(Battle.Status.LOSE, f.battle.getStatus());
    }

    /**
     * {@code checkResult} judges our <b>camp</b>: a survivor that is not a character keeps us in the fight.
     *
     * <p>⚠ This is the only case that can tell the two readings apart, and it needs a <b>masterless</b>
     * summon to do it. With a summon that follows its master (the ordinary kind), "all characters are down"
     * and "the whole camp is down" are the same state, because the master's death took the summon with it —
     * so a case built on one would pass under either reading and prove nothing. A hand-built summon with no
     * master (which {@code Summon} allows: the link is optional by design) survives the wipe, and then the
     * question "is this battle lost" has two answers depending on which list the check reads.
     *
     * <p>The answer taken here is the one L-8 established for the enemy side: a side is wiped out when its
     * <b>camp</b> is, so victory/defeat cannot be declared while something on that side is still standing.
     * It is not a claim that masterless allies are a thing content should create.
     */
    @Test
    public void checkResultReadsOurCampAndNotJustOurCharacters() {
        Fixture f = fixture();
        RecordingSummon orphanSpy = new RecordingSummon();
        f.battle.allies.add(orphanSpy);                 // no master: nothing will take it down with them
        f.battle.startBattle();
        f.battle.processRequests();

        f.hero.takeDamage(9_999_999);
        f.battle.processRequests();

        Assertions.assertTrue(f.hero.isDeath(), "precondition: every character is down");
        Assertions.assertFalse(orphanSpy.isDeath(), "and this ally is not tied to any of them");
        Assertions.assertEquals(Battle.Status.RUNNING, f.battle.getStatus(),
                "so our side is not wiped out: the check reads the camp, like the enemy side's does");
    }

    /**
     * And it is not an enemy for the victory check: killing the monsters wins while the summon still stands.
     *
     * <p>The mirror of {@code EnemyCampSummonTest.killingAllTheMonstersIsNotAWinWhileASummonStands} — there
     * the summon was theirs, here it is ours, and the two must not be confused.
     */
    @Test
    public void aFriendlySummonDoesNotBlockOurVictory() {
        Fixture f = fixture();
        f.battle.startBattle();
        f.battle.processRequests();

        f.monster.takeDamage(9_999_999);
        f.battle.processRequests();

        Assertions.assertFalse(f.summon.isDeath(), "our summon is still standing");
        Assertions.assertEquals(Battle.Status.WIN, f.battle.getStatus(),
                "and it is not an enemy, so the enemy side really is wiped out");
    }

    /** A master with no camp roster is refused rather than guessed at. */
    @Test
    public void aNeutralMasterIsRefused() {
        Fixture f = fixture();
        // Camp is final on CanHit, so a neutral unit is built as one rather than converted into one.
        Summon neutral = new Summon("neutral", Camp.NEUTRAL, new com.laosun.aluminium.utils.AttributeBuilder()
                .setBase(AttributeType.HEALTH, 1_000)
                .setBase(AttributeType.DEFENCE, 100)
                .setBase(AttributeType.ATTACK, 100)
                .setBase(AttributeType.SPEED, 100)
                .build());

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> f.battle.summon(neutral, MINION, GROUP));

        Assertions.assertTrue(refused.getMessage().contains("camp"), refused.getMessage());
        Assertions.assertEquals(2, f.battle.allies.size(), "and nothing was added to either roster");
        Assertions.assertEquals(1, f.battle.enemies.size());
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    private record Fixture(Battle battle, Character hero, Enemy monster, Summon summon) {
    }

    /**
     * A hero, one real monster, and one <b>friendly</b> summon that has already entered the field.
     *
     * <p>Entered through {@code summon()} rather than by hand, because the point of most of these cases is
     * the admission path itself: the camp it lands in, the action bar it joins, and the speed listener it is
     * given. {@code processRequests()} is what pushes it onto the action bar, so a case that needs a turn in
     * play calls it.
     */
    private static Fixture fixture() {
        Character hero = CharacterFactory.create(HERO, LEVEL);
        Enemy monster = EnemyFactory.create(1002011, 90, 1);
        Battle battle = new Battle(List.of(hero), List.of(monster), new Random(0));
        Summon summon = battle.summon(hero, MINION, GROUP);
        battle.processRequests();
        return new Fixture(battle, hero, monster, summon);
    }

    /** {@code BATTLE_START} -> +10% ATK for 「我方全体」. */
    private static TriggerSpec partyAttackRule() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "ATTACK");
        TriggerSpecs.set(effect, "percent", 0.1);
        TriggerSpecs.set(effect, "turns", 2);
        TriggerSpecs.set(effect, "target", "all_allies");

        return TriggerSpecs.rule(TriggerEvent.BATTLE_START.value(), null, effect);
    }

    /**
     * A {@link Summon} that counts what it is told, so "the fan-out reached our camp" is observable.
     *
     * <p>Minimal on purpose: {@code CanHit}'s event hooks are all no-ops, so without a recording subclass the
     * camp fan-out and no fan-out at all would look identical.
     */
    private static final class RecordingSummon extends Summon {
        private int hpLosses;
        private int skillCasts;

        RecordingSummon() {
            super("spy", Camp.PLAYER, new com.laosun.aluminium.utils.AttributeBuilder()
                    .setBase(AttributeType.HEALTH, 1_000)
                    .setBase(AttributeType.DEFENCE, 100)
                    .setBase(AttributeType.ATTACK, 100)
                    .setBase(AttributeType.SPEED, 100)
                    .build());
        }

        @Override
        public void onHpLoss(Battle battle, CanHit target, double before, double after, CanHit source,
                             double amount) {
            hpLosses++;
        }

        @Override
        public void onSkillCast(Battle battle, CanHit user, com.laosun.aluminium.models.skill.Skill skill,
                                List<? extends CanHit> hitTargets, List<? extends CanHit> targets) {
            skillCasts++;
        }
    }
}
