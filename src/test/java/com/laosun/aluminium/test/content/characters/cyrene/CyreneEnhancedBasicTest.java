package com.laosun.aluminium.test.content.characters.cyrene;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1415 昔涟's enhanced basic 「向着爱与明天♪」 (141508) when it is actually cast: <b>two damage instances in one row</b>.
 *
 * <p>Her ultimate installs this row into the {@code COMMON} slot ({@code REPLACE_SKILL}), and its sentence is
 * 「对指定敌方单体造成等同于昔涟 #3% 生命上限的冰伤，<b>随后</b>对敌方全体造成等同于昔涟 #1% 生命上限的冰伤」. Before the
 * engine learned multi-part rows, an {@code AoEAttack} row settled one area hit and dropped the single-target half
 * silently; the data's own max-hit column says the row is two instances
 * ({@code skill_max_hits.json avatars.1415.Skill11 = 2}, pinned in {@link CyreneEnhancedBasicPartsTest}).
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
 * rolls. ⚠ The per-part toughness is read by the same column convention the engine uses for every single-shape skill
 * (see the table entry's own {@code note}: the game's ConfigAbility states 15 for the first hit, which is <b>not</b>
 * stronger evidence than the stance columns - registered as 待核, not silently adopted).
 */
public class CyreneEnhancedBasicTest {

    private static final int CYRENE = 1415;
    private static final int ALLY = 1001;

    /** Lv10 row of 141508: #1 = 0.42, #2 = 3, #3 = 0.42. */
    private static final double LV10_SHARE = 0.42;

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
                List.of(battle.enemies.get(0)));
        battle.processRequests();
        Skill enhanced = cyrene.getSkills().get(SkillType.COMMON);
        Assertions.assertTrue(enhanced.getData().getDescription().contains("向着爱与明天"),
                "precondition: the ultimate installed the enhanced basic; description was "
                        + enhanced.getData().getDescription());

        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        SkillExecutor.execute(battle, enhanced, cyrene, List.of(battle.enemies.get(0)));
        battle.processRequests();

        List<Damage> hits = settled.stream().filter(d -> d.getType() == DamageType.NORMAL).toList();
        Assertions.assertEquals(3, hits.size(),
                "one single-target instance + one area instance per enemy; settled stances were "
                        + hits.stream().map(Damage::getStance).toList());

        double expectedBase = LV10_SHARE * cyrene.getAttribute(AttributeType.HEALTH).get();
        for (Damage hit : hits) {
            Assertions.assertEquals(expectedBase, hit.breakdown().get("base"), 1e-6,
                    "each part takes its own #3 / #1 share from the current level row (at Lv10 both are 0.42 Max HP)");
        }

        CanHit aimed = battle.enemies.get(0);
        Assertions.assertSame(aimed, hits.get(0).getDefender(), "the single-target half goes to the aimed enemy first");
        Assertions.assertSame(aimed, hits.get(1).getDefender(), "the area half reaches the aimed enemy too");
        Assertions.assertSame(battle.enemies.get(1), hits.get(2).getDefender(), "and the second enemy");

        Assertions.assertEquals(30, hits.get(0).getStance(), 1e-6, "the single part reads stance_list.single");
        Assertions.assertEquals(15, hits.get(1).getStance(), 1e-6, "the area part reads stance_list.all, per victim");
        Assertions.assertEquals(15, hits.get(2).getStance(), 1e-6, "the area part reads stance_list.all, per victim");
    }
}
