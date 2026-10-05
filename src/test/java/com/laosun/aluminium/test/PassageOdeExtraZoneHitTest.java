package com.laosun.aluminium.test;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/**
 * Slot 15's narrowing (2026-10-02): "缇宝施放追加攻击触发缇宝的结界的附加伤害时，会额外造成 #1(1) 次附加伤害".
 *
 * The control lives INSIDE one battle. The ode is cast at her in both readings, so its other clause (the passage ode's `DEFENCE_IGNORE`, measured to raise every instance she deals from
 * 82.20 to 90.13) applies identically; the only thing that differs is the extra rule's own gate, the state the ode grants. Note: Comparing "ode vs no ode" instead would measure two effects
 * at once -- which is what the earlier attempts did.
 */
public class PassageOdeExtraZoneHitTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int TRIBBIE = 1403;
    private static final int MONSTER = 1002011;
    private static final int ODE_SLOT = 15;
    private static final String GATE = "献予「门径」之诗";

    @Test
    public void theExtraHitOnlyLandsWhileTheOdeIsOnHer() {
        double gated = hit(true);
        double ungated = hit(false);
        System.out.println("[passage_extra] the enemy lost " + gated + " with the ode's state on her, and "
                + ungated + " after it was removed");
        Assertions.assertTrue(gated > ungated, "the extra instance lands while the ode is on her");
        Assertions.assertTrue(ungated > 0, "and her ordinary damage is still there either way");
    }

    /** One basic attack with the ode cast at her; the state is removed first when {@code keepGate} is false. */
    private static double hit(boolean keepGate) {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character tribbie = CharacterFactory.create(TRIBBIE, LEVEL);
        Battle battle = new Battle(List.of(cyrene, tribbie),
                List.of(EnemyFactory.create(MONSTER, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        tribbie = battle.characters.get(1);
        var ult = tribbie.getSkills().get(SkillType.ULTRA);
        com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, ult, tribbie,
                List.of(battle.enemies.getFirst()));
        battle.processRequests();
        Assertions.assertTrue(tribbie.getBuffManager().hasState("结界"), "precondition: the zone is open");
        var sprite = battle.summonServant(battle.characters.get(0));
        battle.processRequests();
        var ode = sprite.skillAt(ODE_SLOT);
        Assertions.assertNotNull(ode, "precondition: slot 15");
        com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, ode, sprite, List.of(tribbie));
        battle.processRequests();
        tribbie = battle.characters.get(1);
        Assertions.assertTrue(tribbie.getBuffManager().hasState(GATE), "precondition: the ode's state landed");
        if (!keepGate) {
            tribbie.getBuffManager().removeState(GATE);
        }
        double before = battle.enemies.getFirst().getCurrentHp();
        var basic = tribbie.getSkills().get(SkillType.COMMON);
        com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, basic, tribbie,
                List.of(battle.enemies.getFirst()));
        battle.processRequests();
        return before - battle.enemies.getFirst().getCurrentHp();
    }
}
