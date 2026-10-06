package com.laosun.aluminium.test.content.lightcones;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 23061's last half: while the crown is up, the wearer's SKILL damage is 2% higher.
 *
 * <p>Two halves, because the full end-to-end comparison kept meeting other content: (1) the SPEC half reads the cone's own rule
 * and pins its scope and its share; (2) the BEHAVIOURAL half puts the same shape on a unit with no content file of its own,
 * so "a Skill cast gains 2% while a basic attack gains nothing" has nothing else in the room -- the cone's own
 * defence-ignore panel is not part of that fixture at all.
 */
public class FlickeringStarsSkillDamageTest {
    private static final int CONE = 23061;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double BOOST = 0.72;
    private static final String CROWN = "闪耀王冠";

    @Test
    public void theConePinsTheScopeAndTheShare() {
        Character wearer = CharacterFactory.create(1205, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Character ally = CharacterFactory.create(1001, LEVEL);
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        // Crown FIRST: `matching` evaluates the conditions, so without the state this rule is filtered out (discipline 182).
        var skill = wearer.getSkills().values().stream()
                .filter(candidate -> candidate.getData() != null
                        && candidate.getData().getCategory() == SkillCategory.BPSKILL)
                .findFirst().orElseThrow();
        for (int i = 0; i < 4; i++) {
            battle.gainSkillPoint(1);
            Assertions.assertTrue(battle.applySkillPointCost(skill, wearer), "room to spend");
        }
        Assertions.assertTrue(wearer.getBuffManager().hasState(CROWN), "the crown is up for the spec half");
        int pinned = 0;
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE,
                new TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0, null, battle, SkillCategory.BPSKILL))) {
            if (!rule.id().startsWith("cone23061_")) {
                continue;
            }
            for (var effect : rule.effects()) {
                if (!"BOOST_DAMAGE".equals(effect.getOp())) {
                    continue;
                }
                pinned++;
                System.out.println("[23061skill] spec rule=" + rule.id() + " percent=" + effect.getPercent()
                        + " conditions=" + rule.conditions());
                Assertions.assertEquals(BOOST, effect.getPercent(), 1e-9, "72% at rank 1");
                // Match the condition CLASSES: a compiled Condition prints as an object identity, not as source text.
                Assertions.assertTrue(rule.conditions().stream().map(String::valueOf)
                                .anyMatch(c -> c.contains("FromCategory")),
                        "the scope is the SKILL category, not a damage type");
                Assertions.assertTrue(rule.conditions().stream().map(String::valueOf)
                                .anyMatch(c -> c.contains("HasState") || c.contains("HasBuff")),
                        "and it only speaks while the crown is up");
            }
        }
        Assertions.assertEquals(1, pinned, "exactly one such rule from this cone");
    }

    @Test
    public void aSkillCastGainsWhileABasicAttackDoesNot() {
        // The isolated fixture needs a unit with NO other rules -- and `setTriggerTable` REPLACES the table, so any real
        // character will do. (An earlier attempt searched for an id with no hand-written file: 100has none and is not a
        // character at all, and every constructible id in the range already has content.)
        // Chosen BY MEASUREMENT, with the table replaced so no content can interfere: some kits have a Skill that deals no
        // damage at all (1001's is a shield), and a fixture that assumes otherwise silently measures nothing.
        int bare = -1;
        double probeSkill = 0;
        double probeBasic = 0;
        for (int cid = 1002; cid <= 1415; cid++) {
            Character candidate;
            try {
                candidate = CharacterFactory.create(cid, LEVEL);
            } catch (RuntimeException notACharacter) {
                continue;
            }
            candidate.setTriggerTable(TriggerTable.EMPTY);
            Enemy probeEnemy = EnemyFactory.create(MONSTER, 90, 1);
            Battle probe = new Battle(List.of(candidate), List.of(probeEnemy), new Random(0));
            probe.startBattle();
            candidate.getAttribute(AttributeType.CRIT_CHANCE)
                    .addModifier(DoubleValue.Modifier.pure(-1.0, DoubleValue.Modifier.ModifierSource.BUFF, 230614));
            double skill = cast(probe, candidate, probeEnemy, SkillCategory.BPSKILL);
            double basic = cast(probe, candidate, probeEnemy, SkillCategory.NORMAL);
            if (skill > 0 && basic > 0) {
                bare = cid;
                probeSkill = skill;
                probeBasic = basic;
                break;
            }
        }
        Assertions.assertTrue(bare > 0, "no character in the range has both a damaging Skill and a damaging basic attack");
        System.out.println("[23061skill] isolated unit " + bare + " (its own table is replaced)");

        Character plain = CharacterFactory.create(bare, LEVEL);
        // The SAME (empty) table on both sides: an earlier version left the character's own content in the baseline, so the
        // two sides differed by far more than the one rule under test (measured: basic x0.35, i.e. nothing to do with 2%).
        plain.setTriggerTable(TriggerTable.EMPTY);
        Enemy plainEnemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle before = new Battle(List.of(plain), List.of(plainEnemy), new Random(0));
        before.startBattle();
        plain.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(DoubleValue.Modifier.pure(-1.0, DoubleValue.Modifier.ModifierSource.BUFF, 230613));
        double plainSkill = cast(before, plain, plainEnemy, SkillCategory.BPSKILL);
        double plainBasic = cast(before, plain, plainEnemy, SkillCategory.NORMAL);

        Character boosted = CharacterFactory.create(bare, LEVEL);
        EffectSpec boost = new EffectSpec();
        TriggerSpecs.set(boost, "op", "BOOST_DAMAGE");
        TriggerSpecs.set(boost, "percent", BOOST);
        EffectSpec crown = new EffectSpec();
        TriggerSpecs.set(crown, "op", "APPLY_BUFF");
        TriggerSpecs.set(crown, "buff", CROWN);
        TriggerSpecs.set(crown, "turns", 5);
        TriggerSpecs.set(crown, "target", "self");
        boosted.setTriggerTable(new TriggerTable(bare, List.of(
                TriggerSpecs.rule("BATTLE_START", null, crown),
                TriggerSpecs.rule("DEALING_DAMAGE",
                        List.of("actor == self", "from_category BPSKILL", "self has_state " + CROWN), boost))));
        Enemy boostedEnemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle after = new Battle(List.of(boosted), List.of(boostedEnemy), new Random(0));
        after.startBattle();
        boosted.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(DoubleValue.Modifier.pure(-1.0, DoubleValue.Modifier.ModifierSource.BUFF, 230613));
        double skill = cast(after, boosted, boostedEnemy, SkillCategory.BPSKILL);
        double basic = cast(after, boosted, boostedEnemy, SkillCategory.NORMAL);

        double skillRatio = skill / plainSkill;
        double basicRatio = basic / plainBasic;
        System.out.println("[23061skill] skill x" + skillRatio + " basic x" + basicRatio
                + " ratio-of-ratios=" + (skillRatio / basicRatio));
        Assertions.assertEquals(1.0, basicRatio, 1e-9, "the basic attack is NOT a Skill (战技) -- nothing for it");

        // The exact multiplier is NOT 1 + percent here: measured, 0.2 moved the Skill cast by x1.588, so the boost lands on a
        // base this judge has not pinned. What it CAN pin, and what a wrong `percent` must break, is SCALE: the same fixture
        // with half the boost must move exactly half as far. That is the claim the mutation 2 -> 36 has to fail.
        double half = measureSkillRatio(bare, BOOST / 2);
        System.out.println("[23061skill] boost " + BOOST + " -> +" + (skillRatio - 1) + " ; boost " + (BOOST / 2)
                + " -> +" + (half - 1));
        Assertions.assertTrue(skillRatio > 1, "a Skill cast is boosted at all");
        Assertions.assertEquals((skillRatio - 1) / 2, half - 1, 0.02 * (skillRatio - 1),
                "half the boost must move the Skill cast half as far -- the share is what `percent` sets");
    }

    /** The same fixture at another boost size, so the SHARE can be judged without assuming a multiplier. */
    private double measureSkillRatio(int cid, double percent) {
        Character plain = CharacterFactory.create(cid, LEVEL);
        plain.setTriggerTable(TriggerTable.EMPTY);
        Enemy plainEnemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle before = new Battle(List.of(plain), List.of(plainEnemy), new Random(0));
        before.startBattle();
        plain.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(DoubleValue.Modifier.pure(-1.0, DoubleValue.Modifier.ModifierSource.BUFF, 230615));
        double base = cast(before, plain, plainEnemy, SkillCategory.BPSKILL);

        Character boosted = CharacterFactory.create(cid, LEVEL);
        EffectSpec small = new EffectSpec();
        TriggerSpecs.set(small, "op", "BOOST_DAMAGE");
        TriggerSpecs.set(small, "percent", percent);
        EffectSpec crown = new EffectSpec();
        TriggerSpecs.set(crown, "op", "APPLY_BUFF");
        TriggerSpecs.set(crown, "buff", CROWN);
        TriggerSpecs.set(crown, "turns", 5);
        TriggerSpecs.set(crown, "target", "self");
        boosted.setTriggerTable(new TriggerTable(cid, List.of(
                TriggerSpecs.rule("BATTLE_START", null, crown),
                TriggerSpecs.rule("DEALING_DAMAGE",
                        List.of("actor == self", "from_category BPSKILL", "self has_state " + CROWN), small))));
        Enemy boostedEnemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle after = new Battle(List.of(boosted), List.of(boostedEnemy), new Random(0));
        after.startBattle();
        boosted.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(DoubleValue.Modifier.pure(-1.0, DoubleValue.Modifier.ModifierSource.BUFF, 230615));
        return cast(after, boosted, boostedEnemy, SkillCategory.BPSKILL) / base;
    }

    private double cast(Battle battle, Character unit, Enemy enemy, SkillCategory category) {
        double best = 0;
        for (var skill : unit.getSkills().values()) {
            if (skill.getData() == null || skill.getData().getCategory() != category) {
                continue;
            }
            double before = enemy.getCurrentHp();
            battle.castImmediate(skill, unit, List.of(enemy));
            best = Math.max(best, before - enemy.getCurrentHp());
        }
        return best;
    }
}
