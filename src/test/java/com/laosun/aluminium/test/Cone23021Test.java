package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 23021: [假面] at battle start; every skill point RESTORED adds that many [彩焰] layers; four layers pay out a fresh
 * [假面].
 *
 * <p>The discriminating reading is the +2 restore: `Battle.gainSkillPoint(2)` fires ONE event carrying 2, so "每恢复 1 个"
 * must add TWO layers. A judge that only ever restores one point at a time cannot tell `scale: event_amount` from a plain `amount: 1`.
 */
public class Cone23021Test {
    private static final int CONE = 23021;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int CAP = 4;
    private static final String FLAME = "彩焰";
    private static final String MASK = "假面";

    private Character wearer;
    private Character ally;
    private Enemy enemy;

    private Battle battle(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        ally = CharacterFactory.create(ALLY, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    private void makeRoom(Battle battle) {
        var skill = wearer.getSkills().values().stream()
                .filter(candidate -> candidate.getData() != null
                        && candidate.getData().getCategory() == SkillCategory.BPSKILL)
                .findFirst().orElseThrow();
        Assertions.assertTrue(battle.applySkillPointCost(skill, wearer), "made room for a restore");
    }

    @Test
    public void theMaskArrivesAtBattleStartAndCoversTheAllyOnly() {
        // Deltas, not absolutes (discipline 169): character 1002 carries its own 0.05 crit rate / 0.50 crit damage, so an
        // absolute assertion would report the character's own baseline as if the cone had produced it (measured: 0.15 / 0.8).
        Battle baseline = battle(false);
        double baseCritRate = ally.getAttribute(AttributeType.CRIT_CHANCE).get();
        double baseCritDamage = ally.getAttribute(AttributeType.CRIT_ATTACK).get();
        Battle battle = battle(true);
        double allyCritRate = ally.getAttribute(AttributeType.CRIT_CHANCE).get() - baseCritRate;
        double allyCritDamage = ally.getAttribute(AttributeType.CRIT_ATTACK).get() - baseCritDamage;
        double wearerCritRate = wearer.getAttribute(AttributeType.CRIT_CHANCE).get()
                - wearer.getAttribute(AttributeType.CRIT_CHANCE).get();
        boolean masked = wearer.getBuffManager().hasState(MASK);
        System.out.println("[23021] at battle start: masked=" + masked + " ally critRate=" + allyCritRate
                + " ally critDamage=" + allyCritDamage + " wearer critRate=" + wearerCritRate);
        Assertions.assertTrue(masked, "the wearer holds the mask");
        Assertions.assertEquals(0.10, allyCritRate, 1e-9, "队友 crit rate +10%");
        Assertions.assertEquals(0.28, allyCritDamage, 1e-9, "队友 crit damage +28%");
        Assertions.assertEquals(0.0, wearerCritRate, 1e-9, "the wearer is NOT its own 队友 (false case)");
    }

    @Test
    public void everyRestoredPointAddsThatManyLayersAndFourPayOut() {
        Battle battle = battle(true);
        makeRoom(battle);
        battle.gainSkillPoint(1);
        int one = wearer.getBuffManager().stacksOf(FLAME);
        makeRoom(battle);
        battle.gainSkillPoint(2);
        int three = wearer.getBuffManager().stacksOf(FLAME);
        makeRoom(battle);
        battle.gainSkillPoint(1);
        int after = wearer.getBuffManager().stacksOf(FLAME);
        System.out.println("[23021] flame after +1=" + one + " after +2 more=" + three + " after the fourth=" + after
                + " mask=" + wearer.getBuffManager().hasState(MASK));
        Assertions.assertEquals(1, one, "one restored point is one layer");
        Assertions.assertEquals(3, three, "a SINGLE restore of 2 points is TWO more layers -- 每恢复 1 个");
        Assertions.assertEquals(0, after, "the fourth layer pays out and clears the counter");
        Assertions.assertTrue(wearer.getBuffManager().hasState(MASK), "and it grants the mask");
    }

    /**
     * The sentence says "恢复时溢出的战技点也会被计算在内", and that is exactly what a rule listening only to
     * SKILL_POINT_GAINED gets wrong: a restore at the cap credits NOTHING, so no event fires at all -- measured before the
     * overflow event existed.
     */
    @Test
    public void overflowCountsToo() {
        Battle battle = battle(true);
        while (battle.getSkillPoints() < battle.getSkillPointMax()) {
            battle.gainSkillPoint(1);
        }
        int atCap = wearer.getBuffManager().stacksOf(FLAME);
        battle.gainSkillPoint(2);
        int afterOverflow = wearer.getBuffManager().stacksOf(FLAME);
        System.out.println("[23021] at the cap (" + battle.getSkillPoints() + "/" + battle.getSkillPointMax()
                + ") flame=" + atCap + " ; after a refused restore of 2: " + afterOverflow);
        Assertions.assertEquals(0, afterOverflow,
                "a refused restore of 2 takes the counter to 4, which PAYS OUT: back to 0 with a fresh mask");
        Assertions.assertTrue(wearer.getBuffManager().hasState(MASK),
                "the payout listens to both events -- the text says the flame REACHES 4 layers, not \"4 credited layers\"");
    }

    @Test
    public void withoutTheConeNothingHappens() {
        Battle battle = battle(false);
        makeRoom(battle);
        battle.gainSkillPoint(2);
        System.out.println("[23021] without the cone: flame=" + wearer.getBuffManager().stacksOf(FLAME)
                + " mask=" + wearer.getBuffManager().hasState(MASK));
        Assertions.assertEquals(0, wearer.getBuffManager().stacksOf(FLAME), "no cone, no layers (false case)");
        Assertions.assertFalse(wearer.getBuffManager().hasState(MASK), "no cone, no mask (false case)");
    }
}
