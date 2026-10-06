package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1002 Dan Heng, from his own file: the talent that answers "an ally aimed a skill at me".
 *
 * <p>Everything it needs already existed: `SKILL_CAST` carries the aim (so `target == self` means "I was the one aimed at"),
 * `actor is_ally` says who cast it, `DAMAGE_PENETRATION` is the engine's name for RES PEN (抗性穿透), `until: next_attack` is the duration the
 * sentence asks for, and `cooldown` is the rule-level field for "2回合后可再次触发".
 */
public class DanHengTest {
    private static final int DANHENG = 1002;
    private static final int ALLY = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Note: An ally's skill aimed at him raises his penetration; a skill aimed elsewhere does not. */
    @Test
    public void anAllysSkillAimedAtHimRaisesHisPenetration() {
        Fixture f = new Fixture();
        double before = f.danheng.getAttribute(AttributeType.DAMAGE_PENETRATION).get();

        f.battle.castImmediate(f.ally.getSkills().get(SkillType.SKILL), f.ally, List.of(f.danheng));

        Assertions.assertTrue(f.danheng.getAttribute(AttributeType.DAMAGE_PENETRATION).get() > before,
                "「当丹恒成为我方技能的施放目标时，下一次攻击的风属性抗性穿透提高36%」: "
                        + before + " -> " + f.danheng.getAttribute(AttributeType.DAMAGE_PENETRATION).get());
    }

    /** Note: The control: an ally's skill aimed at someone ELSE must leave his penetration alone. */
    @Test
    public void anAllysSkillAimedElsewhereLeavesItAlone() {
        Fixture f = new Fixture();
        double before = f.danheng.getAttribute(AttributeType.DAMAGE_PENETRATION).get();

        f.battle.castImmediate(f.ally.getSkills().get(SkillType.SKILL), f.ally, List.of(f.ally));

        Assertions.assertEquals(before, f.danheng.getAttribute(AttributeType.DAMAGE_PENETRATION).get(), 1e-9,
                "「成为我方技能的施放目标」 -- being on the field is not enough");
    }

    /** Census: the talent and the level convention are where the notes say. */
    @Test
    public void hisFileCarriesTheClauses() {
        var table = TriggerTables.of(DANHENG);
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.SKILL_CAST), "the talent");
        Assertions.assertEquals(2, table.ruleCount(TriggerEvent.BATTLE_START),
                "the technique opening ATK (round 179) and the level convention");
    }

    private static final class Fixture {
        private final Character danheng = CharacterFactory.create(DANHENG, LEVEL);
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        private final Battle battle = new Battle(List.of(danheng, ally), List.of(enemy), new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        });

        private Fixture() {
            battle.startBattle();
        }
    }
}
