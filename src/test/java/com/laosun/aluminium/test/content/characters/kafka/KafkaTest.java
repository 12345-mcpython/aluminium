package com.laosun.aluminium.test.content.characters.kafka;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Signal;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.buff.DotBuff;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1005 卡芙卡, base kit: the ultimate's Shock, the talent's follow-up, the talent's DoT-taken amplification, the Skill's
 * instant DoT settlement, and the trace that lengthens Shock.
 *
 * <p><b>Ultimate.</b> Both halves are capabilities the engine already had - `APPLY_DOT` (with the per-turn magnitude) and
 * `TICK_DOT` (round 105) - so this is also a check that they compose in one rule. The target is hand-made and unresisting,
 * because a 100% BASE chance still rolls (round 131).
 *
 * <p><b>Talent follow-up.</b> 「当卡芙卡的队友对敌方目标施放普攻后，卡芙卡立即发动追加攻击…并有 100% 的基础概率使受到攻击的敌方目标
 * 陷入与终结技相同的dot:shock状态，持续 2 回合，该效果每回合只能触发 1 次」. The rule shipped, but nothing judged it (the status
 * row still lists it as unwritten). This test is the missing judge, and it pins the three words the rule is built on -
 * <b>teammate</b> (not herself), <b>basic attack</b> (not a skill), and <b>once per turn</b>. Kafka's follow-ups are counted
 * with a one-line marker rule on {@code FOLLOW_UP}, because a follow-up is not visible in any attribute.
 *
 * <p><b>Talent, damage taken.</b> On a follow-up, the target takes 30% more DoT damage for two turns. Two assertions that a
 * magnitude or scope mutation cannot survive: the compiled rule's own scope and share, and the measured ratio between DoT and
 * normal damage.
 *
 * <p><b>Skill.</b> 「若指定敌方单体处于持续伤害状态，其当前承受的<b>所有</b>持续伤害立即产生相当于原伤害 75% 的伤害」. {@code TICK_DOT}
 * with no element settles EVERY DoT state on the target. ⚠ The target must survive the tick: the first version of this judge
 * used the standard monster, which DIED to one tick (its HP is smaller than a 75% DoT tick), so the second state's settlement
 * returned zero and the clause looked broken. {@link BattleFixture#dummy()} has 1,000,000 HP.
 *
 * <p><b>Trace.</b> Shock, and the trace that lengthens it by one turn. The monster is chosen to SURVIVE the whole window, and
 * that is the instrument's real requirement: with a 3-HP monster two DOTs kill it on turn three whatever their durations are,
 * so a real one-turn extension cannot move the count - which is what made this clause look unverifiable for several rounds.
 * The highest-HP monster in the probe range has ~990k. A whole turn is `beforeMove()` plus `afterMove()` (the countdown is
 * split into an early and a late pass), and the count is the number of the victim's turns that cost it HP, which for two DOTs
 * of different lengths is the LONGER one.
 *
 * <p><b>L3.</b> The single-clause cases that read one fixture and one clause are folded into the scenario tests below, one
 * {@code Assertions.assertAll} per fixture, so a failing clause still names itself: the talent's once-per-turn follow-up
 * together with her own turn that re-arms it (one {@code FollowUpFixture}), and the talent's +30% DoT-taken judged twice,
 * at the compiled rule and on the measured ratio (one unit and one monster). The cases whose fixtures differ on the very
 * dimension they measure keep their own tests, and nothing was dropped: every expected value, tolerance and message is
 * still asserted.
 */
public class KafkaTest {
    private static final int KAFKA = 1005;
    private static final int LEVEL = 80;
    private static final int ALLY = 1002;
    // The trace judge's synthetic applier: 1210, a different ally id from the ALLY above.
    private static final int ALLY_TRACE = 1210;
    private static final int MONSTER = 1002064;
    private static final int HER_DOT_TURNS = 2;
    private static final int HORIZON = 10;
    private static final String SHOCK = "dot:shock";
    private static final String COUNTER = "追打次数";

    /**
     * Note: The Shock lands with its own per-turn damage, and the same sentence makes it settle once more.
     */
    @Test
    public void herUltimateAppliesShockAndMakesItSettleOnce() {
        Fixture f = new Fixture();
        double before = f.enemy.getCurrentHp();

        f.battle.castImmediate(f.kafka.getSkills().get(SkillType.ULTRA), f.kafka, List.of(f.enemy));

        Assertions.assertTrue(f.enemy.getBuffManager().hasState("dot:shock"),
                "「有100%的基础概率使受到攻击的敌方目标陷入dot:shock状态」 (a 100% base chance to put the attacked enemy target into the Shock (dot:shock) state)");
        Assertions.assertFalse(f.enemy.getBuffManager().allBuffsOf(DotBuff.class).isEmpty(), "…as a damage-over-time state");
        Assertions.assertTrue(f.enemy.getCurrentHp() < before,
                "the ultimate's own damage plus the extra instance 「立即产生相当于原伤害 100% 的伤害」 (immediately deals damage equal to 100% of the original damage)");
    }


    /**
     * Note: An ALLY's basic attack makes Kafka strike again; her own must not.
     */
    @Test
    public void anAllysBasicAttackTriggersHerFollowUp() {
        Character kafka = CharacterFactory.create(KAFKA, LEVEL);
        Character ally = CharacterFactory.create(1002, LEVEL);
        Enemy enemy = Enemy.fromAttributes("Test Dummy", 200000, 100, 100, 90);
        Battle battle = new Battle(List.of(kafka, ally), List.of(enemy), new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        });
        battle.startBattle();
        double before = enemy.getCurrentHp();
        battle.castImmediate(ally.getSkills().get(SkillType.COMMON), ally, List.of(enemy));
        double afterAlly = enemy.getCurrentHp();

        Assertions.assertTrue(afterAlly < before,
                "the ally's basic attack lands and Kafka's follow-up adds to it: " + before + " -> " + afterAlly);
        Assertions.assertTrue(enemy.getBuffManager().hasState("dot:shock"),
                "「并有100%的基础概率使受到攻击的敌方目标陷入与终结技相同的dot:shock状态」 (and a 100% base chance to put the attacked enemy target into the same Shock state as the Ultimate) — the follow-up really fired");
    }

    /**
     * Note: Her OWN basic attack must not grant the follow-up: observable is the Shock it would apply.
     */
    @Test
    public void herOwnBasicAttackDoesNotTriggerTheFollowUp() {
        Fixture f = new Fixture();
        Assertions.assertFalse(f.enemy.getBuffManager().hasState("dot:shock"), "precondition: no Shock");

        f.battle.castImmediate(f.kafka.getSkills().get(SkillType.COMMON), f.kafka, List.of(f.enemy));

        Assertions.assertFalse(f.enemy.getBuffManager().hasState("dot:shock"),
                "「当卡芙卡的**队友**对敌方目标施放普攻后」 (after Kafka's **ally** casts a Basic ATK on an enemy target) -- HER OWN attack is not an ally's, so no follow-up and no Shock");
    }

    /**
     * An ally's basic attack makes Kafka follow up and shocks the target; a second one in the same turn does not, and the
     * limit is hers: after her turn ends, the next ally basic triggers again.
     */
    @Test
    public void anAllyBasicTriggersOneFollowUpPerTurnAndHerTurnRefreshesIt() {
        FollowUpFixture f = new FollowUpFixture();
        f.allyBasic();
        int afterFirstBasic = f.followUps();
        boolean shocked = f.enemy.getBuffManager().hasState(SHOCK);

        f.allyBasic();
        int afterSecondBasic = f.followUps();
        f.kafkaTurn();
        f.allyBasic();
        int afterHerTurn = f.followUps();

        Assertions.assertAll("「该效果每回合只能触发 1 次」, and the turn that re-arms it is hers",
                () -> Assertions.assertEquals(1, afterFirstBasic, "one follow-up"),
                () -> Assertions.assertTrue(shocked, "「陷入与终结技相同的dot:shock状态」"),
                () -> Assertions.assertEquals(1, afterSecondBasic, "「该效果每回合只能触发 1 次」"),
                () -> Assertions.assertEquals(1, afterFirstBasic, "still one before her own turn"),
                () -> Assertions.assertEquals(2, afterHerTurn, "a new turn: the follow-up is available again"));
    }

    /**
     * A skill (not a basic) and Kafka's own basic attack do not trigger it.
     */
    @Test
    public void aSkillOrKafkasOwnAttackDoesNot() {
        FollowUpFixture skill = new FollowUpFixture();
        skill.allySkill();
        Assertions.assertEquals(0, skill.followUps(), "「队友…施放普攻后」 - a skill is not a basic attack");

        FollowUpFixture own = new FollowUpFixture();
        own.kafkaBasic();
        Assertions.assertEquals(0, own.followUps(), "her own attack is not a teammate's");
    }

    /**
     * The talent's +30% DoT-taken, judged twice on one board: the compiled rule's own scope and share, and the measured
     * ratio between DoT and normal damage on the same unit and monster.
     */
    @Test
    public void theTalentRaisesDotDamageByThirtyPercent() {
        Character unit = CharacterFactory.create(KAFKA, LEVEL, true, null, null, 1);   // the +30% DoT-taken is Eidolon 1
        Enemy enemy = BattleFixture.monster();
        var rules = unit.getTriggerTable().matching(TriggerEvent.FOLLOW_UP,
                new TriggerTable.TriggerContext(unit, unit, enemy, 0, 0));
        List<String> scopes = new ArrayList<>();
        double share = -1;
        for (var rule : rules) {
            for (var effect : rule.effects()) {
                if ("MODIFY_DAMAGE_TAKEN".equals(effect.getOp())) {
                    scopes.add(effect.getDamageType());
                    share = effect.getPercent();
                    System.out.println("[1005] rule id=" + rule.id() + " damageType=" + effect.getDamageType()
                            + " percent=" + share + " turns=" + effect.getTurns());
                }
            }
        }

        Battle battle = new Battle(List.of(unit), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        double normal = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.THUNDER, DamageType.NORMAL, 1000));
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.FOLLOW_UP, unit, enemy, 0, 0));
        double dot = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.THUNDER, DamageType.DOT, 1000));
        System.out.println("[1005] normal=" + normal + " dot=" + dot + " ratio=" + (dot / normal));

        double ruleShare = share;
        Assertions.assertAll("the talent's +30% DoT-taken: the compiled rule and the measured ratio",
                () -> scopes.forEach(scope -> Assertions.assertEquals("DOT", scope, "the scope is the DoT type")),
                () -> Assertions.assertEquals(0.3, ruleShare, 1e-9, "the talent states 30%"),
                () -> Assertions.assertEquals(1.3, dot / normal, 1e-6, "DoT damage rises by exactly the authored 30%"));
    }

    @Test
    public void everyDotStateSettlesItsSeventyFivePercent() {
        double none = tickDamage(0);
        double one = tickDamage(1);
        double two = tickDamage(2);
        double perState = one - none;
        System.out.println("[1005] skill instant damage: 0 DoT " + none + ", 1 " + one + ", 2 " + two
                + " ; one state pays " + perState);
        Assertions.assertTrue(perState > 0, "precondition: the planted DoT settles");
        Assertions.assertEquals(perState, two - one, perState * 1e-6,
                "「其当前承受的**所有**持续伤害」: the second state pays exactly what the first did");
    }

    /** Damage the Skill settles on the main target with {@code dots} DoT states planted. */
    private static double tickDamage(int dots) {
        Character kafka = CharacterFactory.create(KAFKA, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.dummy();
        Battle battle = new Battle(new ArrayList<>(List.of(kafka, ally)),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        DamageElement[] elements = {DamageElement.FIRE, DamageElement.ICE};
        for (int i = 0; i < dots; i++) {
            enemy.getBuffManager().addBuff(new DotBuff(ally, elements[i], 20_000, 3, 0, i == 0 ? "dot:burn" : "ctrl:frozen伤"));
        }
        Assertions.assertFalse(enemy.isDeath(), "precondition: the target survives");
        double before = enemy.getCurrentHp();
        battle.castImmediate(kafka.getSkills().get(SkillType.SKILL), kafka, List.of(enemy));
        battle.processRequests();
        Assertions.assertFalse(enemy.isDeath(), "the target must survive the tick, or the second state would settle nothing");
        return before - enemy.getCurrentHp();
    }
    /**
     * The enhanced form's second half: 「或相邻目标…立即产生相当于原伤害 50% 的伤害」. The selector is
     * {@code other_hit_enemies} - the hit set MINUS the main target - so the main target must NOT be paid twice.
     */
    @Test
    public void theNeighboursPayFiftyAndTheMainTargetIsNotPaidTwice() {
        Character kafka = CharacterFactory.create(KAFKA, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy main = BattleFixture.dummy();
        Enemy neighbour = BattleFixture.dummy();
        Battle battle = new Battle(new ArrayList<>(List.of(kafka, ally)),
                new ArrayList<>(List.of(neighbour, main)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        neighbour.getBuffManager().addBuff(new DotBuff(ally, DamageElement.FIRE, 20_000, 3, 0, "dot:burn"));
        main.getBuffManager().addBuff(new DotBuff(ally, DamageElement.ICE, 20_000, 3, 0, "ctrl:frozen伤"));
        double mainBefore = main.getCurrentHp();
        double neighbourBefore = neighbour.getCurrentHp();
        battle.castImmediate(kafka.getSkills().get(SkillType.SKILL), kafka, List.of(main));
        battle.processRequests();
        double mainLost = mainBefore - main.getCurrentHp();
        double neighbourLost = neighbourBefore - neighbour.getCurrentHp();
        System.out.println("[1005] main lost " + mainLost + ", neighbour lost " + neighbourLost);
        Assertions.assertTrue(neighbourLost > 0, "「或相邻目标…产生 50% 的伤害」: the neighbour's DoT ticks too");
        Assertions.assertTrue(mainLost > neighbourLost,
                "and the main target takes its 75% tick ONCE - a second 50% would make it larger than 75%");
    }

    @Test
    public void anExtendedDotBurnsForOneMoreTurn() {
        Assertions.assertEquals(HER_DOT_TURNS, syntheticBurningTurns(HER_DOT_TURNS, 0),
                "a plain synthetic DOT burns for its own length");
        Assertions.assertEquals(HER_DOT_TURNS + 1, syntheticBurningTurns(HER_DOT_TURNS, 1),
                "and one turn of extension adds exactly one");
    }

    @Test
    public void herOwnUltimateBurnsOneLongerThanAnUnlengthenedDot() {
        Assertions.assertEquals(syntheticBurningTurns(HER_DOT_TURNS, 1), herBurningTurns(),
                "the Shock (dot:shock) state lasts 1 turn longer -- two turns plus the trace's one");
    }

    private static int herBurningTurns() {
        Character kafka = CharacterFactory.create(KAFKA, LEVEL);
        Character ally = CharacterFactory.create(ALLY_TRACE, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(kafka, ally), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        battle.castImmediate(kafka.getSkills().get(SkillType.ULTRA), kafka, List.of(enemy));
        return burningTurns(battle, enemy);
    }

    private static int syntheticBurningTurns(int dotTurns, int extension) {
        Character applier = CharacterFactory.create(ALLY_TRACE, LEVEL);
        EffectSpec dot = new EffectSpec();
        TriggerSpecs.set(dot, "op", "APPLY_DOT");
        TriggerSpecs.set(dot, "element", "Thunder");
        TriggerSpecs.set(dot, "amount", 100.0);
        TriggerSpecs.set(dot, "turns", dotTurns);
        TriggerSpecs.set(dot, "target", "target");
        List<EffectSpec> effects = new ArrayList<>();
        effects.add(dot);
        if (extension > 0) {
            effects.add(extension(extension));
        }
        applier.setTriggerTable(new TriggerTable(9105, List.of(TriggerSpecs.rule(
                TriggerEvent.ALLY_ATTACK.name(), List.of(), effects.toArray(new EffectSpec[0])))));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(applier), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        // ALLY_ATTACK, not BATTLE_START: a battle start has no aimed party, so `target: target` is refused there.
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, applier, enemy, 1, 0));
        return burningTurns(battle, enemy);
    }

    private static int burningTurns(Battle battle, Enemy enemy) {
        int burning = 0;
        for (int turn = 0; turn < HORIZON && !enemy.isDeath(); turn++) {
            double before = enemy.getCurrentHp();
            battle.setCurrentMove(new Signal(enemy));
            battle.beforeMove();
            battle.afterMove();
            if (enemy.getCurrentHp() < before) {
                burning++;
            }
        }
        return burning;
    }

    private static EffectSpec extension(int turns) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "EXTEND_BUFF");
        TriggerSpecs.set(effect, "buff", "dot:shock");
        TriggerSpecs.set(effect, "turns", turns);
        TriggerSpecs.set(effect, "target", "target");
        return effect;
    }

    private static final class Fixture {
        private final Character kafka = CharacterFactory.create(KAFKA, LEVEL);
        private final Enemy enemy = Enemy.fromAttributes("Test Dummy", 20000, 100, 100, 90);
        private final Battle battle = new Battle(List.of(kafka), List.of(enemy), new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        });

        private Fixture() {
            battle.startBattle();
        }
    }

    private static final class FollowUpFixture {
        private final Character kafka;
        private final Character ally;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private FollowUpFixture() {
            kafka = CharacterFactory.create(KAFKA, LEVEL);
            ally = CharacterFactory.create(ALLY, LEVEL);
            // A marker on FOLLOW_UP so the number of her follow-ups is countable.
            EffectSpec effect = new EffectSpec();
            TriggerSpecs.set(effect, "op", "ADD_STACK");
            TriggerSpecs.set(effect, "buff", COUNTER);
            TriggerSpecs.set(effect, "amount", 1.0);
            TriggerSpecs.set(effect, "maxStacks", 10);
            TriggerSpecs.set(effect, "permanent", Boolean.TRUE);
            TriggerSpecs.set(effect, "target", "self");
            TriggerSpec marker = TriggerSpecs.rule("FOLLOW_UP", List.of("actor == self"), effect);
            TriggerSpecs.set(marker, "id", "witness_kafka_followup_counter");
            kafka.setTriggerTable(kafka.getTriggerTable().plus(new TriggerTable(KAFKA, List.of(marker))));

            battle = new Battle(new ArrayList<>(List.of(kafka, ally)),
                    new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
            battle.startBattle();
        }

        private void allyBasic() {
            battle.castImmediate(ally.getSkills().get(SkillType.COMMON), ally, List.of(enemy));
        }

        private void allySkill() {
            battle.castImmediate(ally.getSkills().get(SkillType.SKILL), ally, List.of(enemy));
        }

        private void kafkaBasic() {
            battle.castImmediate(kafka.getSkills().get(SkillType.COMMON), kafka, List.of(enemy));
        }

        /** Kafka's own turn boundary (the per-turn limiter counts her turns). */
        private void kafkaTurn() {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_END, kafka, null, 0, 0));
            kafka.tickTriggerCooldowns();   // the per-turn window opens on HER turn start, which the routine ticks
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, kafka, kafka, 0, 0));
        }

        private int followUps() {
            return kafka.getBuffManager().stacksOf(COUNTER);
        }
    }
}
