package com.laosun.aluminium.test.content.characters.luka;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.buff.DotBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Luka (卢卡) (1111), from his own file: [斗志] (Fighting Will) layers, the ultimate's rolled vulnerability and Kinetic
 * Overload (动能过载), the enhanced Basic ATK cycle, the two written traces, and the technique.
 *
 * <p><b>What it needs.</b> Three capabilities: a capped stack counter (`ADD_STACK` + `max_stacks`), a rolled taken-side zone
 * (`MODIFY_DAMAGE_TAKEN` + `base_chance`), and `REMOVE_BUFF` (the mirror of `DISPEL`). The 330% ultimate damage and the
 * Skill's damage are the engine's own skill rows.
 *
 * <p>Note: <b>The `amount` of `ADD_STACK` is load-bearing.</b> It means "this many layers at once", not one layer per
 * firing: one firing attaches exactly `amount` layers (stopping at `max_stacks`), which is what the "获得 N 层" family
 * states. His own file says `amount: 2` on the ultimate, and 1314 Jade (翡翠) states 5, 15, 1 and 3. So one ultimate is +2
 * layers, and the cap is still real and is what the six-cast case below measures.
 *
 * <p>Traces: 行迹 粉碎斗志 is written ({@code chance: 0.5} on each segment of the enhanced Basic ATK, an ADDITIONAL
 * instance that cannot re-trigger itself) and 循环制动 reads {@code STACK_GAINED}. The 2026-11-04 segment table's
 * {@code times} field gives the enhanced Basic ATK its four base segments (3 × 20% + 1 × 80%).
 */
public class LukaTest {
    private static final int LUKA = 1111;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final String FIGHTING_SPIRIT = "char:1111/fighting_spirit";

    /**
     * "战斗开始时,卢卡持有1层[斗志]", and each ultimate adds the two his document states.
     */
    @Test
    public void theLayersStartAtOneAndGrow() {
        Fixture f = new Fixture();
        Assertions.assertEquals(1, f.luka.getBuffManager().stacksOf("char:1111/fighting_spirit"), "「战斗开始时，卢卡持有1层【char:1111/fighting_spirit】」 (at the start of battle, Luka holds 1 stack of Fighting Will (【char:1111/fighting_spirit】))");

        f.ultimate();

        Assertions.assertEquals(3, f.luka.getBuffManager().stacksOf("char:1111/fighting_spirit"),
                "「施放终结技时获得2层【char:1111/fighting_spirit】」 (gains 2 stacks of Fighting Will when casting the Ultimate) -- one from the battle start plus two from the ultimate");
    }

    /**
     * Note: "最多可持有 4 层" holds no matter how often it is applied.
     */
    @Test
    public void theCapHolds() {
        Fixture f = new Fixture();
        for (int i = 0; i < 6; i++) {
            f.ultimate();
        }

        Assertions.assertEquals(4, f.luka.getBuffManager().stacksOf("char:1111/fighting_spirit"),
                "⚠ the cap is what makes 「最多可持有4层【char:1111/fighting_spirit】」 (can hold at most 4 stacks of Fighting Will) true rather than decorative");
    }

    /**
     * Note: The vulnerability is rolled, and an easy draw lands it.
     */
    @Test
    public void theUltimatesVulnerabilityIsRolled() {
        Fixture f = new Fixture();
        f.ultimate();
        Assertions.assertFalse(f.enemy.getBuffManager().allBuffsOf(
                        com.laosun.aluminium.buff.VulnerabilityBuff.class).isEmpty(),
                "「有100%的基础概率使指定敌方单体受到的伤害提高20.00%」 -- the zone is up (an easy draw)");
    }

    /**
     * Note: One layer per relevant cast, and `from_skill` is what keeps the ultimate off the basic attack / Skill rules.
     */
    @Test
    public void eachCastCategoryAddsExactlyOneLayer() {
        Fixture f = new Fixture();
        int start = f.luka.getBuffManager().stacksOf("char:1111/fighting_spirit");

        f.basicAttack();
        Assertions.assertEquals(start + 1, f.luka.getBuffManager().stacksOf("char:1111/fighting_spirit"),
                "「施放普攻【直冲拳】…后，获得1层【char:1111/fighting_spirit】」 (after casting the Basic ATK [直冲拳] ... gains 1 stack of Fighting Will (【char:1111/fighting_spirit】))");

        f.skillCast();
        Assertions.assertEquals(start + 2, f.luka.getBuffManager().stacksOf("char:1111/fighting_spirit"), "…and the Skill adds one too");

        f.ultimate();
        Assertions.assertEquals(start + 3, f.luka.getBuffManager().stacksOf("char:1111/fighting_spirit"),
                "⚠ exactly one: without `from_skill` the Basic ATK (普攻) and Skill (战技) rules would fire on the ultimate as well, "
                        + "because all three are 「attack」 events");
    }

    /**
     * Note: Content-level: the SHIPPED Skill's ceiling must really be there - an unmapped key is silently ignored.
     */
    @Test
    public void theShippedSkillCarriesItsCeiling() {
        Fixture f = new Fixture();
        double attack = f.luka.getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK).get();

        f.skillCast();

        var dots = f.enemy.getBuffManager().allBuffsOf(com.laosun.aluminium.buff.DotBuff.class);
        Assertions.assertEquals(1, dots.size(), "「使目标陷入裂伤状态」 (puts the target into the Bleed (dot:bleed) state)");
        Assertions.assertEquals(attack * 3.38, dots.get(0).getBaseDamage(), 1e-3,
                "⚠ the shipped `cap_scale`/`cap_percent` mapped: the per-turn damage is the owner's attack × 3.38. An "
                        + "unmapped key would leave the DOT uncapped and read 24% of the victim's Max HP instead — a hole no "
                        + "reflective unit test can see, which is why this case goes through the FILE.");
    }

    /**
     * Note: End to end: two layers swap in a skill that loads, and that attack lands.
     */
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
        Assertions.assertEquals(2, f.luka.getBuffManager().stacksOf("char:1111/fighting_spirit"), "precondition: two layers");

        f.basicAttack();                                 // ordinary attack: +1 layer, and it INSTALLS the swap
        var swapped = f.luka.getSkills().get(com.laosun.aluminium.enums.SkillType.COMMON);
        Assertions.assertEquals(com.laosun.aluminium.enums.SkillCategory.NORMAL, swapped.getData().getCategory(),
                "⚠ deep precondition: the swapped skill LOADS (a data-row id would have been EMPTY, and every identity "
                        + "assertion would still pass — the trap that cost rounds 99–114)");
        int before = f.luka.getBuffManager().stacksOf("char:1111/fighting_spirit");

        f.basicAttack();                                 // the enhanced row: +1 from the layer rule, −2 from the cost clause

        Assertions.assertEquals(before + 1 - 2, f.luka.getBuffManager().stacksOf("char:1111/fighting_spirit"),
                "「强化普攻消耗 2 层【char:1111/fighting_spirit】」 (the strengthened Basic ATK consumes 2 stacks of Fighting Will) — keyed on `from_skill_id == 111108`, because the ordinary and the "
                        + "enhanced basic attack are both `Normal` casts");
    }

    /**
     * 卢卡's ultimate: 「获得 2 层【斗志】」 - ONE application that grants <b>two</b> layers.
     *
     * <p>The file's note says `amount` grants one mark ("measured"), i.e. the clause is registered as unwritable. That reading
     * is stale: {@code ADD_STACK} reads {@code amount} as the number of layers (its own comment cites 1314's 「立即获得 15
     * 层【当品】」). This test pins the truth: the ultimate adds two, not one, and the talent's cap of four still holds.
     *
     * <p>One ultimate grants two layers on top of the talent's starting one, and the fourth layer is the cap.
     */
    @Test
    public void theUltimateGrantsTwoLayersAndTheCapHolds() {
        UltFixture f = new UltFixture();
        int before = f.luka.getBuffManager().stacksOf(FIGHTING_SPIRIT);
        f.castUltimate();
        Assertions.assertEquals(before + 2, f.luka.getBuffManager().stacksOf(FIGHTING_SPIRIT),
                "「获得 2 层【char:1111/fighting_spirit】」 - one application, two layers");

        f.castUltimate();
        Assertions.assertEquals(4, f.luka.getBuffManager().stacksOf(FIGHTING_SPIRIT),
                "「最多持有 4 层」 - the second ultimate stops at the cap");
    }

    /**
     * 卢卡's enhanced basic attack, both directions: 「当持有的【斗志】大于等于 2 层时，普攻【直冲拳】强化为【直冲碎天拳】」 -
     * which also says what happens below two layers, so the swap must be removable.
     *
     * <p>Install and removal both hang on {@code ATTACK_FINISHED}: the layers are gained <i>after</i> the hit settles, so an
     * {@code ALLY_ATTACK} rule would turn the very attack that crosses two layers into the enhanced one (measured before the
     * move).
     *
     * <p>The row changes one attack AFTER the layers move, and changes back when the enhanced attack spends them.
     */
    @Test
    public void theRowFollowsTheLayers() {
        CycleFixture f = new CycleFixture();
        Assertions.assertEquals(1, f.stacks(), "the talent starts with one layer");

        f.castBasic();                                    // 1 -> 2 layers, swap installed after the hit
        Assertions.assertEquals(2, f.stacks(), "the basic grants its layer");
        Assertions.assertEquals(8, f.commonSlot(), "「≥ 2 层时强化」 - the swap is in place for the NEXT attack");

        f.castBasic();                                    // the enhanced row: spends two layers, gains one
        Assertions.assertEquals(1, f.stacks(), "「强化普攻消耗 2 层」 - 2 + 1 - 2 = 1");
        Assertions.assertEquals(1, f.commonSlot(), "「< 2 层」 means the ordinary attack is back");

        f.castBasic();                                    // ordinary again: 1 -> 2
        Assertions.assertEquals(2, f.stacks());
        Assertions.assertEquals(8, f.commonSlot(), "the cycle repeats");
    }

    /**
     * 行迹 粉碎斗志: 「【直冲拳】的每 1 段攻击都有 50% 的固定概率使卢卡额外施放 1 段攻击」.
     *
     * <p>Measured: the plain Basic ATK settles <b>1</b> instance (NORMAL, 255); the enhanced one settles <b>2</b> — the 20% hit
     * (NORMAL, 51) plus the trace's extra <b>ADDITIONAL</b> 51. The extra instance is ADDITIONAL on purpose: that is what keeps
     * 「此效果对额外施放的攻击不会生效」 true without a second guard.
     */
    @Test
    public void theCrushingWillTraceAddsOneExtraSegment() {
        TraceFixture f = new TraceFixture();
        Assertions.assertEquals(1, f.settleBasic().size(), "the plain Basic ATK is one instance");

        List<DamageType> enhanced = f.settleEnhancedBasic();
        long normal = enhanced.stream().filter(type -> type == DamageType.NORMAL).count();
        // Note: the BASE half is four instances since 2026-11-04 (「3 段 20% + 1 段 80%」, the `times` field), and the trace
        // rolls 50% on EACH segment, so the ADDITIONAL count is 0..4 with the seeded RNG - which is why the base half is
        // what is pinned here rather than the total.
        Assertions.assertEquals(4, normal, "「先…3 段…每段 20%…再…1 段…80%」 — four segments: " + enhanced);
        Assertions.assertEquals(enhanced.size(), normal + enhanced.stream()
                        .filter(type -> type == DamageType.ADDITIONAL).count(),
                "and everything else on this cast is the trace's ADDITIONAL segment");
    }

    /**
     * 行迹 循环制动: 「每获得 1 层【斗志】，额外恢复 3 点能量」 — now written on {@code STACK_GAINED}.
     *
     * <p>Measured: the Ultimate grants 2 layers at once and pays <b>+6</b> energy (one firing per layer), which is exactly why
     * the event fires per layer rather than carrying a count. Note the earlier version of this test pinned the REGISTRATION
     * (the engine had no "a counter was just gained" event); it now pins the payment.
     */
    @Test
    public void theRecycleTracePaysThreeEnergyPerLayer() {
        TraceFixture f = new TraceFixture();
        int layersBefore = f.luka.getBuffManager().stacksOf("char:1111/fighting_spirit");
        double energyBefore = f.luka.getCurrentEnergy();

        f.castUlt();

        Assertions.assertEquals(layersBefore + 2, f.luka.getBuffManager().stacksOf("char:1111/fighting_spirit"),
                "precondition: the Ultimate grants 2 layers");
        Assertions.assertEquals(energyBefore + 6, f.luka.getCurrentEnergy(), 1e-9,
                "「每获得 1 层…额外恢复 3 点能量」 — two layers, two firings");
    }

    /**
     * <b>Gap witness</b>: the enhanced Basic ATK's segment list is a DATA gap — measured, the engine settles one 20% hit where
     * the document says three 20% hits then one 80% hit. This test pins that reading; when the segments land it goes red.
     */
    @Test
    public void enhancedBasicPlaysItsFourSegments() {
        TraceFixture f = new TraceFixture();
        f.settleBasic();
        List<DamageType> enhanced = f.settleEnhancedBasic();
        // Three 20% instances and one 80% instance, all NORMAL; the trace's ADDITIONAL segments (if any rolled) are the
        // other kind, so the base half is counted by type.
        Assertions.assertEquals(4, enhanced.stream().filter(type -> type == DamageType.NORMAL).count(),
                "the document's three 20% segments plus one 80% segment: " + enhanced);
    }

    /**
     * 卢卡's technique: 「进入战斗后对敌方随机单体造成 50% 攻击力物理伤害，并有 100% 基础概率使该目标陷入与战技相同的裂伤状态。
     * 随后卢卡额外获得 1 层【斗志】」.
     *
     * <p>The two halves must land on <b>the same</b> randomly chosen enemy - that is what the rule-level pick is for (a
     * per-effect {@code random_enemy} would roll twice). The bleed's numbers are the skill's own (`skill_lacerate`).
     *
     * <p>Exactly one enemy is hit, and that same enemy is the one bleeding; 斗志 gains a layer.
     */
    @Test
    public void theTechniqueHitsAndBleedsTheSameEnemy() {
        TechniqueFixture f = new TechniqueFixture(true);
        List<Enemy> damaged = f.enemies.stream().filter(e -> e.getCurrentHp() < e.getMaxHp()).toList();
        Assertions.assertEquals(1, damaged.size(), "a single random enemy takes the hit");
        Enemy victim = damaged.getFirst();
        Assertions.assertEquals(1, victim.getBuffManager().countBuffs(DotBuff.class),
                "「使该目标陷入…dot:bleed」 - the SAME enemy, not a second roll");
        for (Enemy enemy : f.enemies) {
            if (enemy != victim) {
                Assertions.assertEquals(0, enemy.getBuffManager().countBuffs(DotBuff.class),
                        "the other enemy must not be bleeding");
            }
        }
        // The talent already grants a layer at battle start, so the technique is measured as one more.
        int bare = new TechniqueFixture(false).luka.getBuffManager().stacksOf(FIGHTING_SPIRIT);
        Assertions.assertEquals(bare + 1, f.luka.getBuffManager().stacksOf(FIGHTING_SPIRIT),
                "「随后卢卡额外获得 1 层【char:1111/fighting_spirit】」");
    }

    /**
     * Without the technique neither the hit, the bleed nor the layer happens.
     */
    @Test
    public void withoutTheTechniqueNothingHappens() {
        TechniqueFixture f = new TechniqueFixture(false);
        for (Enemy enemy : f.enemies) {
            Assertions.assertEquals(enemy.getMaxHp(), enemy.getCurrentHp(), 1e-9, "no hit");
            Assertions.assertEquals(0, enemy.getBuffManager().countBuffs(DotBuff.class), "no bleed");
        }
        int baseline = new TechniqueFixture(false).luka.getBuffManager().stacksOf(FIGHTING_SPIRIT);
        Assertions.assertEquals(baseline, f.luka.getBuffManager().stacksOf(FIGHTING_SPIRIT), "no extra layer");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static final class Fixture {
        private final Character luka = CharacterFactory.create(LUKA, LEVEL);
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Enemy enemy = BattleFixture.monster();
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

    /** The enhanced-Basic-ATK cycle fixture: it reads the common slot to see which row is installed. */
    private static final class CycleFixture {
        private final Character luka;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private CycleFixture() {
            luka = CharacterFactory.create(LUKA, LEVEL);
            battle = new Battle(new ArrayList<>(List.of(luka, CharacterFactory.create(1001, LEVEL))),
                    new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
            battle.startBattle();
        }

        private int stacks() {
            return luka.getBuffManager().stacksOf(FIGHTING_SPIRIT);
        }

        private int commonSlot() {
            return luka.getSkills().get(SkillType.COMMON).getSkillSlot();
        }

        private void castBasic() {
            battle.castImmediate(luka.getSkills().get(SkillType.COMMON), luka, List.of(enemy));
        }
    }

    /** The ultimate-layer fixture: one cast, then read 【斗志】. */
    private static final class UltFixture {
        private final Character luka;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private UltFixture() {
            luka = CharacterFactory.create(LUKA, LEVEL);
            battle = new Battle(new ArrayList<>(List.of(luka, CharacterFactory.create(1001, LEVEL))),
                    new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
            battle.startBattle();
        }

        private void castUltimate() {
            battle.castImmediate(luka.getSkills().get(SkillType.ULTRA), luka, List.of(enemy));
        }
    }

    /** The trace fixture: it observes the damage types each Basic ATK settles. */
    private static final class TraceFixture {
        private final Character luka;
        private final Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        private final Battle battle;

        private TraceFixture() {
            luka = CharacterFactory.create(LUKA, 80);
            battle = new Battle(List.of(luka), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        /** Casts the plain Basic ATK and returns the settled damage types. */
        private List<DamageType> settleBasic() {
            return cast();
        }

        /** Casts once more — the first cast left Luka at 2 stacks, so this one is the enhanced form. */
        private List<DamageType> settleEnhancedBasic() {
            return cast();
        }

        private void castUlt() {
            battle.castImmediate(luka.getSkills().get(SkillType.ULTRA), luka, List.of(enemy));
            battle.processRequests();
        }

        private List<DamageType> cast() {
            List<DamageType> kinds = new ArrayList<>();
            battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) ->
                    kinds.add(damage.getType()));
            battle.castImmediate(luka.getSkills().get(SkillType.COMMON), luka, List.of(enemy));
            battle.processRequests();
            return kinds;
        }
    }

    /** The technique fixture: two enemies, and the stated 100% base chance's resistance zeroed. */
    private static final class TechniqueFixture {
        private final Character luka;
        private final List<Enemy> enemies = new ArrayList<>();

        private TechniqueFixture(boolean technique) {
            luka = CharacterFactory.create(LUKA, LEVEL);
            enemies.add(BattleFixture.monster());
            enemies.add(BattleFixture.monster());
            Battle battle = new Battle(new ArrayList<>(List.of(luka)), new ArrayList<>(enemies),
                    BattleFixture.seeded());
            if (technique) {
                battle.markTechniqueUsed(luka);
            }
            // The stated 100% base chance is rolled against effect resistance; zero it so the fixture measures the clause.
            for (Enemy enemy : enemies) {
                DoubleValue resistance = enemy.getAttribute(AttributeType.EFFECT_RESISTANCE);
                if (resistance != null && resistance.get() != 0) {
                    resistance.addModifier(DoubleValue.Modifier.pure(-resistance.get()));
                }
            }
            battle.startBattle();
        }
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }

    private static boolean gapMentions(String needle) throws IOException {
        for (var element : gaps()) {
            if (element.getAsString().contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static JsonArray gaps() throws IOException {
        return character().getAsJsonArray("_gaps");
    }

    private static JsonObject character() throws IOException {
        Path file = Path.of("src", "main", "resources", "game", "characters", "1111.json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
