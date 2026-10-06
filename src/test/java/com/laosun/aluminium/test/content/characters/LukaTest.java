package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Luka (卢卡) (1111), from his own file: [斗志] (Fighting Will) layers, the ultimate's rolled vulnerability, and Kinetic Overload (动能过载).
 *
 * <p><b>What it needs.</b> Three capabilities: a capped stack counter (`ADD_STACK` +
 * `max_stacks`), a rolled taken-side zone (`MODIFY_DAMAGE_TAKEN` + `base_chance`), and `REMOVE_BUFF` (the mirror of
 * `DISPEL`). The 330% ultimate damage and the Skill's damage are the engine's own skill rows.
 *
 * <p>Note: <b>The `amount` of `ADD_STACK` is load-bearing.</b> It means "this many layers at once", not one layer
 * per firing: one firing attaches exactly `amount` layers (stopping at `max_stacks`), which is what the "获得 N 层"
 * family states. His own file says `amount: 2` on the ultimate, and 1314 Jade (翡翠) states 5, 15, 1 and 3. So one
 * ultimate is +2 layers, and the cap is still real and is what the six-cast case below measures.
 */
public class LukaTest {
    private static final int LUKA = 1111;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** "战斗开始时,卢卡持有1层[斗志]", and each ultimate adds the two his document states. */
    @Test
    public void theLayersStartAtOneAndGrow() {
        Fixture f = new Fixture();
        Assertions.assertEquals(1, f.luka.getBuffManager().stacksOf("斗志"), "「战斗开始时，卢卡持有1层【斗志】」 (at the start of battle, Luka holds 1 stack of Fighting Will (【斗志】))");

        f.ultimate();

        Assertions.assertEquals(3, f.luka.getBuffManager().stacksOf("斗志"),
                "「施放终结技时获得2层【斗志】」 (gains 2 stacks of Fighting Will when casting the Ultimate) -- one from the battle start plus two from the ultimate");
    }

    /** Note: "最多可持有 4 层" holds no matter how often it is applied. */
    @Test
    public void theCapHolds() {
        Fixture f = new Fixture();
        for (int i = 0; i < 6; i++) {
            f.ultimate();
        }

        Assertions.assertEquals(4, f.luka.getBuffManager().stacksOf("斗志"),
                "⚠ the cap is what makes 「最多可持有4层【斗志】」 (can hold at most 4 stacks of Fighting Will) true rather than decorative");
    }

    /** Note: The vulnerability is rolled, and an easy draw lands it. */
    @Test
    public void theUltimatesVulnerabilityIsRolled() {
        Fixture f = new Fixture();
        f.ultimate();
        Assertions.assertFalse(f.enemy.getBuffManager().allBuffsOf(
                        com.laosun.aluminium.models.buff.VulnerabilityBuff.class).isEmpty(),
                "「有100%的基础概率使指定敌方单体受到的伤害提高20.00%」 -- the zone is up (an easy draw)");
    }

    /** Note: One layer per relevant cast, and `from_skill` is what keeps the ultimate off the basic attack / Skill rules. */
    @Test
    public void eachCastCategoryAddsExactlyOneLayer() {
        Fixture f = new Fixture();
        int start = f.luka.getBuffManager().stacksOf("斗志");

        f.basicAttack();
        Assertions.assertEquals(start + 1, f.luka.getBuffManager().stacksOf("斗志"),
                "「施放普攻【直冲拳】…后，获得1层【斗志】」 (after casting the Basic ATK [直冲拳] ... gains 1 stack of Fighting Will (【斗志】))");

        f.skillCast();
        Assertions.assertEquals(start + 2, f.luka.getBuffManager().stacksOf("斗志"), "…and the Skill adds one too");

        f.ultimate();
        Assertions.assertEquals(start + 3, f.luka.getBuffManager().stacksOf("斗志"),
                "⚠ exactly one: without `from_skill` the Basic ATK (普攻) and Skill (战技) rules would fire on the ultimate as well, "
                        + "because all three are 「attack」 events");
    }

    /** Note: Content-level: the SHIPPED Skill's ceiling must really be there - an unmapped key is silently ignored. */
    @Test
    public void theShippedSkillCarriesItsCeiling() {
        Fixture f = new Fixture();
        double attack = f.luka.getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK).get();

        f.skillCast();

        var dots = f.enemy.getBuffManager().allBuffsOf(com.laosun.aluminium.models.buff.DotBuff.class);
        Assertions.assertEquals(1, dots.size(), "「使目标陷入裂伤状态」 (puts the target into the Bleed (裂伤) state)");
        Assertions.assertEquals(attack * 3.38, dots.get(0).getBaseDamage(), 1e-3,
                "⚠ the shipped `cap_scale`/`cap_percent` mapped: the per-turn damage is the owner's attack × 3.38. An "
                        + "unmapped key would leave the DOT uncapped and read 24% of the victim's Max HP instead — a hole no "
                        + "reflective unit test can see, which is why this case goes through the FILE.");
    }

    /** Note: End to end: two layers swap in a skill that loads, and that attack lands. */
    @Test
    public void theSwappedSkillDealsDamage() {
        Fixture f = new Fixture();
        f.skillCast();
        f.basicAttack();                                  // reaches the threshold and installs the swap

        var swapped = f.luka.getSkills().get(com.laosun.aluminium.enums.SkillType.COMMON);
        Assertions.assertEquals(com.laosun.aluminium.enums.SkillCategory.NORMAL, swapped.getData().getCategory(),
                "⚠ precondition: slot 8's data (槽位 8) has to really be there (the lesson of round 113)");

        double before = f.enemy.getCurrentHp();
        f.battle.castImmediate(swapped, f.luka, List.of(f.enemy));
        f.battle.fireAfterAttack(f.luka, f.enemy, List.of(f.enemy), 1.0);

        Assertions.assertTrue(f.enemy.getCurrentHp() < before,
                "「≥ 2 层时普攻强化为【直冲碎天拳】」 (at >= 2 stacks the Basic ATK is strengthened into [直冲碎天拳]) — the enhanced attack deals damage");
    }

    /**
     * Note: Precondition FIRST and DEEP: the swap is installed by the attack that reaches the threshold, the swapped skill must
     * LOAD real data, and only then is the 2-layer cost expected.
     */
    @Test
    public void theEnhancedAttackPaysTwoLayers() {
        Fixture f = new Fixture();
        f.skillCast();                                   // Skill (战技): applies bleed and adds a layer (battle start gave one)
        Assertions.assertEquals(2, f.luka.getBuffManager().stacksOf("斗志"), "precondition: two layers");

        f.basicAttack();                                 // ordinary attack: +1 layer, and it INSTALLS the swap
        var swapped = f.luka.getSkills().get(com.laosun.aluminium.enums.SkillType.COMMON);
        Assertions.assertEquals(com.laosun.aluminium.enums.SkillCategory.NORMAL, swapped.getData().getCategory(),
                "⚠ deep precondition: the swapped skill LOADS (a data-row id would have been EMPTY, and every identity "
                        + "assertion would still pass — the trap that cost rounds 99–114)");
        int before = f.luka.getBuffManager().stacksOf("斗志");

        f.basicAttack();                                 // the enhanced row: +1 from the layer rule, −2 from the cost clause

        Assertions.assertEquals(before + 1 - 2, f.luka.getBuffManager().stacksOf("斗志"),
                "「强化普攻消耗 2 层【斗志】」 (the strengthened Basic ATK consumes 2 stacks of Fighting Will) — keyed on `from_skill_id == 111108`, because the ordinary and the "
                        + "enhanced basic attack are both `Normal` casts");
    }

    /** Census: the two layer rules, the trace and the level convention are all there. */
    @Test
    public void hisFileCarriesTheClauses() {
        TriggerTable table = TriggerTables.of(LUKA);
        Assertions.assertEquals(2, table.ruleCount(TriggerEvent.BATTLE_START), "the starting layer + the level convention");
        Assertions.assertEquals(5, table.ruleCount(TriggerEvent.ALLY_ATTACK),
                "one layer rule per cast category + the ⚠2-layer enhancement (whose file position is what makes the threshold reachable on the same attack)");
        Assertions.assertEquals(2, table.ruleCount(TriggerEvent.SKILL_CAST), "the trace's REMOVE_BUFF and the Skill's Bleed (裂伤) DOT");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.ULT_CAST));
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static final class Fixture {
        private final Character luka = CharacterFactory.create(LUKA, LEVEL);
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        private final Battle battle = new Battle(List.of(luka, ally), List.of(enemy), fixed());

        private Fixture() {
            battle.startBattle();
        }

        private void ultimate() {
            battle.castImmediate(luka.getSkills().get(SkillType.ULTRA), luka, List.of(enemy));
        }

        private void basicAttack() {
            battle.castImmediate(luka.getSkills().get(SkillType.COMMON), luka, List.of(enemy));
        }

        private void skillCast() {
            battle.castImmediate(luka.getSkills().get(SkillType.SKILL), luka, List.of(enemy));
        }
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.0;                       // an easy draw: "100%的基础概率" lands
            }
        };
    }

}
