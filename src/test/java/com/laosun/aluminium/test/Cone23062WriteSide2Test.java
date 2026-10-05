package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
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
 * 写入端：`Battle.castUltra` 把"本次消耗"送到结算时的伤害实例上 -  - Note: 走真实施放，不手工设值。
 *
 * <p>Note: 为什么必须自己造规则：读取端的判据是手工设 `castEnergySpent` 的，它根本不经过 `Battle`；而实测过，
 * 把写入那行注释掉，全量套件照样绿（845 轮）。
 *
 * <p>Note: 而"造一条探针"也不自由：能读自定义 `scale` 的 op 是闭集 -  - `MODIFY_ATTR` 带
 * `cast_energy_spent` 在装载期就抛（856 轮实测），`ADD_STACK` 则静默不读（854 轮实测：
 * 探针恒为 2 层，写入被注释掉也照样 2 层）。实际上只有 `BOOST_DAMAGE` 认它。
 *
 * <p>所以对照是同一条规则、只换 scale：一次带 `scale: cast_energy_spent`（percent 0.02），
 * 一次不带（percent 0.001）。写入发生时前者远高；写入被注释掉时前者读 0，于是低于后者。
 */
public class Cone23062WriteSide2Test {
    private static final int LEVEL = 80;
    private static final int WEARER = 1003;
    private static final int MONSTER = 1002011;
    private static final double PER_POINT = 0.02;
    private static final double FLAT = 0.001;

    /** 满能量放一次终结技；用一条自造的 BOOST_DAMAGE 规则读实例上的消耗。 */
    private static double ultimateDamage(boolean scaled) {
        Character wearer = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));
        battle.startBattle();

        EffectSpec boost = new EffectSpec();
        TriggerSpecs.set(boost, "op", "BOOST_DAMAGE");
        if (scaled) {
            TriggerSpecs.set(boost, "scale", "cast_energy_spent");
            TriggerSpecs.set(boost, "percent", PER_POINT);
        } else {
            TriggerSpecs.set(boost, "percent", FLAT);
        }
        TriggerSpec rule = TriggerSpecs.rule(TriggerEvent.DEALING_DAMAGE.name(), List.of(), boost);
        TriggerSpecs.set(rule, "id", "probe_reads_cast_energy");
        wearer.setTriggerTable(new TriggerTable(WEARER, List.of(rule)));

        wearer.setCurrentEnergy(wearer.getMaxEnergy());
        double before = enemy.getCurrentHp();
        boolean cast = battle.castUltra(wearer, List.of(enemy));
        Assertions.assertTrue(cast, "the ultimate must be cast at full energy");
        return before - enemy.getCurrentHp();
    }

    @Test
    public void theSpendTheUltimateRecordedReachesTheInstance() {
        double scaled = ultimateDamage(true);
        double flat = ultimateDamage(false);
        System.out.println("[23062] write-side: scaled=" + scaled + " flat=" + flat
                + " (per point " + PER_POINT + ")");
        Assertions.assertTrue(scaled > flat,
                "with the write in place the scaled rule must beat the tiny flat one: " + scaled + " vs " + flat
                        + " -- with it commented out (round 845) the instance reads 0 and they swap");
    }
}
