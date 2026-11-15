package com.laosun.aluminium.test.content.characters.cyrene;

import com.google.gson.reflect.TypeToken;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.beans.SkillEffectSpec;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.data.SkillEffects;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.skill.DefaultSkill;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.JSONReader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 1415 昔涟's cast outcomes: the once-per-battle ultimate and the +50 points of crit rate it hands her and 德谬歌, the
 * memosprite's own 100%-of-her-Max-HP panel, the enhanced basic that ultimate installs (its two data parts, and the
 * instances a cast settles), the ode the memosprite marks the allies with, and the two zone riders measured against
 * settled damage.
 *
 * <p>Merged from the former {@code CyreneUltimateOnceTest}, {@code CyreneUltCritRateTest},
 * {@code CyreneMemospritePanelTest}, {@code CyreneEnhancedBasicTest}, {@code CyreneEnhancedBasicPartsTest},
 * {@code CyreneOdeMarksTheAlliesTest}, {@code CyreneZoneRiderTest} and {@code CyreneZoneBonusPerAllyTest}; every test,
 * assertion, helper and class-level note is preserved.
 *
 * <p><b>L3:</b> the two cases that read one board after the ultimate are folded into one scenario test
 * ({@code Assertions.assertAll}), one message per clause reading: the +50 points of crit rate she and 德谬歌 gain, and the
 * memosprite's 100%-of-her-Max-HP panel. Every assertion, expected value, tolerance and message is unchanged. The
 * once-per-battle gate, the refused-cast case, the enhanced basic's instances and its data parts, the ode and the two zone
 * riders keep their own tests - different boards, data only, or a refusal.
 */
public class CyreneCombatTest {

    private static final int CYRENE = 1415;
    private static final int ALLY = 1001;
    private static final int ENHANCED_BASIC_SLOT = 8;

    /** Lv10 row of 141508: #1 = 0.42, #2 = 3, #3 = 0.42. */
    private static final double LV10_SHARE = 0.42;
    /** The same share at row 6, where the basic attack is pinned: 0.3 Max HP. */
    private static final double LV6_SHARE = 0.3;

    // ---------------------------------------------------------------------------------------------------------------
    // Former CyreneUltimateOnceTest: 「单场战斗中只能施放 1 次」.
    // ---------------------------------------------------------------------------------------------------------------
    /*
     * 「单场战斗中只能施放 1 次」 - 1415's ultimate (141503) is the first row that states it.
     *
     * <p><b>Why a new kind of gate.</b> Every castability the engine had was about the <i>present</i>: energy, skill points,
     * a turn, a counter ({@code castable_when}). This one is about <b>history</b> - the first cast spends the permission and
     * no later one may happen in the same battle. It is recorded by the battle when the cast is <b>committed</b>, so a cast
     * that the other gates refuse does not spend it.
     */

    private record Scene(Battle battle, Character cyrene, Skill ultimate) {
    }

    private static Scene scene() {
        Character cyrene = CharacterFactory.create(CYRENE, 80);
        Battle battle = new Battle(List.of(cyrene), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        return new Scene(battle, cyrene, cyrene.getSkills().get(SkillType.ULTRA));
    }

    /**
     * The first cast happens; the second one is refused.
     */
    @Test
    public void theUltimateIsCastableOnceInABattle() {
        Scene scene = scene();
        Assertions.assertTrue(scene.battle.castImmediate(scene.ultimate, scene.cyrene,
                        List.of(scene.battle.enemies().getFirst())),
                "the first cast is allowed");
        Assertions.assertFalse(scene.battle.castImmediate(scene.ultimate, scene.cyrene,
                        List.of(scene.battle.enemies().getFirst())),
                "「单场战斗中只能施放 1 次」: the second cast must be refused");
    }

    /**
     * A refused cast must NOT spend the single use: the gate answers false before the permission is recorded, so the
     * ultimate is still castable once something else was refused.
     */
    @Test
    public void aRefusedCastDoesNotSpendTheSingleUse() {
        Character cyrene = CharacterFactory.create(CYRENE, 80);
        Battle battle = new Battle(List.of(cyrene), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        Skill ultimate = cyrene.getSkills().get(SkillType.ULTRA);

        // A refused cast never reaches the marking: this one is refused for having no target list at all, which is the
        // same "the gate answered false before anything was spent" path the single-use check sits on.
        Assertions.assertFalse(battle.castImmediate(ultimate, cyrene, null),
                "a cast with no target list is refused");
        Assertions.assertTrue(battle.castImmediate(ultimate, cyrene, List.of(battle.enemies().getFirst())),
                "so the one allowed cast is still available");
    }

    /**
     * A NEW battle starts with the permission again - the record is per battle, not per file.
     */
    @Test
    public void aNewBattleAllowsItAgain() {
        Scene first = scene();
        Assertions.assertTrue(first.battle.castImmediate(first.ultimate, first.cyrene,
                List.of(first.battle.enemies().getFirst())));

        Scene second = scene();
        Assertions.assertTrue(second.battle.castImmediate(second.ultimate, second.cyrene,
                        List.of(second.battle.enemies().getFirst())),
                "another battle's ultimate is not spent by the previous one");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Former CyreneUltCritRateTest and CyreneMemospritePanelTest: one board, read after the ultimate - the crit rate it
    // hands her and 德谬歌, and the panel the memosprite starts with.
    // ---------------------------------------------------------------------------------------------------------------
    /*
     * 1415 昔涟's ultimate: 「昔涟和德谬歌的暴击率提高 50%」 (141503, params [1, 24, 0.5, 12]).
     *
     * <p>CRIT RATE is a <b>point</b> attribute: the document says "+50%", i.e. +0.5 in the engine's unit, and a `percent`
     * modifier would be read as a share of her base instead (the trap already paid for once on CRIT DMG: 0.6 x 0.12 = 0.072).
     *
     * <p>1415 昔涟's memosprite 德谬歌: 「德谬歌初始拥有等同于昔涟 100% 生命上限的生命上限」 (141503, params [1, 24, 0.5, 12]).
     *
     * <p>The panel lives in {@code memosprites/1415.json} as {@code {"attribute": "HEALTH", "percent": 1.0}}; this case
     * measures the result rather than the declaration, because "100% of the summoner's Max HP" is the kind of share that
     * looks right either way.
     */

    /**
     * The ultimate on ONE board: +50 points of crit rate to her and to 德谬歌, and the memosprite's panel reading 100% of
     * her Max HP.
     */
    @Test
    public void theUltimateRaisesTheCritRateAndPanelsTheMemosprite() {
        Character cyrene = CharacterFactory.create(CYRENE, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(cyrene, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        double before = cyrene.getAttribute(AttributeType.CRIT_CHANCE).get();
        SkillExecutor.execute(battle, cyrene.getSkills().get(SkillType.ULTRA), cyrene,
                List.of(battle.enemies().get(0)));
        battle.processRequests();
        double after = cyrene.getAttribute(AttributeType.CRIT_CHANCE).get();
        // 「**昔涟和德谬歌**的暴击率提高 50%」: the memosprite shares the clause.
        CanHit demiurge = battle.memospriteOf(cyrene);
        Assertions.assertNotNull(demiurge, "precondition: 德谬歌 is on the field");
        double hers = cyrene.getAttribute(AttributeType.HEALTH).get();
        Assertions.assertAll("「昔涟和德谬歌的暴击率提高 50%」 and 德谬歌's panel",
                () -> Assertions.assertEquals(0.5, after - before, 1e-6,
                        "「暴击率提高 50%」 (before=" + before + ", after=" + after + ")"),
                () -> Assertions.assertEquals(0.5, demiurge.getAttribute(AttributeType.CRIT_CHANCE).get(),
                        "and 德谬歌 gets the same +50 points"),
                // The panel case stated the same guard on the same memosprite, and it is kept with it.
                () -> Assertions.assertNotNull(demiurge, "precondition: 德谬歌 is on the field"),
                () -> Assertions.assertEquals(hers, demiurge.getMaxHp(), Math.max(1e-6, hers * 1e-9),
                        "「等同于昔涟 100% 生命上限」 (hers=" + hers + ", its=" + demiurge.getMaxHp() + ")"));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Former CyreneEnhancedBasicTest: the enhanced row is two damage instances in one cast.
    // ---------------------------------------------------------------------------------------------------------------
    /*
     * 1415 昔涟's enhanced basic 「向着爱与明天♪」 (141508) when it is actually cast: <b>two damage instances in one row</b>.
     *
     * <p>Her ultimate installs this row into the {@code COMMON} slot ({@code REPLACE_SKILL}), and its sentence is
     * 「对指定敌方单体造成等同于昔涟 #3% 生命上限的冰伤，<b>随后</b>对敌方全体造成等同于昔涟 #1% 生命上限的冰伤」. Before the
     * engine learned multi-part rows, an {@code AoEAttack} row settled one area hit and dropped the single-target half
     * silently; the data's own max-hit column says the row is two instances
     * ({@code skill_max_hits.json avatars.1415.Skill11 = 2}, pinned by the data-side guard below).
     *
     * <p>What this judge pins, all of it readable from the settled instances:
     * <ul>
     *   <li><b>three</b> ordinary hits: the single part on the aimed enemy, then the area part on both enemies (the area
     *       part reaches the aimed enemy too - 「敌方全体」 includes it);</li>
     *   <li>each at Lv10's share from its <b>own</b> parameter: #3 for the single part, #1 for the area part (0.42 at
     *       Lv10, the level the content file raises the slot to);</li>
     *   <li>the sentence's order - single first, area second;</li>
     *   <li>toughness from the column each part's shape reads: {@code single} 30, then {@code all} 15 per victim.</li>
     * </ul>
     *
     * <p>Targets are the shared 1,000,000-HP dummy rather than the standard monster: the question here is "how many
     * instances and with which numbers", and a target that dies half-way would make the hit count depend on the crit
     * rolls.
     *
     * <p><b>✅ The per-part toughness is settled (2026-10-29).</b> The game's own
     * {@code AvatarSkillConfig.ShowStanceList} for 141508 — the same at every level — is {@code [30, 15, 0]}, and the data
     * generator reads those three slots as {@code {single, all, spread}}. So the single-target part carries 30 and the AoE
     * part 15 per victim, which is exactly what this judge asserts. The older note that "the ConfigAbility states 15 for the
     * first hit" was a misreading; it has no standing against the skill's own display table.
     */

    @Test
    public void theSinglePartLandsFirstAndTheAreaPartReachesEveryEnemy() {
        Character cyrene = CharacterFactory.create(CYRENE, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(cyrene, ally),
                List.of(BattleFixture.dummy(), BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        // The ultimate is what replaces her basic attack with the enhanced row (REPLACE_SKILL in 1415.json).
        SkillExecutor.execute(battle, cyrene.getSkills().get(SkillType.ULTRA), cyrene,
                List.of(battle.enemies().get(0)));
        battle.processRequests();
        Skill enhanced = cyrene.getSkills().get(SkillType.COMMON);
        Assertions.assertTrue(enhanced.getData().getDescription().contains("向着爱与明天"),
                "precondition: the ultimate installed the enhanced basic; description was "
                        + enhanced.getData().getDescription());

        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        SkillExecutor.execute(battle, enhanced, cyrene, List.of(battle.enemies().get(0)));
        battle.processRequests();

        List<Damage> hits = settled.stream().filter(d -> d.getType() == DamageType.NORMAL).toList();
        // 1 + 2 x enemies: the area half is dealt in TWO instances of its own share, split 30% / 70% - measured on the
        // 2026-10-30 capture, whose cast against three enemies settles 1 + 3 + 3 = 7 instances (0.30 on the aimed enemy,
        // then 0.09 on every enemy, then 0.21 on every enemy) and whose cast against one settles 3.
        Assertions.assertEquals(5, hits.size(),
                "one single-target instance + one area instance per enemy; settled stances were "
                        + hits.stream().map(Damage::getStance).toList());

        double health = cyrene.getAttribute(AttributeType.HEALTH).get();
        double single = LV6_SHARE * health;
        // The area half's two instances carry 30% and 70% of the same row share.
        List<Double> area = List.of(0.3 * LV6_SHARE * health, 0.7 * LV6_SHARE * health);
        Assertions.assertEquals(single, hits.get(0).breakdown().get("base"), 1e-6,
                "the single-target half keeps the whole row share (0.3 Max HP at row 6)");
        for (int i = 1; i < hits.size(); i++) {
            double expected = area.get((i - 1) / 2);
            Assertions.assertEquals(expected, hits.get(i).breakdown().get("base"), 1e-6,
                    "area instance " + i + " carries its ratio of the row share");
        }

        CanHit aimed = battle.enemies().get(0);
        Assertions.assertSame(aimed, hits.get(0).getDefender(), "the single-target half goes to the aimed enemy first");
        for (int i = 1; i < hits.size(); i++) {
            Assertions.assertSame(battle.enemies().get((i - 1) % 2), hits.get(i).getDefender(),
                    "the area instances reach every enemy, in the roster's order");
        }

        Assertions.assertEquals(30, hits.get(0).getStance(), 1e-6, "the single part reads stance_list.single");
        Assertions.assertEquals(7.5, hits.get(1).getStance(), 1e-6,
                "the area part's stance is shared by its two instances (15 / 2), per victim");
        Assertions.assertEquals(7.5, hits.get(2).getStance(), 1e-6,
                "and the same share applies to the other enemy's instance");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Former CyreneEnhancedBasicPartsTest: the data side of those two parts.
    // ---------------------------------------------------------------------------------------------------------------
    /*
     * 1415 昔涟's enhanced basic 「向着爱与明天♪」 (141508) is <b>one row, two damage parts</b> - and this is the data side of
     * that fact.
     *
     * <p>The sentence is 「对指定敌方单体造成等同于昔涟 #3% 生命上限的冰伤，<b>随后</b>对敌方全体造成等同于昔涟 #1% 生命上限的
     * 冰伤」, so the row has a single-target part (<b>#3</b>, parameter index 2) followed by an area part (<b>#1</b>, index
     * 0) - the two are easy to read backwards because the description mentions #3 before #1. The row's own
     * {@code skill_effect} is {@code AoEAttack}, which is the shape only the <b>second</b> part has.
     *
     * <p>The count is not a hand-written opinion: the game's own {@code SkillMaxHitStatistic} says the enhanced basic is
     * <b>2</b> instances ({@code skill_max_hits.json}, {@code avatars.1415.Skill11 = 2}), and this guard fails if the table
     * and that column ever disagree.
     */

    /**
     * The parts: order, shapes and the parameter each reads, agreed with the game's max-hit column.
     */
    @Test
    public void theRowStatesTheSinglePartFirstAndTheAreaPartSecond() {
        SkillEffectSpec spec = SkillEffects.forSkill(new DefaultSkill(CYRENE, ENHANCED_BASIC_SLOT, 1));
        Assertions.assertNotNull(spec, "skill_effects.json must describe 1415's slot 8");
        List<SkillEffectSpec.DamagePart> parts = spec.getDamageParts();
        Assertions.assertNotNull(parts, "a missing entry settles the row as one area hit and drops the single half");

        Map<String, Map<String, Map<String, Integer>>> table = JSONReader.fromJSON("skill_max_hits.json",
                new TypeToken<Map<String, Map<String, Map<String, Integer>>>>() {
                }.getType());
        Assertions.assertNotNull(table, "skill_max_hits.json must be generated (see README's generator section)");
        // Enhanced attacks are named by their own trigger key (Skill11 / Skill12), not by their data slot (8) --
        // measured in skills.json: 141508's SkillTriggerKey is "Skill11", and the statistic table follows it.
        Integer maxHits = table.getOrDefault("avatars", Map.of())
                .getOrDefault(String.valueOf(CYRENE), Map.of())
                .get("Skill11");
        Assertions.assertEquals(maxHits, parts.size(),
                "the table's part count must equal the game's own max-hit column (Skill11)");

        Assertions.assertEquals(2, parts.size(), "one single-target part + one area part");
        Assertions.assertEquals("SingleAttack", parts.get(0).getShape(), "「对指定敌方单体…」 comes first");
        Assertions.assertEquals(2, parts.get(0).getIndex(), "#3 (0-based index 2) is the single-target share");
        Assertions.assertEquals("AoEAttack", parts.get(1).getShape(), "「随后对敌方全体…」 comes second");
        Assertions.assertEquals(0, parts.get(1).getIndex(), "#1 (0-based index 0) is the area share");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Former CyreneOdeMarksTheAlliesTest: the ode marks only the allies that have a variant (Eidolon 2's count).
    // ---------------------------------------------------------------------------------------------------------------
    /*
     * 德谬歌's 忆灵技 marks <b>the allies that have a variant of its buff</b> - which is how her Eidolon 2 counts them.
     *
     * <p><b>Why the mark exists and who gets it.</b> The Eidolon says 「每有 1 名不同的我方角色获得德谬歌忆灵技的增益效果…」, and the
     * game hands that buff out <b>per character</b>: {@code MServant_CyreneServant_00_AmazingBuff_Hyacine} to 风堇, {@code …_Evernight}
     * to 长夜月, the generic {@code …_AmazingBuff} to 昔涟 herself - and nothing to a character with no variant. The recorded fight
     * shows exactly three carriers (1409 / 1413 / 1415) with 1512 absent, which is that rule at work.
     *
     * <p>What this pins: the mark lands on those three and <b>not</b> on a fourth ally, so the count her Eidolon reads is three.
     */

    private static Enemy dummy() {
        Enemy enemy = Enemy.fromAttributes("target", 900000, 200, 100, 100);
        enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(900000));
        enemy.heal(900000);
        return enemy;
    }

    private static Random noCrit() {
        return new Random(0) {
            @Override
            public double nextDouble() {
                return 0.99;
            }
        };
    }

    /**
     * Casting the memosprite's skill 1 marks the three allies that have a variant, and leaves the fourth alone.
     */
    @Test
    public void theMemospriteMarksOnlyTheAlliesThatHaveAVariant() {
        Character cyrene = CharacterFactory.create(1415, 80);
        Character hyacine = CharacterFactory.create(1409, 80);
        Character evernight = CharacterFactory.create(1413, 80);
        Character robin = CharacterFactory.create(1512, 80);
        Battle battle = new Battle(new ArrayList<>(List.of(cyrene, hyacine, evernight, robin)),
                new ArrayList<>(List.of(dummy())), noCrit());
        battle.startBattle();
        battle.processRequests();

        Summon demiurge = battle.summonMemosprite(cyrene);
        Assertions.assertNotNull(demiurge, "precondition: 德谬歌 is on the field");
        SkillExecutor.execute(battle, demiurge.skillAt(1), demiurge, List.of(battle.enemyUnits().get(0)));
        battle.processRequests();

        Assertions.assertTrue(hyacine.getBuffManager().hasState("char:1409/ode_to_sky")
                        || hyacine.getBuffManager().stacksOf("char:1409/ode_to_sky") > 0,
                "风堇 holds 「char:1409/ode_to_sky」 (two stacks per grant)");
        Assertions.assertTrue(evernight.getBuffManager().hasState("char:1415/ode_to_years"),
                "长夜月 holds 「char:1415/ode_to_years」");
        Assertions.assertTrue(cyrene.getBuffManager().hasState("char:1415/ode_to_true_self"),
                "昔涟 holds 「char:1415/ode_to_true_self」");
        Assertions.assertFalse(robin.getBuffManager().hasState("char:1409/ode_to_sky")
                        || robin.getBuffManager().hasState("char:1415/ode_to_years")
                        || robin.getBuffManager().hasState("char:1415/ode_to_true_self"),
                "知更鸟•晴歌 has NO ode of its own, so the count her Eidolon reads is three, not four");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Former CyreneZoneRiderTest: the skill's barrier pays a 24%-of-the-hit true instance alongside every ally hit.
    // ---------------------------------------------------------------------------------------------------------------
    /*
     * 1415 昔涟's skill barrier: 「结界持续期间，我方全体目标每造成 1 次伤害，都会再额外造成 1 次等同于原伤害 24% 的真实伤害」
     * (141502, params [0.24, 2, 3]).
     *
     * <h2>Why this test is a cross-check against a real fight</h2>
     * Every hit in the whole-battle capture is followed by a {@code True} event whose ratio to the hit is
     * <b>371.472019 / 1547.800079 = 24.0000%</b> - exactly this rule's #1. So the rule and the capture agree, and this case
     * pins the engine side: one ally hit with the barrier up must settle the hit <b>plus</b> a true instance of 24% of it.
     */

    @Test
    public void everyAllyHitGetsATrueRiderOfTwentyFourPercent() {
        Character cyrene = CharacterFactory.create(CYRENE, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(cyrene, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        // Her Skill opens the barrier.
        SkillExecutor.execute(battle, cyrene.getSkills().get(SkillType.SKILL), cyrene,
                List.of(battle.enemies().get(0)));
        battle.processRequests();

        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        SkillExecutor.execute(battle, ally.getSkills().get(SkillType.COMMON), ally,
                List.of(battle.enemies().get(0)));
        battle.processRequests();

        List<Damage> trues = settled.stream().filter(d -> d.getType() == DamageType.TRUE).toList();
        Assertions.assertFalse(trues.isEmpty(),
                "a true rider settles alongside the hit; settled types = " + settled.stream().map(Damage::getType).toList());
        double hitFinal = settled.stream().filter(d -> d.getType() != DamageType.TRUE)
                .mapToDouble(d -> d.breakdown().get("final")).max().orElseThrow();
        double riderFinal = trues.get(0).breakdown().get("final");
        double expected = hitFinal * 0.24;
        Assertions.assertEquals(expected, riderFinal, Math.max(1e-6, expected * 1e-9),
                "「等同于原伤害 24% 的真实伤害」 (hit=" + hitFinal + ", rider=" + riderFinal + ")");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Former CyreneZoneBonusPerAllyTest: the barrier's rider rises 6% per ally carrying the ode (Eidolon 2).
    // ---------------------------------------------------------------------------------------------------------------
    /*
     * 1415 昔涟's Eidolon 2 raises the zone's true damage by <b>6% for each different ally</b> that has received her memosprite's
     * buff, capped at +24% (four allies).
     *
     * <p><b>The arithmetic is measured, not assumed.</b> On the 2026-10-30 fight three allies carried the memosprite's buff
     * (1409 / 1413 / 1415 - 1512 did not) and the recorded rider reads 0.42 where the row says 0.24: {@code 0.24 + 3 x 0.06},
     * <b>additive</b>. A multiplicative reading of 「倍率提高 6%，最多提高 24%」 would give 0.24 x 1.24 = 0.2976, which no cast shows.
     *
     * <p>What this pins: the count is of <b>ally characters</b>, one per character whatever its stacks, and the cap is four.
     */

    /**
     * The zone rider pays 24% plus 6% for every ally holding the mark, measured as a share of the instance it rode on.
     */
    @Test
    public void theZonePaysSixPercentPerMarkedAlly() {
        com.laosun.aluminium.entity.Character striker =
                com.laosun.aluminium.utils.CharacterFactory.create(1001, 80);
        Character cyrene = CharacterFactory.create(CYRENE, 80);
        List<com.laosun.aluminium.entity.Character> allies = new ArrayList<>(List.of(striker, cyrene));
        for (int cid : new int[]{1409, 1413}) {
            allies.add(CharacterFactory.create(cid, 80));
        }
        Enemy target = dummy();
        Battle battle = new Battle(new ArrayList<>(allies), new ArrayList<>(List.of(target)), noCrit());
        battle.startBattle();
        battle.processRequests();
        Assertions.assertNotNull(battle.summonMemosprite(cyrene), "precondition: 德谬歌 is on the field");
        // The zone has to be up - the rider is gated on 「结界」 (1415's skill deploys it).
        cyrene.getBuffManager().addBuff(new com.laosun.aluminium.buff.StateBuff("结界", 99));

        for (int marked = 0; marked <= 4; marked++) {
            if (marked > 0) {
                allies.get(marked - 1).getBuffManager()
                        .addBuff(new com.laosun.aluminium.buff.StateBuff("char:1409/ode_to_sky", 99));
            }
            Assertions.assertTrue(allies.get(Math.max(0, marked - 1)).getBuffManager()
                            .hasState("char:1409/ode_to_sky") == (marked > 0),
                    "the mark is visible to hasState at marked=" + marked);
            List<Damage> settled = new ArrayList<>();
            battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
            battle.castImmediate(striker.getSkills().get(com.laosun.aluminium.enums.SkillType.COMMON),
                    striker, List.of(target));

            double normal = settled.stream().filter(d -> d.getType() == DamageType.NORMAL)
                    .mapToDouble(Damage::toValue).findFirst()
                    .orElseThrow(() -> new AssertionError("the attack settled nothing"));
            double zone = settled.stream().filter(d -> d.getType() == DamageType.TRUE)
                    .mapToDouble(Damage::toValue).findFirst()
                    .orElseThrow(() -> new AssertionError("the zone settled no true damage; instances were " + settled.size()));
            double capped = Math.min(4, marked);
            Assertions.assertEquals(0.24 + 0.06 * capped, zone / normal, 1e-9,
                    marked + " marked allies pays " + (0.24 + 0.06 * capped) + " of the hit");
        }
    }
}
