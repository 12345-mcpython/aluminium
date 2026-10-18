package com.laosun.aluminium.test.content.characters.cyrene;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1415 昔涟's skill barrier: 「结界持续期间，我方全体目标每造成 1 次伤害，都会再额外造成 1 次等同于原伤害 24% 的真实伤害」
 * (141502, params [0.24, 2, 3]).
 *
 * <h2>Why this test is a cross-check against a real fight</h2>
 * Every hit in the whole-battle capture is followed by a {@code True} event whose ratio to the hit is
 * <b>371.472019 / 1547.800079 = 24.0000%</b> - exactly this rule's #1. So the rule and the capture agree, and this case
 * pins the engine side: one ally hit with the barrier up must settle the hit <b>plus</b> a true instance of 24% of it.
 */
public class CyreneZoneRiderTest {

    private static final int CYRENE = 1415;
    private static final int ALLY = 1001;

    @Test
    public void everyAllyHitGetsATrueRiderOfTwentyFourPercent() {
        Character cyrene = CharacterFactory.create(CYRENE, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(cyrene, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        // Her Skill opens the barrier.
        SkillExecutor.execute(battle, cyrene.getSkills().get(SkillType.SKILL), cyrene,
                List.of(battle.enemies.get(0)));
        battle.processRequests();

        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        SkillExecutor.execute(battle, ally.getSkills().get(SkillType.COMMON), ally,
                List.of(battle.enemies.get(0)));
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
}
