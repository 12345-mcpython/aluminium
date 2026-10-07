package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Character 1506 (Silver Wolf LV.999): the batch that took her from 2 rules to 15.
 *
 * <p>From her page: 战技 gains 5 笑点; 欢愉技 gains 15 隐藏分; 终结技 enters 【无敌玩家】 and her trace 1506103 adds 20
 * 隐藏分 as she does; her Ultimate also installs the enhanced basic; three enhanced basics end 【无敌玩家】 and leaving it
 * clears 隐藏分; her true-ending trace pays 20 隐藏分 at 20 counted laughs and another 20 at 40.
 *
 * <h2>One clause is still OPEN - shipped, but not yet passing</h2>
 *
 * <p>Recorded here rather than fixed by guesswork, and deliberately NOT present as a passing test:
 *
 * <ul>
 *   <li><b>the 40% Basic/Skill rider while she holds 【好活当赏】</b> - the Basic deals exactly the same damage with and
 *       without the state (147.84 either way). This file synthesizes its own DAMAGE instance instead of driving her
 *       slot-1 skill, so <b>the fixture may be what is wrong rather than the rule</b>, and that must be separated before
 *       the rule is touched.</li>
 * </ul>
 *
 * <p><b>Eidolon 1's 「保留 20% 的【隐藏分】」 WAS open and is now closed</b>, and the reason is worth keeping: the clear and
 * the retention had been written as TWO rules, and `previousCredited` - what the retention's share reads - is per-RULE and
 * starts at 0, so the share was 20% of nothing. They are one rule now, and it reads a fifth of what she actually held.
 */
public class SilverWolfLV999KitTest {
    private static final int WOLF = 1506;
    private static final int ALLY = 1204;
    private static final int MONSTER = 1002011;
    private static final String LAUGH = "笑点";
    private static final String HIDDEN = "隐藏分";
    private static final String CHARGES = "强化普攻次数";
    private static final String GOD = "无敌玩家";

    /** Her Skill gains the 5 笑点 her page states. */
    @Test
    public void theSkillGainsFiveLaughs() {
        Battle battle = newBattle();
        Character wolf = battle.characters.getFirst();
        int before = battle.partyResourceValue(LAUGH);
        cast(battle, wolf, SkillType.SKILL, battle.enemyUnits().getFirst());
        int gained = battle.partyResourceValue(LAUGH) - before;
        System.out.println("[1506] the Skill gained " + gained + " 笑点");
        Assertions.assertEquals(5, gained, "战技: 「获得 5 个笑点」");
    }

    /** Her Elation skill gains 15 隐藏分. */
    @Test
    public void theElationSkillGainsFifteenHidden() {
        Battle battle = newBattle();
        Character wolf = battle.characters.getFirst();
        cast(battle, wolf, SkillType.SKILL, battle.enemyUnits().getFirst());
        int afterSkill = wolf.getResources().value(HIDDEN);
        fireElationSkill(battle, wolf);
        int afterElation = wolf.getResources().value(HIDDEN);
        System.out.println("[1506] 隐藏分 after the Skill " + afterSkill + " ; after the Elation skill " + afterElation);
        Assertions.assertEquals(15, afterElation - afterSkill,
                "欢愉技 殿堂级操作回放: 「获得 15 点【隐藏分】」");
    }

    /** Her Ultimate enters 【无敌玩家】, fills the enhanced-basic allowance, and pays the trace's 20. */
    @Test
    public void theUltimateEntersGodmodeAndPaysTheTrace() {
        Battle battle = newBattle();
        Character wolf = battle.characters.getFirst();
        int before = wolf.getResources().value(HIDDEN);
        castUltimate(battle, wolf);
        System.out.println("[1506] after the Ultimate: state=" + wolf.getBuffManager().hasState(GOD)
                + " charges=" + wolf.getResources().value(CHARGES)
                + " 隐藏分 " + before + " -> " + wolf.getResources().value(HIDDEN));
        Assertions.assertTrue(wolf.getBuffManager().hasState(GOD), "终结技: 「进入【无敌玩家】状态」");
        Assertions.assertEquals(3, wolf.getResources().value(CHARGES),
                "天赋: 「完整施放 3 次强化普攻后退出」 - so the allowance starts full");
        Assertions.assertEquals(20, wolf.getResources().value(HIDDEN) - before,
                "行迹 隐藏关卡全成就: 「进入【无敌玩家】状态后，获得 20 点【隐藏分】」");
    }

    /** Three enhanced basics spend the allowance and end the state, and leaving it clears 隐藏分. */
    @Test
    public void threeEnhancedBasicsEndGodmodeAndClearHidden() {
        Battle battle = newBattle();
        Character wolf = battle.characters.getFirst();
        castUltimate(battle, wolf);
        wolf.getResources().gain(HIDDEN, 30);
        int beforeExit = wolf.getResources().value(HIDDEN);

        for (int use = 1; use <= 3; use++) {
            cast(battle, wolf, SkillType.COMMON, battle.enemyUnits().getFirst());
            System.out.println("[1506] after enhanced basic " + use + ": slot="
                    + wolf.getSkills().get(SkillType.COMMON).getSkillSlot()
                    + " charges=" + wolf.getResources().value(CHARGES)
                    + " state=" + wolf.getBuffManager().hasState(GOD));
        }
        System.out.println("[1506] 隐藏分 " + beforeExit + " -> " + wolf.getResources().value(HIDDEN));
        Assertions.assertFalse(wolf.getBuffManager().hasState(GOD),
                "天赋: 「完整施放 3 次强化普攻后退出【无敌玩家】状态」");
        Assertions.assertEquals(0, wolf.getResources().value(HIDDEN),
                "天赋: 「退出【无敌玩家】状态时【隐藏分】会被清空」");
    }

    /**
     * Eidolon 1 keeps a fifth of her 隐藏分 when 【无敌玩家】 ends, instead of losing all of it.
     *
     * <p>「退出【无敌玩家】状态时不会清空【隐藏分】，改为保留 20% 的【隐藏分】。」 The clear and the retention live in ONE rule,
     * because `previousCredited` - what the retention's share reads - is per-RULE and starts at 0: as two rules the share
     * was 20% of nothing. That is what this case pins.
     */
    @Test
    public void eidolonOneKeepsAFifthOfTheHidden() {
        int kept = hiddenAfterLeavingGodmode(1);
        int lost = hiddenAfterLeavingGodmode(0);
        System.out.println("[1506] 隐藏分 after leaving 【无敌玩家】: E1 keeps " + kept + " ; E0 keeps " + lost
                + " (a fifth of the 80 she held is 16)");
        // Note: She holds 80 at the exit, not 60 - the Ultimate and its trace pay 20 before the fixture's own 60 - and the
        // rule keeps a fifth of THAT. The first version of this case expected a fifth of 60 and read the rule as wrong.
        Assertions.assertEquals(16, kept, 1e-9,
                "星魂 1: 「改为保留 20% 的【隐藏分】」 - a fifth of the 80 she held at the exit");
        Assertions.assertEquals(0, lost, 1e-9, "and at E0 the base clause clears it as the document says");
    }

    /**
     * Her true-ending trace pays 20 at 20 counted laughs and another 20 at 40.
     */
    @Test
    public void theTrueEndingTracePaysAtTwentyAndAtForty() {        int below = hiddenFromElationCastAt(19);
        int at20 = hiddenFromElationCastAt(20);
        int at39 = hiddenFromElationCastAt(39);
        int at40 = hiddenFromElationCastAt(40);
        System.out.println("[1506] 隐藏分 from one Elation cast at 19/20/39/40 counted laughs: "
                + below + " / " + at20 + " / " + at39 + " / " + at40);
        Assertions.assertEquals(15, below, "under 20 counted laughs the trace pays nothing");
        Assertions.assertEquals(35, at20, "「大于等于 20 个，额外获得 20 点」 -> 15 + 20");
        Assertions.assertEquals(35, at39, "and 39 is still only the first threshold");
        Assertions.assertEquals(55, at40, "「若大于等于 40 个，再额外获得 20 点」 -> 15 + 20 + 20");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * Her 隐藏分 after 【无敌玩家】 ends, at the given Eidolon rank: 60 held going in, so a fifth is 12.
     *
     * <p>The 60 is reached through the Ultimate, the trace's 20 and a plain gain of 40, so the reading is about the exit
     * rather than about how the counter was built.
     */
    private static int hiddenAfterLeavingGodmode(int eidolonRank) {
        Battle battle = newBattle(eidolonRank);
        Character wolf = battle.characters.getFirst();
        castUltimate(battle, wolf);
        wolf.getResources().gain(HIDDEN, 60);
        int atExit = wolf.getResources().value(HIDDEN);
        for (int use = 1; use <= 3; use++) {
            cast(battle, wolf, SkillType.COMMON, battle.enemyUnits().getFirst());
        }
        System.out.println("[1506] E" + eidolonRank + ": 隐藏分 " + atExit + " -> "
                + wolf.getResources().value(HIDDEN));
        return wolf.getResources().value(HIDDEN);
    }

    /**
     * 隐藏分 gained by one Elation skill cast, with the party's counted laughs set to the given figure.
     *
     * <p>The counted figure is the merged snapshot of 【好活当赏】, so it is built by granting the state while the party
     * pool holds that many laughs - the same route the engine takes.
     */
    private static int hiddenFromElationCastAt(int countedLaughs) {
        Battle battle = newBattle();
        Character wolf = battle.characters.getFirst();
        battle.partyResource(LAUGH).gain(countedLaughs);
        battle.noteElationGift(wolf, 3);
        int before = wolf.getResources().value(HIDDEN);
        fireElationSkill(battle, wolf);
        return wolf.getResources().value(HIDDEN) - before;
    }

    /** Fires the Elation skill's cast event, which is what her trace and her slot-20 rule both hang on. */
    private static void fireElationSkill(Battle battle, Character wolf) {
        battle.fireTriggers(TriggerEvent.CAST_SETUP, wolf, battle.enemyUnits().getFirst(), 0, 0,
                com.laosun.aluminium.enums.SkillCategory.ELATION_DAMAGE, 20);
        battle.processRequests();
    }

    /** Casts a slot through the engine's own request path and settles the action. */
    private static void cast(Battle battle, Character unit, SkillType slot, Enemy victim) {
        battle.requestSkill(unit.getSkills().get(slot), unit, List.of(victim));
        battle.processRequests();
        // Note: Driven here because the fixture casts directly rather than through a move, so the engine's own
        // end-of-attack announcement never runs. The rule that ends 【无敌玩家】 hangs on this event.
        battle.fireTriggers(TriggerEvent.ATTACK_FINISHED, unit, victim, 1, 0);
        battle.processRequests();
    }

    private static void castUltimate(Battle battle, Character wolf) {
        wolf.setCurrentEnergy(wolf.getMaxEnergy());
        battle.castUltra(wolf, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
    }

    private static Battle newBattle() {
        return newBattle(0);
    }

    private static Battle newBattle(int eidolonRank) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(WOLF, 80, false, null, null, eidolonRank),
                        CharacterFactory.create(ALLY, 80)),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}
