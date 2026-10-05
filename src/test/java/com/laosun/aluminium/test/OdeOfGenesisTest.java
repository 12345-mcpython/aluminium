package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1415's memosprite skill 10 「献予「创世」之诗」 (data slot 13, SkillID 1141513) -- the half the capabilities now support (2026-10-02).
 *
 * <p>「整场生效，<b>对开拓者•记忆施放时，使开拓者•记忆的攻击力提高，提高数值等同于德谬歌生命上限的 #1%</b>，同时使其暴击率提高，提高数值等同于<b>德谬歌暴击率</b>的 #2%。
 * <b>该效果对迷迷也生效。</b>」
 *
 * <p>⭐ Both factors are read where the engine reads them: the row of the CASTER'S CURRENT LEVEL (never row 1 by hand) and the ACTOR's own
 * attributes (`actor_attr:`) -- 德谬歌 is the caster, and the recipient's own memosprite is 迷迷, a different unit entirely.
 */
public class OdeOfGenesisTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int TRAILBLAZER = 8007;   // 开拓者•记忆
    private static final int MONSTER = 1002011;
    private static final int ODE_OF_GENESIS = 13;

    /** Attack and crit both rise by the ACTOR's share, on the recipient. */
    @Test
    public void theRecipientGainsBothBoosts() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character recipient = CharacterFactory.create(TRAILBLAZER, LEVEL);
        Battle battle = new Battle(List.of(cyrene, recipient),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Summon demiurge = battle.summonServant(cyrene);
        battle.processRequests();
        Skill ode = demiurge.skillAt(ODE_OF_GENESIS);
        Assertions.assertNotNull(ode, "precondition: the memosprite carries slot 13");

        var used = ode.getData().getSkills().get(demiurge.skillLevel(ode) - 1);
        double attackBefore = recipient.getAttribute(AttributeType.ATTACK).get();
        double critBefore = recipient.getAttribute(AttributeType.CRIT_CHANCE).get();

        SkillExecutor.execute(battle, ode, demiurge, List.of(recipient));
        battle.processRequests();

        double attackGain = recipient.getAttribute(AttributeType.ATTACK).get() - attackBefore;
        double critGain = recipient.getAttribute(AttributeType.CRIT_CHANCE).get() - critBefore;
        double expectedAttack = used.get(0) * demiurge.getMaxHp();
        double expectedCrit = used.get(1) * demiurge.getAttribute(AttributeType.CRIT_CHANCE).get();

        System.out.println("[genesis] the row used = " + used + " ; demiurge Max HP = " + demiurge.getMaxHp()
                + " ; its crit = " + demiurge.getAttribute(AttributeType.CRIT_CHANCE).get()
                + "\n           attack: expected " + expectedAttack + " got " + attackGain
                + "\n           crit:   expected " + expectedCrit + " got " + critGain);

        Assertions.assertEquals(expectedAttack, attackGain, Math.abs(expectedAttack) * 1e-6,
                "「攻击力提高，数值等同于德谬歌生命上限的 #1%」");
        Assertions.assertEquals(expectedCrit, critGain, Math.abs(expectedCrit) * 1e-6,
                "「暴击率提高，数值等同于德谬歌暴击率的 #2%」");
    }

    /** 「该效果对迷迷也生效」 -- the same two effects reach the recipient's own memosprite. */
    @Test
    public void theRecipientsOwnMemospriteIsAlsoBoosted() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character recipient = CharacterFactory.create(TRAILBLAZER, LEVEL);
        Battle battle = new Battle(List.of(cyrene, recipient),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Summon demiurge = battle.summonServant(cyrene);
        Summon mimi = battle.summonServant(recipient);
        battle.processRequests();
        Assumptions.assumeTrue(mimi != null, "this character's own memosprite must be fieldable for this clause to be judged");

        var used = demiurge.skillAt(ODE_OF_GENESIS).getData().getSkills()
                .get(demiurge.skillLevel(demiurge.skillAt(ODE_OF_GENESIS)) - 1);
        double before = mimi.getAttribute(AttributeType.ATTACK).get();
        SkillExecutor.execute(battle, demiurge.skillAt(ODE_OF_GENESIS), demiurge, List.of(recipient));
        battle.processRequests();
        double gain = mimi.getAttribute(AttributeType.ATTACK).get() - before;
        double expected = used.get(0) * demiurge.getMaxHp();

        System.out.println("[genesis] 迷迷 (" + mimi.getName() + ") attack gain = " + gain
                + " ; expected " + expected);
        Assertions.assertEquals(expected, gain, Math.abs(expected) * 1e-6,
                "「该效果对迷迷也生效」-- aimed at `summon`, the spelling 1402 and 1413 already use");
    }
}
