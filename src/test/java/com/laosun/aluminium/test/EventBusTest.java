package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.buff.AbstractBuff;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.event.*;
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Event completion (P8-6): verifies that the new events are **emitted at the right time, the
 * right number of times, under the right filter conditions**.
 *
 * <p>This class tests the events' **contract**, not battle outcomes — so the assertions
 * concentrate on "who received it, how many times, carrying what number".
 *
 * <p>Four boundaries deliberately pinned down (all of them places where it is easy to write
 * the wrong thing after adding an event):
 * <ol>
 *   <li>{@link #nonDamagingSkillStillFiresSkillCast()} — heal / shield skills have **no hit
 *       set**; if the implementation says "no hit landed, so do not emit", those effects'
 *       trigger sources would never receive anything;</li>
 *   <li>{@link #fullyShieldedHitFiresNoHpLoss()} — **absorbed by a shield does not count as
 *       HP loss**. HP loss and "taking damage" are two different conventions, and characters
 *       that convert lost HP into resources (Castorice / Mydei / Blade) rely on that
 *       distinction;</li>
 *   <li>{@link #failedSkillSpendFiresNoSkillPointSpent()} — **not spent, not consumed**. Otherwise
 *       counters of the Misha / Sparkle kind ("per 1 point consumed") would book a consumption
 *       that never happened;</li>
 *   <li>{@link #zeroEnergyCharacterFiresNoEnergyEvent()} — characters with no energy bar
 *       **have no energy event**. The 6 stack-resource characters fall into this class.</li>
 * </ol>
 */
public class EventBusTest {
    private static final double EPS = 1e-9;

    /** Event name constants (used in assertions, to avoid spelling drift). */
    private static final String SKILL_CAST = "SKILL_CAST";
    private static final String ENERGY = "ENERGY";
    private static final String HP_LOSS = "HP_LOSS";
    private static final String HEAL = "HEAL";
    private static final String KILL = "KILL";
    private static final String BREAK = "BREAK";
    private static final String SP_GAINED = "SP_GAINED";
    private static final String SP_SPENT = "SP_SPENT";

    // ==================================================================
    // 1. Skill cast: both damaging and non-damaging skills must emit
    // ==================================================================

    /** Damaging skill: `SkillCastEvent` carries the **actual hit set**. */
    @Test
    public void damagingSkillFiresSkillCastWithHitTargets() {
        Battle battle = newBattle();
        Character hero = battle.characters.getFirst();
        Enemy enemy = firstEnemy(battle);
        Probe probe = attachProbe(hero);

        castNow(battle, hero, hero.getSkills().get(SkillType.COMMON), List.of(enemy));

        Event last = probe.last(SKILL_CAST);
        Assertions.assertNotNull(last, "a damaging skill MUST emit SkillCastEvent");
        Assertions.assertEquals(hero, last.user());
        Assertions.assertEquals(List.of(enemy), last.hits(), "actual hit set = that enemy");
    }

    /**
     * ⚠ Core boundary: **non-damaging skills must emit SkillCastEvent too**.
     *
     * <p>The heal skill (Luocha 1203 slot 2, `RESTORE`) deals no damage at all, so its
     * `hitTargets` is **empty**. Effects such as Bronya's "50% chance to restore 1 skill point
     * when casting a skill" and Sushang's "restore 1 skill point after casting a skill on a
     * broken target" have "the cast itself" as their trigger source; if the implementation is
     * written as "no hit landed, so do not emit", they would never receive anything.
     */
    @Test
    public void nonDamagingSkillStillFiresSkillCast() {
        Character luocha = CharacterFactory.create(1203, 80);
        Battle battle = newBattle(luocha);
        Probe probe = attachProbe(luocha);

        Skill healSkill = luocha.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(healSkill, "precondition: Luocha has a skill slot");
        Assertions.assertFalse(healSkill.getData().getEffect().isDamaging(), "precondition: this is a heal skill");

        castNow(battle, luocha, healSkill, List.of(luocha));

        Event last = probe.last(SKILL_CAST);
        Assertions.assertNotNull(last, "a non-damaging skill (heal) MUST emit SkillCastEvent too");
        Assertions.assertTrue(last.hits().isEmpty(), "a heal skill has no hit set");
        Assertions.assertEquals(List.of(luocha), last.chosen(), "but the targets the caller chose must be carried out verbatim");
    }

    /** One cast emits SkillCastEvent **exactly once** (not once per hit). */
    @Test
    public void skillCastFiresOncePerCastNotPerHit() {
        Battle battle = newBattle();
        Character hero = battle.characters.getFirst();
        Probe probe = attachProbe(hero);

        Skill aoe = new DefaultSkill(1003, 3, 1);   // Himeko's ultimate: AoE
        castNow(battle, hero, aoe, List.of(firstEnemy(battle)));

        Assertions.assertEquals(1, probe.count(SKILL_CAST), "an AoE hitting 3 targets still emits only 1 cast event");
    }

    // ==================================================================
    // 2. Energy: emitted only on an actual credit; characters with no energy bar do not emit
    // ==================================================================

    /** Basic attack lands → energy gain → `EnergyEvent`, carrying the **actually credited value** (20). */
    @Test
    public void energyEventCarriesTheActuallyCreditedAmount() {
        Battle battle = newBattle();
        Character hero = battle.characters.getFirst();
        Probe probe = attachProbe(hero);

        castNow(battle, hero, hero.getSkills().get(SkillType.COMMON), List.of(firstEnemy(battle)));

        Event last = probe.last(ENERGY);
        Assertions.assertNotNull(last, "an energy gain must emit EnergyEvent");
        Assertions.assertEquals(hero, last.target());
        Assertions.assertEquals(20, last.amount(), EPS, "basic attack conventional energy gain is 20");
    }

    /** When energy is already full the actual credit is 0 → **no emission** ("clipped by the cap" is not an energy gain). */
    @Test
    public void cappedEnergyFiresNoEvent() {
        Battle battle = newBattle();
        Character hero = battle.characters.getFirst();
        hero.setCurrentEnergy(hero.getMaxEnergy());
        Probe probe = attachProbe(hero);

        castNow(battle, hero, hero.getSkills().get(SkillType.COMMON), List.of(firstEnemy(battle)));

        Assertions.assertEquals(0, probe.count(ENERGY), "already full → actual credit 0 → no event emitted");
    }

    /**
     * ⚠ Characters with no energy bar (the 6 stack-resource ones) **have no energy event**.
     *
     * <p>Castorice 1407's `max_energy` is null in the data and `sp_base` is null everywhere,
     * and her provider is {@code NoConventionalEnergyProvider} (all 5 hooks return null), so
     * the code never even reaches the energy-gain entry point.
     */
    @Test
    public void zeroEnergyCharacterFiresNoEnergyEvent() {
        Character castorice = CharacterFactory.create(1407, 80);
        Battle battle = newBattle(castorice);
        Probe probe = attachProbe(castorice);

        Assertions.assertEquals(0, castorice.getMaxEnergy(), EPS, "precondition: she has no energy bar");

        castNow(battle, castorice, castorice.getSkills().get(SkillType.COMMON), List.of(firstEnemy(battle)));

        Assertions.assertEquals(0, probe.count(ENERGY), "no energy bar → there must be no energy event");
    }

    // ==================================================================
    // 3. HP loss vs taking damage
    // ==================================================================

    /** Taking a normal hit → the HP-loss event carries before/after and the real amount lost. */
    @Test
    public void hpLossReportsBeforeAndAfter() {
        Battle battle = newBattle();
        Character hero = battle.characters.getFirst();
        Enemy enemy = firstEnemy(battle);
        Probe probe = attachProbe(enemy);

        double before = enemy.getCurrentHp();
        battle.castImmediate(hero.getSkills().get(SkillType.COMMON), hero, List.of(enemy));

        Event last = probe.last(HP_LOSS);
        Assertions.assertNotNull(last, "taking a hit must emit HpLossEvent");
        Assertions.assertEquals(enemy, last.target());
        Assertions.assertEquals(before, last.before(), EPS);
        Assertions.assertEquals(enemy.getCurrentHp(), last.after(), EPS);
        Assertions.assertEquals(before - enemy.getCurrentHp(), last.amount(), EPS, "amount = before - after");
    }

    /**
     * ⚠ Core boundary: **a shield absorbs it all → not one drop of HP lost → do not emit
     * HpLossEvent**.
     *
     * <p>"HP loss" and "taking damage" are two different conventions. Characters that convert
     * lost HP into resources (Castorice's 【新蕊】, Mydei's 【血仇】, Blade's 【充能】) want the
     * **amount of HP really lost** — if an event were emitted here and the amount absorbed by
     * the shield counted too, they would accumulate stacks out of thin air.
     */
    @Test
    public void fullyShieldedHitFiresNoHpLoss() {
        Battle battle = newBattle();
        Character hero = battle.characters.getFirst();
        Enemy enemy = firstEnemy(battle);
        Probe probe = attachProbe(enemy);

        battle.grantShield(enemy, 10_000_000);          // shield thick enough that it cannot be broken through
        double hpBefore = enemy.getCurrentHp();

        battle.castImmediate(hero.getSkills().get(SkillType.COMMON), hero, List.of(enemy));

        Assertions.assertEquals(hpBefore, enemy.getCurrentHp(), EPS, "precondition: indeed not one drop of HP lost");
        Assertions.assertEquals(0, probe.count(HP_LOSS), "fully shielded → no HP-loss event");
    }

    // ==================================================================
    // 4. Healing: emitted only when HP is really restored
    // ==================================================================

    /** Heal skill → HP really restored → emit `HealEvent`. */
    @Test
    public void healEventCarriesActuallyHealed() {
        Character luocha = CharacterFactory.create(1203, 80);
        Battle battle = newBattle(luocha);
        Probe probe = attachProbe(luocha);

        luocha.takeDamage(500);                          // lose some HP first, otherwise a heal at full HP is 0
        double before = luocha.getCurrentHp();

        battle.heal(luocha, luocha, 300);

        Event last = probe.last(HEAL);
        Assertions.assertNotNull(last, "a heal must emit HealEvent");
        Assertions.assertEquals(luocha, last.target());
        Assertions.assertEquals(luocha.getCurrentHp() - before, last.amount(), EPS);
        Assertions.assertTrue(last.amount() > 0);
    }

    /** Healing a full-HP target → actual restore 0 → **no emission**. */
    @Test
    public void healingAFullHpTargetFiresNoEvent() {
        Character luocha = CharacterFactory.create(1203, 80);
        Battle battle = newBattle(luocha);
        Probe probe = attachProbe(luocha);

        Assertions.assertEquals(luocha.getMaxHp(), luocha.getCurrentHp(), EPS, "precondition: at full HP");
        battle.heal(luocha, luocha, 300);

        Assertions.assertEquals(0, probe.count(HEAL), "healing at full HP restores 0 → no event emitted");
    }

    // ==================================================================
    // 5. Kill / break
    // ==================================================================

    /** Kill an enemy → `KillEvent`, and the killer is the one who cast the skill. */
    @Test
    public void killEventNamesTheAttacker() {
        Battle battle = newBattle();
        Character hero = battle.characters.getFirst();
        Enemy enemy = firstEnemy(battle);
        Probe probe = attachProbe(enemy);
        probe.attachOn(hero);

        enemy.takeDamage(enemy.getCurrentHp() - 1);      // leave 1 HP
        battle.castImmediate(hero.getSkills().get(SkillType.COMMON), hero, List.of(enemy));

        Event last = probe.last(KILL);
        Assertions.assertNotNull(last, "a kill must emit KillEvent");
        Assertions.assertEquals(enemy, last.victim());
        Assertions.assertEquals(hero, last.attacker());
    }

    /**
     * Additional damage finishing a target off **still emits** KillEvent (same convention as
     * kill energy gain: it does not look at `countsAsAttack`).
     *
     * <p>This also guards against a reverse mistake: if the implementation hung KillEvent
     * behind the "counts as an attack" gate, then additional damage / true damage / break
     * finishing a kill would all emit no event — and that is exactly the case Himeko's
     * "ultimate +5 energy per kill" kind of effect most needs to receive.
     */
    @Test
    public void additionalDamageKillStillFiresEvent() {
        Battle battle = newBattle();
        Character hero = battle.characters.getFirst();
        Enemy enemy = firstEnemy(battle);
        Probe probe = attachProbe(enemy);

        enemy.takeDamage(enemy.getCurrentHp() - 1);      // leave 1 HP

        // Additional damage: notCountsAsAttack, but attributed to the attacker → the kill is a fact
        battle.applyAdditionalDamage(hero, enemy, DamageElement.PHYSICAL, 1000);

        Assertions.assertEquals(0, enemy.getCurrentHp(), EPS, "precondition: the additional damage really finished it off");
        Assertions.assertNotNull(probe.last(KILL), "a kill by additional damage must emit KillEvent too");
    }

    /**
     * ⚠ **An acceptance criterion that was planned wrong**: the original plan required "DOT
     * **does not emit** `KillEvent`".
     *
     * <p>Measurement showed the **opposite**: `tickDots` goes through
     * `Battle.applyDamage(..., KILL_ONLY)`, the same settlement path as a basic attack, so DOT
     * killing someone **does emit** `KillEvent` — and that is **correct**: effects of Himeko's
     * "ultimate +5 energy per 1 enemy defeated" kind need to know that "a DOT finishing the
     * job also counts as a defeat", consistent with the kill-energy convention (`KILL_ONLY`
     * only affects the **energy-gain** category, not the fact of "death").
     *
     * <p>So here we assert the **measured behaviour**, and this correction is recorded in
     * `engine.md` §4.2 and `ROADMAP` P8-6.
     */
    @Test
    public void dotKillAlsoFiresKillEvent() {
        Battle battle = newBattle();
        Character hero = battle.characters.getFirst();
        Enemy enemy = firstEnemy(battle);
        Probe probe = attachProbe(enemy);

        enemy.takeDamage(enemy.getCurrentHp() - 1);      // leave 1 HP
        enemy.getBuffManager().addBuff(new DotBuff(hero, DamageElement.FIRE, 9999, 3));

        battle.tickDots(enemy);

        Assertions.assertTrue(enemy.isDeath(), "precondition: the DOT really did kill it");
        Assertions.assertNotNull(probe.last(KILL),
                "a DOT kill emits KillEvent too (same convention as kill energy gain; the original plan's 'does not emit' was wrong)");
    }

    /** True damage finishing a target off is the same: damage attributed to the attacker kills the target → emit `KillEvent`. */
    @Test
    public void trueDamageKillFiresEvent() {
        Battle battle = newBattle();
        Character hero = battle.characters.getFirst();
        Enemy enemy = firstEnemy(battle);
        Probe probe = attachProbe(enemy);

        enemy.takeDamage(enemy.getCurrentHp() - 1);      // leave 1 HP
        battle.applyTrueDamage(hero, enemy, DamageElement.PHYSICAL, 1000);

        Assertions.assertEquals(0, enemy.getCurrentHp(), EPS, "precondition: the true damage really finished it off");
        Assertions.assertNotNull(probe.last(KILL), "a kill by true damage must emit KillEvent too");
    }

    /** Give the enemy a huge shield → no HP lost → hence no kill (guards against "does not die before the shield breaks" being bypassed by events). */
    @Test
    public void shieldedTargetIsNotReportedAsKilled() {
        Battle battle = newBattle();
        Character hero = battle.characters.getFirst();
        Enemy enemy = firstEnemy(battle);
        Probe probe = attachProbe(enemy);

        battle.grantShield(enemy, 10_000_000);
        battle.castImmediate(hero.getSkills().get(SkillType.COMMON), hero, List.of(enemy));

        Assertions.assertFalse(enemy.isDeath(), "precondition: the shield held, it is not dead");
        Assertions.assertEquals(0, probe.count(KILL));
    }

    // ==================================================================
    // 6. Skill points: emitted only when really spent/gained
    // ==================================================================

    /** Basic attack → skill point +1 → emit `SkillPointEvent.onSkillPointGained`. */
    @Test
    public void skillPointGainFiresEvent() {
        Battle battle = newBattle();
        Character hero = battle.characters.getFirst();
        Probe probe = attachProbe(hero);

        Assertions.assertEquals(3, battle.getSkillPoints(), "precondition: 3 points at the start");
        actWithRealTurn(battle, hero, () -> hero.getSkills().get(SkillType.COMMON),
                () -> List.of(firstEnemy(battle)));

        Event last = probe.last(SP_GAINED);
        Assertions.assertNotNull(last, "a basic attack gaining a point must emit the event");
        Assertions.assertEquals(1, last.amount(), EPS);
    }

    /** Skill → skill point -1 → emit `SkillPointEvent.onSkillPointSpent`. */
    @Test
    public void skillPointSpendFiresEvent() {
        Battle battle = newBattle();
        Character hero = battle.characters.getFirst();
        Probe probe = attachProbe(hero);

        actWithRealTurn(battle, hero, () -> hero.getSkills().get(SkillType.SKILL),
                () -> List.of(firstEnemy(battle)));

        Event last = probe.last(SP_SPENT);
        Assertions.assertNotNull(last, "spending a skill point must emit the event");
        Assertions.assertEquals(1, last.amount(), EPS);
    }

    /**
     * ⚠ Core boundary: **not enough skill points, the action does not go through → do not emit
     * the spend event**.
     *
     * <p>Otherwise Misha's "for every 1 skill point our whole team consumes → next ultimate +1
     * hit" and Sparkle's "restore 1 extra energy when a skill point is consumed" would book a
     * consumption that **never happened at all**.
     */
    @Test
    public void failedSkillSpendFiresNoSkillPointSpent() {
        Battle battle = newBattle();
        Character hero = battle.characters.getFirst();
        Probe probe = attachProbe(hero);

        while (battle.spendSkillPoint()) {
            // drain to zero
        }
        Assertions.assertEquals(0, battle.getSkillPoints(), "precondition: 0 points");

        boolean acted = actWithoutAfterMove(battle, hero, () -> hero.getSkills().get(SkillType.SKILL),
                List.of(firstEnemy(battle)));

        Assertions.assertFalse(acted, "precondition: the action does not go through");
        Assertions.assertEquals(0, probe.count(SP_SPENT), "not spent → do not emit the spend event");
        Assertions.assertEquals(0, battle.getSkillPoints(), "the point count did not change either");
    }

    /** Basic attack while skill points are already full → actual credit 0 → do not emit the gain event. */
    @Test
    public void cappedSkillPointsFireNoGainEvent() {
        Battle battle = newBattle();
        Character hero = battle.characters.getFirst();
        Probe probe = attachProbe(hero);

        battle.gainSkillPoint(100);                       // push it to the cap of 5
        Assertions.assertEquals(5, battle.getSkillPoints(), "precondition: already full");

        actWithRealTurn(battle, hero, () -> hero.getSkills().get(SkillType.COMMON),
                () -> List.of(firstEnemy(battle)));

        Assertions.assertEquals(0, probe.count(SP_GAINED), "already full → actual credit 0 → no event emitted");
    }

    // ==================================================================
    // 7. Event ordering: which events should a plain "basic attack kills an enemy" emit, and in what order
    // ==================================================================

    /**
     * The actual order within one damage instance: **the cast comes before the energy gain**
     * (comparable on the same probe, because both are broadcast to that character).
     *
     * <p>⚠ Lesson: at first I also wanted to assert here that "HP loss comes before the kill",
     * but **a single probe cannot see both** — {@code SkillCastEvent}/{@code EnergyEvent} are
     * broadcast to **our side** (only received if the probe is attached to the character),
     * whereas {@code HpLossEvent}/{@code KillEvent} go to the **defender** (the probe must be
     * attached to the enemy). Forcing a comparison on one probe yields an assertion that is
     * always vacuously true (both are {@code MAX_VALUE}, and {@code MAX < MAX} is false → the
     * test goes red, but red for a reason that is not an engine bug). So it was split into two,
     * each testing its own thing.
     */
    @Test
    public void skillCastIsBroadcastBeforeEnergyGain() {
        Battle battle = newBattle();
        Character hero = battle.characters.getFirst();
        Probe probe = attachProbe(hero);

        actWithRealTurn(battle, hero, () -> hero.getSkills().get(SkillType.COMMON),
                () -> List.of(firstEnemy(battle)));

        int cast = probe.indexOf(SKILL_CAST);
        int energy = probe.indexOf(ENERGY);
        Assertions.assertTrue(cast != Integer.MAX_VALUE, "this character should receive SkillCastEvent");
        Assertions.assertTrue(energy != Integer.MAX_VALUE, "this character should receive EnergyEvent");
        Assertions.assertTrue(cast < energy, "the cast event comes before the energy gain");
    }

    /**
     * The defender's point of view: **HP loss first, then the kill**.
     *
     * <p>Both are broadcast to the defender + the killer, so attaching to the enemy lets us
     * see both at once. Use a low-HP enemy so this basic attack really does kill it.
     */
    @Test
    public void hpLossIsReportedBeforeKill() {
        Battle battle = newBattle();
        Character hero = battle.characters.getFirst();
        Enemy enemy = firstEnemy(battle);
        Probe probe = attachProbe(enemy);

        // Push HP down to 1: this basic attack is guaranteed to kill, so both events are produced in the same settlement
        enemy.takeDamage(enemy.getCurrentHp() - 1);
        Assertions.assertEquals(1, enemy.getCurrentHp(), EPS, "precondition: only 1 HP left");

        actWithRealTurn(battle, hero, () -> hero.getSkills().get(SkillType.COMMON),
                () -> List.of(firstEnemy(battle)));

        int loss = probe.indexOf(HP_LOSS);
        int kill = probe.indexOf(KILL);
        Assertions.assertTrue(loss != Integer.MAX_VALUE, "the defender should receive HpLossEvent");
        Assertions.assertTrue(kill != Integer.MAX_VALUE, "the defender should receive KillEvent");
        Assertions.assertTrue(loss < kill, "within one settlement: HP loss first, then the kill");
    }

    /**
     * Turn events are still expressed by the existing {@code MoveEvent} — do **not** create a
     * new event for "turn start".
     *
     * <p>This is pinned so that nobody later adds a `TurnStartEvent`: that would be a
     * duplicated abstraction.
     */
    @Test
    public void turnBoundariesAreStillMoveEvent() {
        Battle battle = newBattle();
        Character hero = battle.characters.getFirst();
        int[] beforeMoveCount = {0};
        int[] afterMoveCount = {0};
        hero.beforeMove = () -> beforeMoveCount[0]++;
        hero.afterMove = () -> afterMoveCount[0]++;

        actWithRealTurn(battle, hero, () -> hero.getSkills().get(SkillType.COMMON),
                () -> List.of(firstEnemy(battle)));
        battle.afterMove();

        Assertions.assertTrue(beforeMoveCount[0] >= 1, "MoveEvent.beforeMove covers turn start");
        Assertions.assertTrue(afterMoveCount[0] >= 1, "MoveEvent.afterMove covers turn end");
    }

    // ==================================================================
    // Helpers: the probe buff and battle setup
    // ==================================================================

    /** Event record: stores only the minimal fields the assertions need. */
    private record Event(String name, CanHit user, CanHit target, CanHit attacker, CanHit victim,
                         double before, double after, double amount,
                         List<? extends CanHit> hits, List<? extends CanHit> chosen) {
    }

    /**
     * A probe buff attached to the target: implements **all** 8 new events and records what it
     * receives into a list.
     *
     * <p>Deliberately attached to a **buff** rather than subclassing {@code CanHit}: this also
     * verifies that the forwarding chain "`CanHit` → `BuffManager` → buff" is connected — and
     * that is exactly the path character mechanics will actually use to subscribe to events.
     *
     * <p>⚠ The buff MUST **explicitly implement** every event interface: `BuffManager`
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

        /** Index of the first occurrence; {@code Integer.MAX_VALUE} if absent (convenient for comparing order). */
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

    /** Attach a probe to {@code target} and return it so it can be asserted on. */
    private static Probe attachProbe(CanHit target) {
        Probe probe = new Probe();
        probe.attachOn(target);
        return probe;
    }

    /** Default team: Himeko 1003 (Fire; basic attack and skill are both damaging). */
    private static Battle newBattle() {
        return newBattle(CharacterFactory.create(1003, 80));
    }

    private static Battle newBattle(Character hero) {
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(1_000_000));   // so one hit does not kill it
        enemy.heal(1_000_000);
        Battle battle = new Battle(List.of(hero), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    /** Bypass the queue and settle directly (verifying "the event itself" needs no action bar involvement). */
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
            if (battle.currentMove == null) {
                break;
            }
            if (battle.currentMove.getCanHit() == actor) {
                battle.beforeMove();
                return battle.performAction(skill.get(), targets);
            }
            battle.afterMove();
        }
        Assertions.fail("did not reach " + actor.getName() + "'s turn within 30 steps");
        return false;
    }
}
