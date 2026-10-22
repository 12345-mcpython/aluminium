package com.laosun.aluminium.test.content.characters.archer;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1015 Archer (Archer): his Eidolons 3, 4, 5 and 6.
 *
 * <p>⚠ Every case is stated at TWO ranks: a clause that is correctly gated and one that applies for free differ only at the
 * lower rank, so a case pinned at E6 alone cannot tell them apart. ⚠ Eidolon 5's clause is a LEVEL raise, and the number it
 * moves is the talent's follow-up share - so that case is written as a ratio between the two ranks (2.2 / 2.0 = 1.1),
 * which is the property that survives any change to the fixture's attack power.
 */
public class ArcherEidolonLevelsTest {
    private static final int ARCHER = 1015;
    private static final int ALLY = 1204;
    private static final String CHARGE = "充能";
    private static final String LAYERS = "回路层数";

    /**
     * Eidolon 3: 战技 +2, 普攻 +1 (clamped by its own 9-row table). Eidolon 5: 终结技 +2, 天赋 +2.
     */
    @Test
    public void theEidolonsRaiseTheSlotsTheyName() {
        int[] atZero = levelsAt(0);
        int[] atThree = levelsAt(3);
        int[] atFive = levelsAt(5);
        System.out.println("[1015] COMMON/SKILL/ULTRA/TALENT: E0 " + shown(atZero) + " ; E3 " + shown(atThree)
                + " ; E5 " + shown(atFive));
        Assertions.assertArrayEquals(new int[]{9, 10, 10, 10}, atZero,
                "等级口径：一律 Lv10，而普攻只有 9 行 ⇒ 被它自己的表夹到 9");
        Assertions.assertArrayEquals(new int[]{9, 12, 10, 10}, atThree,
                "星魂 3：战技+2 ⇒ 第 12 行；普攻+1 正好补到 10，仍被 9 行表夹住 ⇒ 还是 9");
        Assertions.assertArrayEquals(new int[]{9, 12, 12, 12}, atFive,
                "星魂 5：终结技+2、天赋+2 ⇒ 各第 12 行（星魂 3 的战技 +2 仍在）");
    }

    /**
     * Eidolon 5 moves the talent's row: the follow-up share goes 2.0 → 2.2.
     */
    @Test
    public void theFollowUpFollowsTheTalentRow() {
        double atZero = followUpPerAttack(0);
        double atFive = followUpPerAttack(5);
        System.out.println("[1015] 追加攻击 ÷ 攻击力: E0 " + atZero + " ; E5 " + atFive
                + "（写死 2.0 的话两档一样）");
        Assertions.assertEquals(1.1, atFive / atZero, 1e-9, "星魂 5 给天赋 +2 级 ⇒ 第 12 行是 2.2（E0 是 2.0）");
    }

    /**
     * Eidolon 4: 「造成的终结技伤害提高 50%」 - the Ultimate's own boost zone gains 0.5.
     */
    @Test
    public void theEidolonFourRaisesOnlyTheUltimatesBoostZone() {
        double atZero = boostZoneOfTheUltimate(0);
        double atFour = boostZoneOfTheUltimate(4);
        System.out.println("[1015] 终结技的增伤区: E0 ×" + atZero + " ; E4 ×" + atFour);
        Assertions.assertEquals(atZero + 0.5, atFour, 1e-9, "星魂 4：终结技那一类的增伤 +50%");
    }

    /**
     * Eidolon 6's three halves: a skill point each turn, one more layer, and 20% defence ignored on skill damage.
     */
    @Test
    public void theEidolonSixHasAllThreeHalves() {
        Assertions.assertEquals(0, skillPointGainOnHerTurn(0), "E0：回合开始不给战技点");
        Assertions.assertEquals(1, skillPointGainOnHerTurn(6), "星魂 6：回合开始时恢复 1 个战技点");

        Assertions.assertEquals(2, layersAtEidolon(0), "E0：层数上限 2");
        Assertions.assertEquals(3, layersAtEidolon(6), "星魂 6：上限提高 1 层");

        double atZero = defenceFactorOfASkillHit(0);
        double atSix = defenceFactorOfASkillHit(6);
        System.out.println("[1015] 战技那发的防御区: E0 ×" + atZero + " ; E6 ×" + atSix);
        Assertions.assertTrue(atSix > atZero + 1e-9,
                "星魂 6：「战技伤害无视 20% 的防御力」⇒ 防御区变大（" + atZero + " -> " + atSix + "）");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static String shown(int[] levels) {
        return "C=" + levels[0] + " S=" + levels[1] + " U=" + levels[2] + " T=" + levels[3];
    }

    private static int[] levelsAt(int eidolonRank) {
        Character archer = newBattle(eidolonRank, 1);
        return new int[]{
                archer.skillLevel(archer.getSkills().get(SkillType.COMMON)),
                archer.skillLevel(archer.getSkills().get(SkillType.SKILL)),
                archer.skillLevel(archer.getSkills().get(SkillType.ULTRA)),
                archer.skillLevel(archer.getSkills().get(SkillType.TALENT))};
    }

    /**
     * The follow-up instance's settled damage per point of attack power (so the zones cancel between ranks).
     */
    private static double followUpPerAttack(int eidolonRank) {
        Battle battle = battle(eidolonRank);
        Character archer = battle.characters().getFirst();
        Character ally = battle.characters().get(1);
        double atk = archer.getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK).get();
        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        battle.castImmediate(ally.getSkills().get(SkillType.COMMON), ally, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        return settled.stream().filter(d -> d.getType() == DamageType.ADDITIONAL)
                .mapToDouble(d -> d.breakdown().get("final")).sum() / atk;
    }

    /**
     * The {@code BoostArea} factor of the Ultimate's own instance.
     */
    private static double boostZoneOfTheUltimate(int eidolonRank) {
        Battle battle = battle(eidolonRank);
        Character archer = battle.characters().getFirst();
        archer.setCurrentEnergy(archer.getMaxEnergy());
        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        battle.castUltra(archer, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        return settled.stream().filter(d -> d.getType() == DamageType.NORMAL)
                .map(d -> d.breakdown().get("BoostArea"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the Ultimate settled no ordinary instance"));
    }

    /**
     * How many skill points her own turn start hands the team.
     */
    private static int skillPointGainOnHerTurn(int eidolonRank) {
        Battle battle = battle(eidolonRank);
        Character archer = battle.characters().getFirst();
        int before = battle.getSkillPoints();
        battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, archer, archer, 0, 0));
        battle.processRequests();
        return battle.getSkillPoints() - before;
    }

    /**
     * The largest layer count the loop reaches after enough casts (the cap, which Eidolon 6 raises by one).
     */
    private static int layersAtEidolon(int eidolonRank) {
        Battle battle = battle(eidolonRank);
        Character archer = battle.characters().getFirst();
        Skill skill = archer.getSkills().get(SkillType.SKILL);
        for (int cast = 0; cast < 4; cast++) {
            battle.castImmediate(skill, archer, List.of(battle.enemyUnits().getFirst()));
        }
        battle.processRequests();
        return archer.getBuffManager().stacksOf(LAYERS);
    }

    /**
     * The {@code DefenceArea} factor of a plain Skill hit.
     */
    private static double defenceFactorOfASkillHit(int eidolonRank) {
        Battle battle = battle(eidolonRank);
        Character archer = battle.characters().getFirst();
        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        battle.castImmediate(archer.getSkills().get(SkillType.SKILL), archer,
                List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        return settled.stream().filter(d -> d.getType() == DamageType.NORMAL)
                .map(d -> d.breakdown().getOrDefault("DefenceArea", 1.0))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the Skill settled no ordinary instance"));
    }

    private static Character newBattle(int eidolonRank, int enemies) {
        return battle(eidolonRank, enemies, true).characters().getFirst();
    }

    private static Battle battle(int eidolonRank) {
        return battle(eidolonRank, 1, false);
    }

    /**
     * A battle whose Charge pool is topped up, so the talent's follow-up is available whatever the Eidolon rank.
     */
    private static Battle battle(int eidolonRank, int enemies, boolean topUpCharge) {
        List<com.laosun.aluminium.models.enemy.Enemy> foes = new ArrayList<>();
        for (int i = 0; i < enemies; i++) {
            // Note: a REAL monster rather than the 0-DEF dummy: the defence-ignore reading needs a defence factor that is
            // not 1.0 (measured: with the dummy it reads 1.0 -> 1.0 and proves nothing).
            foes.add(BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL));
        }
        Battle battle = new Battle(
                List.of(CharacterFactory.create(ARCHER, 80, false, null, null, eidolonRank),
                        CharacterFactory.create(ALLY, 80)),
                foes, BattleFixture.alwaysRoll(0.9999));
        battle.startBattle();
        battle.processRequests();
        battle.characters().getFirst().getResources().get(CHARGE).setValue(4);
        return battle;
    }
}
