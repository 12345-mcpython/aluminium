package com.laosun.aluminium.test;

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
 * 卢卡 (1111), from his own file (2026-09-28): 【斗志】 layers, the ultimate's rolled vulnerability, and 动能过载.
 *
 * <p><b>What it needed.</b> Three capabilities that landed in the days before: a capped stack counter (`ADD_STACK` +
 * `max_stacks`), a rolled taken-side zone (`MODIFY_DAMAGE_TAKEN` + `base_chance`), and `REMOVE_BUFF` (the mirror of
 * `DISPEL`). The 330% ultimate damage and the Skill's damage are the engine's own skill rows.
 *
 * <p>⚠ <b>A measured correction, since closed (2026-09-29).</b> This class used to record that `ADD_STACK`'s `amount`
 * did <b>not</b> mean "this many layers at once" — one firing added exactly <b>one</b> layer whatever `amount` said — so
 * 「获得2层【斗志】」 could not be said and his file registered it rather than pretending. `ADD_STACK` now attaches
 * `amount` layers (stopping at `max_stacks`), which is what the 「获得 N 层」 family states: his own file already says
 * `amount: 2` on the ultimate, and 1314 翡翠 states 5, 15, 1 and 3. So one ultimate is +2 layers, and the cap is still
 * real and is what the six-cast case below measures.
 */
public class LukaTest {
    private static final int LUKA = 1111;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** 「战斗开始时，卢卡持有1层【斗志】」, and each ultimate adds the two his document states. */
    @Test
    public void theLayersStartAtOneAndGrow() {
        Fixture f = new Fixture();
        Assertions.assertEquals(1, f.luka.getBuffManager().stacksOf("斗志"), "「战斗开始时，卢卡持有1层【斗志】」");

        f.ultimate();

        Assertions.assertEquals(3, f.luka.getBuffManager().stacksOf("斗志"),
                "\u300c\u65bd\u653e\u7ec8\u7ed3\u6280\u65f6\u83b7\u5f972\u5c42\u3010\u6597\u5fd7\u3011\u300d -- one from the battle start plus two from the ultimate");
    }

    /** ⚠ 「最多可持有 4 层」 holds no matter how often it is applied. */
    @Test
    public void theCapHolds() {
        Fixture f = new Fixture();
        for (int i = 0; i < 6; i++) {
            f.ultimate();
        }

        Assertions.assertEquals(4, f.luka.getBuffManager().stacksOf("斗志"),
                "⚠ the cap is what makes 「最多可持有4层【斗志】」 true rather than decorative");
    }

    /** ⚠ The vulnerability is rolled, and an easy draw lands it. */
    @Test
    public void theUltimatesVulnerabilityIsRolled() {
        Fixture f = new Fixture();
        f.ultimate();
        Assertions.assertFalse(f.enemy.getBuffManager().allBuffsOf(
                        com.laosun.aluminium.models.buff.VulnerabilityBuff.class).isEmpty(),
                "「有100%的基础概率使指定敌方单体受到的伤害提高20.00%」 -- the zone is up (an easy draw)");
    }

    /** ⚠ One layer per relevant cast, and `from_skill` is what keeps the ultimate off the 普攻/战技 rules. */
    @Test
    public void eachCastCategoryAddsExactlyOneLayer() {
        Fixture f = new Fixture();
        int start = f.luka.getBuffManager().stacksOf("斗志");

        f.basicAttack();
        Assertions.assertEquals(start + 1, f.luka.getBuffManager().stacksOf("斗志"),
                "「施放普攻【直冲拳】…后，获得1层【斗志】」");

        f.skillCast();
        Assertions.assertEquals(start + 2, f.luka.getBuffManager().stacksOf("斗志"), "…and the Skill adds one too");

        f.ultimate();
        Assertions.assertEquals(start + 3, f.luka.getBuffManager().stacksOf("斗志"),
                "⚠ exactly one: without `from_skill` the 普攻 and 战技 rules would fire on the ultimate as well, "
                        + "because all three are 「attack」 events");
    }

    /** ⚠ Content-level: the SHIPPED Skill's ceiling must really be there — an unmapped key is silently ignored. */
    @Test
    public void theShippedSkillCarriesItsCeiling() {
        Fixture f = new Fixture();
        double attack = f.luka.getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK).get();

        f.skillCast();

        var dots = f.enemy.getBuffManager().allBuffsOf(com.laosun.aluminium.models.buff.DotBuff.class);
        Assertions.assertEquals(1, dots.size(), "「使目标陷入裂伤状态」");
        Assertions.assertEquals(attack * 3.38, dots.get(0).getBaseDamage(), 1e-3,
                "⚠ the shipped `cap_scale`/`cap_percent` mapped: the per-turn damage is the owner's attack × 3.38. An "
                        + "unmapped key would leave the DOT uncapped and read 24% of the victim's Max HP instead — a hole no "
                        + "reflective unit test can see, which is why this case goes through the FILE.");
    }

    /** ⚠ End to end: two layers swap in a skill that loads, and that attack lands. */
    @Test
    public void theSwappedSkillDealsDamage() {
        Fixture f = new Fixture();
        f.skillCast();
        f.basicAttack();                                  // reaches the threshold and installs the swap

        var swapped = f.luka.getSkills().get(com.laosun.aluminium.enums.SkillType.COMMON);
        Assertions.assertEquals(com.laosun.aluminium.enums.SkillCategory.NORMAL, swapped.getData().getCategory(),
                "⚠ precondition: 槽位 8 的数据必须真的在（第 113 轮的教训）");

        double before = f.enemy.getCurrentHp();
        f.battle.castImmediate(swapped, f.luka, List.of(f.enemy));
        f.battle.fireAfterAttack(f.luka, f.enemy, List.of(f.enemy), 1.0);

        Assertions.assertTrue(f.enemy.getCurrentHp() < before,
                "「≥ 2 层时普攻强化为【直冲碎天拳】」 — the enhanced attack deals damage");
    }

    /**
     * ⚠ Precondition FIRST and DEEP: the swap is installed by the attack that reaches the threshold, the swapped skill must
     * LOAD real data, and only then is the 2-layer cost expected.
     */
    @Test
    public void theEnhancedAttackPaysTwoLayers() {
        Fixture f = new Fixture();
        f.skillCast();                                   // 战技: applies 裂伤 and adds a layer (battle start gave one)
        Assertions.assertEquals(2, f.luka.getBuffManager().stacksOf("斗志"), "precondition: two layers");

        f.basicAttack();                                 // ordinary attack: +1 layer, and it INSTALLS the swap
        var swapped = f.luka.getSkills().get(com.laosun.aluminium.enums.SkillType.COMMON);
        Assertions.assertEquals(com.laosun.aluminium.enums.SkillCategory.NORMAL, swapped.getData().getCategory(),
                "⚠ deep precondition: the swapped skill LOADS (a data-row id would have been EMPTY, and every identity "
                        + "assertion would still pass — the trap that cost rounds 99–114)");
        int before = f.luka.getBuffManager().stacksOf("斗志");

        f.basicAttack();                                 // the enhanced row: +1 from the layer rule, −2 from the cost clause

        Assertions.assertEquals(before + 1 - 2, f.luka.getBuffManager().stacksOf("斗志"),
                "「强化普攻消耗 2 层【斗志】」 — keyed on `from_skill_id == 111108`, because the ordinary and the "
                        + "enhanced basic attack are both `Normal` casts");
    }

    /** Census: the two layer rules, the trace and the level convention are all there. */
    @Test
    public void hisFileCarriesTheClauses() {
        TriggerTable table = TriggerTables.of(LUKA);
        Assertions.assertEquals(2, table.ruleCount(TriggerEvent.BATTLE_START), "the starting layer + the level convention");
        Assertions.assertEquals(5, table.ruleCount(TriggerEvent.ALLY_ATTACK),
                "one layer rule per cast category + the ⚠2-layer enhancement (whose file position is what makes the threshold reachable on the same attack)");
        Assertions.assertEquals(2, table.ruleCount(TriggerEvent.SKILL_CAST), "the trace's REMOVE_BUFF and the Skill's 裂伤 DOT");
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
                return 0.0;                       // an easy draw: 「100%的基础概率」 lands
            }
        };
    }

}
