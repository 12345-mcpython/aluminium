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

/** "处于[往昔的涟漪]状态时在[追忆]达到 12 点时可激活终结技" (2026-10-02). */
public class CyreneRippleTierTest {
    private static final String STATE = "往昔的涟漪";
    private static final String MEMORY = "追忆";

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
