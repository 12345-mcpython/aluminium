package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.WaveManager;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1415's memosprite skill 8, the fifth clause: "若施放前目标被消灭则对<b>新入场</b>的敌方目标施放" (2026-10-02).
 *
 * <p>What tbgd says the clause IS, read out of `GlobalModifiers` in `Servant_CyreneServant_00_Ability.json`:
 * `MServant_CyreneServant_00_AmazingBuff_Mydeimos_OnWaveMonster` listens for <b>`OnWaveMonster`</b> and answers with
 * `TurnInsertAction{TargetType: ModifierOwnerEntity, AutoCast: true}` -- when a wave monster enters, <b>万敌 himself acts again</b>. The
 * victims are the skill's own business, which is exactly what our `CAST_SKILL` already does.
 *
 * <p>So the clause is two facts, and both are content now: a durable mark the ode puts on him, and the restrike when a wave arrives.
 * Note: The English text's "the target gets defeated" is NOT what that data states -- the game's predicate is `ByTargetAliveState` on the
 * MODIFIER OWNER -- so that half is registered rather than invented.
 */
public class OdeWaveRestrikeTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int MYDEI = 1404;
    private static final int MONSTER = 1002011;
    private static final int ODE_TO_STRIFE = 16;
    private static final int GODSLAYER = 11;
    private static final int MULTI_WAVE_STAGE = 310030;
    private static final String MARK = "献予「纷争」之诗";

    /** The ode marks him; a wave monster entering then makes him strike again. */
    @Test
    public void aWaveMonsterEnteringMakesHimStrikeAgain() {
        Assumptions.assumeFalse(Constant.stages().isEmpty(), "stage.json has not been generated");
        var stage = Constant.stages().get(MULTI_WAVE_STAGE);
        Assertions.assertNotNull(stage, "310030 is a multi-wave stage");

        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character mydei = CharacterFactory.create(MYDEI, LEVEL);
        Battle battle = new Battle(List.of(cyrene, mydei), new ArrayList<>(), new Random(0));
        new WaveManager(battle, stage);

        // the ode reaches him: the mark goes on
        Summon demiurge = battle.summonServant(cyrene);
        battle.processRequests();
        Skill ode = demiurge.skillAt(ODE_TO_STRIFE);
        Assertions.assertNotNull(ode, "precondition: the memosprite carries its own skill 16");
        SkillExecutor.execute(battle, ode, demiurge, List.of(mydei));
        battle.processRequests();
        Assertions.assertTrue(mydei.getBuffManager().hasState(MARK),
                "「对万敌施放时」-- the ode leaves its mark on him");

        int before = mydei.getSkills().get(SkillType.SKILL).getSkillSlot();
        boolean entered = battle.getWaveManager().nextWave();
        Assertions.assertTrue(entered, "310030 has a wave to enter");
        int after = mydei.getSkills().get(SkillType.SKILL).getSkillSlot();
        System.out.println("[ode] wave monsters = " + battle.waveMonsters().size() + " ; his SKILL slot " + before + " -> " + after);

        Assertions.assertEquals(GODSLAYER, after,
                "「对**新入场**的敌方目标施放」-- a wave monster entering makes him restrike");
    }

    /** Without the ode's mark, a wave changes nothing -- the gate is the mark, not the wave. */
    @Test
    public void withoutTheMarkAWaveChangesNothing() {
        Assumptions.assumeFalse(Constant.stages().isEmpty(), "stage.json has not been generated");
        var stage = Constant.stages().get(MULTI_WAVE_STAGE);

        Character mydei = CharacterFactory.create(MYDEI, LEVEL);
        Battle battle = new Battle(List.of(mydei, CharacterFactory.create(1002, LEVEL)), new ArrayList<>(), new Random(0));
        new WaveManager(battle, stage);
        Assertions.assertFalse(mydei.getBuffManager().hasState(MARK), "precondition: no ode has reached him");

        int before = mydei.getSkills().get(SkillType.SKILL).getSkillSlot();
        battle.getWaveManager().nextWave();
        int after = mydei.getSkills().get(SkillType.SKILL).getSkillSlot();
        System.out.println("[ode] without the mark: his SKILL slot " + before + " -> " + after);

        Assertions.assertEquals(before, after,
                "「对万敌施放时」-- the clause is about the ode reaching him, not about any wave");
    }
}
