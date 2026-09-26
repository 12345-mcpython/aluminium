package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Skill **slot mapping** (the core of P8-2): {@code SkillType} → the slot number in {@code skills.json}.
 *
 * <p>Before the fix, {@code Character.Builder.build()} wrote every slot as
 * {@code new DefaultSkill(cid, 1, level)} — so basic attack / skill / ultimate / talent **all**
 * resolved to slot 1, and the multipliers, toughness reduction, element and {@code sp_need} of all
 * six slots were those of the basic attack.
 *
 * <p>Why this bug went unnoticed for so long: the existing `SkillExecutorTest` / `SuperBreakTest`
 * both **construct** `new DefaultSkill(cid, slot, ...)` themselves and never check what the builder
 * assembled; and `EnergyTest` verifies the provider's dispatch logic. So nobody ever walked the path
 * of "what skill does a character actually get". This class fills that gap.
 */
public class SkillSlotMappingTest {
    private static final double EPS = 1e-9;

    /** The slot table itself: 1 basic attack / 2 skill / 3 ultimate / 4 talent / 6 map basic attack / 7 technique (5 does not exist in the data). */
    @Test
    public void slotTableIsTheSingleSourceOfTruth() {
        Assertions.assertEquals(Map.of(
                        SkillType.COMMON, 1,
                        SkillType.SKILL, 2,
                        SkillType.ULTRA, 3,
                        SkillType.TALENT, 4,
                        SkillType.MAZE, 6,
                        SkillType.TECHNIQUE, 7),
                Constant.SKILL_SLOT);

        // The summon's two slots belong to the memosprite (P9-4) and are not in the character slot table
        Assertions.assertFalse(Constant.SKILL_SLOT.containsKey(SkillType.SUMMON_SKILL));
        Assertions.assertFalse(Constant.SKILL_SLOT.containsKey(SkillType.SUMMON_TALENT));

        // The dividing line between intrinsic and attached-at-battle-start
        for (SkillType type : new SkillType[]{SkillType.COMMON, SkillType.SKILL,
                SkillType.ULTRA, SkillType.TALENT}) {
            Assertions.assertTrue(type.isIntrinsic(), type + " should be an intrinsic skill");
        }
        for (SkillType type : new SkillType[]{SkillType.MAZE, SkillType.TECHNIQUE,
                SkillType.SUMMON_SKILL, SkillType.SUMMON_TALENT}) {
            Assertions.assertFalse(type.isIntrinsic(), type + " should not be an intrinsic skill");
        }
    }

    /**
     * Map basic attack / technique are **not assembled when the character is created**, but attached
     * in {@code Battle.startBattle()}.
     *
     * <p>This is a deliberate layering: the map basic attack (slot 6, attack type {@code MazeNormal})
     * and the technique (slot 7, {@code Maze}) are things on the map; the in-battle basic attack is
     * slot 1's {@code Normal}, and the two are not the same thing.
     */
    @Test
    public void mapSkillsAreAttachedAtBattleStartNotAtBuild() {
        Character hero = CharacterFactory.create(1204, 80);

        // When created: no map skills
        Assertions.assertFalse(hero.getSkills().containsKey(SkillType.MAZE),
                "a freshly created character should not have the map basic attack");
        Assertions.assertFalse(hero.getSkills().containsKey(SkillType.TECHNIQUE),
                "a freshly created character should not have the technique");
        // All four intrinsic ones are present
        for (SkillType type : new SkillType[]{SkillType.COMMON, SkillType.SKILL,
                SkillType.ULTRA, SkillType.TALENT}) {
            Assertions.assertTrue(hero.getSkills().containsKey(type), type + " should be intrinsic");
        }

        // After the battle starts: map skills are attached, and resolve to **their own slots**
        Battle battle = newBattle(hero);
        for (SkillType type : new SkillType[]{SkillType.MAZE, SkillType.TECHNIQUE}) {
            Skill attached = hero.getSkills().get(type);
            Assertions.assertNotNull(attached, "should be attached after the battle starts: " + type);
            int slot = Constant.SKILL_SLOT.get(type);
            var raw = Constant.SKILLS.get(hero.getCid()).get(slot);
            Assertions.assertEquals(raw.attackType(), attached.getData().getSkillType(),
                    type + " should resolve to slot " + slot);
        }
        Assertions.assertEquals("MazeNormal", hero.getSkills().get(SkillType.MAZE)
                .getData().getSkillType());
        Assertions.assertEquals("Maze", hero.getSkills().get(SkillType.TECHNIQUE)
                .getData().getSkillType());
    }

    /** A map skill explicitly installed is not overwritten at battle start (tests / custom scenarios). */
    @Test
    public void explicitlyInstalledMapSkillIsNotOverwritten() {
        Character hero = CharacterFactory.create(1204, 80);
        Skill custom = new DefaultSkill(1204, 6, 1);
        hero.setSkill(SkillType.MAZE, custom);

        newBattle(hero);

        Assertions.assertSame(custom, hero.getSkills().get(SkillType.MAZE),
                "an existing one should not be re-assembled");
    }

    /**
     * A real character: each slot gets **its own** data, no longer the basic attack's.
     *
     * <p>Using Jing Yuan (1204): basic attack single-target 0.5 / skill blast / ultimate AoE /
     * talent bounce.
     */
    @Test
    public void eachSlotResolvesItsOwnData() {
        Character jingYuan = CharacterFactory.create(1204, 80);

        Skill common = jingYuan.getSkills().get(SkillType.COMMON);
        Skill skill = jingYuan.getSkills().get(SkillType.SKILL);
        Skill ultra = jingYuan.getSkills().get(SkillType.ULTRA);
        Skill talent = jingYuan.getSkills().get(SkillType.TALENT);

        // The attack types all differ (before the fix all four were "Normal")
        Assertions.assertEquals("Normal", common.getData().getSkillType());
        Assertions.assertEquals("BPSkill", skill.getData().getSkillType());
        Assertions.assertEquals("Ultra", ultra.getData().getSkillType());
        Assertions.assertNull(talent.getData().getSkillType(), "the talent slot's attack type is null in the data");
    }

    /**
     * Field-by-field alignment with the raw data in {@code Constant.SKILLS} (P8-2's acceptance test).
     */
    @Test
    public void builderDataMatchesTheRawSkillData() {
        int cid = 1204;
        Character jingYuan = CharacterFactory.create(cid, 80);
        newBattle(jingYuan);                 // start a battle to attach the map skills, so all six slots can be compared

        for (Map.Entry<SkillType, Integer> entry : Constant.SKILL_SLOT.entrySet()) {
            int slot = entry.getValue();
            var raw = Constant.SKILLS.get(cid).get(slot);
            var data = jingYuan.getSkills().get(entry.getKey()).getData();

            Assertions.assertEquals(raw.attackType(), data.getSkillType(),
                    entry.getKey() + "'s attack type");
            Assertions.assertEquals(raw.maxLevel(), data.getMaxLevel(),
                    entry.getKey() + "'s max level");
            Assertions.assertEquals(raw.paramList(), data.getSkills(),
                    entry.getKey() + "'s parameter table");
            Assertions.assertEquals(raw.stanceList().single(), data.getStanceList().single(),
                    entry.getKey() + "'s single-target toughness reduction");
        }
    }

    /**
     * Toughness reduction values also follow the slot: Jing Yuan's basic attack single-target 30 /
     * skill AoE 30 / ultimate AoE 60 / talent single-target 15.
     */
    @Test
    public void stanceValuesFollowTheSlot() {
        Character jingYuan = CharacterFactory.create(1204, 80);

        var common = jingYuan.getSkills().get(SkillType.COMMON).getData().getStanceList();
        var skill = jingYuan.getSkills().get(SkillType.SKILL).getData().getStanceList();
        var ultra = jingYuan.getSkills().get(SkillType.ULTRA).getData().getStanceList();
        var talent = jingYuan.getSkills().get(SkillType.TALENT).getData().getStanceList();

        Assertions.assertEquals(30, common.single(), EPS);
        Assertions.assertEquals(30, skill.all(), EPS);
        Assertions.assertEquals(60, ultra.all(), EPS);
        Assertions.assertEquals(15, talent.single(), EPS);
    }

    /**
     * Element also follows the slot: Natasha's skill/ultimate are **healing** (in the data
     * `element = Unknown` → null), while her basic attack deals physical damage.
     */
    @Test
    public void nonDamagingSlotsHaveNoElement() {
        Character natasha = CharacterFactory.create(1105, 80);

        Assertions.assertNotNull(natasha.getSkills().get(SkillType.COMMON).getData().getElement(),
                "the basic attack deals physical damage");
        Assertions.assertNull(natasha.getSkills().get(SkillType.SKILL).getData().getElement(),
                "the skill is healing → no element");
        Assertions.assertNull(natasha.getSkills().get(SkillType.ULTRA).getData().getElement(),
                "the ultimate is healing → no element");
    }

    /**
     * The max level differs by slot (basic attack 10 / skill · ultimate · talent 15) — proving that
     * "what is read is its own slot's max_level".
     */
    @Test
    public void maxLevelComesFromTheSlot() {
        Character jingYuan = CharacterFactory.create(1204, 80);

        Assertions.assertEquals(10, jingYuan.getSkills().get(SkillType.COMMON).getData().getMaxLevel());
        Assertions.assertEquals(15, jingYuan.getSkills().get(SkillType.SKILL).getData().getMaxLevel());
        Assertions.assertEquals(15, jingYuan.getSkills().get(SkillType.ULTRA).getData().getMaxLevel());
    }

    /**
     * All 93 characters' four **intrinsic** slots can be assembled (the data is complete, with no
     * EMPTY fallback).
     *
     * <p>The criterion is "the parameter table is non-empty": {@code SkillData.EMPTY}'s parameter
     * table is empty.
     * The map skills (6/7) are not checked here — they are only attached after the battle starts,
     * and are covered by {@link #mapSkillsAreAttachedAtBattleStartNotAtBuild()}.
     */
    @Test
    public void everyCharacterHasAllIntrinsicSlots() {
        Constant.CHARACTERS.keySet().forEach(cid -> {
            Character c = CharacterFactory.create(cid, 80);
            for (SkillType type : List.of(SkillType.COMMON, SkillType.SKILL,
                    SkillType.ULTRA, SkillType.TALENT)) {
                Skill skill = c.getSkills().get(type);
                Assertions.assertNotNull(skill, "cid=" + cid + " is missing " + type);
                Assertions.assertFalse(skill.getData().getSkills().isEmpty(),
                        "cid=" + cid + "'s " + type + " parameter table is empty (data not fetched?)");
            }
        });
    }

    /**
     * All 93 characters' **map slots** (6/7) can also be attached after the battle starts, with
     * non-empty parameters.
     */
    @Test
    public void everyCharacterGetsMapSkillsAtBattleStart() {
        Constant.CHARACTERS.keySet().forEach(cid -> {
            Character c = CharacterFactory.create(cid, 80);
            newBattle(c);
            for (SkillType type : List.of(SkillType.MAZE, SkillType.TECHNIQUE)) {
                Skill skill = c.getSkills().get(type);
                Assertions.assertNotNull(skill, "cid=" + cid + " is missing " + type + " after the battle starts");
                Assertions.assertFalse(skill.getData().getSkills().isEmpty(),
                        "cid=" + cid + "'s " + type + " parameter table is empty");
            }
        });
    }

    /**
     * Skill **level really is wired into damage**: {@code SkillExecutor} takes the parameter row with
     * {@code skill.getLevel() - 1}.
     *
     * <p>⚠ I previously wrote "the level is not wired into damage yet" here — **that was wrong**, and
     * it has been corrected.
     * How it came about: I built an level-8 skill but asserted that
     * {@code getData().getSkills().getFirst()} was 1.2, whereas {@code getSkills()} returns the
     * **entire** per-level table, so {@code getFirst()} of course still returns tier 1.
     * I mistook my own wrong row lookup for the engine not looking up a row.
     *
     * <p>The real call chain: {@code skill.getLevel()} → {@code index = level - 1} →
     * {@code levels.get(index)}.
     * For the end-to-end view see {@link #skillLevelScalesActualDamage}.
     */
    @Test
    public void skillLevelSelectsTheParameterRow() {
        Character hero = CharacterFactory.create(1204, 80);
        List<List<Double>> table = hero.getSkills().get(SkillType.COMMON).getData().getSkills();

        Assertions.assertEquals(0.5, table.getFirst().getFirst(), EPS, "tier 1 = 0.5");
        Assertions.assertEquals(1.2, table.get(7).getFirst(), EPS, "tier 8 = 1.2");
        Assertions.assertEquals(table.get(7), table.get(8 - 1), "level 8 → index 7");
    }

    /**
     * End to end: the ratio of the damage dealt by the same skill at level 1 vs level 8 = the ratio
     * of the multipliers (1.2 / 0.5 = 2.4).
     *
     * <p>This nails down "level is wired in" — it covers {@code SkillExecutor}'s row lookup, the
     * multiplier lookup and the damage pipeline at once.
     */
    @Test
    public void skillLevelScalesActualDamage() {
        double lv1 = damageOfBasicAttack(1);
        double lv8 = damageOfBasicAttack(8);

        Assertions.assertEquals(1.2 / 0.5, lv8 / lv1, 1e-6,
                "the damage ratio of level 8 / level 1 should equal the multiplier ratio 2.4, actual " + (lv8 / lv1));
        Assertions.assertTrue(lv1 > 0 && lv8 > lv1, "the higher the level, the higher the damage");
    }

    /**
     * But **a character assembled by the factory starts at skill level 1**: {@code Builder}'s
     * {@code skillLevel} initial value is 1; to level up, call {@code skillLevel(type)} (+1) or
     * {@code setSkillLevel(type, level)}.
     *
     * <p>This is not a defect: skill level belongs to P8's progression system (traces/eidolons add
     * levels); this item is only responsible for "the slot is right, the data is right, the level
     * takes effect".
     */
    @Test
    public void factoryCharactersStartAtSkillLevelOne() {
        Character hero = CharacterFactory.create(1204, 80);
        for (SkillType type : List.of(SkillType.COMMON, SkillType.SKILL,
                SkillType.ULTRA, SkillType.TALENT)) {
            Assertions.assertEquals(1, hero.getSkills().get(type).getLevel(),
                    type + "'s initial skill level is 1");
        }

        // After raising it two levels, what is actually used is the tier-3 multiplier
        Character leveled = Character.builder().cid(1204).level(80).isPromote()
                .skillLevel(SkillType.COMMON).skillLevel(SkillType.COMMON).build();
        Assertions.assertEquals(3, leveled.getSkills().get(SkillType.COMMON).getLevel());
        Assertions.assertEquals(0.7, leveled.getSkills().get(SkillType.COMMON)
                .getData().getSkills().get(2).getFirst(), EPS, "level 3 → tier 3 = 0.7");
    }

    /** Deals one basic attack at the given skill level, returning the damage dealt to the enemy. */
    private static double damageOfBasicAttack(int level) {
        Character hero = CharacterFactory.create(1204, 80);
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(1_000_000));
        enemy.heal(1_000_000);

        Battle battle = new Battle(List.of(hero), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.castImmediate(new DefaultSkill(1204, 1, level), hero, List.of(enemy));
        return 1_000_000 - enemy.getCurrentHp();
    }

    /** Starts a minimal battle (only to trigger {@code startBattle()}'s map-skill attachment). */
    private static Battle newBattle(Character hero) {
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        Battle battle = new Battle(List.of(hero), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }
}
