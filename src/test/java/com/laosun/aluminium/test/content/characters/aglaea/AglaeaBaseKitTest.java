package com.laosun.aluminium.test.content.characters.aglaea;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 1402 阿格莱雅's base kit and the resource audit that keeps it honest: the talent's 【间隙织线】 application, the
 * additional thunder damage that state enables, the technique's four halves, and 【至高之姿】's replacement of her basic
 * attack (and its ban on the Skill slot).
 *
 * <p>The thread-state tests judge the applying half of 「施放攻击时使目标进入【间隙织线】状态」, with "while the
 * Garmentmaker (衣匠) is on the field" as the variable; the fissure test judges the other half (the additional
 * 30%-of-ATK thunder damage) with that condition held fixed, so the two sentences compose and neither test claims the
 * other's clause. The technique is measured against the same fight <b>without</b> it declared, so the difference is the
 * technique and not something else the entry point does. The stance tests read the swap and the castability gate that
 * every cast path consults; the ordinary row comes back on the state's own end, which is why the restore hangs on
 * {@code SUMMON_LEFT} rather than {@code STATE_ENDED} (the anchored removal does not announce the latter).
 *
 * <p>The clause-coverage audit at the end names every section of {@code 1402_阿格莱雅.md} and checks, in both
 * directions, that each section is a rule, an engine/data fact, or a registered gap — a section with neither is a silent
 * hole, and a rule no section names cannot be checked against the document.
 *
 * <p>Merged from the former {@code AglaeaThreadStateTest}, {@code AglaeaFissureTest}, {@code AglaeaTechniqueTest},
 * {@code AglaeaStanceSkillSwapTest} and {@code AglaeaClauseCoverageTest}; every test, assertion and Javadoc of
 * measurement is preserved.
 */
public class AglaeaBaseKitTest {
    private static final int AGLAEA = 1402;
    private static final int LEVEL = 80;
    private static final String THREAD = "char:1402/gap_thread";
    private static final int ENHANCED_BASIC_SLOT = 8;

    private static final Path FILE = Path.of("src", "main", "resources", "game", "characters", "1402.json");
    private static final String GAP = "gap:";
    private static final String RULE = "rule:";
    private static final String DATA = "data:";

    /** Section title (as the document writes it) → what answers for it. */
    private static final Map<String, List<String>> SECTION = new LinkedHashMap<>();

    /**
     * The one rule that is not a clause: it states which row of the skill tables this file's numbers come from (M-32),
     * so it answers for the whole file rather than for a sentence.
     */
    private static final Set<String> NOT_A_CLAUSE = Set.of("level_convention");

    static {
        SECTION.put("普攻：刺纹之蜜", List.of(DATA + " the skill row 140201 deals it; no rule needed"));
        SECTION.put("战技：高举吧，升华的名讳", List.of(RULE + "skill_effect", RULE + "skill_effect_2"));
        SECTION.put("终结技：共舞吧，命定的衣匠", List.of(RULE + "ult_effect",
                RULE + "p1402_stance_speed_follows_the_layers",
                RULE + "memosprite_immune_to_control",
                RULE + "countdown_makes_the_garmentmaker_perish"));
        SECTION.put("天赋：金玫之指", List.of(RULE + "talent_fissure_thread_applies_state",
                RULE + "talent_fissure_thread_bonus", DATA + " the memosprite panel is MemospriteSpec 11402"));
        SECTION.put("地图攻击：攻击", List.of(DATA + " the map attack happens outside battle"));
        SECTION.put("秘技：披星百裂", List.of(RULE + "technique_meteoric_sunder"));
        SECTION.put("强化普攻：孤锋千吻", List.of(RULE + "p1402_stance_enhances_her_basic",
                RULE + "p1402_memosprite_leaving_restores_her_basic",
                RULE + "p1402_garmentmaker_hits_the_same_target",
                RULE + "p1402_garmentmaker_hits_the_neighbours"));
        SECTION.put("强化战技：高举吧，升华的名讳", List.of(RULE + "skill_effect"));
        SECTION.put("忆灵：衣匠（面板）", List.of(DATA + " MemospriteSpec 11402"));
        SECTION.put("忆灵技能 1：刺纹之陷", List.of(DATA + " memosprites/1402.json states its attack block (Blast, 1.54 / 0.924, stance 30 / 15)"));
        SECTION.put("忆灵技能 2：泪水锻造的匠躯", List.of(RULE + "p1402_memosprite_speed_stack_on_threaded_hit",
                GAP + "行动时自动施放"));
        SECTION.put("忆灵技能 3：飞驰之夏", List.of(RULE + "memosprite_speeding_summer_advances_itself"));
        SECTION.put("忆灵技能 4：枯草之盈", List.of(RULE + "memosprite_bloom_of_drying_grass_pays_energy"));
        SECTION.put("行迹：短视之惩", List.of(RULE + "p1402_trace_short_sighted_atk_for_her",
                RULE + "p1402_trace_short_sighted_atk_for_the_garmentmaker"));
        SECTION.put("行迹：织运之竭", List.of(RULE + "p1402_eidolon_trace_keeps_one_layer_when_it_leaves",
                RULE + "p1402_eidolon_trace_clears_the_kept_layer",
                RULE + "p1402_eidolon_trace_gives_the_layer_back"));
        SECTION.put("行迹：飞驰之阳", List.of(RULE + "trace_racing_sun_fills_half_the_energy"));
        SECTION.put("星魂 1：飘曳金星的行列", List.of(RULE + "eidolon1_threaded_target_takes_more",
                RULE + "eidolon1_threaded_attack_pays_energy",
                RULE + "eidolon1_threaded_attack_pays_energy_for_the_garmentmaker"));
        SECTION.put("星魂 2：行舟命运的眼睑", List.of(RULE + "eidolon2_a_layer_when_she_acts",
                RULE + "eidolon2_a_layer_when_the_memosprite_acts",
                RULE + "eidolon2_another_units_skill_clears_it"));
        SECTION.put("星魂 3：华珠凝滞的礼物", List.of(RULE + "eidolon3_levels"));
        SECTION.put("星魂 4：大理石内的闪烁", List.of(RULE + "p1402_eidolon4_her_attack_also_stacks_it",
                RULE + "p1402_eidolon4_the_cap_rises_by_one", GAP + "星魂 6 的后半句"));
        SECTION.put("星魂 5：漆黑苦难的织者", List.of(RULE + "eidolon5_levels"));
        SECTION.put("星魂 6：盈虚无常的金线", List.of(RULE + "p1402_eidolon6_thunder_penetration_while_in_the_stance",
                GAP + "星魂 6 的后半句"));
        // Not a section of HER document, but five rules of this file answer for it: 1415's poem is written on the
        // beneficiary's table, because the buff lands on Aglaea and the Garmentmaker.
        SECTION.put("1415 忆灵技 12（写在本文件的跨角色规则）", List.of(
                RULE + "memosprite_ode_of_romance_marks_aglaea",
                RULE + "memosprite_ode_of_romance_spends_itself_for_energy",
                RULE + "memosprite_ode_of_romance_raises_damage_and_pierces_defence",
                RULE + "memosprite_ode_of_romance_also_reaches_the_garmentmaker",
                RULE + "memosprite_ode_of_romance_ends_with_her_stance",
                RULE + "memosprite_ode_of_romance_stance_end_also_clears_the_garmentmaker"));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Talent: her attacks apply 【间隙织线】 (the clause the follow-up damage's condition reads).
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void withTheMemoSpriteOutTheAttackThreadsTheTarget() {
        Assertions.assertTrue(attacked(true), "the Garmentmaker (衣匠) is out, so the attack marks the target");
    }

    @Test
    public void withoutItTheTargetIsUntouched() {
        Assertions.assertFalse(attacked(false), "no Garmentmaker (衣匠), no mark -- the sentence is conditional on it");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Talent: the additional thunder damage the state enables.
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void theAdditionalDamageLandsOnlyOnAThreadedTarget() {
        double threaded = skillDamage(true);
        double plain = skillDamage(false);
        Assertions.assertTrue(plain > 0, "precondition: the skill lands: " + plain);
        Assertions.assertTrue(threaded > plain,
                "the additional damage must land on a 【char:1402/gap_thread】 (Seam Stitch) target: " + plain + " -> " + threaded);
        double extra = threaded - plain;
        double attack = attackOf();
        // Note: The arithmetic of a flat addend is pinned by `AddDamageOpTest` (it lands in the BASE layer, so defence and the
        // other zones apply AFTERWARDS). What this test pins is the CONTENT: the clause fires, and its size is 30% of her ATK
        // BEFORE mitigation -- an upper bound. Measured: 122.145 against 209.563 unmitigated.
        Assertions.assertTrue(extra > 0, "the additional damage must be positive: " + extra);
        Assertions.assertTrue(extra < 0.30 * attack,
                "and it cannot exceed the unmitigated 30% of her ATK (" + attack + "): " + extra);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Technique: all four halves, against the same fight without it declared.
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void theTechniqueSummonsHitsEveryonePaysThirtyAndThreadsExactlyOne() {
        Scene with = run(true);
        Scene without = run(false);

        Assertions.assertNotNull(with.battle.summonOf(with.aglaea), "「召唤忆灵衣匠」 — the memosprite is on the field");
        Assertions.assertNull(without.battle.summonOf(without.aglaea), "control: nothing summons it without the technique");

        Assertions.assertTrue(with.lost > 0, "「对敌方全体造成…100% 攻击力的雷属性伤害」");
        Assertions.assertTrue(with.lost > without.lost, "and it lands on top of the routine opening");
        Assertions.assertEquals(30.0, with.energy - without.energy, 1e-9, "「恢复 30 点能量」");

        Assertions.assertEquals(1, with.threaded, "「使敌方随机目标陷入」 — exactly one enemy is threaded");
        Assertions.assertEquals(0, without.threaded, "control: no technique, no 【char:1402/gap_thread】");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // 【至高之姿】: the basic attack is replaced and the Skill slot is banned, both until the memosprite leaves.
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void theStanceSwapsHerBasicAndItsEndPutsItBack() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(aglaea), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        Assertions.assertEquals(1, aglaea.getSkills().get(SkillType.COMMON).getSkillSlot(),
                "precondition: the ordinary basic attack is in the slot");

        battle.castImmediate(aglaea.getSkills().get(SkillType.ULTRA), aglaea, List.of(enemy));
        battle.processRequests();
        Assertions.assertTrue(aglaea.getBuffManager().hasState("char:1402/supreme_posture"), "precondition: the stance is on");
        Assertions.assertEquals(ENHANCED_BASIC_SLOT, aglaea.getSkills().get(SkillType.COMMON).getSkillSlot(),
                "「普通攻击强化为【孤锋千吻】」 — the slot now holds data row 8");

        Summon garmentmaker = battle.summonOf(aglaea);
        Assertions.assertNotNull(garmentmaker, "precondition: the Ultimate brought the memosprite out");
        // The state's end: the memosprite leaves, and the swap must go with it.
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                TriggerEvent.SUMMON_LEFT, garmentmaker, garmentmaker, 0, 0));
        battle.processRequests();
        Assertions.assertEquals(1, aglaea.getSkills().get(SkillType.COMMON).getSkillSlot(),
                "「衣匠消失时…」 — the ordinary basic attack is back");
    }

    /**
     * 「普通攻击强化为【孤锋千吻】**且无法施放战技**」: the stance bans the Skill slot, and the ban leaves with it.
     *
     * <p>Read through {@code SkillCastability.allows}, the gate every cast path consults — a ban only the executor honoured
     * would let the action bar schedule the cast anyway.
     */
    @Test
    public void theStanceBansHerSkillAndGivesItBack() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(aglaea), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        var skill = aglaea.getSkills().get(SkillType.SKILL);
        Assertions.assertTrue(com.laosun.aluminium.trigger.SkillCastability.allows(skill, aglaea, battle),
                "precondition: the Skill is castable before the stance");

        battle.castImmediate(aglaea.getSkills().get(SkillType.ULTRA), aglaea, List.of(enemy));
        battle.processRequests();
        Assertions.assertTrue(aglaea.getBuffManager().hasState("char:1402/supreme_posture"), "precondition: the stance is on");
        Assertions.assertFalse(com.laosun.aluminium.trigger.SkillCastability.allows(skill, aglaea, battle),
                "「…且无法施放战技」");

        // The stance is ANCHORED to the memosprite ("衣匠消失时…解除"), so it has to really leave for the ban to go: a
        // hand-fired SUMMON_LEFT removes the swaps (they were placed by rules) but not an anchored buff.
        Summon garmentmaker = battle.summonOf(aglaea);
        Character ally = CharacterFactory.create(1001, LEVEL);
        battle.applyDamage(garmentmaker,
                new com.laosun.aluminium.damage.Damage(ally, garmentmaker,
                        com.laosun.aluminium.enums.DamageElement.THUNDER,
                        com.laosun.aluminium.enums.DamageType.NORMAL, 1_000_000));
        battle.processRequests();
        Assertions.assertFalse(aglaea.getBuffManager().hasState("char:1402/supreme_posture"), "precondition: the stance ended with it");
        Assertions.assertTrue(com.laosun.aluminium.trigger.SkillCastability.allows(skill, aglaea, battle),
                "the ban is the stance's, so it leaves with it");
    }

    /**
     * 「阿格莱雅与衣匠…**分别**造成…攻击力的雷属性伤害」: the memosprite's half exists as its own instances, attributed to the
     * summon (whose ATTACK is synced from hers), reading the enhanced row's 3rd/4th cells.
     *
     * <p>Three enemies on purpose: the row is a Blast, so the memosprite's half is one instance on the aimed enemy plus one
     * per neighbour - and the neighbour selector only has something to pick with a real row of enemies.
     */
    @Test
    public void theGarmentmakerDealsItsOwnHalfOfTheJointAttack() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        Enemy left = BattleFixture.monster();
        Enemy aimed = BattleFixture.monster();
        Enemy right = BattleFixture.monster();
        Battle battle = new Battle(List.of(aglaea), List.of(left, aimed, right), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.castImmediate(aglaea.getSkills().get(SkillType.ULTRA), aglaea, List.of(aimed));
        battle.processRequests();
        Summon garmentmaker = battle.summonOf(aglaea);
        Assertions.assertNotNull(garmentmaker, "precondition: the memosprite is out");

        int[] fromSummon = {0};
        double[] dealt = {0};
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> {
            if (damage.getAttacker() == garmentmaker) {
                fromSummon[0]++;
                dealt[0] += applied;
            }
        });
        battle.castImmediate(aglaea.getSkills().get(SkillType.COMMON), aglaea, List.of(aimed));
        battle.processRequests();

        Assertions.assertEquals(3, fromSummon[0],
                "its half is one instance on the aimed enemy plus one on each neighbour (" + fromSummon[0] + ")");
        Assertions.assertTrue(dealt[0] > 0, "and it deals real damage: " + dealt[0]);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Clause coverage: every section of the document answers for a rule, a fact, or a registered gap.
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void everySectionIsARuleOrARegisteredGap() throws IOException {
        Set<String> ruleIds = ruleIds();
        String gaps = gapsJoined();
        for (Map.Entry<String, List<String>> entry : SECTION.entrySet()) {
            for (String answer : entry.getValue()) {
                if (answer.startsWith(RULE)) {
                    String id = answer.substring(RULE.length());
                    Assertions.assertTrue(ruleIds.contains(id), entry.getKey() + " names a rule the file does not have: " + id);
                } else if (answer.startsWith(GAP)) {
                    String marker = answer.substring(GAP.length());
                    Assertions.assertTrue(gaps.contains(marker),
                            entry.getKey() + " is claimed as a registered gap, but no _gaps entry says: " + marker);
                }
            }
        }
    }

    @Test
    public void everyRuleAnswersForASection() throws IOException {
        Set<String> named = new LinkedHashSet<>();
        for (List<String> answers : SECTION.values()) {
            for (String answer : answers) {
                if (answer.startsWith(RULE)) {
                    named.add(answer.substring(RULE.length()));
                }
            }
        }
        List<String> orphans = new ArrayList<>();
        for (String id : ruleIds()) {
            if (!named.contains(id) && !NOT_A_CLAUSE.contains(id)) {
                orphans.add(id);
            }
        }
        Assertions.assertTrue(orphans.isEmpty(), "rules that answer for no named section: " + orphans);
    }

    /**
     * The gap list must stay an honest list of <b>open clauses</b>: the file is partial, every entry says what is
     * missing (a ⛔) or records what landed (a ⭐), and none of them points at another file instead of naming the
     * mechanism.
     */
    @Test
    public void theGapListNamesItsMechanismsInsteadOfPointingElsewhere() throws IOException {
        JsonObject root = root();
        Assertions.assertEquals("partial", root.get("_status").getAsString(),
                "two thirds of the document have no rule, so this file is not complete");
        JsonArray gaps = root.getAsJsonArray("_gaps");
        Assertions.assertTrue(gaps.size() > 0, "a partial file states at least one gap");
        for (var element : gaps) {
            String gap = element.getAsString();
            Assertions.assertTrue(gap.startsWith("⛔") || gap.startsWith("⭐"),
                    "a gap entry starts with ⛔ (open) or ⭐ (landed): " + gap);
            Assertions.assertFalse(gap.contains("见 CONTENT-STATUS"),
                    "a gap names the mechanism itself rather than pointing at the ledger: " + gap);
        }
    }

    /**
     * Whether her attack left the state on the target, with or without the Garmentmaker (衣匠) on the field.
     */
    private static boolean attacked(boolean withMemoSprite) {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        Character ally = CharacterFactory.create(1001, LEVEL);
        Enemy enemy = BattleFixture.monster();
        enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(900000));
        enemy.heal(900000);
        Random noCrit = new Random(0) {
            @Override
            public double nextDouble() {
                return 0.99;
            }
        };
        Battle battle = new Battle(List.of(aglaea, ally), List.of(enemy), noCrit);
        battle.startBattle();
        if (withMemoSprite) {
            battle.summonMemosprite(aglaea);
        }
        // Note: COMMON: her Skill heals/summons, so her attack is the basic one.
        battle.castImmediate(aglaea.getSkills().get(SkillType.COMMON), aglaea, List.of(enemy));
        return enemy.getBuffManager().hasState(THREAD);
    }

    private static double attackOf() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        return aglaea.getAttribute(AttributeType.ATTACK).get();
    }

    /**
     * Her skill's damage against a victim, with the state applied or not.
     */
    private static double skillDamage(boolean threaded) {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        Character ally = CharacterFactory.create(1001, LEVEL);
        if (threaded) {
            EffectSpec state = new EffectSpec();
            TriggerSpecs.set(state, "op", "APPLY_BUFF");
            TriggerSpecs.set(state, "buff", THREAD);
            TriggerSpecs.set(state, "turns", 5);
            TriggerSpecs.set(state, "target", "target");
            ally.setTriggerTable(new TriggerTable(9701, List.of(TriggerSpecs.rule(
                    TriggerEvent.ALLY_ATTACK.name(), List.of(), state))));
        }
        Enemy enemy = BattleFixture.monster();
        enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(900000));
        enemy.heal(900000);
        Random noCrit = new Random(0) {
            @Override
            public double nextDouble() {
                return 0.99;
            }
        };
        Battle battle = new Battle(List.of(aglaea, ally), List.of(enemy), noCrit);
        battle.startBattle();
        // "while the Garmentmaker (衣匠) is on the field" -- held fixed, and it is also the condition the rule needs.
        battle.summonMemosprite(aglaea);
        if (threaded) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, enemy, 1, 0));
        }
        double before = enemy.getCurrentHp();
        // Note: COMMON, not SKILL: her Skill heals/summons the Garmentmaker (衣匠) (no damage at all), so her attack is the basic one.
        battle.castImmediate(aglaea.getSkills().get(SkillType.COMMON), aglaea, List.of(enemy));
        double dealt = before - enemy.getCurrentHp();
        Assertions.assertFalse(enemy.isDeath(), "the judged hit must not kill the victim");
        Assertions.assertTrue(dealt > 0 && dealt < 0.5 * before,
                "a real measurement, not the whole bar: " + dealt + " of " + before);
        return dealt;
    }

    /** What one battle looks like after BATTLE_START, with or without the technique declared. */
    private static Scene run(boolean technique) {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        // Note: above half of her bar, because her trace 飞驰之阳 raises energy TO 50% at battle start - measured
        // 2026-11-04: starting from zero, the trace's floor swallows the technique's 30 points and the delta reads 0.
        // That is the trace's own clause working, not the technique's failing, so the fixture starts above the floor.
        aglaea.setCurrentEnergy(aglaea.getMaxEnergy() * 0.6);
        if (technique) {
            // The engine stamps this state before any BATTLE_START rule runs, which is the gate the rule reads.
            aglaea.getBuffManager().addBuff(new StateBuff("sys:technique", 1));
        }
        Enemy first = BattleFixture.dummy();
        Enemy second = BattleFixture.dummy();
        Battle battle = new Battle(new ArrayList<>(List.of(aglaea)),
                new ArrayList<>(List.of(first, second)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        double lost = 0;
        int threaded = 0;
        for (var unit : battle.enemyUnits()) {
            lost += unit.getMaxHp() - unit.getCurrentHp();
            if (unit.getBuffManager().hasState(THREAD)) {
                threaded++;
            }
        }
        return new Scene(battle, aglaea, lost, aglaea.getCurrentEnergy(), threaded);
    }

    private record Scene(Battle battle, Character aglaea, double lost, double energy, int threaded) {
    }

    private static JsonObject root() throws IOException {
        return JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static Set<String> ruleIds() throws IOException {
        Set<String> ids = new LinkedHashSet<>();
        for (var element : root().getAsJsonArray("rules")) {
            ids.add(element.getAsJsonObject().get("id").getAsString());
        }
        return ids;
    }

    private static String gapsJoined() throws IOException {
        StringBuilder joined = new StringBuilder();
        for (var element : root().getAsJsonArray("_gaps")) {
            joined.append(element.getAsString()).append('\n');
        }
        return joined.toString();
    }
}
