package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1415's memosprite skill 12 "献予'浪漫'之诗" -- the two effects whose lifetime is another state.
 *
 * <p>"阿格莱雅与衣匠造成的伤害提高 #2[i]% 并无视目标 #3[i]% 的防御，<b>持续至阿格莱雅退出[至高之姿]状态</b>。"
 *
 * <p>Three readings: both boosts land (including on the Garmentmaker, which the sentence names explicitly), and both come off again when the
 * stance leaves her. The last one is the sentence's own lifetime, spelled as a companion rule on `STATE_ENDED` with `"kind": "own"` -- the source
 * filter, because removing by name alone also took a `ALL_DAMAGE_TYPE_BOOST` she already carried with it (measured).
 */
public class OdeOfRomanceStanceTest2 {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int AGLAEA = 1402;
    private static final int MONSTER = 1002011;
    private static final int ODE_OF_ROMANCE = 14;
    private static final String STANCE = "至高之姿";

    @Test
    public void bothBoostsLandAndBothLeaveWithTheStance() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        Battle battle = new Battle(List.of(cyrene, aglaea),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Summon demiurge = battle.summonServant(cyrene);
        Summon garmentmaker = battle.summonServant(aglaea);
        battle.processRequests();
        Assertions.assertNotNull(garmentmaker, "the sentence names the Garmentmaker, so it must be on the field");

        Skill ode = demiurge.skillAt(ODE_OF_ROMANCE);
        Assertions.assertNotNull(ode, "precondition: the memosprite carries slot 14");
        var used = ode.getData().getSkills().get(demiurge.skillLevel(ode) - 1);
        double expectedBoost = used.get(1);
        double expectedPierce = used.get(2);

        double pierceBefore = aglaea.getAttribute(AttributeType.DEFENCE_IGNORE).get();
        double servantBoostBefore = garmentmaker.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        double servantPierceBefore = garmentmaker.getAttribute(AttributeType.DEFENCE_IGNORE).get();

        SkillExecutor.execute(battle, ode, demiurge, List.of(aglaea));
        battle.processRequests();

        double pierceGain = aglaea.getAttribute(AttributeType.DEFENCE_IGNORE).get() - pierceBefore;
        double servantBoostGain = garmentmaker.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - servantBoostBefore;
        double servantPierceGain = garmentmaker.getAttribute(AttributeType.DEFENCE_IGNORE).get() - servantPierceBefore;
        System.out.println("[stance2] hers pierce +" + pierceGain + " (=" + expectedPierce + ")"
                + " ; the Garmentmaker damage +" + servantBoostGain + " (=" + expectedBoost + ")"
                + " pierce +" + servantPierceGain + " (=" + expectedPierce + ")");

        Assertions.assertEquals(expectedPierce, pierceGain, Math.abs(expectedPierce) * 1e-6,
                "「并无视目标 #3% 的防御」-- hers");
        Assertions.assertEquals(expectedBoost, servantBoostGain, Math.abs(expectedBoost) * 1e-6,
                "「阿格莱雅**与衣匠**造成的伤害提高 #2%」-- the Garmentmaker is named too");
        Assertions.assertEquals(expectedPierce, servantPierceGain, Math.abs(expectedPierce) * 1e-6,
                "and it gets the pierce as well");

        // the lifetime: the stance leaves her, and both copies come off
        battle.fireStateEnded(aglaea, STANCE);
        battle.processRequests();
        System.out.println("[stance2] after the stance ended: the Garmentmaker damage = "
                + garmentmaker.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() + " (was " + servantBoostBefore + " before the ode)"
                + " ; its pierce = " + garmentmaker.getAttribute(AttributeType.DEFENCE_IGNORE).get()
                + " ; hers pierce = " + aglaea.getAttribute(AttributeType.DEFENCE_IGNORE).get());

        Assertions.assertEquals(servantBoostBefore, garmentmaker.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), 1e-6,
                "「持续至阿格莱雅退出【" + STANCE + "】状态」-- the Garmentmaker is back where it started");
        Assertions.assertEquals(servantPierceBefore, garmentmaker.getAttribute(AttributeType.DEFENCE_IGNORE).get(), 1e-6,
                "and its pierce is gone");
        Assertions.assertTrue(aglaea.getAttribute(AttributeType.DEFENCE_IGNORE).get() < pierceBefore + expectedPierce,
                "and hers is gone too");
    }
}
