package com.laosun.aluminium.test.content.characters.robinsummeretto;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.damage.Damage;
import com.laosun.aluminium.models.entity.Summon;
import com.laosun.aluminium.models.buff.StateBuff;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1512 知更鸟•晴歌's 【Fever】 state also <b>raises her own damage</b>: 「使知更鸟•晴歌与自身造成的伤害提高
 * (0.84 + 气氛值 x 0.028)」 (memospirite 11512's SkillP01, params [0.84, 0.028, ...]).
 *
 * <h2>Why the reading is a difference, not a ratio</h2>
 * Her own panel may already carry an ALL_DAMAGE_TYPE_BOOST (traces), so a ratio would mix two unknowns. The boost
 * bucket is additive, so the <b>difference</b> between two gauge readings is exactly the part the rule adds:
 * {@code BoostArea(气氛值 50) - BoostArea(气氛值 0) = 50 x 0.028 = 1.4}.
 */
public class FeverDamageBoostTest {

    private static final int ROBIN = 1512;
    private static final int ALLY = 1001;
    private static final String VIBES = "气氛值";
    private static final String FEVER = "Fever";

    @Test
    public void theGaugeAddsTwoPointEightPercentDamagePerPoint() {
        double at0 = boostAfterWarmUp(0);
        double at50 = boostAfterWarmUp(50);
        Assertions.assertEquals(1.4, at50 - at0, 1e-6,
                "50 points x 0.028 = 1.4 more boost with a full gauge; got " + (at50 - at0));
    }

    @Test
    public void theBaseEightyFourPercentIsThereEvenWithAnEmptyGauge() {
        double withoutFever = boostAfterWarmUp(0, false);
        double withFever = boostAfterWarmUp(0);
        Assertions.assertEquals(0.84, withFever - withoutFever, 1e-6,
                "(0.84 + 0 x 0.028): the state itself adds 84%; got " + (withFever - withoutFever));
    }

    private static double boostAfterWarmUp(int vibes) {
        return boostAfterWarmUp(vibes, true);
    }

    /**
     * The same clause covers her memosprite: 「使知更鸟•晴歌与<b>自身</b>造成的伤害提高」 - a separate rule on
     * {@code actor == summon}, and it only became reachable once {@code SkillExecutor} raised {@code SUMMON_ATTACK} for a
     * data-driven memosprite.
     */
    @Test
    public void theMemospriteGetsTheSameBaseBoost() {
        double without = memoBoost(0, false);
        double with = memoBoost(0, true);
        Assertions.assertEquals(0.84, with - without, 1e-6,
                "with Fever the memosprite's own hit carries +84%; without it, nothing (" + without + " vs " + with + ")");
    }

    /** The memosprite's own cast, measured as the boost bucket of the settled damage. */
    private static double memoBoost(int vibes, boolean fever) {
        Character robin = CharacterFactory.create(ROBIN, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(robin, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        battle.summonMemosprite(robin);
        battle.processRequests();
        if (vibes > 0) {
            battle.partyResource(VIBES).gain(vibes);
        }
        if (fever) {
            robin.getBuffManager().addBuff(new StateBuff(FEVER, 99));
        }
        battle.processRequests();
        Summon memo = (Summon) battle.memospriteOf(robin);
        Assertions.assertNotNull(memo, "precondition: the memosprite is out");
        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        SkillExecutor.execute(battle, memo.skillAt(1), memo, List.of(battle.enemies().get(0)));
        battle.processRequests();
        Assertions.assertFalse(settled.isEmpty(), "precondition: the memosprite's cast settled");
        return settled.get(0).breakdown().get("BoostArea");
    }

    /**
     * The boost the rule leaves on the NEXT hit (it is applied while a hit is dealing damage, so a warm-up cast puts
     * it up first - `turns: 1` lasts through it).
     */
    private static double boostAfterWarmUp(int vibes, boolean fever) {
        Character robin = CharacterFactory.create(ROBIN, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(robin, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        if (vibes > 0) {
            battle.partyResource(VIBES).gain(vibes);
        }
        Assertions.assertEquals(Math.min(vibes, 50), battle.partyResourceValue(VIBES),
                "precondition: the gauge moved");
        if (fever) {
            robin.getBuffManager().addBuff(new StateBuff(FEVER, 99));
        }
        battle.processRequests();
        SkillExecutor.execute(battle, robin.getSkills().get(SkillType.COMMON), robin,
                List.of(battle.enemies().get(0)));
        battle.processRequests();

        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        SkillExecutor.execute(battle, robin.getSkills().get(SkillType.COMMON), robin,
                List.of(battle.enemies().get(0)));
        battle.processRequests();
        Assertions.assertFalse(settled.isEmpty(), "precondition: the measured cast settled");
        return settled.get(0).breakdown().get("BoostArea");
    }
}
