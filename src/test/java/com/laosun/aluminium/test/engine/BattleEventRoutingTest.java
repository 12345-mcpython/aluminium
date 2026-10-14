package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.buff.AbstractBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.event.KillEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The routing rules of the battle's event-announcement surface.
 *
 * <p>These lived as eight one-line private methods inside a 3800-line {@code Battle}, each carrying a routing
 * decision that nothing stated in one place; they now have a file whose whole subject is routing
 * ({@link com.laosun.aluminium.BattleEvents}). This pins the rules that file documents, because they are cheap to
 * break and silent when broken:
 *
 * <ul>
 *   <li><b>The involved parties are told</b> -- including the victim of a kill, which is the party nobody would
 *       guess is on the list.</li>
 *   <li><b>The whole party is told</b>, because our members subscribe to what happens to each other.</li>
 *   <li><b>Exactly once each.</b> A unit that is both an involved party and a party member must not hear it twice:
 *       the same buff called twice is a doubled effect, and that is what the de-duplication exists to prevent.</li>
 *   <li><b>The enemy camp is not told.</b> "We forgot them" and "we deliberately exclude them" look identical from
 *       outside, so the exclusion is asserted rather than assumed.</li>
 * </ul>
 *
 * <p>⚠ The recorder is a <b>buff</b>, not a unit: the engine delivers these hooks to buffs
 * ({@code BuffManager.onKill} walks its buffs), which is the only way a test can observe them. Everything it does
 * is record, so attaching it cannot change the battle.
 */
public class BattleEventRoutingTest {

    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /**
     * A kill reaches the killer, the victim, and every living party member -- once each.
     */
    @Test
    public void aKillReachesTheInvolvedPartiesAndTheWholePartyOnceEach() {
        Character a = CharacterFactory.create(1003, LEVEL);
        Character b = CharacterFactory.create(1001, LEVEL);
        Enemy victim = EnemyFactory.create(MONSTER, LEVEL, 1);

        Recorder onKiller = attach(a);
        Recorder onBystander = attach(b);
        Recorder onVictim = attach(victim);

        Battle battle = new Battle(List.of(a, b), List.of(victim), BattleFixture.alwaysRoll(1.0));
        battle.startBattle();
        battle.applyDamage(victim, new Damage(a, victim, DamageElement.FIRE, DamageType.NORMAL,
                victim.getMaxHp() * 10));

        Assertions.assertTrue(victim.isDeath(), "precondition: the blow was lethal");
        Assertions.assertEquals(1, onKiller.kills, "the killer is an involved party");
        Assertions.assertEquals(1, onVictim.kills,
                "and so is the victim -- it is the subject of the event, which is the least obvious entry");
        Assertions.assertEquals(1, onBystander.kills,
                "a teammate that neither attacked nor was attacked still hears about the kill");
    }

    /**
     * A unit that is both the actor and a party member is told once, not twice.
     *
     * <p>Without the de-duplication the killer -- reached as an involved party <i>and</i> again as a member of
     * {@code allies} -- would run its kill reaction twice, and any buff keyed on it would double.
     */
    @Test
    public void anInvolvedPartyIsNotToldTwiceAsAPartyMember() {
        Character a = CharacterFactory.create(1003, LEVEL);
        Character b = CharacterFactory.create(1001, LEVEL);
        Enemy victim = EnemyFactory.create(MONSTER, LEVEL, 1);

        Recorder onKiller = attach(a);

        Battle battle = new Battle(List.of(a, b), List.of(victim), BattleFixture.alwaysRoll(1.0));
        battle.startBattle();
        battle.applyDamage(victim, new Damage(a, victim, DamageElement.FIRE, DamageType.NORMAL,
                victim.getMaxHp() * 10));

        Assertions.assertEquals(1, onKiller.kills,
                "the killer is reached as an involved party AND as a party member; told exactly once, or its kill "
                        + "reaction runs twice");
    }

    /**
     * The full-party pass tells OUR side only -- but an enemy that was itself involved still hears it.
     *
     * <p>⚠ This is the rule I first got wrong, and the code was right: {@code dispatch} announces to the involved
     * parties <b>regardless of camp</b>, and then to our side. So the monster that landed the killing blow is told
     * (it is part of what happened, and a boss mechanic or a counter has to be able to react to it), while an enemy
     * that merely stood there is not -- it has no rules table and no buff that reads events.
     */
    @Test
    public void onlyAnInvolvedEnemyHearsTheEvent() {
        Character a = CharacterFactory.create(1003, LEVEL);
        Character b = CharacterFactory.create(1001, LEVEL);
        Enemy attacker = EnemyFactory.create(MONSTER, LEVEL, 1);
        Enemy bystanderEnemy = EnemyFactory.create(MONSTER, LEVEL, 1);

        Recorder onAttacker = attach(attacker);
        Recorder onEnemyBystander = attach(bystanderEnemy);
        Recorder onVictim = attach(b);

        Battle battle = new Battle(List.of(a, b), List.of(attacker, bystanderEnemy), BattleFixture.alwaysRoll(1.0));
        battle.startBattle();
        battle.applyDamage(b, new Damage(attacker, b, DamageElement.PHYSICAL, DamageType.NORMAL,
                b.getMaxHp() * 10));

        Assertions.assertTrue(b.getCurrentHp() <= 0, "precondition: our unit was killed");
        Assertions.assertEquals(1, onVictim.kills, "our own unit is the event's subject, so it is told");
        Assertions.assertEquals(1, onAttacker.kills,
                "the enemy that did the killing is an involved party, so it IS told -- camp does not exclude it");
        Assertions.assertEquals(0, onEnemyBystander.kills,
                "but an uninvolved enemy is not on the list: the whole-side pass is our side only");
    }

    /**
     * Attaches a kill recorder to a unit as a buff.
     *
     * @param unit the unit to observe
     * @return the recorder, for reading afterwards
     */
    private static Recorder attach(CanHit unit) {
        Recorder recorder = new Recorder();
        unit.getBuffManager().addBuff(recorder);
        return recorder;
    }

    /**
     * A buff that counts the kill announcements it receives.
     *
     * <p>It has to name {@link KillEvent} itself: {@code AbstractBuff} implements only the attack and skill-cast
     * events, and {@code BuffManager.onKill} delivers to whichever of its buffs {@code instanceof KillEvent}. The
     * empty overrides below are the rest of the price of being a buff, and keeping them empty is the point -- a
     * recorder must be inert apart from the one hook it reads, or attaching it would change the battle it observes.
     */
    private static final class Recorder extends AbstractBuff implements KillEvent {

        private int kills;

        private Recorder() {
            super(99, false);           // long enough not to tick away during a case
        }

        @Override
        public void onKill(Battle battle, CanHit attacker, CanHit victim) {
            kills++;
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
}
