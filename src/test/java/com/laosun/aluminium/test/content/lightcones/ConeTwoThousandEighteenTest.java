package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 匿影 (20018): 「施放战技后，使装备者的<b>下一次普攻</b>造成的伤害提高，提高数值等同于装备者攻击力的 <b>60%</b>」.
 *
 * <p><b>Two things the old note got wrong, both measured here.</b>
 * <ul>
 *   <li>「战技后再接战技，总伤害也变大」 was a <b>within-instance comparison</b> dominated by a weakness BREAK: the second skill broke
 *       the target, and the break instance is 4843.99 next to a 557.38 normal hit. The cone's own addend on that hit is 0.</li>
 *   <li>The marker is NOT burned by the skill's own damage: `SKILL_CAST` arms it, and it is still there when the basic lands - the
 *       addend on the basic is exactly 0.6 x ATK.</li>
 * </ul>
 */
public class ConeTwoThousandEighteenTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1204;

    private static final class Run {
        private final Character wearer;
        private final Battle battle;
        private final Enemy enemy;
        private final List<Damage> settled = new ArrayList<>();

        Run() {
            wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(20018, LEVEL, false, 1));
            enemy = BattleFixture.monster();
            battle = new Battle(new ArrayList<>(List.of(wearer)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.seeded());
            battle.startBattle();
            battle.addDamageObserver((Damage damage, double applied, double hpLoss, double shield, boolean died) ->
                    settled.add(damage));
        }

        List<Damage> cast(SkillType slot) {
            settled.clear();
            battle.castImmediate(wearer.getSkills().get(slot), wearer, List.of(enemy));
            battle.processRequests();
            return List.copyOf(settled);
        }

        double flatOfNormal(List<Damage> instances) {
            return instances.stream().filter(d -> d.getType() == DamageType.NORMAL)
                    .mapToDouble(Damage::getFlatAddend).findFirst().orElse(0);
        }
    }

    /**
     * The skill's own hit carries nothing; the basic that follows carries 60% of her attack.
     */
    @Test
    public void theBonusGoesToTheNextBasicAndNotToTheSkillItself() {
        Run run = new Run();
        List<Damage> skill = run.cast(SkillType.SKILL);
        Assertions.assertEquals(0.0, run.flatOfNormal(skill), 1e-9,
                "「下一次**普攻**」 - the skill's own hit is not a basic attack");

        List<Damage> basic = run.cast(SkillType.COMMON);
        double expected = 0.6 * run.wearer.getAttribute(AttributeType.ATTACK).get();
        Assertions.assertEquals(expected, run.flatOfNormal(basic), 1e-6,
                "the basic carries 「等同于装备者攻击力的 60%」 as an addend (" + expected + ")");
    }
}
