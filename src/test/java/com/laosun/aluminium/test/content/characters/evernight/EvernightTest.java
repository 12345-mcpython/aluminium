package com.laosun.aluminium.test.content.characters.evernight;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1413 长夜月 (Evernight) from her own file: the memosprite 「长夜」 carries most of her kit, so the cases here judge the
 * ultimate's attribution and its 【至暗之谜】 vulnerability, the talent's enemy-count scaling, the memosprite skill's cast
 * gate and 【亿质】 gain, and the re-summon path.
 *
 * <p>Each case keeps the rationale that motivated it directly above the test it pins.
 */
public class EvernightTest {

    private static final int EVERNIGHT = 1413;
    private static final int LEVEL = 80;
    private static final int HYACINE = 1409;
    private static final int VICTIM = 1002011;
    private static final int BYSTANDER = 1002012;
    private static final int ALLY = 1001;
    private static final String MEMORY = "char:1413/myriad_essence";
    private static final int RAINCLOUDS = 1;
    private static final double VULNERABILITY = 0.3;

    /**
     * The last clause of 1413's 忆灵技「迷梦，流失，如露」(1141307): 「长夜月的【忆质】大于等于 #3 点…时施放」.
     *
     * <p><b>Why this needs a judge of its own.</b> Before the engine had a castability spelling, the sentence was simply
     * not enforced: the servant cast anyway and, because the row prices the hit <i>per point</i>, a short counter produced a
     * smaller number (0 points produced 0) - a wrong answer with no symptom. The gate is now stated by the row itself
     * ({@code castable_when} in {@code skill_effects.json}) and checked at the one cast entry, so the failure is a
     * <b>refusal</b>: nothing settles, and the counter is not spent.
     *
     * <p>16 is the row's #3, the same at every level ({@code [0.06, 0.03, 16]} … {@code [0.168, 0.084, 16]}).
     *
     * <p>15 points: refused - and refused BEFORE the spell settles, so nothing is consumed.
     */
    @Test
    public void fifteenPointsCannotCast() {
        Scene scene = sceneWith(15);

        Assertions.assertFalse(scene.cast(),
                "\"【忆质】>= 16 to cast\" must refuse a cast at 15 points");
        Assertions.assertEquals(15, scene.owner.getResources().get(MEMORY).value(),
                "a refused cast spends nothing");
    }

    /**
     * 16 points: allowed, and the price is paid (the spend is the rule's own half, so this asserts the cast happened).
     */
    @Test
    public void sixteenPointsCanCast() {
        Scene scene = sceneWith(16);

        Assertions.assertTrue(scene.cast(), "16 points meets the row's #3");
    }

    /**
     * 1413 长夜月's 忆灵技1 clause 「…施放后获得 <b>#4</b> 点【忆质】」 (data row {@code [0.25, 0.05, 4, 1]}, {@code #4} = 1).
     *
     * <p>Three facts, each of which a plausible wrong implementation would get wrong:
     * <ol>
     *   <li><b>the count comes from the cast's own row</b> ({@code amount_from_cast_param: 3}), so it is 1 at every level
     *       and would follow the data if the row changed;</li>
     *   <li><b>it is HER memosprite's cast</b> - another character's memosprite casting its own slot 1 must not hand her
     *       anything (the {@code actor == summon} reading, the shape {@code AGENT.md} §3.7zb had to fix once already);</li>
     *   <li><b>it accumulates</b> - twice cast, two points.</li>
     * </ol>
     *
     * <p>⚠ The other half of that sentence (「每有 #3 点【忆质】额外造成 #2%」) is still registered: the game folds it into
     * the same six instances' {@code DamagePercentage}, and the engine has no "scale this instance by a counter's tiers"
     * spelling yet.
     *
     * <p>Casting 长夜's slot 1 gives one 【亿质】, and casting it again gives another.
     */
    @Test
    public void herMemospriteSkillGrantsOneMemoryPerCast() {
        Fixture f = new Fixture();
        Assertions.assertEquals(0, f.memory(), "precondition: nothing has been cast yet");

        f.castRainclouds();
        Assertions.assertEquals(1, f.memory(), "「施放后获得 #4 点【char:1413/myriad_essence】」 -- #4 is 1 in the row");

        f.castRainclouds();
        Assertions.assertEquals(2, f.memory(), "and it accumulates, one per cast");
    }

    /**
     * Another character's memosprite casting its own slot 1 is not 长夜's cast.
     */
    @Test
    public void anotherCharactersMemospriteGrantsNothing() {
        Fixture f = new Fixture();
        Summon ika = (Summon) f.battle.memospriteOf(f.hyacine);
        Assertions.assertNotNull(ika, "precondition: 小伊卡 is out");

        SkillExecutor.execute(f.battle, ika.skillAt(RAINCLOUDS), ika, List.of(f.battle.enemies().getFirst()));
        f.battle.processRequests();

        Assertions.assertEquals(0, f.memory(),
                "小伊卡 casting its own slot 1 must not feed 长夜月's 【char:1413/myriad_essence】 -- 「长夜施放后」 is HER memosprite");
    }

    /**
     * 1413 长夜月's 「长夜」 can leave the field and be summoned again.
     *
     * <p><b>Why this case exists.</b> Her slot 7 states 「施放后…并使「长夜」消失」 - the memosprite leaves without dying - and a
     * recorded fight (2026-10-30) shows it acting again later, so the re-summon path is not hypothetical: it is what the capture
     * does before its cast 69. Driving that cast threw
     * <pre>Effect targets "summon" but Evernight has no summon on the field</pre>
     * from a {@code SUMMONED} rule, which means the rule that listens for the arrival cannot see the arrival.
     *
     * <p>Summon, dismiss, summon again: the second arrival must reach the field like the first one.
     */
    @Test
    public void aDismissedMemospriteCanBeSummonedAgain() {
        Character evernight = CharacterFactory.create(EVERNIGHT, LEVEL);
        Battle battle = new Battle(List.of(evernight), new ArrayList<>(List.of(dummy())), noCrit());
        battle.startBattle();

        Summon first = battle.summonMemosprite(evernight);
        Assertions.assertNotNull(first, "the first summon is on the field");

        first.perish();
        Assertions.assertNull(battle.memospriteOf(evernight), "a perished memosprite is not on the field any more");

        Summon second = Assertions.assertDoesNotThrow(() -> battle.summonMemosprite(evernight),
                "the re-summon must not be refused");
        Assertions.assertNotNull(second, "and it arrives");
        Assertions.assertSame(second, battle.memospriteOf(evernight), "the field now holds the new one");
    }

    /**
     * 1413 Evernight (长夜月)'s talent: "场上敌方目标数量等于4或以上/3/2/1名时，我方忆灵造成的伤害为原伤害的120%/125%/130%/150%" (when the number of enemy targets on the field is 4 or more/3/2/1, the damage our memosprites deal is 120%/125%/130%/150% of the original damage).
     *
     * <p>The whole memosprite chain is exercised here: the damage TYPE (`DamageType.MEMORY`), the scoped boost granted to the MASTER
     * (its table is consulted when its memosprite strikes -- measured), and the `enemy_count` condition.
     *
     * <p>Note: Judged with the same memosprite hit at FOUR and at ONE enemy: the content grants +0.20 and +0.50, so the ratio must be
     * 1.50/1.20 = 1.25 exactly -- round 65 measured this zone to hold no other contributions.
     */
    @Test
    public void fewerEnemiesMeansMoreMemospriteDamage() {
        double four = memospriteDamage(4);
        double one = memospriteDamage(1);
        Assertions.assertTrue(four > 0 && one > 0, "precondition: the memosprite (忆灵) landed both hits");
        Assertions.assertEquals(1.50 / 1.20, one / four, 1e-9,
                "120% at four enemies and 150% at one: " + four + " vs " + one);
    }

    /**
     * 1413 长夜月's ultimate deals its damage <b>through her memosprite</b> 「长夜」.
     *
     * <p><b>Why this is a case and not a detail.</b> Her ultimate's text says 「使忆灵「长夜」对敌方全体造成等同于「长夜」200%生命上限的
     * 冰属性伤害」, and the game's own ability agrees: {@code Avatar_Evernight_00_Skill03_Servant_Damage} states
     * {@code AttackProperty.AttackType = "Ultra"} (so the ROW is the ultimate's) with
     * {@code OnStart[0].TargetType = Caster.GetSummoner} (so the HIT belongs to the servant). The recorded fight stamps those
     * instances with the servant as attacker — measured: {@code attacker_kind = Servant} while {@code type = Ultimate}.
     *
     * <p>Attribution is not cosmetic: 「长夜造成的伤害提高 60%」 (【至暗之谜】) and the 24% zone rider both key off the attacker.
     *
     * <p>The ultimate's instances are attributed to the memosprite, and the row is still the ultimate's.
     */
    @Test
    public void theUltimatesDamageIsDealtByTheMemosprite() {
        Character evernight = CharacterFactory.create(EVERNIGHT, LEVEL);
        Battle battle = new Battle(List.of(evernight), new ArrayList<>(List.of(dummy())), noCrit());
        battle.startBattle();
        Summon evey = battle.summonMemosprite(evernight);
        Assertions.assertNotNull(evey, "precondition: 「长夜」 is on the field");

        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        SkillExecutor.execute(battle, evernight.getSkills().get(SkillType.ULTRA), evernight,
                List.of(battle.enemyUnits().get(0)));

        List<Damage> hits = settled.stream().filter(d -> d.getType() == com.laosun.aluminium.enums.DamageType.NORMAL)
                .toList();
        Assertions.assertFalse(hits.isEmpty(), "the ultimate settled something");
        Assertions.assertTrue(hits.stream().allMatch(d -> d.getAttacker() == evey),
                "every instance is credited to 「长夜」, not to 长夜月 herself");
        Assertions.assertFalse(hits.stream().anyMatch(d -> d.getAttacker() == evernight),
                "and none of them to her");
    }

    /**
     * 1413 长夜月's ultimate 【至暗之谜】: 「期间敌方全体受到的伤害提高 30%，长夜月和忆灵「长夜」造成的伤害提高 60%」
     * (141303, params [2, 2, 0.6, 0.3]).
     *
     * <p>Measured as the ally's damage before and after the ultimate, with the target already broken so the toughness factor
     * stays at 1.0 on both sides - the same isolation trick the vulnerability tests learned the hard way.
     */
    @Test
    public void theUltimateRaisesTheDamageTheEnemyTakesByThirtyPercent() {
        Character evernight = CharacterFactory.create(EVERNIGHT, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(evernight, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        ((com.laosun.aluminium.enemy.Enemy) battle.enemies().get(0)).breakEnemy(DamageElement.ICE);

        double before = allyHit(battle, ally);
        SkillExecutor.execute(battle, evernight.getSkills().get(SkillType.ULTRA), evernight,
                List.of(battle.enemies().get(0)));
        battle.processRequests();
        double after = allyHit(battle, ally);

        Assertions.assertTrue(before > 0 && after > 0, "precondition: both ally hits land");
        Assertions.assertEquals(1.0 + VULNERABILITY, after / before, (1.0 + VULNERABILITY) * 1e-6,
                "【char:1413/darkest_riddle】 raises the damage the enemies take by 30% (before=" + before + ", after=" + after + ")");
    }

    /**
     * 长夜 with a counter set to the given number of 【亿质】, on the field and ready to cast slot 7.
     */
    private static Scene sceneWith(int points) {
        Character owner = CharacterFactory.create(EVERNIGHT, LEVEL);
        Battle battle = new Battle(List.of(owner), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.summonMemosprite(owner);
        Summon servant = battle.memospriteOf(owner);
        Assertions.assertNotNull(servant, "长夜 must be on the field for its slot 7 to exist");
        Assertions.assertNotNull(servant.skillAt(7), "data slot 7 is 「迷梦，流失，如露」");
        owner.getResources().get(MEMORY).setValue(points);
        return new Scene(owner, servant, battle);
    }

    private record Scene(Character owner, Summon servant, Battle battle) {
        private boolean cast() {
            return battle.castImmediate(servant.skillAt(7), servant, List.of(battle.enemies().getFirst()));
        }
    }

    /** 长夜月 with her memosprite out, plus another character's memosprite as the control. */
    private static final class Fixture {
        final Battle battle;
        final Character evernight;
        final Character hyacine;

        Fixture() {
            this.evernight = CharacterFactory.create(EVERNIGHT, LEVEL);
            this.hyacine = CharacterFactory.create(HYACINE, LEVEL);
            this.battle = new Battle(List.of(evernight, hyacine), List.of(BattleFixture.dummy()),
                    BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
            battle.summonMemosprite(evernight);
            battle.summonMemosprite(hyacine);
            battle.processRequests();
            Assertions.assertNotNull(battle.memospriteOf(evernight), "precondition: 长夜 is out");
        }

        int memory() {
            return evernight.getResources().value(MEMORY);
        }

        /** 长夜 casts its own data slot 1. */
        void castRainclouds() {
            Summon longNight = (Summon) battle.memospriteOf(evernight);
            Skill skill = longNight.skillAt(RAINCLOUDS);
            Assertions.assertNotNull(skill, "precondition: 长夜 carries data slot 1");
            SkillExecutor.execute(battle, skill, longNight, List.of(battle.enemies().getFirst()));
            battle.processRequests();
        }
    }

    /**
     * One hit by the memosprite against a victim that cannot die, on a field of the given size.
     */
    private static double memospriteDamage(int enemies) {
        Character master = CharacterFactory.create(EVERNIGHT, LEVEL);
        List<Enemy> foes = new ArrayList<>();
        Enemy victim = null;
        for (int i = 0; i < enemies; i++) {
            Enemy foe = EnemyFactory.create(i == 0 ? VICTIM : BYSTANDER, 90, 1);
            foe.setAttribute(AttributeType.HEALTH, new DoubleValue(900000));
            foe.heal(900000);
            foes.add(foe);
            if (i == 0) {
                victim = foe;
            }
        }
        Battle battle = new Battle(List.of(master), foes, noCrit());
        battle.startBattle();
        Summon memosprite = battle.summonMemosprite(master);
        Assertions.assertNotNull(memosprite, "precondition: the memosprite (忆灵) is out");
        double before = victim.getCurrentHp();
        // Note: COMMON, not SKILL: a memosprite's stated attack is installed in its COMMON slot.
        battle.castImmediate(memosprite.getSkills().get(SkillType.COMMON), memosprite, List.of(victim));
        double dealt = before - victim.getCurrentHp();
        Assertions.assertFalse(victim.isDeath(), "the judged hit must not kill the victim");
        Assertions.assertTrue(dealt > 0 && dealt < 0.4 * before,
                "a real measurement, not the whole bar: " + dealt + " of " + before);
        return dealt;
    }

    private static double allyHit(Battle battle, Character ally) {
        double before = battle.enemies().get(0).getCurrentHp();
        SkillExecutor.execute(battle, ally.getSkills().get(SkillType.COMMON), ally,
                List.of(battle.enemies().get(0)));
        battle.processRequests();
        return before - battle.enemies().get(0).getCurrentHp();
    }

    /**
     * An enemy with a deep health pool, so a hit is measurable without the field emptying.
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
}
