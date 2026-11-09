package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.*;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * "使敌方全体全属性抗性降低 X%" - the victim's side of the resistance zone, pinned from both ends.
 *
 * <p>The damage pipeline takes the victim's own resistance, subtracts this attribute, and only then applies the
 * attacker's penetration; negative resistance is fully effective, which is why the reduction is not folded into
 * penetration.
 */
public class ResistanceReductionTest {
    private static final int ALLY = 1210;
    private static final int TRINNON = 1403;
    private static final int LEVEL = 80;
    private static final double EPS = 1e-6;

    /**
     * The engine reads the attribute: the same hit deals more once the reduction is on the victim.
     */
    @Test
    public void theDamagePipelineSubtractsTheVictimsResistanceReduction() {
        Character caster = CharacterFactory.create(ALLY, LEVEL);
        Character applier = Character.fromAttributes("applier", 10_000, 100, 100, 100);
        applier.setTriggerTable(new TriggerTable(9996, List.of(TriggerSpecs.rule(
                TriggerEvent.TURN_START.name(), List.of(), reduction(0.2)))));
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(caster, applier), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        double before = hit(battle, caster, enemy);

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, applier, enemy, 0, 0));
        double after = hit(battle, caster, enemy);

        Assertions.assertEquals(0.2, enemy.getAttribute(AttributeType.RESISTANCE_REDUCTION).get(), EPS,
                "the reduction landed on the victim");
        // Measured (round 8/20, same pipeline change): 380.952390022659 -> 46.1904852834533 = x1.25, which is
        // a 20% reduction against a victim whose physical resistance is 20% (0.8 -> 1.0 = 1/0.8). It is also the
        // evidence for the zone's shape: damage is multiplied by (1 - resistance).
        Assertions.assertEquals(1.25, after / before, EPS,
                "All-Type RES Reduction 20% (全属性抗性降低 20%): " + before + " -> " + after);
    }

    /**
     * 1321's shipped aura is E2's: 「大丽花在场时，敌方全体全属性抗性降低20%」 - a stated 0.2 on every enemy, but only
     * WITH Eidolon 2 (the sentence lives in her 星魂 2, not in the trace list; the file said 「行迹/光环」 and shipped it
     * unconditionally until 2026-11-09, so an E0 Dahlia was lowering every enemy's resistance).
    @Test
    public void herAuraStatesTwentyPercentOnEveryEnemyOnlyWithTheEidolon() {
        Character e0 = CharacterFactory.create(1321, LEVEL);
        Enemy plain = BattleFixture.monster();
        new Battle(List.of(e0), List.of(plain), BattleFixture.seeded()).startBattle();
        Assertions.assertEquals(0.0, plain.getAttribute(AttributeType.RESISTANCE_REDUCTION).get(), EPS,
                "E0: 星魂 2 has not been unlocked, so no aura");

        Character e2 = CharacterFactory.create(1321, LEVEL);
        e2.setEidolonRank(2);
        Enemy reduced = BattleFixture.monster();
        new Battle(List.of(e2), List.of(reduced), BattleFixture.seeded()).startBattle();
        Assertions.assertEquals(0.2, reduced.getAttribute(AttributeType.RESISTANCE_REDUCTION).get(), EPS,
                "E2: 「大丽花在场时，敌方全体全属性抗性降低20%」");
    }

    /**
     * One identical hit, returned as the enemy's HP loss.
     */
    private static double hit(Battle battle, Character attacker, Enemy enemy) {
        double before = enemy.getCurrentHp();
        battle.applyDamage(enemy, new Damage(attacker, enemy, DamageElement.PHYSICAL, DamageType.NORMAL, 1000));
        return before - enemy.getCurrentHp();
    }

    private static EffectSpec reduction(double value) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", AttributeType.RESISTANCE_REDUCTION.attributeString);
        TriggerSpecs.set(effect, "percent", value);
        TriggerSpecs.set(effect, "permanent", true);
        TriggerSpecs.set(effect, "target", "all_enemies");
        return effect;
    }

    /**
     * Two more readers: 1203's Ultimate and 1304's Basic Attack state their own percentages.
     */
    @Test
    public void theTwoNewReadersStateTheirOwnPercentages() {
        Character fuxuan = CharacterFactory.create(1203, LEVEL);
        Enemy first = BattleFixture.monster();
        Battle firstBattle = new Battle(List.of(fuxuan), List.of(first), BattleFixture.seeded());
        firstBattle.startBattle();

        firstBattle.castImmediate(fuxuan.getSkills().get(SkillType.ULTRA), fuxuan, List.of(first));

        Assertions.assertEquals(0.2, first.getAttribute(AttributeType.RESISTANCE_REDUCTION).get(), EPS,
                "\"when casting the Ultimate ... all enemies get 20% All-Type RES Reduction\" (「施放终结技时…敌方全体全属性抗性降低20%」)");

        Character nihility = CharacterFactory.create(1304, LEVEL);
        Enemy second = BattleFixture.monster();
        Battle secondBattle = new Battle(List.of(nihility), List.of(second), BattleFixture.seeded());
        secondBattle.startBattle();

        secondBattle.castImmediate(nihility.getSkills().get(SkillType.COMMON), nihility, List.of(second));

        Assertions.assertEquals(0.12, second.getAttribute(AttributeType.RESISTANCE_REDUCTION).get(), EPS,
                "\"casting a Basic ATK lowers the target's All-Type RES by 12%\" (「施放普攻时使目标的全属性抗性降低12%」)");
    }
}