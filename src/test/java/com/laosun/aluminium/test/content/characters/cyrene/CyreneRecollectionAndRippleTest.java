package com.laosun.aluminium.test.content.characters.cyrene;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.ControlBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1415 昔涟's 【追忆】 (Recollection) economy and the 【往昔的涟漪】 state that re-tiers it: 3 points from the enhanced
 * basic and 1 from the ordinary one, the cap of 24 with its overflow to 27, the cleanse at 24 (and at 12 only inside the
 * ripple), and the ripple's last clause - the other slots are closed while it holds.
 *
 * <p>Merged from the former {@code CyreneRecollectionTest}, {@code CyreneRecollectionCapTest},
 * {@code CyreneRippleTierTest} and {@code CyreneRippleClosesOtherSlotsTest}; every test, assertion, helper and
 * class-level note is preserved.
 *
 * <p><b>L3:</b> the two pairs that read one board are folded into scenario tests ({@code Assertions.assertAll}), one
 * message per clause reading: the basic attack's 1 outside the ripple and 3 inside it, and the cap of 24 with the cleanse
 * a full pool performs. Every assertion, expected value, tolerance and message is unchanged. The ripple's twelve-point
 * tier (its own board, entered by the ultimate), the skill's refusal inside the ripple and its castable counterpart
 * outside it (a rejection against a positive, on opposite states) keep their own tests - an {@code assertAll} is not
 * where either belongs.
 */
public class CyreneRecollectionAndRippleTest {

    private static final int CYRENE = 1415;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    // The resource and the state it is re-tiered by; the absorbed files called the state STATE and the resource MEMORY.
    private static final String RECOLLECTION = "char:1415/remembrance";
    private static final String RIPPLE = "char:1415/ripples_of_the_past";

    // ---------------------------------------------------------------------------------------------------------------
    // Former CyreneRecollectionTest: the enhanced basic's 3 points, and the ordinary basic's 1 outside the ripple.
    // ---------------------------------------------------------------------------------------------------------------
    /*
     * 1415 昔涟's <b>enhanced basic</b> 「向着爱与明天♪」 (141508) and the 【追忆】 its first clause states.
     *
     * <p>The row's sentence is 「获得 <b>#2</b> 点【追忆】并对指定敌方单体造成…#3%…随后对敌方全体…#1%…」 - #2 is 3 at every level,
     * and it is a row value, so the rule reads {@code cast_param} 1 rather than a literal.
     *
     * <p>⚠ <b>Why the ordinary basic needed a gate.</b> The enhanced basic is still a {@code Normal} attack, so the
     * ordinary 「获得 #2 点」 (+1) rule fired for it as well: 1 + 3 where the document says 3. The ordinary rule now says
     * "not while 【往昔的涟漪】 holds" - which is exactly when slot 8 is installed - and both sentences stay true.
     */

    /**
     * The basic attack's 【追忆】 clause read on ONE battle: the ordinary row pays its own 1 outside the ripple, then the
     * ultimate installs the enhanced row and a cast of that row pays exactly 3 - the ordinary +1 must not fire inside the
     * ripple.
     */
    @Test
    public void theBasicAttackPaysOneOutsideTheRippleAndThreeInside() {
        Character cyrene = CharacterFactory.create(CYRENE, 80);
        Battle battle = new Battle(List.of(cyrene), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        double beforeOrdinary = cyrene.getResources().get(RECOLLECTION).exactValue();

        SkillExecutor.execute(battle, cyrene.getSkills().get(SkillType.COMMON), cyrene,
                List.of(battle.enemies().getFirst()));
        battle.processRequests();
        double afterOrdinary = cyrene.getResources().get(RECOLLECTION).exactValue();

        SkillExecutor.execute(battle, cyrene.getSkills().get(SkillType.ULTRA), cyrene,
                List.of(battle.enemies().getFirst()));
        battle.processRequests();
        Skill enhanced = cyrene.getSkills().get(SkillType.COMMON);
        boolean installed = enhanced.getData().getDescription().contains("向着爱与明天");
        double before = cyrene.getResources().get(RECOLLECTION).exactValue();

        SkillExecutor.execute(battle, enhanced, cyrene, List.of(battle.enemies().getFirst()));
        battle.processRequests();
        double after = cyrene.getResources().get(RECOLLECTION).exactValue();

        Assertions.assertAll("the basic attack's 「获得 #2 点【char:1415/remembrance】」, outside and inside 【char:1415/ripples_of_the_past】",
                () -> Assertions.assertEquals(beforeOrdinary + 1.0, afterOrdinary, 1e-9,
                        "the ordinary basic's own 「获得 #2 点」 (1) still fires when 【char:1415/ripples_of_the_past】 is not up"),
                () -> Assertions.assertTrue(installed, "precondition: the ultimate installed the enhanced basic"),
                () -> Assertions.assertEquals(3.0, after - before, 1e-9,
                        "「获得 #2 点【char:1415/remembrance】」 = 3; the ordinary basic's +1 must not also fire inside the ripple"));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Former CyreneRecollectionCapTest: the cap at 24, its overflow to 27, and the cleanse a full pool performs.
    // ---------------------------------------------------------------------------------------------------------------
    /*
     * "[追忆] (Recollection) reaching 24 points activates the ultimate and removes all of her own negative effects" and "may overflow up to 2 points".
     */

    /**
     * The cap, its overflow allowance and the cleanse on ONE board: 24 with an overflow of 3, then a debuff carried into
     * a cast that reaches the cap, which is what a full 【追忆】 removes.
     */
    @Test
    public void theCapOverflowsToTwentySevenAndAFullPoolCleansesHer() {
        Character her = CharacterFactory.create(CYRENE, LEVEL);
        Battle battle = new Battle(List.of(her), List.of(EnemyFactory.create(MONSTER, 100, 1)), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        her = battle.characters().getFirst();
        var res = her.getResources().get(RECOLLECTION);
        int cap = res.getMax();
        int overflow = res.getMaxOverflow();
        System.out.println("[recollection] max=" + cap + " overflow=" + overflow);
        var control = com.laosun.aluminium.Constant.CONTROL_EFFECTS.get("IMPRISONED");
        her.getBuffManager().addBuff(new ControlBuff(control, 3));
        int debuffsBefore = her.getBuffManager().debuffCount();
        her.getResources().gain(RECOLLECTION, 23);
        var basic = her.getSkills().get(SkillType.COMMON);
        com.laosun.aluminium.skill.SkillExecutor.execute(battle, basic, her,
                List.of(battle.enemies().getFirst()));
        battle.processRequests();
        int left = battle.characters().getFirst().getBuffManager().debuffCount();
        System.out.println("[recollection] after reaching 24, her debuffs read " + left);
        Assertions.assertAll("【char:1415/remembrance】's stated cap with its overflow, and the cleanse a full pool performs",
                () -> Assertions.assertEquals(24, cap, "the stated cap"),
                () -> Assertions.assertEquals(3, overflow, "and it may overflow to 27"),
                () -> Assertions.assertTrue(debuffsBefore > 0, "precondition: she carries a debuff"),
                () -> Assertions.assertEquals(0, left, "a full [char:1415/remembrance] (Recollection) cleanses her"));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Former CyreneRippleTierTest: inside the ripple twelve points already cleanse her; outside it, they do not.
    // ---------------------------------------------------------------------------------------------------------------
    /*
     * "处于[往昔的涟漪]状态时在[追忆]达到 12 点时可激活终结技".
     */

    @Test
    public void twelveCleansesOnlyInsideTheRipple() {
        int insideRipple = debuffsLeft(true);
        int outside = debuffsLeft(false);
        System.out.println("[ripple] at twelve points: debuffs left inside the ripple = " + insideRipple
                + " ; outside it = " + outside);
        Assertions.assertEquals(0, insideRipple, "inside the ripple, twelve points cleanse her");
        Assertions.assertTrue(outside > 0, "outside it, twelve is not enough");
    }

    private static int debuffsLeft(boolean enterTheRipple) {
        Character her = CharacterFactory.create(CYRENE, LEVEL);
        Battle battle = new Battle(List.of(her), List.of(EnemyFactory.create(MONSTER, 100, 1)), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        her = battle.characters().getFirst();
        Assertions.assertTrue(her.getSkills().containsKey(SkillType.ULTRA), "precondition: her ultimate");
        com.laosun.aluminium.skill.SkillExecutor.execute(battle, her.getSkills().get(SkillType.ULTRA), her,
                List.of(battle.enemies().getFirst()));
        battle.processRequests();
        her = battle.characters().getFirst();
        if (!enterTheRipple) {
            her.getBuffManager().removeState(RIPPLE);
        }
        Assertions.assertEquals(enterTheRipple, her.getBuffManager().hasState(RIPPLE),
                "precondition: the ripple is " + (enterTheRipple ? "up" : "down"));
        var control = com.laosun.aluminium.Constant.CONTROL_EFFECTS.get("IMPRISONED");
        her.getBuffManager().addBuff(new ControlBuff(control, 3));
        her.getResources().gain(RECOLLECTION, 11);
        // her basic is the writer that carries her to twelve
        com.laosun.aluminium.skill.SkillExecutor.execute(battle, her.getSkills().get(SkillType.COMMON), her,
                List.of(battle.enemies().getFirst()));
        battle.processRequests();
        return battle.characters().getFirst().getBuffManager().debuffCount();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Former CyreneRippleClosesOtherSlotsTest: 「仅能使用该普攻」 - the other slots are closed inside the ripple.
    // ---------------------------------------------------------------------------------------------------------------
    /*
     * 「普攻强化为【向着爱与明天♪】且<b>仅能使用该普攻</b>」 - 1415's ultimate's last clause.
     *
     * <p><b>How it is expressed.</b> The clause is put on the rows it excludes ({@code castable_when} on the skill and on the
     * ultimate: "not while 【往昔的涟漪】 holds") rather than as a whitelist the engine would have to enforce: the enhanced
     * basic is installed by {@code REPLACE_SKILL}, so "the only basic is the enhanced one" is already true, and what needed
     * saying is that the other slots are closed during the state.
     *
     * <p>⚠ <b>A reading, registered.</b> The sentence names only the basic attack; whether the ripple form keeps a skill at all
     * is not stated in the rows this repo has. The reading is therefore written as data - one line per slot - so a fuller
     * skill table can overturn it without touching the engine.
     */

    private static Battle battle() {
        Character cyrene = CharacterFactory.create(CYRENE, 80);
        Battle battle = new Battle(List.of(cyrene), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        return battle;
    }

    private static void enterRipple(Battle battle, Character cyrene) {
        cyrene.getBuffManager().addBuff(new com.laosun.aluminium.buff.StateBuff(RIPPLE, 99));
    }

    /**
     * Inside the ripple the skill is refused - and, being refused, it settles nothing.
     */
    @Test
    public void theSkillIsRefusedInsideTheRipple() {
        Battle battle = battle();
        Character cyrene = battle.characters().getFirst();
        enterRipple(battle, cyrene);
        Skill skill = cyrene.getSkills().get(SkillType.SKILL);

        Assertions.assertFalse(battle.castImmediate(skill, cyrene, List.of(battle.enemies().getFirst())),
                "「仅能使用该普攻」: the skill slot is closed while the ripple holds");
    }

    /**
     * Outside the ripple the same skill is castable - the gate is the state, not the slot.
     */
    @Test
    public void theSkillIsCastableOutsideTheRipple() {
        Battle battle = battle();
        Character cyrene = battle.characters().getFirst();
        Skill skill = cyrene.getSkills().get(SkillType.SKILL);

        Assertions.assertTrue(battle.castImmediate(skill, cyrene, List.of(battle.enemies().getFirst())),
                "outside the ripple the skill works as before");
    }

    /**
     * The enhanced basic is the one thing still usable inside the ripple.
     */
    @Test
    public void theEnhancedBasicStillWorksInsideTheRipple() {
        Battle battle = battle();
        Character cyrene = battle.characters().getFirst();
        enterRipple(battle, cyrene);
        // Install the enhanced basic the way the ultimate does (REPLACE_SKILL), then cast it.
        SkillExecutor.execute(battle, cyrene.getSkills().get(SkillType.ULTRA), cyrene,
                List.of(battle.enemies().getFirst()));
        battle.processRequests();
        Skill enhanced = cyrene.getSkills().get(SkillType.COMMON);
        Assertions.assertTrue(enhanced.getData().getDescription().contains("向着爱与明天"),
                "precondition: the enhanced basic is installed");

        Assertions.assertTrue(battle.castImmediate(enhanced, cyrene, List.of(battle.enemies().getFirst())),
                "「普攻强化为【向着爱与明天♪】」: the enhanced basic is castable inside the ripple");
    }
}
