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

        private int cap() {
            return robin.getResources().get("气氛值").getMax();
        }

        private int value() {
            return robin.getResources().value("气氛值");
        }
    }
}









