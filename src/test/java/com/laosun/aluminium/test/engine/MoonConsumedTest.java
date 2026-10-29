package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1212 Jingliu's [月色]: "我方目标受到伤害或消耗生命值时，镜流获得 1 层[月色]".
 *
 * <p>The fixture walks the documented precondition chain: two layers of [朔望] are what puts her into [转魄], and only then can either half of the disjunction stack. The
 * two halves are separated by making 1205's shipped skill pay an HP price (HP_CONSUMED) versus a plain hit on the observer (TAKING_HIT).
 */
public class MoonConsumedTest {
    private static final int OBSERVER = 1212;
    private static final int PAYER = 1205;
    private static final int LEVEL = 80;

    private Battle ready(Character observer, Character payer, Enemy enemy) {
        Battle battle = new Battle(List.of(observer, payer), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        // Chain: 1 layer on SKILL_CAST, 1 on ULT_CAST -> 2 layers -> the entry rule grants [转魄].
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, observer, enemy, 0, 0));
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, observer, enemy, 0, 0));
        // Note: The entry rule listens on SKILL_CAST and its condition is `>= 2` layers, so the event that PUSHES the count to two cannot be the
        // one that observes it: one more SKILL_CAST is what actually fires the entry (round 228/240).
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, observer, enemy, 0, 0));
        System.out.println("[1212] syzygy=" + observer.getBuffManager().stacksOf("朔望")
                + " inZhuanpo=" + observer.getBuffManager().hasState("转魄"));
        Assertions.assertTrue(observer.getBuffManager().hasState("转魄"),
                "precondition: two layers of Syzygy put her into Zhuanpo");
        return battle;
    }

    @Test
    public void aSpentPriceStacksOneLayer() {
        Character observer = CharacterFactory.create(OBSERVER, LEVEL);
        Character payer = CharacterFactory.create(PAYER, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = ready(observer, payer, enemy);
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, payer, enemy, 0, 0));
        int layers = observer.getBuffManager().stacksOf("月色");
        System.out.println("[1212] afterPrice layers=" + layers + " payerHp=" + payer.getCurrentHp());
        Assertions.assertEquals(1, layers, "paying an HP price stacks one layer of Moon");
    }

    @Test
    public void aPlainHitDoesNotUseThePriceRule() {
        Character observer = CharacterFactory.create(OBSERVER, LEVEL);
        Character payer = CharacterFactory.create(PAYER, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = ready(observer, payer, enemy);
        battle.applyDamage(observer, new Damage(enemy, observer, DamageElement.PHYSICAL, DamageType.NORMAL, 40));
        int afterHit = observer.getBuffManager().stacksOf("月色");
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, payer, enemy, 0, 0));
        int afterPrice = observer.getBuffManager().stacksOf("月色");
        System.out.println("[1212] afterHit=" + afterHit + " afterPrice=" + afterPrice);
        Assertions.assertEquals(1, afterPrice - afterHit, "only the price half is driven here");
    }
}
