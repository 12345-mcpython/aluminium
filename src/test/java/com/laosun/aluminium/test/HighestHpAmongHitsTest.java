package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
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
 * `highest_hp_attack_hit`: "被攻击目标中当前生命值最高的目标" (2026-10-02).
 *
 * <p>Reader: 1403 缇宝's ultimate, whose zone rider picks that unit, and 1415's ode of passage, which names the rider. It is the sibling of
 * `random_hit_enemy`: the same hit pool, a different pick -- the highest 当前生命值 instead of a roll.
 *
 * <p>Note: The first attempt read `ctx.attackHitTargets()` alone and answered "empty" on `DEALING_DAMAGE`, which is the event this selector is FOR: that
 * field belongs to `ATTACK_FINISHED`, and a per-hit context only has its instance's snapshot. The sibling's own comment says so, and reading BOTH is
 * what fixed it. This judge therefore goes through a real skill cast, the only way such a snapshot exists.
 */
public class HighestHpAmongHitsTest {
    private static final int LEVEL = 80;
    private static final int OWNER = 1402;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;

    private static EffectSpec speedBoost() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "SPEED");
        TriggerSpecs.set(effect, "percent", 0.5);
        TriggerSpecs.set(effect, "permanent", Boolean.TRUE);
        TriggerSpecs.set(effect, "target", "highest_hp_attack_hit");
        return effect;
    }

    /** A real cast: exactly one of the units the attack connected with is picked, and it is one of them. */
    @Test
    public void oneOfTheUnitsTheAttackHitIsPicked() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        owner = battle.characters.getFirst();
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(
                TriggerSpecs.rule("DEALING_DAMAGE", List.of(), speedBoost()))));

        double[] before = battle.enemies.stream().mapToDouble(e -> e.getAttribute(AttributeType.SPEED).get()).toArray();
        Skill skill = owner.getSkills().values().stream()
                .filter(s -> s != null && s.getData() != null && s.getData().getEffect().isDamaging())
                .findFirst().orElse(null);
        Assertions.assertNotNull(skill, "precondition: the owner has a damaging skill");
        SkillExecutor.execute(battle, skill, owner, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        double[] after = battle.enemies.stream().mapToDouble(e -> e.getAttribute(AttributeType.SPEED).get()).toArray();

        int raised = 0;
        for (int i = 0; i < after.length; i++) {
            if (after[i] > before[i]) {
                raised++;
            }
        }
        System.out.println("[highest_hit] after a real cast, units of the hit set whose speed rose = " + raised);
        Assertions.assertEquals(1, raised,
                "「被攻击目标中当前生命值最高的目标」-- ONE of the units the attack hit");
    }

    /** With no instance at all there is no hit set, and the selector says so rather than aiming at nobody. */
    @Test
    public void anEmptyHitSetIsRefusedLoudly() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Battle battle = new Battle(List.of(owner, CharacterFactory.create(ALLY, LEVEL)),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        owner = battle.characters.getFirst();
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(
                TriggerSpecs.rule("DEALING_DAMAGE", List.of(), speedBoost()))));

        boolean threw = false;
        try {
            battle.fireTriggers(com.laosun.aluminium.enums.TriggerEvent.DEALING_DAMAGE);
        } catch (IllegalArgumentException | IllegalStateException expected) {
            threw = true;
            String message = String.valueOf(expected.getMessage());
            System.out.println("[highest_hit] no instance -> " + expected.getClass().getSimpleName() + ": "
                    + message.substring(0, Math.min(140, message.length())));
        }
        Assertions.assertTrue(threw, "a rule that names a victim it cannot find must say so");
    }
}
