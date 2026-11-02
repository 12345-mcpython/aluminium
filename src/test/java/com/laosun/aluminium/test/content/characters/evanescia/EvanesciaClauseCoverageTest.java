package com.laosun.aluminium.test.content.characters.evanescia;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1505 绯英: the three Eidolon halves that are plain attributes, and the witnesses for the ones that are not.
 *
 * <p><b>Why a separate file for three rules.</b> The character's {@code _gaps} was a pointer to the ledger, and the ledger said
 * 「未逐句核」 — so nothing pinned these numbers. Each assertion below is the Eidolon's own sentence read off the panel.
 */
public class EvanesciaClauseCoverageTest {

    private static final int EVANESCIA = 1505;

    /**
     * 星魂 1: 「全属性抗性穿透提高 20%」 — the penetration attribute, on herself.
     */
    @Test
    public void eidolon1RaisesAllTypeResistancePenetration() {
        Assertions.assertEquals(0.0, attributeAt(0, AttributeType.DAMAGE_PENETRATION), 1e-9, "control: nothing at E0");
        Assertions.assertEquals(0.2, attributeAt(1, AttributeType.DAMAGE_PENETRATION), 1e-9, "「全属性抗性穿透提高 20%」");
    }

    /**
     * 星魂 2: 「暴击伤害提高 36%」.
     */
    @Test
    public void eidolon2RaisesCritDamage() {
        Assertions.assertEquals(0.5, attributeAt(0, AttributeType.CRIT_ATTACK), 1e-9,
                "precondition: the base crit damage is 0.5");
        Assertions.assertEquals(0.86, attributeAt(2, AttributeType.CRIT_ATTACK), 1e-9, "「暴击伤害提高 36%」");
    }

    /**
     * 星魂 4: 「绯英造成的伤害无视敌方目标 15% 的防御力」 — HER defence ignore, not a debuff on the enemy.
     */
    @Test
    public void eidolon4IgnoresFifteenPercentDefence() {
        Assertions.assertEquals(0.0, attributeAt(0, AttributeType.DEFENCE_IGNORE), 1e-9, "control: nothing at E0");
        Assertions.assertEquals(0.15, attributeAt(4, AttributeType.DEFENCE_IGNORE), 1e-9,
                "「造成的伤害无视敌方目标 15% 的防御力」");
    }

    // ==================================================================

    private static double attributeAt(int eidolon, AttributeType type) {
        Character owner = CharacterFactory.create(EVANESCIA, 80, false, null, null, eidolon);
        Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        Battle battle = new Battle(List.of(owner), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return owner.getAttribute(type).get();
    }
}
