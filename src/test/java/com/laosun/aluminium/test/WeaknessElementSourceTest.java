package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * `ADD_ELEMENTAL_WEAKNESS` 的两个具名元素来源（`cacb8b8`）：
 * `party_first`（角色 1006："场上我方目标持有属性的弱点"，其技能说明指明编队第一位）
 * 与 `random_absent`（角色 1405："添加 1 个随机属性弱点，优先添加目标尚未拥有的弱点"）。
 *
 * <p>Note: 这两个值在装载期是闭集的一部分：既不合法元素、也不在 `SPECIAL_ELEMENTS` 里的名字装载期就抛，
 * 所以"拼错仍然响亮"这条防线没被这次改动打开。
 *
 * <p>Note: 判据不装光锥、也不换表 -  - 那条规则就挂在被测角色自己的表上（`setTriggerTable` 只会顶掉别的表，
 * 而这里没有别的表）。
 */
public class WeaknessElementSourceTest {
    private static final int WEARER = 1006;
    private static final int MONSTER = 1002011;
    private static final int LEVEL = 80;

    /** 给角色挂一条"战技命中时给目标加某种来源的弱点"，返回 {battle, wearer, enemy}。 */
    private static Object[] scene(String element) {
        Character wearer = CharacterFactory.create(WEARER, LEVEL);
        EffectSpec add = new EffectSpec();
        TriggerSpecs.set(add, "op", "ADD_ELEMENTAL_WEAKNESS");
        TriggerSpecs.set(add, "element", element);
        TriggerSpecs.set(add, "target", "target");
        TriggerSpec rule = TriggerSpecs.rule(TriggerEvent.SKILL_CAST.name(), List.of(), add);
        TriggerSpecs.set(rule, "id", "probe_element_source");
        TriggerSpecs.set(rule, "when", List.of("actor == self"));
        wearer.setTriggerTable(new TriggerTable(WEARER, List.of(rule)));
        // Note: 第二位队友的属性与装备者不同：否则 `characters.getFirst()` 与 `getLast()` 是同一个，
        // `party_first` 的变异就看不见了（判据的形状决定了变异能不能必红）。
        Character other = CharacterFactory.create(1002, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, other), List.of(enemy), new Random(0));
        battle.startBattle();
        return new Object[]{battle, wearer, enemy};
    }

    /** 这个敌人原本就有的弱点。 */
    private static List<DamageElement> before(Enemy enemy) {
        List<DamageElement> had = new ArrayList<>();
        for (DamageElement e : DamageElement.values()) {
            if (enemy.isWeakTo(e)) {
                had.add(e);
            }
        }
        return had;
    }

    private static void fire(Battle battle, Character wearer, Enemy enemy) {
        battle.fireTriggers(TriggerEvent.SKILL_CAST, wearer, enemy, 0, 0);
    }

    @Test
    public void partyFirstTakesTheElementOfTheFirstPartyMember() {
        Object[] s = scene("party_first");
        Battle battle = (Battle) s[0];
        Character wearer = (Character) s[1];
        Enemy enemy = (Enemy) s[2];
        DamageElement expected = wearer.getElement();
        Assertions.assertFalse(enemy.isWeakTo(expected), "the enemy must not already have it, or nothing is inserted");

        fire(battle, wearer, enemy);
        System.out.println("[element] party_first: wearer=" + expected + " inserted=" + enemy.isWeakTo(expected)
                + " (had " + before(enemy).size() + " before)");
        Assertions.assertTrue(enemy.isWeakTo(expected),
                "the first party member's element is what 1006's own skill text names");
    }

    @Test
    public void randomAbsentOnlyPicksAnElementTheTargetLacks() {
        Object[] s = scene("random_absent");
        Battle battle = (Battle) s[0];
        Character wearer = (Character) s[1];
        Enemy enemy = (Enemy) s[2];
        List<DamageElement> had = before(enemy);

        fire(battle, wearer, enemy);
        List<DamageElement> now = before(enemy);
        List<DamageElement> added = new ArrayList<>(now);
        added.removeAll(had);
        System.out.println("[element] random_absent: had=" + had.size() + " now=" + now.size()
                + " added=" + added + " (the clause prefers one it did not have)");
        Assertions.assertEquals(1, added.size(), "exactly one element is added");
        Assertions.assertFalse(had.contains(added.getFirst()),
                "and it is one the target did NOT already have -- that is the mutation point");
    }
}
