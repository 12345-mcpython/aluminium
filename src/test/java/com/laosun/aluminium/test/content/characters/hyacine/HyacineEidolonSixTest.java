package com.laosun.aluminium.test.content.characters.hyacine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1409 风堇's Rank 6, second half: 「<b>小伊卡在场时</b>，我方全体目标的全属性<u>抗性穿透</u>提高 <b>20%</b>」.
 *
 * <p>Two things are being measured, and they are different facts:
 * <ul>
 *   <li>the number -- {@code DAMAGE_PENETRATION} is the all-type spelling (it subtracts from whatever element the hit
 *       already resolved, see {@code Battle}'s resistance zone), and 0.2 is a ratio attribute, so the share lands as
 *       absolute points;</li>
 *   <li>the <b>gate</b> -- 「小伊卡在场时」 is an event, not a loadout fact: the aura must be absent at
 *       {@code BATTLE_START} and present only once a summon has actually entered the field. Reading it off
 *       {@code BATTLE_START} would have been the cheap version, and it would have given a wrong number for the whole
 *       first stretch of every fight.</li>
 * </ul>
 *
 * <p>⚠ The first half of the same sentence (「清空累计治疗数值改为 12%」) is registered, not written: the tally that the
 * memosprite's skill 1 clears is that skill's own mechanic and has no rule yet.
 */
public class HyacineEidolonSixTest {

    private static final int HYACINE = 1409;
    private static final int ALLY = 1001;
    private static final double EPS = 1e-9;

    @Test
    public void rankSixGivesTheWholeSideResistancePenetrationOnceIcaIsOut() {
        Fixture f = new Fixture(6);
        Assertions.assertEquals(0.0, f.pen(f.hyacine), EPS,
                "precondition: 「小伊卡在场时」 -- nothing before a summon has entered the field");
        Assertions.assertEquals(0.0, f.pen(f.ally), EPS,
                "precondition: the aura is not a BATTLE_START fact");

        f.skillCast();     // 战技召唤小伊卡 ⇒ SUMMONED

        Assertions.assertTrue(f.battle.memospriteOf(f.hyacine) != null, "precondition: 小伊卡 is out");
        Assertions.assertEquals(0.2, f.pen(f.hyacine), EPS, "「我方全体…抗性穿透提高 20%」 (the caster)");
        Assertions.assertEquals(0.2, f.pen(f.ally), EPS, "「我方全体…抗性穿透提高 20%」 (an ally)");
        CanHit ika = f.battle.memospriteOf(f.hyacine);
        Assertions.assertEquals(0.2, ika.getAttribute(AttributeType.DAMAGE_PENETRATION).get(), EPS,
                "「我方全体目标」 is the CAMP, so the memosprite in it is included too");
    }

    @Test
    public void theSkillGivesNothingWithoutRankSix() {
        Fixture f = new Fixture(0);
        f.skillCast();
        Assertions.assertTrue(f.battle.memospriteOf(f.hyacine) != null, "precondition: 小伊卡 is out");
        Assertions.assertEquals(0.0, f.pen(f.hyacine), EPS, "Rank 6 is what turns the aura on");
        Assertions.assertEquals(0.0, f.pen(f.ally), EPS, "Rank 6 is what turns the aura on");
    }

    private static final class Fixture {
        final Battle battle;
        final Character hyacine;
        final Character ally;

        Fixture(int eidolon) {
            this.hyacine = CharacterFactory.create(HYACINE, 80, true, null, null, eidolon);
            this.ally = CharacterFactory.create(ALLY, 80);
            this.battle = new Battle(List.of(hyacine, ally), List.of(BattleFixture.dummy()),
                    BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        void skillCast() {
            SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.SKILL), hyacine,
                    List.of(battle.enemies().getFirst()));
            battle.processRequests();
        }

        double pen(CanHit unit) {
            return unit.getAttribute(AttributeType.DAMAGE_PENETRATION).get();
        }
    }
}
