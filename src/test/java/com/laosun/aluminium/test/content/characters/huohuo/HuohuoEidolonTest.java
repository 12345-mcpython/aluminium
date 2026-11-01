package com.laosun.aluminium.test.content.characters.huohuo;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1217 藿藿's Eidolons 3, 5 and 6.
 *
 * <p>3 and 5 are the level clauses the data spells as {@code skill_add_level}; 6 is the one measured here: 「为我方目标提供治疗时，
 * 使目标造成的伤害提高 50%，持续 2 回合」 - it hangs on {@code HEALED}, which {@code Battle.heal} broadcasts after the amount is
 * settled, and it must fire only for HER heals (「为我方目标提供治疗时」).
 */
public class HuohuoEidolonTest {

    private static final int LEVEL = 80;
    private static final int HUOHUO = 1217;
    private static final int ALLY = 1001;

    /**
     * Eidolon 3 and 5: the level clauses.
     */
    @Test
    public void theLevelEidolonsRaiseTheirSlots() {
        Assertions.assertEquals(level(0, SkillType.ULTRA) + 2, level(3, SkillType.ULTRA), "E3: 终结技 +2");
        Assertions.assertEquals(level(0, SkillType.TALENT) + 2, level(3, SkillType.TALENT), "E3: 天赋 +2");
        Assertions.assertEquals(level(0, SkillType.SKILL) + 2, level(5, SkillType.SKILL), "E5: 战技 +2");
        Assertions.assertEquals(level(0, SkillType.COMMON) + 1, level(5, SkillType.COMMON), "E5: 普攻 +1");
    }

    /**
     * Eidolon 6: the healed ally gains 50% damage dealt, and only when SHE healed it.
     */
    @Test
    public void eidolonSixBoostsTheAllySheHeals() {
        Assertions.assertEquals(0.0, damageBoost(0, true), 1e-9, "E0: nothing");
        Assertions.assertEquals(0.5, damageBoost(6, true), 1e-9, "「使目标造成的伤害提高 50%」");

        // Somebody else's heal must not trigger hers.
        Fixture f = new Fixture(6);
        Character other = CharacterFactory.create(1002, LEVEL);
        f.battle.heal(other, f.ally, 100.0);
        Assertions.assertEquals(0.0, f.ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), 1e-9,
                "「为我方目标提供治疗时」 - the healer must be her");
    }

    private static double damageBoost(int eidolon, boolean healedByHer) {
        Fixture f = new Fixture(eidolon);
        double before = f.ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        if (healedByHer) {
            f.battle.heal(f.huohuo, f.ally, 100.0);
        }
        return f.ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - before;
    }

    private static int level(int eidolon, SkillType slot) {
        Fixture f = new Fixture(eidolon);
        return 1 + f.huohuo.skillLevelBonus(slot);
    }

    // ==================================================================

    private static final class Fixture {
        private final Character huohuo;
        private final Character ally;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private Fixture(int eidolon) {
            huohuo = CharacterFactory.create(HUOHUO, LEVEL, true, null, null, eidolon);
            ally = CharacterFactory.create(ALLY, LEVEL);
            battle = new Battle(new ArrayList<>(List.of(huohuo, ally)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.seeded());
            battle.startBattle();
            // A full-HP ally cannot be healed for anything, and a zero heal has no amount to broadcast.
            battle.applyDamage(ally, new com.laosun.aluminium.damage.Damage(battle.enemies().getFirst(), ally,
                    com.laosun.aluminium.enums.DamageElement.PHYSICAL, com.laosun.aluminium.enums.DamageType.NORMAL, 200.0));
        }
    }
}

