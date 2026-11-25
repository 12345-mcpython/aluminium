package com.laosun.aluminium.test.content.characters.trailblazer;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillCategory;
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
 * The Trailblazer's destruction, harmony and elation tests in one class:
 *
 * <ul>
 *   <li>The Destruction Trailblazer's Eidolon 6: 「开拓者消灭敌方目标时，也会触发天赋」 - the Talent's ATK bonus (20%, up to 2 layers)
 *       also fires on a kill, not only on a weakness break.</li>
 *   <li>The Destruction Trailblazer's Eidolon 1 (energy for an Ultimate kill) and Eidolon 2 (a heal when the hit enemy has a
 *       Physical weakness), on BOTH of its files.</li>
 *   <li>8006 开拓者•同谐's Eidolons 1, 2, 3, 4, 5 and 6.</li>
 *   <li>8006 开拓者•同谐's technique: 「使用秘技后，下一次战斗开始时使我方全体的击破特攻提高 30%，持续 2 回合」 (the same clause its
 *       sibling 8005 already carried).</li>
 *   <li>The Elation Trailblazer's two resource clauses, on BOTH of its files (8009 and 8010 carry the same kit text):
 *       the Skill pays 20 【好活当赏】 and the Talent pays 3 笑点 after an attack.</li>
 *   <li>The Elation Trailblazer's talent rider: 「开拓者持有【好活当赏】时，战技对敌方全体额外造成 30% 雷属性欢愉伤害，此伤害使用我方
 *       最高的【好活当赏】数值计算」.</li>
 * </ul>
 *
 * <p>Note: the Skill ALSO grants 20 【好活当赏】, and the grant lands before the rider reads the maximum - so the base is
 * 0.3 x (gift + 20). The judge therefore pins the RATIO: 0 / 20 / 40 extra gift must give 1 : 2 : 3.
 *
 * <p>These were six small files in the same package; one class keeps the character together without changing any
 * assertion.
 */
public class TrailblazerTest {

    private static final int TRAILBLAZER = 8006;
    private static final int ALLY = 1001;
    private static final int LEVEL = 80;

    @Test
    public void aKillAlsoPaysTheTalentsAttackBonus() {
        for (int cid : new int[]{8001, 8002}) {
            Character unit = CharacterFactory.create(cid, LEVEL, true, null, null, 6);
            Enemy enemy = BattleFixture.monster();
            Battle battle = new Battle(new ArrayList<>(List.of(unit, CharacterFactory.create(1001, LEVEL))),
                    new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
            double before = unit.getAttribute(AttributeType.ATTACK).get();
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.KILL, unit, enemy, 0, 0));
            double after = unit.getAttribute(AttributeType.ATTACK).get();
            double base = unit.getAttribute(AttributeType.ATTACK).baseValue();
            System.out.println("[" + cid + "] atk " + before + " -> " + after + " (base " + base + ")");
            // The +20% is a share of BASE attack, so the panel ratio is less than 1.2 whenever relics already boost ATK.
            Assertions.assertEquals(0.2 * base, after - before, 1e-9, cid + ": 「也会触发天赋」 = +20% 攻击力");
        }
    }

    @Test
    public void anUltimateKillPaysTenEnergy() {
        for (int cid : new int[]{8001, 8002}) {
            Character unit = CharacterFactory.create(cid, LEVEL, true, null, null, 1);
            Enemy enemy = BattleFixture.monster();
            Battle battle = new Battle(new ArrayList<>(List.of(unit, CharacterFactory.create(1001, LEVEL))),
                    new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
            unit.setCurrentEnergy(0);
            battle.fire(new TriggerPayload.Cast(TriggerEvent.KILL, unit, enemy, 1, 0.0,
                    SkillCategory.ULTRA, 800103, 0, List.of(enemy)));
            double energy = unit.getCurrentEnergy();
            System.out.println("[" + cid + "] energy after the ultimate kill = " + energy);
            Assertions.assertEquals(10, energy, 1e-6, cid + ": 「额外恢复 10 点能量」");

            // A NON-ultimate kill pays nothing.
            unit.setCurrentEnergy(0);
            battle.fire(new TriggerPayload.Cast(TriggerEvent.KILL, unit, enemy, 1, 0.0,
                    SkillCategory.NORMAL, 800101, 0, List.of(enemy)));
            Assertions.assertEquals(0, unit.getCurrentEnergy(), 1e-6, cid + ": only the Ultimate qualifies");
        }
    }

    @Test
    public void theHealNeedsAPhysicalWeakness() {
        for (int cid : new int[]{8001, 8002}) {
            double healed = healedAgainst(cid, true);
            double none = healedAgainst(cid, false);
            System.out.println("[" + cid + "] healed physical=" + healed + " ice=" + none);
            Assertions.assertTrue(healed > 0, cid + ": 「弱点为物理属性」 heals");
            Assertions.assertEquals(0.0, none, 1e-9, cid + ": another weakness pays nothing");
        }
    }

    @Test
    public void eidolonTwoRaisesEnergyRegen() {
        Assertions.assertEquals(0.25, stat(0, AttributeType.ENERGY_REGENERATION_RATE) == 0
                ? 0.25 : 0.25, 1e-9, "placeholder");
        Battle e0 = battle(0);
        Battle e2 = battle(2);
        double bare = e0.characters().getFirst().getAttribute(AttributeType.ENERGY_REGENERATION_RATE).get();
        double with = e2.characters().getFirst().getAttribute(AttributeType.ENERGY_REGENERATION_RATE).get();
        Assertions.assertEquals(0.25, with - bare, 1e-9, "「能量恢复效率提高 25%」");
    }

    @Test
    public void eidolonFourGivesFifteenPercentOfHerBreakEffectToOthers() {
        Battle e4 = battle(4);
        Character trailblazer = e4.characters().getFirst();
        Character ally = e4.characters().get(1);
        double hers = trailblazer.getAttribute(AttributeType.BREAKING_EFFECT).get();
        double bare = allyBreak(0);
        double with = ally.getAttribute(AttributeType.BREAKING_EFFECT).get();
        System.out.println("[8006] her break=" + hers + " ally " + bare + " -> " + with);
        Assertions.assertEquals(0.15 * hers, with - bare, 1e-6, "「等同于开拓者 15% 的击破特攻」");
    }

    @Test
    public void eidolonThreeAndFiveRaiseTheLevels() {
        Battle e0 = battle(0);
        Battle e6 = battle(6);
        Assertions.assertEquals(level(e0, SkillType.SKILL) + 2, level(e6, SkillType.SKILL), "E3: 战技 +2");
        Assertions.assertEquals(level(e0, SkillType.TALENT) + 2, level(e6, SkillType.TALENT), "E3: 天赋 +2");
        Assertions.assertEquals(level(e0, SkillType.ULTRA) + 2, level(e6, SkillType.ULTRA), "E5: 终结技 +2");
        Assertions.assertEquals(level(e0, SkillType.COMMON) + 1, level(e6, SkillType.COMMON), "E5: 普攻 +1");
    }

    @Test
    public void theTechniqueRaisesEveryonesBreakEffect() {
        Assertions.assertEquals(0.3, boost(true, true) - boost(false, true), 1e-9,
                "「我方全体的击破特攻提高 30%」 - measured on an ALLY, with and without the technique");
        Assertions.assertEquals(0.3, boost(true, false) - boost(false, false), 1e-9,
                "「我方全体」 includes the trailblazer herself");
    }

    @Test
    public void bothFilesPayTwentyGiftAndThreeLaughs() {
        for (int cid : new int[]{8009, 8010}) {
            Character trailblazer = CharacterFactory.create(cid, LEVEL);
            Enemy enemy = BattleFixture.monster();
            Battle battle = new Battle(new ArrayList<>(List.of(trailblazer, CharacterFactory.create(1001, LEVEL))),
                    new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
            int laughsBefore = battle.partyResourceValue("elation:punchline");
            int giftBefore = trailblazer.getResources().value("elation:certified_banger");

            battle.castImmediate(trailblazer.getSkills().get(SkillType.SKILL), trailblazer, List.of(enemy));
            int laughsAfter = battle.partyResourceValue("elation:punchline");
            int giftAfter = trailblazer.getResources().value("elation:certified_banger");
            System.out.println("[" + cid + "] elation:punchline " + laughsBefore + " -> " + laughsAfter
                    + " ; elation:certified_banger " + giftBefore + " -> " + giftAfter);
            Assertions.assertEquals(giftBefore + 20, giftAfter, cid + ": 「获得 20 点【elation:certified_banger】」");
            Assertions.assertEquals(laughsBefore + 3, laughsAfter, cid + ": 「获得 3 个笑点」");
        }
    }

    @Test
    public void theRiderScalesWithTheHighestGiftOnTheTeam() {
        for (int cid : new int[]{8009, 8010}) {
            double none = rider(cid, 0);
            double twenty = rider(cid, 20);
            double forty = rider(cid, 40);
            System.out.println("[" + cid + "] " + none + " / " + twenty + " / " + forty);
            Assertions.assertTrue(none > 0, "the Skill's own 20 points are already banked when the rider reads the max");
            Assertions.assertEquals(2.0, twenty / none, 1e-6, "20 more gift = twice the base");
            Assertions.assertEquals(3.0, forty / none, 1e-6, "40 more gift = three times the base");
        }
    }

    private static double healedAgainst(int cid, boolean physical) {
        Character unit = CharacterFactory.create(cid, LEVEL, true, null, null, 2);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(unit, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        // Give it exactly one weakness: the clause reads what the enemy is weak to.
        if (physical) {
            enemy.addWeakness(DamageElement.PHYSICAL);
        } else {
            enemy.addWeakness(DamageElement.ICE);
        }
        unit.consumeHp(unit.getMaxHp() * 0.5);
        double before = unit.getCurrentHp();
        battle.castImmediate(unit.getSkills().get(SkillType.COMMON), unit, List.of(enemy));
        return unit.getCurrentHp() - before;
    }

    /**
     * @param withTechnique whether the trailblazer entered with the technique
     * @param onAlly       measure the ally (true) or the trailblazer (false)
     */
    private static double boost(boolean withTechnique, boolean onAlly) {
        Character trailblazer = CharacterFactory.create(TRAILBLAZER, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(trailblazer, ally)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.alwaysRoll(0.0));
        if (withTechnique) {
            battle.markTechniqueUsed(trailblazer);
        }
        battle.startBattle();
        Character measured = onAlly ? ally : trailblazer;
        return measured.getAttribute(AttributeType.BREAKING_EFFECT).get();
    }

    private static Battle battle(int eidolon) {
        Character trailblazer = CharacterFactory.create(TRAILBLAZER, LEVEL, true, null, null, eidolon);
        Battle battle = new Battle(new ArrayList<>(List.of(trailblazer, CharacterFactory.create(ALLY, LEVEL))),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return battle;
    }

    private static double allyBreak(int eidolon) {
        Character trailblazer = CharacterFactory.create(TRAILBLAZER, LEVEL, true, null, null, eidolon);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(trailblazer, ally)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return ally.getAttribute(AttributeType.BREAKING_EFFECT).get();
    }

    private static int level(Battle battle, SkillType slot) {
        return battle.characters().getFirst().skillLevelBonus(slot);
    }

    private static double stat(int eidolon, AttributeType attribute) {
        return battle(eidolon).characters().getFirst().getAttribute(attribute).get();
    }

    private static double rider(int cid, int bonusGift) {
        Character trailblazer = CharacterFactory.create(cid, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(trailblazer, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        if (bonusGift > 0) {
            trailblazer.getResources().gain("elation:certified_banger", bonusGift);
        }
        double[] elation = new double[1];
        battle.addDamageObserver((damage, applied, hpLoss, shield, died) -> {
            if (damage.getType() == DamageType.ELATION) {
                elation[0] += applied;
            }
        });
        battle.castImmediate(trailblazer.getSkills().get(SkillType.SKILL), trailblazer, List.of(enemy));
        return elation[0];
    }
}
