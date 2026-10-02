package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 写入端：`Battle.castUltra` 把"本次消耗"送到结算时的伤害实例上。
 *
 * <p>⚠ 为什么必须有这一半：读取端的判据是**手工设** `castEnergySpent` 的，它根本不经过 `Battle`。
 * 实测过：把 `Battle` 那行写入注释掉，全量套件**照样绿**。
 *
 * <p>⚠ 两次选错探针，都是同一个原因 —— 能读 `scale` 的 op 是一张**闭集**：
 * `{BOOST_DAMAGE, DAMAGE, MODIFY_ATTR, MODIFY_DAMAGE_TAKEN}`（TriggerInterpreter:256）。
 * `ADD_STACK` 不在里面，实测：探针固定读到 2 层，写入被注释掉也照样 2 层。所以这里用 `MODIFY_ATTR`。
 */
public class Cone23062WriteSideTest {
    private static final int LEVEL = 80;
    private static final int WEARER = 1003;
    private static final int MONSTER = 1002011;
    private static final double PER_POINT = 0.001;

    /** 放一次满能量的终结技，返回 ATTACK 的增长（＝读到的本次消耗 × percent）。 */
    private static double[] attackGrowthFromUltimate() {
        Character wearer = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));
        battle.startBattle();

        EffectSpec boost = new EffectSpec();
        TriggerSpecs.set(boost, "op", "MODIFY_ATTR");
        TriggerSpecs.set(boost, "attribute", "ATTACK");
        TriggerSpecs.set(boost, "scale", "cast_energy_spent");
        TriggerSpecs.set(boost, "percent", PER_POINT);
        TriggerSpecs.set(boost, "permanent", Boolean.TRUE);
        TriggerSpecs.set(boost, "target", "self");
        TriggerSpec rule = TriggerSpecs.rule(TriggerEvent.DEALING_DAMAGE.name(), List.of(), boost);
        TriggerSpecs.set(rule, "id", "probe_reads_cast_energy");
        wearer.setTriggerTable(new TriggerTable(WEARER, List.of(rule)));

        double before = wearer.getAttribute(AttributeType.ATTACK).get();
        wearer.setCurrentEnergy(wearer.getMaxEnergy());
        double max = wearer.getMaxEnergy();
        boolean cast = battle.castUltra(wearer, List.of(enemy));
        Assertions.assertTrue(cast, "the ultimate must be cast at full energy");
        double growth = wearer.getAttribute(AttributeType.ATTACK).get() - before;
        return new double[]{growth, max};
    }

    @Test
    public void theSpendTheUltimateRecordedReachesTheInstance() {
        double[] result = attackGrowthFromUltimate();
        System.out.println("[23062] write-side: maxEnergy=" + result[1]
                + " growth=" + result[0] + " expected=" + (result[1] * PER_POINT));
        Assertions.assertTrue(result[0] > 0,
                "the instance must carry a non-zero spend: the write in Battle.castUltra is what feeds it"
                        + " (with it disabled this reads 0, which is the accident of round 845)");
    }
}
