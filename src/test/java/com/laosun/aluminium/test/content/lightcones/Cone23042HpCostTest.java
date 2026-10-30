package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.equipment.Weapon;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Cone 23042 愿虹光永驻天空's last clause: 「施放普攻/战技/终结技时，<b>消耗我方全体当前生命值 1%</b>，并使忆灵的<b>下一次攻击后</b>额外造成 1
 * 次数值为<b>消耗总量 2.5%</b> 的附加伤害，随后清空。」.
 *
 * <p><b>What had to exist first.</b> Three things the engine did not have: a scale for <b>current</b> HP (`target_current_hp`,
 * where the family only had `*_max_hp`), a way for the next effect of the same rule to read <b>how much the consumption
 * took</b> (`amountFromPrevious`, which only resource-moving ops filled), and a light cone that can <b>declare its own
 * counter</b> (two-sided declarations were refused outright).
 */
public class Cone23042HpCostTest {

    private static final int WEARER = 1415;
    private static final int ALLY = 1001;
    private static final int CONE = 23042;
    private static final double EPS = 1e-6;

    private record Scene(Battle battle, Character wearer, Character ally, List<Damage> settled) {
    }

    private static Scene scene() {
        Character wearer = CharacterFactory.create(WEARER, 80, true, Weapon.build(CONE, 80, false, 1));
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(wearer, ally), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        return new Scene(battle, wearer, ally, settled);
    }

    /**
     * A cast consumes 1% of <b>current</b> HP from every ally, and the counter holds the sum.
     */
    @Test
    public void aCastConsumesOnePercentOfCurrentHpAndAccumulatesIt() {
        Scene scene = scene();
        double before = scene.wearer.getCurrentHp() + scene.ally.getCurrentHp();

        SkillExecutor.execute(scene.battle, scene.wearer.getSkills().get(SkillType.SKILL), scene.wearer,
                List.of(scene.battle.enemies().getFirst()));
        scene.battle.processRequests();

        double after = scene.wearer.getCurrentHp() + scene.ally.getCurrentHp();
        double consumed = before - after;
        Assertions.assertTrue(consumed > 0, "「消耗我方全体当前生命值 1%」: something was consumed");
        Assertions.assertEquals(0.01 * before, consumed, 0.01 * before * 0.01,
                "1% of the CURRENT total, not of Max HP");
        Assertions.assertEquals(consumed, scene.wearer.getResources().get("虹光累计消耗").exactValue(), 0.5,
                "「消耗总量」: the counter carries what the cast took, for the next memosprite attack");
    }

    /**
     * The memosprite's next attack pays 2.5% of the counter out as ONE additional instance, and clears it.
     */
    @Test
    public void theNextMemospriteAttackPaysItOutOnce() {
        Scene scene = scene();
        scene.battle.summonMemosprite(scene.wearer);
        Summon servant = scene.battle.summonOf(scene.wearer);
        Assertions.assertNotNull(servant, "her memosprite is out");

        SkillExecutor.execute(scene.battle, scene.wearer.getSkills().get(SkillType.SKILL), scene.wearer,
                List.of(scene.battle.enemies().getFirst()));
        scene.battle.processRequests();
        double pooled = scene.wearer.getResources().get("虹光累计消耗").exactValue();
        Assertions.assertTrue(pooled > 0, "precondition: the cast pooled something");

        scene.battle.fire(TriggerPayload.Simple.of(TriggerEvent.SUMMON_ATTACK, servant,
                scene.battle.enemies().getFirst(), 1, 0));
        scene.battle.processRequests();

        List<Damage> riders = scene.settled.stream().filter(d -> d.getType() == DamageType.ADDITIONAL).toList();
        Assertions.assertEquals(1, riders.size(), "exactly ONE additional instance per attack");
        Assertions.assertEquals(0.025 * pooled, riders.getFirst().breakdown().get("base"), 0.5,
                "2.5% of the pooled total");
        Assertions.assertEquals(0, scene.wearer.getResources().get("虹光累计消耗").exactValue(), EPS,
                "「随后清空」: the pool is spent");

        int before = scene.settled.size();
        scene.battle.fire(TriggerPayload.Simple.of(TriggerEvent.SUMMON_ATTACK, servant,
                scene.battle.enemies().getFirst(), 1, 0));
        scene.battle.processRequests();
        Assertions.assertEquals(before, scene.settled.size(),
                "a second attack has an empty pool, so it pays out nothing");
    }
}
