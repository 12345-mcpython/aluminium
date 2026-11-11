package com.laosun.aluminium.test.content.characters.acheron;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1308 Acheron: the [残梦] resource the document caps at 9, and the talent that scopes an 8% vulnerability to ultimate damage.
 *
 * <p>These were two small files in the same package; one class keeps the character together without changing any assertion.
 */
public class AcheronTest {
    private static final int ACHERON = 1308;
    private static final int LEVEL = 80;

    /**
     * Note: One Slashed Dream per Skill, stopped at the document's nine.
     */
    @Test
    public void theSkillFeedsSlashedDreamUpToNine() {
        Character acheron = CharacterFactory.create(ACHERON, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(acheron), List.of(enemy), fixed());
        battle.startBattle();

        Assertions.assertEquals(0, dreamOf(acheron), "the document states no initial value, so it starts at 0");
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, acheron, enemy, 0, 0));
        Assertions.assertEquals(1, dreamOf(acheron), "「获得1点【残梦】」 (gains 1 point of [残梦])");

        for (int i = 0; i < 11; i++) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, acheron, enemy, 0, 0));
        }
        Assertions.assertEquals(9, dreamOf(acheron),
                "「【残梦】达到9点时可激活终结技」 (at 9 points of [残梦] the Ultimate can be activated) -- twelve casts must still read nine");
    }

    /**
     * 1308 Acheron's talent: enemies enter battle with "受到的终结技伤害提高 8%", which DamageType.ULTRA scopes.
     *
     * <p>Judged twice, and both bind the number: the compiled rule's scope and share, and the measured ratio between ultimate and normal damage.
     */
    @Test
    public void theRuleStatesTheUltimateScopeAndShare() {
        Character unit = CharacterFactory.create(ACHERON, LEVEL);
        Enemy enemy = BattleFixture.monster();
        var rules = unit.getTriggerTable().matching(TriggerEvent.BATTLE_START,
                new TriggerTable.TriggerContext(unit, unit, enemy, 0, 0));
        double share = -1;
        String scope = null;
        for (var rule : rules) {
            for (var effect : rule.effects()) {
                if ("MODIFY_DAMAGE_TAKEN".equals(effect.getOp())) {
                    share = effect.getPercent();
                    scope = effect.getDamageType();
                    System.out.println("[1308] rule id=" + rule.id() + " damageType=" + scope + " percent=" + share);
                }
            }
        }
        Assertions.assertEquals("ULTRA", scope, "scoped to ultimate damage");
        Assertions.assertEquals(0.08, share, 1e-9, "the talent states 8%");
    }

    @Test
    public void ultimateDamageIsAmplifiedAndNormalIsNot() {
        Character unit = CharacterFactory.create(ACHERON, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(unit), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        double normal = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.THUNDER, DamageType.NORMAL, 1000));
        double ultra = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.THUNDER, DamageType.ULTRA, 1000));
        System.out.println("[1308] normal=" + normal + " ultra=" + ultra + " ratio=" + (ultra / normal));
        Assertions.assertEquals(1.08, ultra / normal, 1e-6, "ultimate damage rises by exactly the authored 8%");
    }

    /**
     * The declared resource's value, read through the combatant's own manager.
     */
    private static int dreamOf(Character unit) {
        return unit.getResources().get("残梦").getValue();
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }
}
