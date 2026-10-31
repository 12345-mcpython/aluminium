package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Light cone 23010: the wearer's SKILL and ULTIMATE damage are 18% higher (its crit damage is the row's own props).
 *
 * <p>The two halves are two rules, because {@code from_category} takes a single value. The judge checks both directions: each
 * category must be lifted, and a plain attack must not be -- a rule written without the condition would lift all three.
 */
public class BeforeDawnTest {
    private static final int CONE = 23010;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final double SHARE = 0.18;

    private Character wearer;
    private Enemy enemy;

    private Battle battle(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        return battle;
    }

    private int rulesFor(SkillCategory category) {
        int seen = 0;
        // Note: Build first: reading wearer on the same line as its own construction is an NPE (measured).
        Battle battle = battle(true);
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE,
                new TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0, null, battle, category))) {
            if (!rule.id().startsWith("cone23010_")) {
                continue;
            }
            for (var effect : rule.effects()) {
                System.out.println("[23010] category=" + category + " rule=" + rule.id()
                        + " op=" + effect.getOp() + " percent=" + effect.getPercent()
                        + " conditions=" + rule.conditions());
                Assertions.assertEquals(SHARE, effect.getPercent(), 1e-9, "18% at rank 1");
                seen++;
            }
        }
        return seen;
    }

    /**
     * The third sentence, in its 2026-10-31 shape: 「施放战技或终结技后获得【梦身】」 is a one-stack state, and 「触发追加攻击时
     * <b>消耗【梦身】</b>」 is a SECOND rule that reads the stack and removes it in the same firing. The old shape wrote a 1-turn
     * {@code FOLLOW_UP_DAMAGE_BOOST} window instead, which paid every follow-up in that turn rather than one.
     */
    @Test
    public void theUltimateArmsTheFollowUp() {
        Battle battle = battle(true);
        var gains = wearer.getTriggerTable().rulesFor(TriggerEvent.ULT_CAST).stream()
                .filter(r -> r.id().equals("cone23010_gain_dream_body")).toList();
        Assertions.assertEquals(1, gains.size(), "施放终结技后获得【梦身】");
        var stack = gains.getFirst().effects().getFirst();
        Assertions.assertEquals("ADD_STACK", stack.getOp());
        Assertions.assertEquals("梦身", stack.getBuff());
        Assertions.assertEquals(1, stack.getMaxStacks(), "one stack at a time");

        var spends = wearer.getTriggerTable().rulesFor(TriggerEvent.DEALING_DAMAGE).stream()
                .filter(r -> r.id().equals("cone23010_follow_up_damage")).toList();
        Assertions.assertEquals(1, spends.size(), "触发追加攻击时消耗【梦身】");
        var rule = spends.getFirst();
        Assertions.assertEquals(List.of("actor == self", "damage_is_additional", "self_stacks:梦身 >= 1.0"),
                rule.conditions().stream().map(c -> c.source()).toList(),
                "the follow-up is an ADDITIONAL instance, and the state must still be there");
        Assertions.assertEquals("BOOST_DAMAGE", rule.effects().getFirst().getOp());
        Assertions.assertEquals(0.48, rule.effects().getFirst().getPercent(), 1e-9, "48% at rank 1");
        Assertions.assertEquals("REMOVE_STACK", rule.effects().get(1).getOp(), "and the state is spent in the same firing");
    }

}
