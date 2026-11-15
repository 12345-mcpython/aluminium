package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.DotBuff;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Light cone 23029, its expressible clause, read off the shipped file (discipline 232).
 *
 * <p>Its clause reads 那无数个春天 (23029): 「若目标处于<b>装备者施加的</b>持续伤害状态，有 60% 的基础概率把【卸甲】<b>升级成</b>【穷寇】，
 * 【穷寇】状态下额外受到的伤害提高 14%」.
 *
 * <p><b>What was missing.</b> The old note said the engine had no way to 「就地升级一个状态」. It needs none: an upgrade is one rule
 * that REMOVES the old state and APPLIES the new one in the same firing, with a rule-level {@code chance} so a single roll decides
 * both. The other half is a numeric variable the corpus could not read - {@code target_dots_from_self}, the DoTs on the target whose
 * source is the wearer.
 */
public class ThoseManySpringsTest {
    private static final int WEAPON_ID = 23029;
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;
    private static final int CONE_WEARER = 1204;

    @Test
    public void theShippedClauseCarriesTheNumbers() {
        Character c = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(WEAPON_ID, LEVEL));
        // 2026-10-19: the armour-break clause is a BASIC_ATTACK rule now (it carries the 60% chance and the state).
        var rules = c.getTriggerTable().rulesFor(TriggerEvent.BASIC_ATTACK).stream()
                .filter(r -> r.id().startsWith("cone23029_")).toList();
        System.out.println("[23029] rules=" + rules.size());
        Assertions.assertEquals(2, rules.size(), "the armour-break clause and its upgrade");
        var effect = rules.getFirst().effects().getFirst();
        System.out.println("[23029] spec op=" + effect.getOp() + " percent=" + effect.getPercent()
                + " turns=" + effect.getTurns() + " target=" + effect.getTarget());
        // 2026-10-19: the rule now carries the state and the chance; the vulnerability share is the SECOND effect, and it
        // is the #3 value (10% at rank 1) - the old 0.18 was #6, the wrong number.
        var hit = rules.getFirst().effects().get(1);
        Assertions.assertEquals(0.1, hit.getPercent(), 1e-9, "10% more damage taken at rank 1");
        Assertions.assertEquals(2, hit.getTurns(), 1e-9, "for two turns");
        Assertions.assertEquals(0.6, hit.getBaseChance(), 1e-9, "a 60% base chance");
        Assertions.assertEquals("target", hit.getTarget(), "on the enemy it hit");
    }

    /**
     * With MY DoT on the target, 【卸甲】 becomes 【穷寇】; without it, nothing happens.
     */
    @Test
    public void onlyMyOwnDotUpgradesTheArmourBreak() {
        Run withMine = new Run();
        withMine.myDot();
        withMine.castSkill();
        Assertions.assertFalse(withMine.enemy.getBuffManager().hasState("char:23029/disarm"), "【char:23029/disarm】 is gone - it was upgraded");
        Assertions.assertTrue(withMine.enemy.getBuffManager().hasState("char:23029/cornered_foe"), "and 【char:23029/cornered_foe】 took its place");

        Run withoutMine = new Run();
        withoutMine.anyoneElsesDot();
        withoutMine.castSkill();
        Assertions.assertTrue(withoutMine.enemy.getBuffManager().hasState("char:23029/disarm"),
                "「装备者施加的持续伤害」 - somebody else's DoT does not upgrade it");
        Assertions.assertFalse(withoutMine.enemy.getBuffManager().hasState("char:23029/cornered_foe"));
    }

    // ==================================================================

    private static final class Run {
        private final Character wearer;
        private final Enemy enemy;
        private final Battle battle;

        Run() {
            wearer = CharacterFactory.create(CONE_WEARER, LEVEL, true, Weapon.build(23029, LEVEL, false, 1));
            enemy = BattleFixture.monster();
            // Always rolls low, so the 60% lands and the assertion is about the rule, not the dice.
            battle = new Battle(new ArrayList<>(List.of(wearer)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
            enemy.getBuffManager().addBuff(new com.laosun.aluminium.buff.StateBuff("char:23029/disarm", 2));
        }

        void myDot() {
            enemy.getBuffManager().addBuff(new DotBuff(wearer, DamageElement.FIRE, 10, 2));
        }

        void anyoneElsesDot() {
            // A real other applier: a DotBuff insists on a source (a DoT kill is credited to it).
            Character other = CharacterFactory.create(1001, LEVEL);
            enemy.getBuffManager().addBuff(new DotBuff(other, DamageElement.ICE, 10, 2));
        }

        void castSkill() {
            battle.castImmediate(wearer.getSkills().get(SkillType.SKILL), wearer, List.of(enemy));
            battle.processRequests();
        }
    }
}
