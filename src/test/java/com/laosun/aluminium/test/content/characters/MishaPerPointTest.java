package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerInterpreter;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1312 Misha: "我方全体每消耗 1 个战技点…米沙恢复 2.00 点能量" -- 2 PER POINT.
 *
 * <p>MishaTest fires the event with one point; this one hands the rule a context whose amount is 2 and
 * expects 4, which is the difference a per-action reading cannot show.
 */
public class MishaPerPointTest {
    private static double energyAfter(int pts) {
        Character c = CharacterFactory.create(1312, 80, true, null, null);
        Enemy e = EnemyFactory.create(1002011, 90, 1);
        Battle b = new Battle(List.of(c), List.of(e), new Random(0));
        b.startBattle();
        c.setCurrentEnergy(0);
        TriggerTable.TriggerContext ctx = new TriggerTable.TriggerContext(c, c, e, 1, pts, null, b, SkillCategory.UNSPECIFIED);
        var rules = TriggerTables.of(1312).rulesFor(TriggerEvent.SKILL_POINT_SPENT).stream()
                .filter(r -> "talent_energy_on_skill_point_spent".equals(r.id())).toList();
        Assertions.assertEquals(1, rules.size());
        double before = c.getCurrentEnergy();
        TriggerInterpreter.apply(b, rules.getFirst(), ctx);
        return c.getCurrentEnergy() - before;
    }

    @Test
    public void twoSpentPointsReturnFourEnergy() {
        Assertions.assertEquals(2.0, energyAfter(1), 1e-6);
        Assertions.assertEquals(4.0, energyAfter(2), 1e-6, "two points must be 4 energy");
        System.out.println("[1312] per-point ok: 1 -> " + energyAfter(1) + ", 2 -> " + energyAfter(2));
    }
}
