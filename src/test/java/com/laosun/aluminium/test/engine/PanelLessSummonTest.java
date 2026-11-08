package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enemy.SummonFactory;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * {@code SummonFactory.panelLess}: the game's 「非忆灵召唤物」 (神君 / 浮元 / 阿哈) has no panel of its own — it uses its summoner's.
 *
 * <p>The alternative two data paths both need data the documents do not give (a `monster_config` row or a `MemospriteSpec`), while
 * the documents say the opposite: 神君 hits 「for 66% of Jing Yuan's ATK」, 浮元 「uses 灵砂's own attributes」. So the honest panel is
 * the summoner's own, <b>shared live</b> — a buff on the character moves the summon's numbers too.
 */
public class PanelLessSummonTest {
    private static final int CARRIER = 1204;
    private static final int LEVEL = 80;

    @Test
    public void aPanelLessSummonSharesItsSummonersAttributes() {
        Character carrier = CharacterFactory.create(CARRIER, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(carrier), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();

        Summon lord = SummonFactory.panelLess(carrier, "神君");

        Assertions.assertEquals(carrier.getAttribute(AttributeType.ATTACK).get(),
                lord.getAttribute(AttributeType.ATTACK).get(), 1e-9,
                "「沿用角色自身的属性」 — ATTACK is the summoner's");
        Assertions.assertEquals(carrier.getAttribute(AttributeType.SPEED).get(),
                lord.getAttribute(AttributeType.SPEED).get(), 1e-9, "…and so is SPEED");
        Assertions.assertEquals(carrier.getLevel(), lord.getLevel(), "the level comes along, so the defence zone agrees");
    }

    @Test
    public void theShareIsLiveRatherThanFrozen() {
        Character carrier = CharacterFactory.create(CARRIER, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(carrier), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        Summon lord = SummonFactory.panelLess(carrier, "神君");
        double before = lord.getAttribute(AttributeType.ATTACK).get();

        // A buff on the CHARACTER must move the summon too - that is what "uses the character's stats" means.
        carrier.getBuffManager().addBuff(com.laosun.aluminium.buff.StatModifierBuff.of(
                AttributeType.ATTACK, "add_percent", 0.5, "buff", 3, false));
        battle.processRequests();

        Assertions.assertTrue(lord.getAttribute(AttributeType.ATTACK).get() > before,
                "the panel is shared live (" + before + " -> " + lord.getAttribute(AttributeType.ATTACK).get() + ")");
    }
}
