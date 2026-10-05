package com.laosun.aluminium.test;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.buff.ControlBuff;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/** \u300c\u5904\u4e8e\u3010\u5f80\u6614\u7684\u6d9f\u6f2a\u3011\u72b6\u6001\u65f6\u5728\u3010\u8ffd\u5fc6\u3011\u8fbe\u5230 12 \u70b9\u65f6\u53ef\u6fc0\u6d3b\u7ec8\u7ed3\u6280\u300d (2026-10-02). */
public class CyreneRippleTierTest {
    private static final String STATE = "\u5f80\u6614\u7684\u6d9f\u6f2a";
    private static final String MEMORY = "\u8ffd\u5fc6";

    @Test
    public void twelveCleansesOnlyInsideTheRipple() {
        int insideRipple = debuffsLeft(true);
        int outside = debuffsLeft(false);
        System.out.println("[ripple] at twelve points: debuffs left inside the ripple = " + insideRipple
                + " ; outside it = " + outside);
        Assertions.assertEquals(0, insideRipple, "inside the ripple, twelve points cleanse her");
        Assertions.assertTrue(outside > 0, "outside it, twelve is not enough");
    }

    private static int debuffsLeft(boolean enterTheRipple) {
        Character her = CharacterFactory.create(1415, 80);
        Battle battle = new Battle(List.of(her), List.of(EnemyFactory.create(1002011, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        her = battle.characters.getFirst();
        Assertions.assertTrue(her.getSkills().containsKey(SkillType.ULTRA), "precondition: her ultimate");
        com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, her.getSkills().get(SkillType.ULTRA), her,
                List.of(battle.enemies.getFirst()));
        battle.processRequests();
        her = battle.characters.getFirst();
        if (!enterTheRipple) {
            her.getBuffManager().removeState(STATE);
        }
        Assertions.assertEquals(enterTheRipple, her.getBuffManager().hasState(STATE),
                "precondition: the ripple is " + (enterTheRipple ? "up" : "down"));
        var control = com.laosun.aluminium.Constant.CONTROL_EFFECTS.get("IMPRISONED");
        her.getBuffManager().addBuff(new ControlBuff(control, 3));
        her.getResources().gain(MEMORY, 11);
        // her basic is the writer that carries her to twelve
        com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, her.getSkills().get(SkillType.COMMON), her,
                List.of(battle.enemies.getFirst()));
        battle.processRequests();
        return battle.characters.getFirst().getBuffManager().debuffCount();
    }
}
