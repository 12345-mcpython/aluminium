package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * `summon_attr:<ATTRIBUTE>`: a magnitude that is a share of the RULE OWNER'S MEMOSPRITE (2026-10-02).
 *
 * <p>Reader: 1415's memosprite skill 10 「献予「创世」之诗」 -- 「使开拓者•记忆的攻击力提高，提高数值等同于<b>德谬歌生命上限</b>的 #1%，同时使其暴击率
 * 提高，提高数值等同于<b>德谬歌暴击率</b>的 #2%」. The existing family names the owner's own attributes (`self_attr:`), so a share of the
 * MEMOSPRITE's had no spelling.
 *
 * <p>⭐ The reading is a comparison of two numbers, both readable: the modifier's value and `0.1 × the memosprite's Max HP`. A mutant that
 * reads the OWNER's Max HP instead gives a different number, which is what makes this discriminating.
 */
public class SummonAttrScaleTest {
    private static final int LEVEL = 80;
    /** 1402 Aglaea: the panel of her memosprite is HEALTH 0.66 + flat 720, so its Max HP DIFFERS from hers. */
    private static final int CYRENE = 1402;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final double SHARE = 0.1;

    /** The owner's ATK rises by a share of the MEMOSPRITE's Max HP -- not by a share of her own. */
    @Test
    public void theMagnitudeIsAShareOfTheMemosprite() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(cyrene, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Summon demiurge = battle.summonServant(cyrene);
        battle.processRequests();
        Assertions.assertNotNull(demiurge, "precondition: the memosprite is on the field");

        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "ATTACK");
        TriggerSpecs.set(effect, "scale", "summon_attr:HEALTH");
        TriggerSpecs.set(effect, "percent", SHARE);
        TriggerSpecs.set(effect, "permanent", Boolean.TRUE);
        TriggerSpecs.set(effect, "target", "self");
        cyrene.setTriggerTable(new TriggerTable(CYRENE, List.of(
                TriggerSpecs.rule("SKILL_CAST", List.of("actor == self"), effect))));

        double before = cyrene.getAttribute(AttributeType.ATTACK).get();
        Skill skill = cyrene.getSkills().get(SkillType.SKILL);
        SkillExecutor.execute(battle, skill, cyrene, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        double gained = cyrene.getAttribute(AttributeType.ATTACK).get() - before;

        double fromSummon = SHARE * demiurge.getMaxHp();
        double fromOwner = SHARE * cyrene.getMaxHp();
        System.out.println("[summon_attr] the memosprite's Max HP = " + demiurge.getMaxHp()
                + " ; hers = " + cyrene.getMaxHp() + " ; the gain = " + gained
                + " ; 10% of the memosprite = " + fromSummon + " ; 10% of her own = " + fromOwner);

        Assertions.assertEquals(fromSummon, gained, fromSummon * 1e-6,
                "「提高数值等同于**德谬歌生命上限**的 #1%」-- the MEMOSPRITE's, not the owner's");
        Assertions.assertNotEquals(fromOwner, gained, fromSummon * 1e-6,
                "and the Max HP of the summoner differs from the memosprite here, so the reading is not a coincidence");
    }
}
