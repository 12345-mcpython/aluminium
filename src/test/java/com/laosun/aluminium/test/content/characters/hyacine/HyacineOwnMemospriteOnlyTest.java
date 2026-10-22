package com.laosun.aluminium.test.content.characters.hyacine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.buff.StateBuff;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1409 风堇's four "小伊卡 did something" clauses, asked the one question a <b>one-memosprite</b> battle cannot ask:
 * does the gate mean <b>my</b> memosprite, or <b>a</b> memosprite?
 *
 * <h2>Why this exists</h2>
 * Every clause was written with {@code actor is_summon}, which reads "the actor is a memosprite" -- true for
 * <em>anyone's</em> memosprite. A judge that fields one memosprite per battle cannot tell the two apart, so the whole
 * suite was green while the gate was wrong. The whole-battle replay ({@code AGENT.md} §3.7zb) fielded four
 * memsprites at once and made it visible: 1413's 「长夜」 casting its own data slot 1 made <b>风堇</b> deal an
 * {@code ADDITIONAL} instance to every enemy and clear her 累计治疗 tally.
 *
 * <p>The spelling that says "mine" is {@code actor == summon}: {@code TriggerConditionParser.Equality} compares the
 * actor's identity against the owner's summons ({@code summon.getMaster() == ctx.owner()}), and it keeps answering
 * true for a <b>dead</b> one, which is what {@code SUMMON_LEFT} needs.
 *
 * <p>Three readings, one per clause, each with 风堇's own memosprite on the field as the control:
 * <ol>
 *   <li>the tally's damage and its 50% clear do <b>not</b> run for another character's memosprite;</li>
 *   <li>that memosprite leaving does <b>not</b> advance 风堇 and does <b>not</b> take back her Rank 6 aura;</li>
 *   <li>that memosprite acting does <b>not</b> shorten 小伊卡's own timed effects.</li>
 * </ol>
 */
public class HyacineOwnMemospriteOnlyTest {

    private static final int HYACINE = 1409;
    private static final int EVERNIGHT = 1413;
    private static final int WOUNDED_ALLY = 1001;
    private static final String TALLY = "小伊卡累计治疗";
    private static final double EPS = 1e-9;

    /**
     * 「对敌方全体造成等同于本场战斗风堇与小伊卡累计治疗数值 #1%」: another character's memosprite casting ITS slot 1
     * must not be counted as 小伊卡's cast.
     */
    @Test
    public void anotherCharactersMemospriteCastingSlotOneIsNotHerOwn() {
        Fixture f = new Fixture(0);
        double tallyBefore = f.tally();
        Assertions.assertTrue(tallyBefore > 0, "precondition: the heal fed the tally");

        List<Damage> byIka = f.castOtherMemospriteSlotOne();

        Assertions.assertTrue(byIka.isEmpty(),
                "the damage is 「小伊卡对敌方全体造成…」 -- 长夜 casting its own slot 1 is not 小伊卡's cast, but "
                        + byIka.size() + " instance(s) were attributed to 小伊卡");
        Assertions.assertEquals(tallyBefore, f.tally(), EPS,
                "and 「清空累计治疗数值的 50%」 is 小伊卡's cast's consequence, not anyone's: tally was "
                        + tallyBefore + ", now " + f.tally());
    }

    /**
     * The same clause at Rank 6, where 「清空改为 12%」 is a <b>twin rule</b> (`min_eidolon: 6`) - so this gate has to
     * be right in two places, and a judge that only fields E0 would let the twin drift back.
     */
    @Test
    public void anotherCharactersMemospriteCastingSlotOneDoesNotClearTwelvePercentEither() {
        Fixture f = new Fixture(6);
        double tallyBefore = f.tally();
        Assertions.assertTrue(tallyBefore > 0, "precondition: the heal fed the tally");

        f.castOtherMemospriteSlotOne();

        Assertions.assertEquals(tallyBefore, f.tally(), EPS,
                "Stars 6: 「清空累计治疗数值改为 12%」 is still 小伊卡's cast's consequence (tally was " + tallyBefore
                        + ", now " + f.tally() + ")");
    }

    /**
     * 「消失时，使风堇行动提前 30%」 and the Rank 6 aura's 「小伊卡在场时」: another character's memosprite leaving is
     * not 小伊卡 leaving.
     */
    @Test
    public void anotherCharactersMemospriteLeavingIsNotHerOwn() {
        Fixture f = new Fixture(6);
        double waitBefore = f.remainingWaitOf(f.hyacine);
        double penBefore = f.hyacine.getAttribute(AttributeType.DAMAGE_PENETRATION).get();
        Assertions.assertEquals(0.2, penBefore, EPS, "precondition: the Rank 6 aura is up while 小伊卡 is out");

        f.longNight.perish();
        f.battle.processRequests();

        Assertions.assertEquals(waitBefore, f.remainingWaitOf(f.hyacine), Math.max(EPS, waitBefore * EPS),
                "长夜 leaving is not 小伊卡 leaving, so 风堇's action must not move (was " + waitBefore + ", now "
                        + f.remainingWaitOf(f.hyacine) + ")");
        Assertions.assertEquals(penBefore, f.hyacine.getAttribute(AttributeType.DAMAGE_PENETRATION).get(), EPS,
                "and the aura 「小伊卡在场时」 must stay up: 小伊卡 is still there");
    }

    /**
     * 「小伊卡施放技能后使自身所有持续效果持续回合数减 1」: another character's memosprite acting must not shorten
     * 小伊卡's clock.
     */
    @Test
    public void anotherCharactersMemospriteActingDoesNotShortenItsOwnEffects() {
        Fixture f = new Fixture(0);
        f.ika.getBuffManager().addBuff(new StateBuff("测试", 3));
        Assertions.assertEquals(3, f.turnsOf(f.ika, "测试"), "precondition: three turns to start with");

        f.castOtherMemospriteSlotOne();

        Assertions.assertEquals(3, f.turnsOf(f.ika, "测试"),
                "「小伊卡施放技能后」 means 小伊卡's own cast -- 长夜 acting must not take a turn off her state");
    }

    /** 风堇 + 1413 + a wounded ally, both memsprites out, one heal already counted into the tally. */
    private static final class Fixture {
        final Battle battle;
        final Character hyacine;
        final Character evernight;
        final Summon ika;
        final Summon longNight;

        Fixture(int eidolon) {
            this.hyacine = CharacterFactory.create(HYACINE, 80, true, null, null, eidolon);
            this.evernight = CharacterFactory.create(EVERNIGHT, 80);
            Character ally = CharacterFactory.create(WOUNDED_ALLY, 80);
            this.battle = new Battle(List.of(hyacine, evernight, ally), List.of(BattleFixture.dummy()),
                    BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
            // Both memsprites are stated by the fixture, not left to a kit rule: hers is summoned by her own skill /
            // ultimate, and the point here is the CONTROL (her own memosprite standing beside someone else's).
            battle.summonMemosprite(hyacine);
            battle.processRequests();
            this.ika = (Summon) battle.memospriteOf(hyacine);
            Summon other = (Summon) battle.memospriteOf(evernight);
            if (other == null) {
                // 1413's own rule summons it at battle start; a fixture that states its own premise must not depend on
                // that (and `summonMemosprite` is idempotent, so this is a no-op when the rule did run).
                other = (Summon) battle.summonMemosprite(evernight);
                battle.processRequests();
            }
            this.longNight = other;
            Assertions.assertNotNull(ika, "precondition: 小伊卡 is out");
            Assertions.assertNotNull(longNight, "precondition: 长夜 is out -- the 'other character's memosprite'");
            ally.consumeHp(ally.getAttribute(AttributeType.HEALTH).get() * 0.5);
            SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.SKILL), hyacine, List.of(ally));
            battle.processRequests();
        }

        double tally() {
            return hyacine.getResources().value(TALLY);
        }

        /** Has the OTHER character's memosprite cast its own data slot 1, and reports what was attributed to 小伊卡. */
        List<Damage> castOtherMemospriteSlotOne() {
            Skill slotOne = longNight.skillAt(1);
            Assertions.assertNotNull(slotOne, "precondition: 长夜 carries data slot 1");
            List<Damage> byIka = new ArrayList<>();
            battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> {
                if (damage.getAttacker() == ika) {
                    byIka.add(damage);
                }
            });
            SkillExecutor.execute(battle, slotOne, longNight, List.of(battle.enemies().getFirst()));
            battle.processRequests();
            return byIka;
        }

        int turnsOf(CanHit unit, String state) {
            return unit.getBuffManager().allBuffsOf(StateBuff.class).stream()
                    .filter(buff -> state.equals(buff.getState()))
                    .mapToInt(StateBuff::remainingTurns)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("the state 「" + state + "」 is gone"));
        }

        /** The unit's remaining wait, read off the action bar's own snapshot. */
        double remainingWaitOf(CanHit unit) {
            for (var signal : battle.queue().snapshot()) {
                if (signal.getCanHit() == unit) {
                    return battle.queue().getTimeRemaining(signal);
                }
            }
            throw new AssertionError(unit.getName() + " is not on the action bar");
        }
    }
}
