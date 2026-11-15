package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.buff.AbstractBuff;
import com.laosun.aluminium.buff.DotBuff;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.event.*;
import com.laosun.aluminium.skill.DefaultSkill;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Event completion: verifies that the events are emitted at the right time, the right number
 * of times, under the right filter conditions.
 *
 * <p>This class tests the events' contract, not battle outcomes - so the assertions
 * concentrate on "who received it, how many times, carrying what number".
 *
 * <p><b>L3:</b> the single-clause cases are grouped into the scenario methods below, one
 * {@code assertAll} per scenario so a failing clause still names itself. The boundaries
 * deliberately pinned down (all of them places where it is easy to write the wrong thing
 * after adding an event):
 * <ol>
 *   <li>{@link #luochaSkillCastAndHealScenario()} - heal / shield skills have no hit set; if
 *       the implementation said "no hit landed, so do not emit", those effects' trigger
 *       sources would never receive anything;</li>
 *   <li>{@link #fullyShieldedHitScenario()} - absorbed by a shield does not count as HP loss
 *       (nor as a kill). HP loss and "taking damage" are two different conventions, and
 *       characters that convert lost HP into resources (Castorice / Mydei / Blade) rely on
 *       that distinction;</li>
 *   <li>{@link #failedSkillSpendScenario()} - not spent, not consumed. Otherwise counters of
 *       the Misha / Sparkle kind ("per 1 point consumed") would book a consumption that never
 *       happened;</li>
 *   <li>{@link #zeroEnergyCharacterScenario()} - characters with no energy bar have no energy
 *       event. The stack-resource characters fall into this class.</li>
 * </ol>
 */
public class EventBusTest {
    private static final double EPS = 1e-9;

    /**
     * Event name constants (used in assertions, to avoid spelling drift).
     */
    private static final String SKILL_CAST = "SKILL_CAST";
    private static final String ENERGY = "ENERGY";
    private static final String HP_LOSS = "HP_LOSS";
    private static final String HEAL = "HEAL";
    private static final String KILL = "KILL";
    private static final String BREAK = "BREAK";
    private static final String SP_GAINED = "SP_GAINED";
    private static final String SP_SPENT = "SP_SPENT";

    /**
     * Scenario: a damaging skill cast emits {@code SkillCastEvent} exactly once and carries the
     * actual hit set - one event per cast, not one event per hit.
     */
    @Test
    public void skillCastScenario() {
        Battle basicBattle = newBattle();
        Character basicHero = basicBattle.characters().getFirst();
        Enemy basicEnemy = firstEnemy(basicBattle);
        Probe basicProbe = attachProbe(basicHero);
        castNow(basicBattle, basicHero, basicHero.getSkills().get(SkillType.COMMON), List.of(basicEnemy));
        Event basicCast = basicProbe.last(SKILL_CAST);

        Battle aoeBattle = newBattle();
        Character aoeHero = aoeBattle.characters().getFirst();
        Probe aoeProbe = attachProbe(aoeHero);
        Skill aoe = new DefaultSkill(1003, 3, 1);   // Himeko's ultimate: AoE
        castNow(aoeBattle, aoeHero, aoe, List.of(firstEnemy(aoeBattle)));

        Assertions.assertAll(
                () -> Assertions.assertNotNull(basicCast, "a damaging skill MUST emit SkillCastEvent"),
                () -> Assertions.assertEquals(basicHero, basicCast.user(), "the caster is the event user"),
                () -> Assertions.assertEquals(List.of(basicEnemy), basicCast.hits(),
                        "actual hit set = that enemy"),
                () -> Assertions.assertEquals(1, aoeProbe.count(SKILL_CAST),
                        "an AoE hitting 3 targets still emits only 1 cast event"));
    }

    /**
     * Scenario: a Luocha heal skill - a non-damaging cast still emits {@code SkillCastEvent}
     * (with an empty hit set and the caller's chosen targets), while {@code HealEvent} fires
     * only for HP really restored.
     *
     * <p>Core boundary: heal / shield skills have no hit set. Effects such as Bronya's "50%
     * chance to restore 1 skill point when casting a skill" and Sushang's "restore 1 skill
     * point after casting a skill on a broken target" have "the cast itself" as their trigger
     * source; if the implementation is written as "no hit landed, so do not emit", they would
     * never receive anything.
     */
    @Test
    public void luochaSkillCastAndHealScenario() {
        Character luocha = CharacterFactory.create(1203, 80);
        Battle battle = newBattle(luocha);
        Probe probe = attachProbe(luocha);
        Skill healSkill = luocha.getSkills().get(SkillType.SKILL);
        double maxHp = luocha.getMaxHp();
        double hpBeforeAnyHeal = luocha.getCurrentHp();

        battle.heal(luocha, luocha, 300);               // full HP: the actual restore is 0
        int healCountAtFullHp = probe.count(HEAL);

        castNow(battle, luocha, healSkill, List.of(luocha));
        Event castEvent = probe.last(SKILL_CAST);

        luocha.takeDamage(500);                         // lose some HP first, otherwise a heal at full HP is 0
        double hpBeforeRealHeal = luocha.getCurrentHp();
        battle.heal(luocha, luocha, 300);
        Event healEvent = probe.last(HEAL);

        Assertions.assertAll(
                () -> Assertions.assertNotNull(healSkill, "precondition: Luocha has a skill slot"),
                () -> Assertions.assertFalse(healSkill.getData().getEffect().isDamaging(),
                        "precondition: this is a heal skill"),
                () -> Assertions.assertEquals(maxHp, hpBeforeAnyHeal, EPS, "precondition: at full HP"),
                () -> Assertions.assertEquals(0, healCountAtFullHp,
                        "healing at full HP restores 0 → no event emitted"),
                () -> Assertions.assertNotNull(castEvent,
                        "a non-damaging skill (heal) MUST emit SkillCastEvent too"),
                () -> Assertions.assertTrue(castEvent.hits().isEmpty(), "a heal skill has no hit set"),
                () -> Assertions.assertEquals(List.of(luocha), castEvent.chosen(),
                        "but the targets the caller chose must be carried out verbatim"),
                () -> Assertions.assertNotNull(healEvent, "a heal must emit HealEvent"),
                () -> Assertions.assertEquals(luocha, healEvent.target(),
                        "the healed target is the event target"),
                () -> Assertions.assertEquals(luocha.getCurrentHp() - hpBeforeRealHeal, healEvent.amount(), EPS,
                        "the event carries the HP really restored"),
                () -> Assertions.assertTrue(healEvent.amount() > 0, "a real heal restores a positive amount"));
    }

    /**
     * Scenario: {@code EnergyEvent} is emitted only on an actual credit - a basic attack credits
     * the conventional 20, while an already-full bar credits 0 and therefore emits nothing
     * ("clipped by the cap" is not an energy gain).
     */
    @Test
    public void energyCreditScenario() {
        Battle battle = newBattle();
        Character hero = battle.characters().getFirst();
        Probe probe = attachProbe(hero);
        castNow(battle, hero, hero.getSkills().get(SkillType.COMMON), List.of(firstEnemy(battle)));
        Event credited = probe.last(ENERGY);

        Battle cappedBattle = newBattle();
        Character cappedHero = cappedBattle.characters().getFirst();
        cappedHero.setCurrentEnergy(cappedHero.getMaxEnergy());
        Probe cappedProbe = attachProbe(cappedHero);
        castNow(cappedBattle, cappedHero, cappedHero.getSkills().get(SkillType.COMMON),
                List.of(firstEnemy(cappedBattle)));

        Assertions.assertAll(
                () -> Assertions.assertNotNull(credited, "an energy gain must emit EnergyEvent"),
                () -> Assertions.assertEquals(hero, credited.target(),
                        "the credited character is the event target"),
                () -> Assertions.assertEquals(20, credited.amount(), EPS,
                        "basic attack conventional energy gain is 20"),
                () -> Assertions.assertEquals(0, cappedProbe.count(ENERGY),
                        "already full → actual credit 0 → no event emitted"));
    }

    /**
     * Scenario: characters with no energy bar have no energy event at all.
     *
     * <p>Castorice (1407) has no energy bar in her data (max energy 0), so the energy-gain
     * entry point never credits anything and no {@code EnergyEvent} reaches a subscriber.
     */
    @Test
    public void zeroEnergyCharacterScenario() {
        Character castorice = CharacterFactory.create(1407, 80);
        Battle battle = newBattle(castorice);
        Probe probe = attachProbe(castorice);

        castNow(battle, castorice, castorice.getSkills().get(SkillType.COMMON), List.of(firstEnemy(battle)));

        Assertions.assertAll(
                () -> Assertions.assertEquals(0, castorice.getMaxEnergy(), EPS,
                        "precondition: she has no energy bar"),
                () -> Assertions.assertEquals(0, probe.count(ENERGY),
                        "no energy bar → there must be no energy event"));
    }

    /**
     * Scenario: {@code HpLossEvent} reports the HP really lost (before / after / amount), and
     * within one lethal settlement the loss is reported before the kill.
     *
     * <p>The two cases live on independent battlefields: one is a plain hit on a full-HP
     * enemy, the other needs a 1-HP enemy so the same basic attack really does kill.
     */
    @Test
    public void hpLossScenario() {
        Battle battle = newBattle();
        Character hero = battle.characters().getFirst();
        Enemy enemy = firstEnemy(battle);
        Probe probe = attachProbe(enemy);
        double hpBeforeHit = enemy.getCurrentHp();
        battle.castImmediate(hero.getSkills().get(SkillType.COMMON), hero, List.of(enemy));
        Event hpLoss = probe.last(HP_LOSS);

        Battle lethalBattle = newBattle();
        Character lethalHero = lethalBattle.characters().getFirst();
        Enemy lethalEnemy = firstEnemy(lethalBattle);
        Probe lethalProbe = attachProbe(lethalEnemy);
        lethalEnemy.takeDamage(lethalEnemy.getCurrentHp() - 1);   // leave 1 HP
        double hpBeforeLethalHit = lethalEnemy.getCurrentHp();
        // This basic attack is guaranteed to kill, so both events are produced in the same settlement.
        actWithRealTurn(lethalBattle, lethalHero, () -> lethalHero.getSkills().get(SkillType.COMMON),
                () -> List.of(firstEnemy(lethalBattle)));
        int lossIndex = lethalProbe.indexOf(HP_LOSS);
        int killIndex = lethalProbe.indexOf(KILL);

        Assertions.assertAll(
                () -> Assertions.assertNotNull(hpLoss, "taking a hit must emit HpLossEvent"),
                () -> Assertions.assertEquals(enemy, hpLoss.target(),
                        "the defender is the event target"),
                () -> Assertions.assertEquals(hpBeforeHit, hpLoss.before(), EPS,
                        "before = the pre-hit HP"),
                () -> Assertions.assertEquals(enemy.getCurrentHp(), hpLoss.after(), EPS,
                        "after = the post-hit HP"),
                () -> Assertions.assertEquals(hpBeforeHit - enemy.getCurrentHp(), hpLoss.amount(), EPS,
                        "amount = before - after"),
                () -> Assertions.assertEquals(1, hpBeforeLethalHit, EPS, "precondition: only 1 HP left"),
                () -> Assertions.assertTrue(lossIndex != Integer.MAX_VALUE,
                        "the defender should receive HpLossEvent"),
                () -> Assertions.assertTrue(killIndex != Integer.MAX_VALUE,
                        "the defender should receive KillEvent"),
                () -> Assertions.assertTrue(lossIndex < killIndex,
                        "within one settlement: HP loss first, then the kill"));
    }

    /**
     * Scenario: a hit fully absorbed by a shield loses no HP and is not a kill, so neither
     * {@code HpLossEvent} nor {@code KillEvent} is emitted.
     *
     * <p>"HP loss" and "taking damage" are two different conventions. Characters that convert
     * lost HP into resources (Castorice / Mydei / Blade) want the amount of HP really lost - if
     * the amount absorbed by the shield counted too, they would accumulate stacks out of thin
     * air. The same shield also guards against "does not die before the shield breaks" being
     * bypassed by a kill event.
     */
    @Test
    public void fullyShieldedHitScenario() {
        Battle battle = newBattle();
        Character hero = battle.characters().getFirst();
        Enemy enemy = firstEnemy(battle);
        Probe probe = attachProbe(enemy);

        battle.grantShield(enemy, 10_000_000);          // shield thick enough that it cannot be broken through
        double hpBeforeShieldedHit = enemy.getCurrentHp();
        battle.castImmediate(hero.getSkills().get(SkillType.COMMON), hero, List.of(enemy));
        boolean deadAfterShieldedHit = enemy.isDeath();

        Assertions.assertAll(
                () -> Assertions.assertEquals(hpBeforeShieldedHit, enemy.getCurrentHp(), EPS,
                        "precondition: indeed not one drop of HP lost"),
                () -> Assertions.assertEquals(0, probe.count(HP_LOSS),
                        "fully shielded → no HP-loss event"),
                () -> Assertions.assertFalse(deadAfterShieldedHit,
                        "precondition: the shield held, it is not dead"),
                () -> Assertions.assertEquals(0, probe.count(KILL),
                        "fully shielded → no kill event"));
    }

    /**
     * Scenario: {@code KillEvent} fires on every real death and names the killer - by a cast, by
     * additional damage, by true damage, and by a DOT.
     *
     * <p>The original plan required "DOT does not emit {@code KillEvent}". Measurement showed
     * the opposite: {@code tickDots} goes through {@code Battle.applyDamage(..., KILL_ONLY)},
     * the same settlement path as a basic attack, so a DOT kill does emit the event - and that
     * is correct, because effects of the "ultimate +5 energy per enemy defeated" kind need to
     * know that a DOT finishing the job also counts as a defeat. {@code KILL_ONLY} only affects
     * the energy-gain category, not the fact of death.
     *
     * <p>Each case is arranged on its own battlefield (a 1-HP enemy, a different damage source):
     * they are the same clause but different inputs.
     */
    @Test
    public void killScenario() {
        Battle castBattle = newBattle();
        Character castHero = castBattle.characters().getFirst();
        Enemy castEnemy = firstEnemy(castBattle);
        Probe castProbe = attachProbe(castEnemy);
        castProbe.attachOn(castHero);
        castEnemy.takeDamage(castEnemy.getCurrentHp() - 1);      // leave 1 HP
        castBattle.castImmediate(castHero.getSkills().get(SkillType.COMMON), castHero, List.of(castEnemy));
        Event castKill = castProbe.last(KILL);

        Battle additionalBattle = newBattle();
        Character additionalHero = additionalBattle.characters().getFirst();
        Enemy additionalEnemy = firstEnemy(additionalBattle);
        Probe additionalProbe = attachProbe(additionalEnemy);
        additionalEnemy.takeDamage(additionalEnemy.getCurrentHp() - 1);      // leave 1 HP
        // Additional damage: notCountsAsAttack, but attributed to the attacker the kill is a fact.
        additionalBattle.applyAdditionalDamage(additionalHero, additionalEnemy, DamageElement.PHYSICAL, 1000);
        double hpAfterAdditional = additionalEnemy.getCurrentHp();
        Event additionalKill = additionalProbe.last(KILL);

        Battle dotBattle = newBattle();
        Character dotHero = dotBattle.characters().getFirst();
        Enemy dotEnemy = firstEnemy(dotBattle);
        Probe dotProbe = attachProbe(dotEnemy);
        dotEnemy.takeDamage(dotEnemy.getCurrentHp() - 1);      // leave 1 HP
        dotEnemy.getBuffManager().addBuff(new DotBuff(dotHero, DamageElement.FIRE, 9999, 3));
        dotBattle.tickDots(dotEnemy);
        boolean dotDead = dotEnemy.isDeath();
        Event dotKill = dotProbe.last(KILL);

        Battle trueBattle = newBattle();
        Character trueHero = trueBattle.characters().getFirst();
        Enemy trueEnemy = firstEnemy(trueBattle);
        Probe trueProbe = attachProbe(trueEnemy);
        trueEnemy.takeDamage(trueEnemy.getCurrentHp() - 1);      // leave 1 HP
        trueBattle.applyTrueDamage(trueHero, trueEnemy, DamageElement.PHYSICAL, 1000);
        double hpAfterTrue = trueEnemy.getCurrentHp();
        Event trueKill = trueProbe.last(KILL);

        Assertions.assertAll(
                () -> Assertions.assertNotNull(castKill, "a kill must emit KillEvent"),
                () -> Assertions.assertEquals(castEnemy, castKill.victim(),
                        "the victim is the killed enemy"),
                () -> Assertions.assertEquals(castHero, castKill.attacker(),
                        "the attacker is the one who cast the skill"),
                () -> Assertions.assertEquals(0, hpAfterAdditional, EPS,
                        "precondition: the additional damage really finished it off"),
                () -> Assertions.assertNotNull(additionalKill,
                        "a kill by additional damage must emit KillEvent too"),
                () -> Assertions.assertTrue(dotDead, "precondition: the DOT really did kill it"),
                () -> Assertions.assertNotNull(dotKill,
                        "a DOT kill emits KillEvent too (same convention as kill energy gain; the original plan's 'does not emit' was wrong)"),
                () -> Assertions.assertEquals(0, hpAfterTrue, EPS,
                        "precondition: the true damage really finished it off"),
                () -> Assertions.assertNotNull(trueKill, "a kill by true damage must emit KillEvent too"));
    }

    /**
     * Scenario: one real turn emits the whole event family - the skill-point credit, the cast
     * before its energy gain, and the turn boundaries still expressed by {@code MoveEvent}.
     *
     * <p>Ordering note: a single probe cannot compare HP loss with the kill - those go to the
     * defender, whereas {@code SkillCastEvent}/{@code EnergyEvent} are broadcast to our side.
     * Forcing both onto one probe yields a vacuously true comparison, so the loss-before-kill
     * order is asserted in {@link #hpLossScenario()} on the defender's probe instead.
     *
     * <p>The turn boundaries are pinned here so that nobody later adds a {@code TurnStartEvent}:
     * that would be a duplicated abstraction.
     */
    @Test
    public void realTurnScenario() {
        Battle basicBattle = newBattle();
        Character basicHero = basicBattle.characters().getFirst();
        Probe basicProbe = attachProbe(basicHero);
        int[] beforeMoveCount = {0};
        int[] afterMoveCount = {0};
        basicHero.beforeMove = () -> beforeMoveCount[0]++;
        basicHero.afterMove = () -> afterMoveCount[0]++;
        int skillPointsAtStart = basicBattle.getSkillPoints();
        actWithRealTurn(basicBattle, basicHero, () -> basicHero.getSkills().get(SkillType.COMMON),
                () -> List.of(firstEnemy(basicBattle)));
        Event gained = basicProbe.last(SP_GAINED);
        int castIndex = basicProbe.indexOf(SKILL_CAST);
        int energyIndex = basicProbe.indexOf(ENERGY);
        basicBattle.afterMove();
        int beforeMoves = beforeMoveCount[0];
        int afterMoves = afterMoveCount[0];

        Battle spendBattle = newBattle();
        Character spendHero = spendBattle.characters().getFirst();
        Probe spendProbe = attachProbe(spendHero);
        actWithRealTurn(spendBattle, spendHero, () -> spendHero.getSkills().get(SkillType.SKILL),
                () -> List.of(firstEnemy(spendBattle)));
        Event spent = spendProbe.last(SP_SPENT);

        Battle cappedBattle = newBattle();
        Character cappedHero = cappedBattle.characters().getFirst();
        Probe cappedProbe = attachProbe(cappedHero);
        cappedBattle.gainSkillPoint(100);                // push it to the cap of 5
        int pointsAtCap = cappedBattle.getSkillPoints();
        actWithRealTurn(cappedBattle, cappedHero, () -> cappedHero.getSkills().get(SkillType.COMMON),
                () -> List.of(firstEnemy(cappedBattle)));

        Assertions.assertAll(
                () -> Assertions.assertEquals(3, skillPointsAtStart, "precondition: 3 points at the start"),
                () -> Assertions.assertNotNull(gained, "a basic attack gaining a point must emit the event"),
                () -> Assertions.assertEquals(1, gained.amount(), EPS,
                        "the gain event carries the actual credit of 1"),
                () -> Assertions.assertTrue(castIndex != Integer.MAX_VALUE,
                        "this character should receive SkillCastEvent"),
                () -> Assertions.assertTrue(energyIndex != Integer.MAX_VALUE,
                        "this character should receive EnergyEvent"),
                () -> Assertions.assertTrue(castIndex < energyIndex,
                        "the cast event comes before the energy gain"),
                () -> Assertions.assertTrue(beforeMoves >= 1, "MoveEvent.beforeMove covers turn start"),
                () -> Assertions.assertTrue(afterMoves >= 1, "MoveEvent.afterMove covers turn end"),
                () -> Assertions.assertNotNull(spent, "spending a skill point must emit the event"),
                () -> Assertions.assertEquals(1, spent.amount(), EPS,
                        "the spend event carries the actual credit of 1"),
                () -> Assertions.assertEquals(5, pointsAtCap, "precondition: already full"),
                () -> Assertions.assertEquals(0, cappedProbe.count(SP_GAINED),
                        "already full → actual credit 0 → no event emitted"));
    }

    /**
     * Scenario: not enough skill points - the action does not go through, so the spend event is
     * not emitted and the point count does not change.
     *
     * <p>Otherwise Misha's "for every 1 skill point our whole team consumes, the next ultimate
     * gains 1 hit" and Sparkle's "restore 1 extra energy when a skill point is consumed" would
     * book a consumption that never happened at all.
     */
    @Test
    public void failedSkillSpendScenario() {
        Battle battle = newBattle();
        Character hero = battle.characters().getFirst();
        Probe probe = attachProbe(hero);

        while (battle.spendSkillPoint()) {
            // drain to zero
        }
        int pointsBefore = battle.getSkillPoints();

        boolean acted = actWithoutAfterMove(battle, hero, () -> hero.getSkills().get(SkillType.SKILL),
                List.of(firstEnemy(battle)));

        Assertions.assertAll(
                () -> Assertions.assertEquals(0, pointsBefore, "precondition: 0 points"),
                () -> Assertions.assertFalse(acted, "precondition: the action does not go through"),
                () -> Assertions.assertEquals(0, probe.count(SP_SPENT),
                        "not spent → do not emit the spend event"),
                () -> Assertions.assertEquals(0, battle.getSkillPoints(),
                        "the point count did not change either"));
    }

    // ==================================================================
    // Helpers: the probe buff and battle setup
    // ==================================================================

    /**
     * Event record: stores only the minimal fields the assertions need.
     */
    private record Event(String name, CanHit user, CanHit target, CanHit attacker, CanHit victim,
                         double before, double after, double amount,
                         List<? extends CanHit> hits, List<? extends CanHit> chosen) {
    }

    /**
     * A probe buff attached to the target: implements all 8 new events and records what it
     * receives into a list.
     *
     * <p>Deliberately attached to a buff rather than subclassing {@code CanHit}: this also
     * verifies that the forwarding chain "`CanHit` to `BuffManager` to buff" is connected - and
     * that is exactly the path character mechanics will actually use to subscribe to events.
     *
     * <p>Note: The buff MUST explicitly implement every event interface: `BuffManager`
     * forwards one by one via {@code instanceof} (it is not "all buffs receive all events").
     */
    private static final class Probe extends AbstractBuff
            implements SkillCastEvent, EnergyEvent, HpLossEvent, HealEvent,
            KillEvent, BreakEvent, SkillPointEvent {
        private final List<Event> events = new ArrayList<>();

        Probe() {
            super(999, false);
        }

        void attachOn(CanHit target) {
            target.getBuffManager().addBuff(this);
        }

        int count(String name) {
            return (int) events.stream().filter(e -> e.name().equals(name)).count();
        }

        Event last(String name) {
            for (int i = events.size() - 1; i >= 0; i--) {
                if (events.get(i).name().equals(name)) {
                    return events.get(i);
                }
            }
            return null;
        }

        /**
         * Index of the first occurrence; {@code Integer.MAX_VALUE} if absent (convenient for comparing order).
         */
        int indexOf(String name) {
            for (int i = 0; i < events.size(); i++) {
                if (events.get(i).name().equals(name)) {
                    return i;
                }
            }
            return Integer.MAX_VALUE;
        }

        private void add(String name, CanHit user, CanHit target, CanHit attacker, CanHit victim,
                         double before, double after, double amount,
                         List<? extends CanHit> hits, List<? extends CanHit> chosen) {
            events.add(new Event(name, user, target, attacker, victim, before, after, amount, hits, chosen));
        }

        @Override
        public void onSkillCast(Battle battle, CanHit user, Skill skill,
                                List<? extends CanHit> hitTargets, List<? extends CanHit> targets) {
            add(SKILL_CAST, user, null, null, null, 0, 0, 0, hitTargets, targets);
        }

        @Override
        public void onEnergyGain(Battle battle, CanHit target, double actuallyAdded) {
            add(ENERGY, null, target, null, null, 0, 0, actuallyAdded, List.of(), List.of());
        }

        @Override
        public void onHpLoss(Battle battle, CanHit target, double before, double after,
                             CanHit source, double amount) {
            add(HP_LOSS, null, target, source, null, before, after, amount, List.of(), List.of());
        }

        @Override
        public void onHeal(Battle battle, CanHit healer, CanHit target, double actuallyHealed) {
            add(HEAL, healer, target, null, null, 0, 0, actuallyHealed, List.of(), List.of());
        }

        @Override
        public void onKill(Battle battle, CanHit attacker, CanHit victim) {
            add(KILL, null, null, attacker, victim, 0, 0, 0, List.of(), List.of());
        }

        @Override
        public void onBreak(Battle battle, CanHit attacker, CanHit target, DamageElement element) {
            add(BREAK, null, target, attacker, null, 0, 0, 0, List.of(), List.of());
        }

        @Override
        public void onSkillPointGained(Battle battle, int amount) {
            add(SP_GAINED, null, null, null, null, 0, 0, amount, List.of(), List.of());
        }

        @Override
        public void onSkillPointSpent(Battle battle, int amount) {
            add(SP_SPENT, null, null, null, null, 0, 0, amount, List.of(), List.of());
        }

        @Override
        public boolean canAct() {
            return true;
        }

        @Override
        public void applyEffect(CanHit target) {
        }

        @Override
        public void removeBuff(CanHit target) {
        }

        @Override
        public void tickEffect(CanHit target) {
        }
    }

    /**
     * Attach a probe to {@code target} and return it so it can be asserted on.
     */
    private static Probe attachProbe(CanHit target) {
        Probe probe = new Probe();
        probe.attachOn(target);
        return probe;
    }

    /**
     * Default team: Himeko 1003 (Fire; basic attack and skill are both damaging).
     */
    private static Battle newBattle() {
        return newBattle(CharacterFactory.create(1003, 80));
    }

    private static Battle newBattle(Character hero) {
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(1_000_000));   // so one hit does not kill it
        enemy.heal(1_000_000);
        Battle battle = BattleFixture.battle(List.of(hero), List.of(enemy));
        battle.startBattle();
        return battle;
    }

    /**
     * Bypass the queue and settle directly (verifying "the event itself" needs no action bar involvement).
     */
    private static void castNow(Battle battle, CanHit user, Skill skill, List<? extends CanHit> targets) {
        battle.castImmediate(skill, user, targets);
    }

    private static Enemy firstEnemy(Battle battle) {
        return battle.enemyUnits().getFirst();
    }

    private static boolean actWithRealTurn(Battle battle, CanHit actor,
                                           java.util.function.Supplier<Skill> skill,
                                           java.util.function.Supplier<List<? extends CanHit>> targets) {
        boolean result = actWithoutAfterMove(battle, actor, skill, targets.get());
        battle.afterMove();
        return result;
    }

    private static boolean actWithoutAfterMove(Battle battle, CanHit actor,
                                               java.util.function.Supplier<Skill> skill,
                                               List<? extends CanHit> targets) {
        for (int i = 0; i < 30; i++) {
            battle.stepForward();
            if (battle.currentMove() == null) {
                break;
            }
            if (battle.currentMove().getCanHit() == actor) {
                battle.beforeMove();
                return battle.performAction(skill.get(), targets);
            }
            battle.afterMove();
        }
        Assertions.fail("did not reach " + actor.getName() + "'s turn within 30 steps");
        return false;
    }
}
