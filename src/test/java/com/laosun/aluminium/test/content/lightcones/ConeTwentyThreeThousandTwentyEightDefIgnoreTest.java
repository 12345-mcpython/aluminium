package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 偏偏希望无价 (23028), second sentence, the ULTIMATE half: 「战斗开始时和装备者施放普攻后，使终结技造成的伤害无视目标
 * <b>20%</b> 的防御，持续 <b>2</b> 回合」.
 *
 * <p>Modelled as a window state plus an INSTANCE modifier scoped by CAST category: the state says "while I am up", and
 * {@code from_category ULTRA} says "only for an ultimate". The follow-up half is still open - the engine has no FOLLOW_UP category.
 */
public class ConeTwentyThreeThousandTwentyEightDefIgnoreTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1204;

    private static final class Run {
        private final Character wearer;
        private final Enemy enemy;
        private final Battle battle;
        private final List<Damage> settled = new ArrayList<>();

        Run() {
            wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(23028, LEVEL, false, 1));
            enemy = BattleFixture.monster();
            battle = new Battle(new ArrayList<>(List.of(wearer)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.seeded());
            battle.startBattle();
            battle.addDamageObserver((Damage damage, double applied, double hpLost, double shield, boolean died) ->
                    settled.add(damage));
        }

        List<Damage> cast(SkillType slot) {
            settled.clear();
            if (slot == SkillType.ULTRA) {
                wearer.setCurrentEnergy(wearer.getMaxEnergy());
            }
            battle.castImmediate(wearer.getSkills().get(slot), wearer, List.of(enemy));
            battle.processRequests();
            return List.copyOf(settled);
        }

        static double ignoreOf(List<Damage> instances) {
            return instances.stream()
                    .filter(d -> d.getType() == com.laosun.aluminium.enums.DamageType.NORMAL)
                    .mapToDouble(Damage::getDefenceIgnore).max().orElse(0);
        }
    }

    /**
     * The window is up at battle start, so the first ultimate ignores defence - and a basic in the same window does not.
     */
    @Test
    public void theUltimateIgnoresDefenceWhileTheWindowIsUp() {
        Run run = new Run();
        Assertions.assertEquals(0.20, Run.ignoreOf(run.cast(SkillType.ULTRA)), 1e-9,
                "「战斗开始时…使终结技造成的伤害无视目标 20% 的防御」");

        Run other = new Run();
        Assertions.assertEquals(0.0, Run.ignoreOf(other.cast(SkillType.COMMON)), 1e-9,
                "「终结技」 - a basic attack in the same window is not covered");
    }

    /**
     * A follow-up attack in the window ignores it too - the engine's marker for one is the DAMAGE TYPE ({@code ADDITIONAL}),
     * which is why 「或追加攻击」 needed no new {@code SkillCategory}.
     */
    @Test
    public void aFollowUpInTheWindowIgnoresDefenceAsWell() {
        Run run = new Run();
        run.battle.applyAdditionalDamage(run.wearer, run.enemy,
                com.laosun.aluminium.enums.DamageElement.PHYSICAL, 500);
        run.battle.processRequests();
        double ignore = run.settled.stream()
                .filter(d -> d.getType() == com.laosun.aluminium.enums.DamageType.ADDITIONAL)
                .mapToDouble(Damage::getDefenceIgnore).max().orElse(0);
        Assertions.assertEquals(0.20, ignore, 1e-9,
                "「使终结技**或追加攻击**造成的伤害无视目标 20% 的防御」 - the follow-up half");
    }

    /**
     * The window closes: after its two turns the ultimate no longer ignores defence.
     */
    @Test
    public void theWindowClosesAfterItsTwoTurns() {
        Run run = new Run();
        for (int turn = 1; turn <= 2; turn++) {
            run.battle.setCurrentMove(run.battle.queue().snapshot().stream()
                    .filter(signal -> signal.getCanHit() == run.wearer).findFirst().orElseThrow());
            run.battle.beforeMove();
            run.battle.afterMove();
        }
        Assertions.assertEquals(0.0, Run.ignoreOf(run.cast(SkillType.ULTRA)), 1e-9,
                "「持续 2 回合」 - three turns in, the window is spent");
    }
}
