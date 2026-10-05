package com.laosun.aluminium.test;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/**
 * The dragon's talent 【灼掠幽墟的晦翼】 (2026-10-02): 「造成 #2 次伤害，每次伤害对敌方随机单体造成等同于遐蝶 #1% 生命上限的量子属性伤害…」.
 *
 * ⭐ Two-sided: summoning the dragon deals its six hits and heals our side; and the rule that carries the damage ratio is there BY ID, because 1141517 raises it with `MODIFY_RULE`.
 */
public class DragonHollowWingTest {
    private static final int LEVEL = 80;
    private static final int CASTORICE = 1407;
    private static final int OTHER = 1002;
    private static final int MONSTER = 1002011;

    @Test
    public void theDragonTalentHitsAndHeals() {
        double[] r = run();
        System.out.println("[dragon] the enemy lost " + r[0] + " ; our side gained " + r[1]
                + " HP (the dragon pays " + r[2] + " of its own master's max HP)");
        // ⭐ COUNTABLE (2026-10-02): six hits of #1 of her max HP, each settled on the enemy -- and `r[3]` is the mitigation the judge measured itself with one plain hit.
        // ⭐ PLUS the small-pack extra (2026-10-02): this battle has ONE enemy, so 1141517's second half raises the ratio by `#5` as well.
        double expected = 6 * (0.56 + 0.0024) * r[4] * r[3];
        System.out.println("[dragon]   expected 6 x (56% + 0.24%) x her max HP " + r[4] + " x mitigation " + r[3]
                + " = " + expected);
        Assertions.assertEquals(expected, r[0], expected * 1e-6, "six hits of #1 of her max HP");
        Assertions.assertTrue(r[1] > 0, "and the party must be healed");
    }

    /** [what the enemy lost, what our side gained, the HP cost the talent paid, the mitigation, her max HP]. */
    private static double[] run() {
        Character her = CharacterFactory.create(CASTORICE, LEVEL);
        Character other = CharacterFactory.create(OTHER, LEVEL);
        Battle battle = new Battle(List.of(her, other),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        her = battle.characters.getFirst();
        double enemyBefore = battle.enemies.getFirst().getCurrentHp();
        // ⭐ BOTH of us must be wounded: a heal on a full unit gains exactly nothing, which is what the first reading measured.
        her.takeDamage(her.getMaxHp() * 0.5);
        battle.characters.get(1).takeDamage(battle.characters.get(1).getMaxHp() * 0.5);
        battle.processRequests();
        double partyBefore = battle.characters.get(1).getCurrentHp();
        double herBefore = battle.characters.getFirst().getCurrentHp();
        // ⭐ measure the enemy's mitigation with one plain 1000-damage hit of the same element, before the talent fires
        double probeBefore = battle.enemies.getFirst().getCurrentHp();
        battle.applyDamage(battle.enemies.getFirst(), new com.laosun.aluminium.models.Damage(
                battle.characters.getFirst(), battle.enemies.getFirst(),
                com.laosun.aluminium.enums.DamageElement.QUANTUM,
                com.laosun.aluminium.enums.DamageType.NORMAL, 1000));
        battle.processRequests();
        double mitigation = (probeBefore - battle.enemies.getFirst().getCurrentHp()) / 1000.0;
        enemyBefore = battle.enemies.getFirst().getCurrentHp();
        double herMax = battle.characters.getFirst().getMaxHp();
        // ⭐ The talent fires when the dragon ARRIVES; `summonMemosprite` uses the spec in memosprites/1407.json (servant 11407).
        battle.summonMemosprite(her);
        battle.processRequests();
        return new double[]{enemyBefore - battle.enemies.getFirst().getCurrentHp(),
                battle.characters.get(1).getCurrentHp() - partyBefore,
                herBefore - battle.characters.getFirst().getCurrentHp(), mitigation, herMax};
    }
}
