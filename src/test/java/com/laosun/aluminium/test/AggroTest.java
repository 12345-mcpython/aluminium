package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.Path;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * P5-1 / P5-2 验收：命途仇恨值、仇恨表、以及"嘲讽不改数值"。
 *
 * <p>锚点（已用 {@code character_data.json} 的 {@code aggro} 列全量核对）：
 * 存护 150 / 毁灭 125 / 其他 100 / 巡猎·智识 75。
 */
public class AggroTest {
    private static final double EPS = 1e-9;

    @Test
    public void pathCarriesTheOfficialAggroLadder() {
        Assertions.assertEquals(150, Path.PRESERVATION.getAggro());
        Assertions.assertEquals(125, Path.DESTRUCTION.getAggro());
        Assertions.assertEquals(100, Path.OTHER.getAggro());
        Assertions.assertEquals(75, Path.HUNT.getAggro(), "巡猎比常规低，别当成 100");
        Assertions.assertEquals(75, Path.ERUDITION.getAggro());
        Assertions.assertEquals(Path.DESTRUCTION, Path.fromName("毁灭"));
        Assertions.assertEquals(Path.PRESERVATION, Path.fromName("存护"));
        Assertions.assertEquals(Path.OTHER, Path.fromName("不存在的命途"), "未收录降级而不是炸");
    }

    @Test
    public void pathIsDerivedFromCharacterData() {
        Character preservation = Character.builder().cid(1104).level(80).build();   // 杰帕德：存护
        Character destruction = Character.builder().cid(1212).level(80).build();    // 镜流：毁灭
        Character hunt = Character.builder().cid(1209).level(80).build();           // 彦卿：巡猎

        Assertions.assertEquals(Path.PRESERVATION, preservation.getPath());
        Assertions.assertEquals(150, preservation.getAggro(), "仇恨值直接来自角色的 aggro 列");
        Assertions.assertEquals(Path.DESTRUCTION, destruction.getPath());
        Assertions.assertEquals(125, destruction.getAggro());
        Assertions.assertEquals(Path.HUNT, hunt.getPath());
        Assertions.assertEquals(75, hunt.getAggro());
    }

    @Test
    public void aggroTableSplitsProbabilityByWeight() {
        Character preservation = withAggro(150);
        Character other = withAggro(100);
        Battle battle = new Battle(List.of(preservation, other), List.of(dummy()), new Random(0));

        Map<com.laosun.aluminium.models.CanHit, Double> table = battle.getAggroTable(List.of(preservation, other));

        Assertions.assertEquals(0.6, table.get(preservation), EPS, "150 / 250");
        Assertions.assertEquals(0.4, table.get(other), EPS, "100 / 250");
    }

    @Test
    public void enemyHasTheNeutralAggroWeight() {
        Battle battle = new Battle(List.of(withAggro(100)), List.of(dummy()), new Random(0));

        Assertions.assertEquals(100, battle.aggroOf(battle.enemies.getFirst()), EPS,
                "敌人没有命途，走常规档");

        Map<com.laosun.aluminium.models.CanHit, Double> table = battle.getAggroTable(List.of(withAggro(150)));
        Assertions.assertEquals(1.0, table.values().iterator().next(), EPS, "只有一个候选 → 概率 1");
        Assertions.assertTrue(battle.getAggroTable(List.of()).isEmpty(), "空列表 → 空表，不除零");
    }

    // ==================================================================

    /** 造一个指定仇恨值的角色：直接设 aggro，绕开数据。 */
    private static Character withAggro(int aggro) {
        Character c = Character.fromAttributes("c" + aggro, 10_000, 100, 100, 100);
        c.setAggro(aggro);
        return c;
    }

    private static Enemy dummy() {
        return EnemyFactory.create(1002011, 90, 1);
    }
}
