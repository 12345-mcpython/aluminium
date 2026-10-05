package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1408: "变身期间...施放攻击后回复等同于自身生命上限 20% 的生命值" (while transformed, casting an attack restores HP equal to 20% of her own Max HP).
 *
 * <p>TWO-WAY, and the control is the SAME fight without the transformation. She is hurt first through the battle's own damage
 * entry point, so a heal has something to restore.
 */
public class TransformationHealsOnAttackTest {
    private static final int OWNER = 1408;
    private static final int MONSTER = 1002011;
    private static final String STATE = "变身";

    /** Transformed, an attack heals her for a fifth of her (raised) Max HP. */
    @Test
    public void anAttackHealsTheTransformedHer() {
        double healed = healFromOneAttack(true);
        Assertions.assertEquals(0.20, healed, 1e-6,
                "「回复等同于自身生命上限 20% 的生命值」 (share of Max HP)");
    }

    /** Note: Without the transformation the sentence has not started, so no attack heals her. */
    @Test
    public void withoutTheTransformationNothingIsHealed() {
        Assertions.assertEquals(0.0, healFromOneAttack(false), 1e-9,
                "「变身期间」 (while transformed)-- no transformation, no heal");
    }

    // ==================================================================

    /** the heal as a SHARE of Max HP, so the size of the pool does not matter. */
    private static double healFromOneAttack(boolean transform) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        if (transform) {
            Skill ult = owner.getSkills().get(SkillType.ULTRA);
            Assertions.assertNotNull(ult, "precondition: she has an ultimate");
            SkillExecutor.execute(battle, ult, owner, List.of(owner));
            battle.processRequests();
        }
        Assertions.assertEquals(transform, owner.getBuffManager().hasState(STATE),
                "precondition: the transformation is " + (transform ? "on" : "off"));

        // Note: HALF OF CURRENT HP, never half of MAX HP: the transformation raises Max HP (to 3.x) WITHOUT filling the pool, so
        // "50% of Max HP" is lethal here -- and a dead unit heals nothing, which is exactly how the first three attempts
        // measured a healthy 0.0 and looked like a broken HEAL. Measured: hurt=0.0 of 5312.8 after that hit.
        battle.applyTrueDamage(battle.enemies.get(0), owner, DamageElement.ICE, owner.getCurrentHp() * 0.5);
        battle.processRequests();
        double hurt = owner.getCurrentHp();
        double maxHp = owner.getMaxHp();
        Assertions.assertTrue(hurt < maxHp, "precondition: she was really hurt (" + hurt + " of " + maxHp + ")");

        // Note: Her BASIC attack, not her skill: the document says Khaslana (卡厄斯兰那) "拥有 1 个强化普攻和 2 个强化战技" (has 1 enhanced basic attack and 2 enhanced Skills), so the transformed
        // form attacks with the basic slot.
        Skill skill = owner.getSkills().get(SkillType.COMMON);
        Assertions.assertNotNull(skill, "precondition: she has a basic attack");
        SkillExecutor.execute(battle, skill, owner, List.of(battle.enemies.get(0)));
        battle.processRequests();
        return (owner.getCurrentHp() - hurt) / maxHp;
    }
}
