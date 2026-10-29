package com.laosun.aluminium.test.content.memosprites;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.damage.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.state.Resource;
import com.laosun.aluminium.models.entity.Summon;
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1413 长夜月's 忆灵技1 「追忆，蹁跹，如雨」 states <b>two</b> shares in one sentence, and the game folds the second into
 * the same six instances:
 *
 * <p>「对敌方单体造成等同于「长夜」<b>#1[i]%</b>生命上限的冰属性伤害，长夜月当前<b>每有 #3 点【忆质】</b>，则额外造成等同于
 * 「长夜」<b>#2[f1]%</b>生命上限的冰属性伤害」 - Lv8's row is {@code [0.6, 0.12, 4, 1]}, so the cast's share is
 * {@code 0.6 + (忆质 / 4) x 0.12}.
 *
 * <p><b>Measured, not guessed.</b> The recorded fight has the same skill twice: once at share 0.06 per segment
 * (0.6 x 0.1) and once at 0.09 (0.9 x 0.1). The gap is 0.03 = {@code (10 / 4) x 0.12} - ten points of 【忆质】 at the
 * second cast - and the instance count is still <b>six</b> both times, which is what says "folded into the share" and
 * not "two extra hits" (extra hits would each roll their own crit and fire their own events).
 *
 * <p><b>Whose 忆质.</b> The sentence says 「长夜月当前每有…」 - the summoner's, and the caster is the memosprite. The
 * resource is read through the same "the servant fights with its master's sheet" rule the damage base uses
 * ({@code Summon.damageBase}), so this case also pins that the reader is not the servant's own (empty) resource bag.
 */
public class EvernightMemoryFoldTest {

    private static final int EVERNIGHT = 1413;

    private static final int SERVANT = 11413;

    private static final int SKILL_SLOT = 1;

    /**
     * The panel the capture's own cast 4 was played at - so the numbers below are that cast's, to the last digit.
     */
    private static final double MASTER_MAX_HP = 4526.4984;

    /**
     * Lv8: {@code 0.06 = 0.6 x 0.1} per segment is what the capture's first cast reads, which is how the level is known.
     */
    private static final int LEVEL = 8;

    /**
     * The ability's HitSplitRatio list, in order ({@code skill_effects.json} 11413/1).
     */
    private static final double[] SPLITS = {0.1, 0.1, 0.1, 0.1, 0.1, 0.5};

    /**
     * The three points of the curve: no resource, exactly one step, and the capture's own ten points.
     */
    @Test
    public void theShareGrowsWithTheSummonersResource() {
        assertShareAt(0, 0.60, "no 忆质: the row's own #1, which is the capture's first cast");
        assertShareAt(4, 0.72, "one step of #3 = 4 points adds #2 = 0.12");
        assertShareAt(10, 0.90, "the capture's second cast: 0.6 + (10/4) x 0.12 = 0.9, i.e. 0.09 per segment");
    }

    /**
     * The same curve at a level where the row's numbers are flat enough to see the index: Lv10 is {@code [0.7, 0.14,
     * 4, 1]}, so 4 points read 0.84 - a different row, not a different rule.
     */
    @Test
    public void theTermsAreReadFromTheRowsOwnLevel() {
        List<Double> bases = castAt(10, 4);
        double share = 0.7 + (4 / 4.0) * 0.14;
        assertBases(bases, share, "Lv10's #1 + one step of #3 x #2");
    }

    /**
     * Casts the skill at {@code level} with {@code points} of 【忆质】 on the <b>summoner</b>, and returns the instances'
     * bases.
     */
    private static List<Double> castAt(int level, int points) {
        Character evernight = CharacterFactory.create(EVERNIGHT, 80);
        evernight.setAttribute(AttributeType.HEALTH, new DoubleValue(MASTER_MAX_HP));
        Battle battle = new Battle(List.of(evernight), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Summon evey = battle.memospriteOf(evernight);
        Assertions.assertNotNull(evey, "precondition: her talent summons 「长夜」 at battle start");

        Resource resource = evernight.getResources().get("亿质");
        if (resource == null) {
            // Her own rules register it in a real fight; the judge states the number it needs rather than replaying a kit.
            resource = evernight.getResources().register("亿质", 100, 0);
        }
        resource.setValue(points);
        Assertions.assertNull(evey.getResources().get("亿质"),
                "precondition: the servant has no 忆质 of its own - the sentence names the summoner's");

        Skill skill = new DefaultSkill(SERVANT, SKILL_SLOT, level);
        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        SkillExecutor.execute(battle, skill, evey, List.of(battle.enemies().getFirst()));
        battle.processRequests();

        return settled.stream().map(hit -> hit.breakdown().get("base")).toList();
    }

    private static void assertShareAt(int points, double expectedShare, String why) {
        assertBases(castAt(LEVEL, points), expectedShare, why);
    }

    /**
     * Every instance is {@code split x share x the SUMMONER's Max HP}, in the ability's own split order.
     */
    private static void assertBases(List<Double> bases, double share, String why) {
        Assertions.assertEquals(SPLITS.length, bases.size(),
                "the row settles six instances whatever the share is - the extra is folded in, not added as hits (" + why
                        + ")");
        for (int i = 0; i < SPLITS.length; i++) {
            Assertions.assertEquals(SPLITS[i] * share * MASTER_MAX_HP, bases.get(i), 1e-6,
                    "instance " + (i + 1) + " is split " + SPLITS[i] + " of share " + share + " (" + why + ")");
        }
    }
}
