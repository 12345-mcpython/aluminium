package com.laosun.aluminium.test.content.characters.luka;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.DotBuff;
import com.laosun.aluminium.buff.StackBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1111 卢卡's Eidolons 1-6: the two level bumps (data-driven, from {@code skill_add_level}), 「每获得 1 层【斗志】，
 * 攻击力提高 5%，最多叠加 4 层」, the bleed-gated damage boost, the physical-weakness layer, and the per-segment laceration.
 *
 * <p>Note on the E4 measurement: he starts with ONE layer from his talent, so the panel already carries 0.05 x base at battle
 * start. The judge therefore measures the step each ADDITION makes, not the distance from the starting panel - comparing the
 * latter against "0.05 x layers" reads one layer short and looks like an engine defect when it is a baseline mistake.
 *
 * <p>The Eidolon 1 condition is the new {@code any_enemy has_state dot:bleed} - the enemy camp, not the party - so the check
 * turns on the bleeding enemy and then asks whether his damage-taken boost moved, and off again for the control.
 *
 * <p>Eidolon 6's clause is per SEGMENT, which {@code DEALING_DAMAGE} already provides once per hit - no segment ordinal is
 * needed. The check plants a laceration on a target that survives, then compares his basic attack with and without the
 * Eidolon.
 */
public class LukaEidolonTest {

    private static final int LEVEL = 80;
    private static final int LUKA = 1111;
    private static final String FIGHTING_SPIRIT = "char:1111/fighting_spirit";
    private static final String LACERATION = "dot:bleed";

    @Test
    public void eidolonThreeAndFiveRaiseTheSkillLevels() {
        Battle e0 = battle(0);
        Battle e6 = battle(6);
        Assertions.assertEquals(level(e0, SkillType.SKILL) + 2, level(e6, SkillType.SKILL), "E3: 战技 +2");
        Assertions.assertEquals(level(e0, SkillType.TALENT) + 2, level(e6, SkillType.TALENT), "E3: 天赋 +2");
        Assertions.assertEquals(level(e0, SkillType.ULTRA) + 2, level(e6, SkillType.ULTRA), "E5: 终结技 +2");
        Assertions.assertEquals(level(e0, SkillType.COMMON) + 1, level(e6, SkillType.COMMON), "E5: 普攻 +1");
    }

    @Test
    public void eidolonFourPaysFivePercentPerLayerAndStopsAtFour() {
        Battle battle = battle(4);
        Character luka = battle.characters().getFirst();
        double base = luka.getAttribute(AttributeType.ATTACK).baseValue();
        int startLayers = luka.getBuffManager().stacksOf(FIGHTING_SPIRIT);
        Assertions.assertEquals(1, startLayers, "precondition: his talent gives one layer at battle start");

        // Each ADDITION before the cap is worth exactly one layer.
        for (int layer = startLayers + 1; layer <= 4; layer++) {
            double before = luka.getAttribute(AttributeType.ATTACK).get();
            addWill(luka, 1);
            Assertions.assertEquals(0.05 * base, luka.getAttribute(AttributeType.ATTACK).get() - before, 1e-6,
                    "layer " + layer + " of 「每获得 1 层【char:1111/fighting_spirit】，攻击力提高 5%」");
        }

        // Past the cap nothing moves.
        double atCap = luka.getAttribute(AttributeType.ATTACK).get();
        addWill(luka, 3);
        System.out.println("[1111] layers=" + luka.getBuffManager().stacksOf(FIGHTING_SPIRIT) + " atk stays " + atCap);
        Assertions.assertEquals(atCap, luka.getAttribute(AttributeType.ATTACK).get(), 1e-9,
                "「最多叠加 4 层」");
    }

    /**
     * 1111 卢卡's Eidolon 1: 「当卢卡行动时，若指定的敌方目标处于dot:bleed状态，卢卡造成的伤害提高 15%，持续 2 回合」.
     */
    @Test
    public void actingAgainstABleedingEnemyRaisesHisDamage() {
        Assertions.assertEquals(0.0, boostAfterTurn(1, false), 1e-9, "control: nothing while no enemy is bleeding");
        Assertions.assertEquals(0.15, boostAfterTurn(1, true), 1e-9, "「若指定的敌方目标处于dot:bleed…造成的伤害提高 15%」");
        Assertions.assertEquals(0.0, boostAfterTurn(0, true), 1e-9, "control: the Eidolon is what pays it");
    }

    /**
     * 1111 卢卡's Eidolon 2: 「若战技击中的敌方目标弱点为物理属性，获得 1 层【斗志】」.
     */
    @Test
    public void theSkillPaysALayerOnlyAgainstAPhysicalWeakness() {
        int plain = layersAgainst(false);
        int weak = layersAgainst(true);
        System.out.println("[1111] plain=" + plain + " weak=" + weak);
        Assertions.assertEquals(plain + 1, weak, "「弱点为物理属性…获得 1 层」 - one MORE than the same Skill without it");
    }

    /**
     * 1111 卢卡's Eidolon 6: 「本次强化普攻使用的【直冲拳】每施放过 1 段攻击，则额外使其当前承受的dot:bleed状态立即产生 1 次相当于原伤害
     * 8% 的伤害」.
     */
    @Test
    public void eachSegmentTicksTheLacerationOnce() {
        double atFive = basicDamage(5, true);
        double atSix = basicDamage(6, true);
        double unbleeding = basicDamage(6, false);
        System.out.println("[1111] basic: E5 " + atFive + ", E6 " + atSix + " ; E6 on a healthy target " + unbleeding);
        Assertions.assertTrue(atSix > atFive, "「每施放过 1 段攻击…额外产生 1 次 8% 的伤害」");
        Assertions.assertEquals(atFive, unbleeding, atFive * 1e-6,
                "the control: with no laceration there is nothing to tick, at any rank");
    }

    // ==================================================================

    private static Battle battle(int eidolon) {
        Character luka = CharacterFactory.create(LUKA, LEVEL, true, null, null, eidolon);
        Battle battle = new Battle(new ArrayList<>(List.of(luka, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
        battle.startBattle();
        return battle;
    }

    private static int level(Battle battle, SkillType slot) {
        return battle.characters().getFirst().skillLevelBonus(slot);
    }

    private static void addWill(Character luka, int layers) {
        for (int i = 0; i < layers; i++) {
            luka.getBuffManager().addBuff(new StackBuff(FIGHTING_SPIRIT, 1, true, 99));
        }
    }

    /** His all-damage boost after his turn starts, with a bleeding enemy if asked. */
    private static double boostAfterTurn(int eidolon, boolean bleeding) {
        Character luka = CharacterFactory.create(LUKA, LEVEL, true, null, null, eidolon);
        Character holder = CharacterFactory.create(1001, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(luka, holder)),
                new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
        battle.startBattle();
        if (bleeding) {
            enemy.getBuffManager().addBuff(new DotBuff(holder, DamageElement.PHYSICAL, 10, 3, 0, "dot:bleed"));
        }
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, luka, luka, 0, 0));
        return luka.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
    }

    private static int layersAgainst(boolean physicalWeakness) {
        Character luka = CharacterFactory.create(LUKA, LEVEL, true, null, null, 2);
        Enemy enemy = BattleFixture.monster();
        if (physicalWeakness) {
            enemy.addWeakness(DamageElement.PHYSICAL);
        }
        Battle battle = new Battle(new ArrayList<>(List.of(luka, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        int before = luka.getBuffManager().stacksOf(FIGHTING_SPIRIT);
        battle.castImmediate(luka.getSkills().get(SkillType.SKILL), luka, List.of(enemy));
        return luka.getBuffManager().stacksOf(FIGHTING_SPIRIT) - before;
    }

    /** His basic attack's damage against a target with (or without) a planted laceration. */
    private static double basicDamage(int eidolon, boolean bleeding) {
        Character luka = CharacterFactory.create(LUKA, LEVEL, true, null, null, eidolon);
        Enemy enemy = BattleFixture.dummy();
        Battle battle = new Battle(new ArrayList<>(List.of(luka)), new ArrayList<>(List.of(enemy)),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        if (bleeding) {
            enemy.getBuffManager().addBuff(new DotBuff(luka, DamageElement.PHYSICAL, 10_000, 3, 0, LACERATION));
        }
        double before = enemy.getCurrentHp();
        battle.castImmediate(luka.getSkills().get(SkillType.COMMON), luka, List.of(enemy));
        battle.processRequests();
        Assertions.assertFalse(enemy.isDeath(), "the target must survive, or a second tick would settle nothing");
        return before - enemy.getCurrentHp();
    }
}
