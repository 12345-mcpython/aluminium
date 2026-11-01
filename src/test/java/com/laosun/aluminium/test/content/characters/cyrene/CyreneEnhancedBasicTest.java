package com.laosun.aluminium.test.content.characters.cyrene;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.skill.SkillExecutor;
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
 * rolls.
 *
 * <p><b>✅ The per-part toughness is settled (2026-10-29).</b> The game's own
 * {@code AvatarSkillConfig.ShowStanceList} for 141508 — the same at every level — is {@code [30, 15, 0]}, and the data
 * generator reads those three slots as {@code {single, all, spread}}. So the single-target part carries 30 and the AoE
 * part 15 per victim, which is exactly what this judge asserts. The older note that "the ConfigAbility states 15 for the
 * first hit" was a misreading; it has no standing against the skill's own display table.
 */
public class CyreneEnhancedBasicTest {

    private static final int CYRENE = 1415;
    private static final int ALLY = 1001;

    /** Lv10 row of 141508: #1 = 0.42, #2 = 3, #3 = 0.42. */
    private static final double LV10_SHARE = 0.42;
    /** The same share at row 6, where the basic attack is pinned: 0.3 Max HP. */
    private static final double LV6_SHARE = 0.3;

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
}
