package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
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
 * `actor_attr:<ATTRIBUTE>`: a magnitude that is a share of the ACTOR's own attribute (2026-10-02).
 *
 * <p>Reader: 1415's memosprite skill 10 「献予「创世」之诗」 -- 「<b>对开拓者•记忆施放时</b>，使开拓者•记忆的攻击力提高，提高数值等同于<b>德谬歌生命上限</b>的
 * #1%」. Read that carefully: the rule belongs to 开拓者•记忆, but the share is of <b>德谬歌</b> -- the unit doing the casting. Neither
 * `self_attr:` (the owner) nor `summon_attr:` (the owner's memosprite, which for 8007 is 迷迷) names that unit; the actor does.
 *
 * <p>⭐ So this judge is deliberately THREE-way: the gain must equal the actor's share, and must not equal the owner's -- which is the exact
 * mistake the two existing spellings would have made.
 */
public class ActorAttrScaleTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int RECIPIENT = 8007;   // 开拓者•记忆, whose own memosprite is 迷迷, NOT 德谬歌
    private static final int MONSTER = 1002011;
    private static final int ODE_OF_ROMANCE = 14;
    private static final double SHARE = 0.1;

    /** The share is of the CASTER, not of the rule owner. */
    @Test
    public void theShareIsOfTheActorNotTheOwner() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character recipient = CharacterFactory.create(RECIPIENT, LEVEL);
        Battle battle = new Battle(List.of(cyrene, recipient),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Summon demiurge = battle.summonServant(cyrene);
        battle.processRequests();
        Skill ode = demiurge.skillAt(ODE_OF_ROMANCE);
        Assertions.assertNotNull(ode, "precondition: the memosprite carries slot 14");

        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "ATTACK");
        TriggerSpecs.set(effect, "scale", "actor_attr:HEALTH");
        TriggerSpecs.set(effect, "percent", SHARE);
        TriggerSpecs.set(effect, "permanent", Boolean.TRUE);
        TriggerSpecs.set(effect, "target", "self");
        recipient.setTriggerTable(new TriggerTable(RECIPIENT, List.of(
                TriggerSpecs.rule("CAST_SETUP", List.of("actor is_summon"), effect))));

        double before = recipient.getAttribute(AttributeType.ATTACK).get();
        SkillExecutor.execute(battle, ode, demiurge, List.of(recipient));
        battle.processRequests();
        double gained = recipient.getAttribute(AttributeType.ATTACK).get() - before;

        double fromActor = SHARE * demiurge.getMaxHp();
        double fromOwner = SHARE * recipient.getMaxHp();
        System.out.println("[actor_attr] the actor 德谬歌 Max HP = " + demiurge.getMaxHp()
                + " ; the owner Max HP = " + recipient.getMaxHp() + " ; the gain = " + gained
                + " ; 10% of the actor = " + fromActor + " ; 10% of the owner = " + fromOwner);

        Assertions.assertEquals(fromActor, gained, Math.abs(fromActor) * 1e-6,
                "「提高数值等同于**德谬歌**生命上限的 #1%」-- the ACTOR is the unit the share is of");
        Assertions.assertNotEquals(fromOwner, gained, Math.abs(fromActor) * 1e-6,
                "and the rule owner is a different unit here, so the two readings are distinguishable");
    }
}
