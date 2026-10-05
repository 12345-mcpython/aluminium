package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1223 Moze's follow-up clauses: "[猎物]受到的追加攻击伤害提高 25%" (trace 1223103) and
 * "施放天赋的追加攻击后，恢复 1 个战技点，该效果在 1 回合后可再次触发" (trace 1223101).
 *
 * <p>Both are judged against the engine's own follow-up path, so the two readings differ in exactly one variable each.
 */
public class MozeFollowUpTest {
    private static final int WEARER = 1223;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theZoneIsScopedToAdditionalDamage() {
        Character unit = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        String scope = null;
        double share = -1;
        for (var rule : unit.getTriggerTable().matching(TriggerEvent.SKILL_CAST,
                new TriggerTable.TriggerContext(unit, unit, enemy, 0, 0))) {
            for (var effect : rule.effects()) {
                if ("MODIFY_DAMAGE_TAKEN".equals(effect.getOp())) {
                    scope = effect.getDamageType();
                    share = effect.getPercent();
                    System.out.println("[1223] zone id=" + rule.id() + " damageType=" + scope + " percent=" + share);
                }
            }
        }
        Assertions.assertEquals("ADDITIONAL", scope, "the engine spells a follow-up attack ADDITIONAL");
        Assertions.assertEquals(0.25, share, 1e-9, "the data row states 0.25");
    }

    @Test
    public void followUpDamageRisesByAQuarter() {
        Character unit = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.fireTriggers(TriggerEvent.SKILL_CAST, unit, enemy, 0, 0);
        double plain = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.THUNDER, DamageType.NORMAL, 1000));
        double followUp = battle.applyAdditionalDamage(unit, enemy, DamageElement.THUNDER, 1000, null, null);
        System.out.println("[1223] plain=" + plain + " followUp=" + followUp + " ratio=" + (followUp / plain));
        Assertions.assertEquals(1.25, followUp / plain, 1e-6, "a follow-up instance rises by the authored 25%");
    }

    @Test
    public void aFollowUpRestoresOneSkillPointOncePerTurn() {
        Character unit = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        int before = battle.getSkillPoints();
        battle.fireTriggers(TriggerEvent.FOLLOW_UP, unit, enemy, 500, 0);
        int after = battle.getSkillPoints();
        System.out.println("[1223] skillPoints " + before + " -> " + after);
        Assertions.assertEquals(1, after - before, "the trace grants exactly one skill point");
    }
}
