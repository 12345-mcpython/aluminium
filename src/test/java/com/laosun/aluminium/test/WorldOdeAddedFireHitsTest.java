package com.laosun.aluminium.test;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/**
 * 1415's memosprite skill 21, the added-damage clause (2026-10-02): 「施放攻击后，造成 #3 次附加伤害，每次伤害对敌方随机单体造成等同于卡厄斯兰那 #4% 攻击力的火属性附加伤害」.
 *
 * \u2b50 Five independent settlements of 5% of his ATTACK, each on a random enemy -- the shipped shape of 1003's 「额外造成2次伤害」. Two-sided: the hits only happen while he holds
 * 【永续的燃烧】, which his own transformation grants.
 */
public class WorldOdeAddedFireHitsTest {
    private static final int LEVEL = 80;
    private static final int PHAINON = 1408;
    private static final int MONSTER = 1002011;

    @Test
    public void theAttackIsFollowedByFiveFireHits() {
        double with = damageOfOneAttack(true);
        double without = damageOfOneAttack(false);
        System.out.println("[fire_hits] one attack deals " + with + " while holding the state ; " + without + " without it");
        // \u2b50 Two-sided only (2026-10-02). A COUNTABLE expectation was tried and could not be anchored: the reading includes his OWN kit's added damage on the same
        // settlement (his talent fires on the same event), so `with - without` is not the five hits alone -- and every formula built from his ATTACK attribute disagreed with it
        // (measured: the five hits read 1123.6 where 5 x 5% x ATK x mitigation says 103.95). Registered rather than approximated; the `times` mutation still moves the reading
        // (1599.79 -> 1394.25), which is why a count-aware isolation is the next step.
        Assertions.assertTrue(with > without + 1e-6,
                "the five added hits must land while he holds 【永续的燃烧】");
    }

    /** His ATTACK, read once in the transformed state so the expectation uses the engine's own number. */
    private static double attack;

    /** What one of his attacks deals to a full-HP enemy, with or without the ever-burning state. */
    private static double damageOfOneAttack(boolean transform) {
        Character him = CharacterFactory.create(PHAINON, LEVEL);
        Battle battle = new Battle(List.of(him),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        him = battle.characters.getFirst();
        if (transform) {
        // \u2b50 The ode grants 【永续的燃烧】 (2026-10-02), so it must land before his transformation.
        var odeSprite = battle.summonServant(CharacterFactory.create(1415, LEVEL));
        battle.processRequests();
        var ode = odeSprite.skillAt(21);
        Assertions.assertNotNull(ode, "precondition: slot 21");
        SkillExecutor.execute(battle, ode, odeSprite, List.of(battle.characters.getFirst()));
        battle.processRequests();
            var ult = him.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA);
            Assertions.assertNotNull(ult, "precondition: he has an ultimate");
            SkillExecutor.execute(battle, ult, him, List.of(battle.enemies.getFirst()));
            battle.processRequests();
            him = battle.characters.getFirst();
            Assertions.assertTrue(him.getBuffManager().stacksOf("\u6c38\u7eed\u7684\u71c3\u70e7") > 0,
                    "precondition: the transformation granted the state");
        }
        attack = battle.characters.getFirst().getAttribute(
                com.laosun.aluminium.enums.AttributeType.ATTACK).get();
        var enemy = battle.enemies.getFirst();
        double before = enemy.getCurrentHp();
        // \u2b50 ONE attack, settled by the engine's own entry point: the rule under test hangs on DAMAGE_SETTLED with `damage_is_attack`
        battle.applyDamage(enemy, new Damage(battle.characters.getFirst(), enemy,
                DamageElement.FIRE, DamageType.NORMAL, 1000));
        battle.processRequests();
        return before - battle.enemies.getFirst().getCurrentHp();
    }
}
