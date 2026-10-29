package com.laosun.aluminium.test.content.characters.robinsummeretto;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 「「晴空乐手」全员登台」 - 1512's 【Fever】 gate, and the two gauge thresholds that bring the band on.
 *
 * <p><b>What the band actually is.</b> Not three different memosprites: <b>several instances of the same one</b>. The
 * recorded fight settles it - the vulnerability aura's number matches <b>two</b> of them, and the servant's own row prices
 * 1 / 2 / 3 members separately. The talent's params state the two thresholds (6 and 12), and 「全员登台」 is therefore a
 * fact about the <b>count</b>.
 */
public class RobinBandOnStageTest {

    private static final int ROBIN = 1512;
    private static final int ALLY = 1001;
    private static final String VIBES = "气氛值";
    private static final String FEVER = "Fever";

    private record Scene(Battle battle, Character robin) {
    }

    private static Scene scene() {
        Character robin = CharacterFactory.create(ROBIN, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(robin, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return new Scene(battle, robin);
    }

    private static void setGauge(Scene scene, int points) {
        scene.battle.partyResource(VIBES).setValue(points);
        scene.battle.fire(new com.laosun.aluminium.trigger.TriggerPayload.ResourceChanged(
                com.laosun.aluminium.enums.TriggerEvent.RESOURCE_CHANGED, scene.robin, VIBES, 1));
        scene.battle.processRequests();
    }

    /**
     * The band grows one instance at each threshold — and the skill's own +6 gauge is what crosses the first one, so a
     * single cast leaves TWO on stage (measured: 0 -> 2). A second cast pushes the gauge to 12 and the band to three.
     */
    @Test
    public void theGaugeBringsTheBandOnOneAtATime() {
        Scene scene = scene();
        Assertions.assertEquals(0, scene.battle.summonCountOf(scene.robin), "nothing is out before the skill");

        castSkill(scene);
        Assertions.assertEquals(2, scene.battle.summonCountOf(scene.robin),
                "the skill summons the first, and its own +6 gauge crosses the 6 threshold for the second");

        castSkill(scene);
        Assertions.assertEquals(3, scene.battle.summonCountOf(scene.robin),
                "the second cast's gauge reaches 12 and brings the third");

        castSkill(scene);
        Assertions.assertEquals(3, scene.battle.summonCountOf(scene.robin),
                "and neither threshold fires twice: the band is three");
    }

    /**
     * 「全员登台」 opens 【Fever】: it is off at two and on at three.
     */
    @Test
    public void feverOpensOnlyWithTheWholeBand() {
        Scene scene = scene();
        castSkill(scene);
        Assertions.assertFalse(scene.robin.getBuffManager().hasState(FEVER),
                "two of three is not 全员登台");

        castSkill(scene);
        Assertions.assertTrue(scene.robin.getBuffManager().hasState(FEVER),
                "all three on stage ⇒ 【Fever】");
    }

    private static void castSkill(Scene scene) {
        SkillExecutor.execute(scene.battle, scene.robin.getSkills().get(SkillType.SKILL), scene.robin,
                List.of(scene.battle.enemies().getFirst()));
        scene.battle.processRequests();
    }
}