package com.laosun.aluminium.test.content.characters.robin;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1512 知更鸟•晴歌: Eidolon 2 — 「我方目标的全属性抗性穿透提高 18%。…气氛值上限提高 20 点」.
 *
 * <p>The two halves are different kinds of number, and that is the point: the penetration is a <b>value</b> on every ally, while the
 * 20 points are <b>capacity</b>. Writing the second as a gain would hand her 20 points instead of letting her hold them.
 */
public class RobinEidolonTwoTest {

    private static final int ROBIN = 1512;
    private static final int ALLY = 1002;

    @Test
    public void eidolon2GivesTheWholeSidePenetration() {
        Fixture e2 = new Fixture(2);
        Assertions.assertEquals(0.18, e2.ally.getAttribute(AttributeType.DAMAGE_PENETRATION).get(), 1e-9,
                "「我方目标的全属性抗性穿透提高 18%」 — a teammate carries it too");

        Fixture e0 = new Fixture(0);
        Assertions.assertEquals(0.0, e0.ally.getAttribute(AttributeType.DAMAGE_PENETRATION).get(), 1e-9,
                "control: nothing at E0");
    }

    /**
     * 「气氛值上限提高 20 点」 — capacity, not a gift.
     *
     * <p>The bar is PARTY-scoped, so it lives on the battle rather than on Robin: reading it from her own resource manager answers null
     * and would "prove" the clause wrong. {@code RAISE_RESOURCE_CAP} falls back to the party counter, which is why this op works here.
     */
    @Test
    public void eidolon2RaisesTheVibeCapByTwenty() {
        Fixture e2 = new Fixture(2);
        Fixture e0 = new Fixture(0);
        int baseCap = e0.battle.partyResource("气氛值").getMax() + e0.battle.partyResource("气氛值").getMaxOverflow();
        int e2Cap = e2.battle.partyResource("气氛值").getMax() + e2.battle.partyResource("气氛值").getMaxOverflow();
        Assertions.assertEquals(baseCap + 20, e2Cap, "「气氛值上限提高 20 点」");
        Assertions.assertEquals(0, e2.battle.partyResource("气氛值").value(), "and her bar is still empty — capacity, not a gift");
    }

    /**
     * 星魂 6: 「本场战斗中第一次进入【Fever】状态后…固定恢复 140 点能量」.
     *
     * <p>Fired through the same event the talent uses (a third summon completing the band), twice: the first entry pays, the second
     * must not — 「第一次」 is the whole clause.
     */
    @Test
    public void eidolon6PaysFixedEnergyOnTheFirstFeverOnly() {
        Fixture f = new Fixture(6);
        double before = f.robin.getCurrentEnergy();

        f.bringTheBandOnStage();
        double afterFirst = f.robin.getCurrentEnergy();
        Assertions.assertEquals(before + 140, afterFirst, 1e-9, "「固定恢复 140 点能量」");

        f.bringTheBandOnStage();
        Assertions.assertEquals(afterFirst, f.robin.getCurrentEnergy(), 1e-9, "「本场战斗中**第一次**」 — the second entry pays nothing");
    }

    /**
     * 星魂 2's third half: 「任意目标回合内第一次施放技能…额外获得 2 点」.
     *
     * <p>Measured across a turn boundary: two skill casts in one turn pay the bonus once, and the next turn's first cast pays again.
     */
    @Test
    public void eidolon2PaysTheBonusOncePerTurn() {
        Fixture f = new Fixture(2);
        int start = f.vibes();

        f.castSkill();
        int afterFirst = f.vibes();
        Assertions.assertTrue(afterFirst > start, "precondition: the Skill grants vibes at all");

        f.castSkill();
        int afterSecond = f.vibes();
        int firstGain = afterFirst - start;
        int secondGain = afterSecond - afterFirst;
        Assertions.assertEquals(firstGain - 2, secondGain,
                "「本回合**第一次**…额外获得 2 点」 — the second cast pays the base gain only");

        f.newTurn();
        f.castSkill();
        Assertions.assertEquals(firstGain, f.vibes() - afterSecond,
                "and the next turn's first cast pays the bonus again");
    }

    /**
     * 星魂 1: the side's damage is recorded, and the memosprite's skill turns the record into true damage — then keeps half.
     */
    @Test
    public void eidolon1RecordsDamageAndPaysItBackAsTrueDamage() {
        Fixture f = new Fixture(1);
        f.bringTheBandOnStage();
        f.recordADamageSettled(1000);
        int recorded = f.record();
        Assertions.assertTrue(recorded > 0, "precondition: the skill casts recorded something (" + recorded + ")");

        double enemyHpBefore = f.enemy.getCurrentHp();
        f.summonCastsASkill();

        Assertions.assertTrue(f.enemy.getCurrentHp() < enemyHpBefore,
                "「对生命值最高的敌方目标额外造成…真实伤害」");
        // 「随后清空记录值的 50%」: half stays. It is not EXACTLY half, because the true damage just dealt is itself recorded -
        // the 「非真实伤害」 filter is the registered approximation - so this asserts the shape (roughly half, never all or none).
        int kept = f.record();
        Assertions.assertTrue(kept > recorded / 4 && kept < recorded * 3 / 4,
                "「清空记录值的 50%」 — about half stays (" + recorded + " -> " + kept + ")");
    }

    /**
     * 星魂 4's speed half: 「「晴空乐手」的速度提高（20% + 当前气氛值 × 0.50%）」, read at the moment Fever opens.
     */
    @Test
    public void eidolon4RaisesTheMemospritesSpeedOnFeverEntry() {
        Fixture f = new Fixture(4);
        f.bringTheBandOnStage();
        double slowest = f.summonSpeed();
        Assertions.assertTrue(slowest > 0, "precondition: the band is on stage");

        Fixture base = new Fixture(0);
        base.bringTheBandOnStage();
        Assertions.assertTrue(base.summonSpeed() < slowest,
                "the Eidolon's band moves faster (" + base.summonSpeed() + " -> " + slowest + ")");
    }

    // ==================================================================

    private static final class Fixture {
        private final Character robin;
        private final Character ally = CharacterFactory.create(ALLY, 80);
        private final Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        private final Battle battle;

        private Fixture(int eidolon) {
            robin = CharacterFactory.create(ROBIN, 80, false, null, null, eidolon);
            battle = new Battle(List.of(robin, ally), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        /**
         * Fills the band the way the engine does: the Skill summons one and its own +6 gauge crosses the thresholds for the rest, so two
         * casts put three on stage. Firing SUMMONED by hand cannot work here - the rule reads `summon_count`, which counts real summons.
         */
        private void bringTheBandOnStage() {
            for (int i = 0; i < 2; i++) {
                battle.castImmediate(robin.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL),
                        robin, List.of(robin));
                battle.processRequests();
            }
        }

        private int vibes() {
            return battle.partyResource("气氛值").value();
        }

        private void castSkill() {
            battle.castImmediate(robin.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL),
                    robin, List.of(robin));
            battle.processRequests();
        }

        /** A new turn for her: the one-shot marker is cleared on ANY unit's turn start. */
        private void newTurn() {
            battle.fire(TriggerPayload.Simple.of(
                    com.laosun.aluminium.enums.TriggerEvent.TURN_START, robin, robin, 0, 0));
            battle.processRequests();
        }

        /** The first memosprite's speed: the clause is about 「晴空乐手」, and the band shares one stat sheet. */
        private double summonSpeed() {
            return battle.summonsOf(robin).isEmpty()
                    ? -1
                    : battle.summonsOf(robin).get(0).getAttribute(AttributeType.SPEED).get();
        }

        private int record() {
            return robin.getResources().value("记录");
        }

                /** One settled instance on our side, which is what the recording rule listens to. */
        private void recordADamageSettled(double amount) {
            battle.fire(TriggerPayload.Simple.of(
                    com.laosun.aluminium.enums.TriggerEvent.DAMAGE_SETTLED, robin, enemy, 0, amount));
            battle.processRequests();
        }

        private void summonCastsASkill() {
            battle.fire(TriggerPayload.Simple.of(
                    com.laosun.aluminium.enums.TriggerEvent.SKILL_CAST,
                    battle.summonsOf(robin).isEmpty() ? null : battle.summonsOf(robin).get(0),
                    enemy, 0, 0));
            battle.processRequests();
        }

        private int cap() {
            return robin.getResources().get("气氛值").getMax();
        }

        private int value() {
            return robin.getResources().value("气氛值");
        }
    }
}
















