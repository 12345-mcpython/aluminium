package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.StateBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1309's trace (行迹) "即兴装饰" (1309102): "处于[协奏]状态时，我方全体发动追加攻击造成的暴击伤害提高 25%".
 *
 * <p>The aura is owned by 1309 but must react to an ALLY's follow-up -- which the engine already supports, because fireTriggers walks the whole party. The judge makes
 * the ALLY deal the damage, so a self-only implementation would fail it.
 */
public class RobinAuraTest {
    private static final int OWNER = 1309;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theAuraNamesTheFollowUpScopeAndShare() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        owner.getBuffManager().addBuff(new StateBuff("协奏", 3, true));
        int seen = 0;
        for (var rule : owner.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE,
                new TriggerTable.TriggerContext(owner, owner, enemy, 0, 0))) {
            for (var effect : rule.effects()) {
                if ("MODIFY_ATTR".equals(effect.getOp()) && "CRIT_ATTACK".equals(effect.getAttribute())) {
                    seen++;
                    System.out.println("[1309] id=" + rule.id() + " scope=" + effect.getDamageType()
                            + " percent=" + effect.getPercent() + " instance=" + effect.getInstance());
                    Assertions.assertEquals("ADDITIONAL", effect.getDamageType(), "scoped to follow-up damage");
                    Assertions.assertEquals(0.25, effect.getPercent(), 1e-9, "the data row states 0.25");
                }
            }
        }
        Assertions.assertEquals(1, seen, "exactly one such modifier, and it needs the state");
    }

    @Test
    public void anAlliesFollowUpIsBoostedByTheOwnersAura() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(owner, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        owner.getBuffManager().addBuff(new StateBuff("协奏", 3, true));
        ally.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(DoubleValue.Modifier.pure(2.0, DoubleValue.Modifier.ModifierSource.BUFF, 130901));
        Assertions.assertTrue(ally.getAttribute(AttributeType.CRIT_CHANCE).get() >= 1.0,
                "precondition: the ally's hits always crit");
        double normal = battle.applyDamage(enemy, new Damage(ally, enemy, DamageElement.QUANTUM, DamageType.NORMAL, 1000));
        double followUp = battle.applyAdditionalDamage(ally, enemy, DamageElement.QUANTUM, 1000, null, null);
        System.out.println("[1309] ally normal=" + normal + " ally followUp=" + followUp
                + " ratio=" + (followUp / normal));
        // MEASURED first, then pinned: (1.5 + 0.25) / 1.5. A self-only implementation would read exactly 1.0 here, which is
        // what the third test asserts for the state-less control.
        Assertions.assertEquals(1.1666666667, followUp / normal, 1e-6,
                "the owner's aura reaches the ALLY's follow-up");
    }

    @Test
    public void theAuraIsInertWithoutTheState() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(owner, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        ally.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(DoubleValue.Modifier.pure(2.0, DoubleValue.Modifier.ModifierSource.BUFF, 130902));
        double normal = battle.applyDamage(enemy, new Damage(ally, enemy, DamageElement.QUANTUM, DamageType.NORMAL, 1000));
        double followUp = battle.applyAdditionalDamage(ally, enemy, DamageElement.QUANTUM, 1000, null, null);
        System.out.println("[1309] no state: normal=" + normal + " followUp=" + followUp
                + " ratio=" + (followUp / normal));
        Assertions.assertEquals(1.0, followUp / normal, 1e-6, "without the state the aura does nothing");
    }
}
