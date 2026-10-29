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
 * 1413 长夜月's 忆灵技2 「迷梦，流失，如露」 (1141307) prices its hit <b>per point</b> of 【忆质】, and prices the aimed
 * target differently from the rest:
 *
 * <p>「…当前每持有 1 点【忆质】对<b>主目标</b>造成等同于「长夜」<b>#1</b>%生命上限的冰属性伤害，对<b>其他敌方目标</b>造成等同于
 * 「长夜」<b>#2</b>%生命上限的冰属性伤害，施放后，消耗全部【忆质】和生命值，并使「长夜」消失。」
 *
 * <p><b>The numbers are the capture's, not a guess.</b> Cast 6 of {@code veritas-085418} reads
 * {@code 3.0000000028} on the main target and {@code 1.5000000014} on the other; the Lv1 row is {@code [0.06, 0.03, 16]},
 * and {@code 0.06 x 50 = 3.0} / {@code 0.03 x 50 = 1.5} exactly - fifty points. (The Lv10 row would need 17.857 points,
 * which is not a whole number of points, so the row is the Lv1 one.)
 *
 * <p>This is <b>not</b> the fold {@code EvernightMemoryFoldTest} covers: there the resource <b>adds</b> a step share to
 * a fixed one, here it <b>multiplies</b> the whole share, and the two parameter indices are per-target.
 */
public class EvernightDreamDumpTest {

    private static final int EVERNIGHT = 1413;

    private static final int SERVANT = 11413;

    private static final int SKILL_SLOT = 7;

    /** The panel the capture's cast 6 was played at, so the expected numbers are that cast's own. */
    private static final double MASTER_MAX_HP = 4309.879191533505;

    /** The row the capture's numbers pin: {@code 0.06 = 3.0 / 50}. */
    private static final int LEVEL = 1;

    /**
     * Fifty points: the main target is priced at {@code #1 x 50 = 3.0} of Max HP, every other enemy at
     * {@code #2 x 50 = 1.5}.
     */
    @Test
    public void everyPointIsPricedOnItsOwnTarget() {
        List<Damage> hits = castWith(50);
        Assertions.assertEquals(2, hits.size(), "precondition: two living enemies are hit");
        Assertions.assertEquals(3.0 * MASTER_MAX_HP, baseOf(hits.get(0)), 1e-6,
                "the aimed target reads #1 x 50 (the capture's 3.0000000028)");
        Assertions.assertEquals(1.5 * MASTER_MAX_HP, baseOf(hits.get(1)), 1e-6,
                "and every other enemy reads #2 x 50 (the capture's 1.5000000014)");
    }

    /**
     * Nothing held, nothing dealt - the row is a count, so the same cast with zero points is 0 on both.
     */
    @Test
    public void noPointsMeansNoDamage() {
        List<Damage> hits = castWith(0);
        Assertions.assertEquals(2, hits.size(), "precondition: the cast still reaches both enemies");
        for (Damage hit : hits) {
            Assertions.assertEquals(0, baseOf(hit), 1e-9, "a count of zero multiplies the share to nothing");
        }
    }

    /**
     * 「施放后，消耗全部【忆质】和生命值，并使「长夜」消失」 - the three post-cast consequences, each observable.
     *
     * <p>Read together they also say the ORDER was right: the hit is priced at the 50 points the cast <b>started</b>
     * with (asserted above), and only afterwards is the counter emptied - a rule pinned to {@code CAST_SETUP} would have
     * zeroed it before the hits and dealt nothing.
     */
    @Test
    public void theCastSpendsEverythingAndTakesTheServantWithIt() {
        Scene scene = castScene(50);
        // ⚠ The pool ends at 2, not 0, and that is the measured behaviour: this clause CONSUMES HP, her own talent
        // answers 「每次生命降低时…获得 2 点【忆质】」, so 2 comes straight back. The assertion is still sharp: without
        // `spendAll` the number would be 50 (what she started with), so this distinguishes "emptied, then refunded by
        // her kit" from "never emptied".
        Assertions.assertEquals(2, scene.evernight().getResources().get("亿质").value(), 1e-9,
                "「消耗全部【忆质】」 emptied the pool; the 2 left is her talent's grant for the HP this same clause "
                        + "consumed (50 would mean the spend never happened)");
        Assertions.assertEquals(1, scene.evernight().getCurrentHp(), 1e-6,
                "「消耗…生命值」: down to the op's documented 1-point floor");
        Assertions.assertFalse(scene.evernight().isDeath(), "…and she does not fall - the sentence does not say so");
        Assertions.assertTrue(scene.evey().isDeath(),
                "「并使「长夜」消失」: dismissed through perish(), so no KILL is credited either");
    }

    /**
     * Casts 11413 slot 7 at {@link #LEVEL} with {@code points} of 【亿质】 on the <b>summoner</b>.
     */
    private static List<Damage> castWith(int points) {
        return castScene(points).hits();
    }

    /**
     * The same cast, with the pieces the post-cast case has to look at.
     */
    private static Scene castScene(int points) {
        Character evernight = CharacterFactory.create(EVERNIGHT, 80);
        evernight.setAttribute(AttributeType.HEALTH, new DoubleValue(MASTER_MAX_HP));
        Battle battle = new Battle(List.of(evernight),
                List.of(BattleFixture.dummy(), BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Summon evey = battle.memospriteOf(evernight);
        Assertions.assertNotNull(evey, "precondition: her talent summons 「长夜」 at battle start");

        Resource resource = evernight.getResources().get("亿质");
        if (resource == null) {
            resource = evernight.getResources().register("亿质", 100, 0);
        }
        resource.setValue(points);
        Assertions.assertNull(evey.getResources().get("亿质"),
                "precondition: the counter is the summoner's - the sentence says 「长夜月当前每持有…」");

        Skill skill = new DefaultSkill(SERVANT, SKILL_SLOT, LEVEL);
        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        SkillExecutor.execute(battle, skill, evey, List.of(battle.enemies().getFirst()));
        battle.processRequests();
        return new Scene(evernight, evey, settled);
    }

    /**
     * The pieces a case needs after the cast: the summoner, its memosprite, and the settled instances.
     */
    private record Scene(Character evernight, Summon evey, List<Damage> hits) {
    }

    private static double baseOf(Damage damage) {
        return damage.breakdown().get("base");
    }
}
