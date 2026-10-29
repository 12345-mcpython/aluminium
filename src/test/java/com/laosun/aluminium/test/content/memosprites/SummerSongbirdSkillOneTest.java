package com.laosun.aluminium.test.content.memosprites;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.damage.Damage;
import com.laosun.aluminium.models.entity.Summon;
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1512 Robin • Summeretto (知更鸟•晴歌)'s memosprite 「晴空乐手」, data skill 1 「叽叽啾啾四重奏」 (SkillID 1151201): the damage
 * share is the row's <b>second</b> parameter, and the row's first is a constant {@code 0}.
 *
 * <p><b>The reading this pins.</b> {@code skills.json}'s row is {@code [0, 0.75] … [0, 2.1]} and the prose names the share as
 * {@code #2[f1]%} (「造成等同于「晴空乐手」#2[f1]%生命上限的风属性伤害」). Every other damage row in the corpus keeps its
 * multiplier at index 1, so the engine's ordinary reading ({@code params.getFirst()}) took the <b>0</b> and settled the
 * minimum instead of the hit - measured in the recorded fight
 * ({@code scenarios/veritas-085418.replay.json}, cast 17: capture 256638.88, engine 3.00 over three enemies).
 *
 * <p><b>Why this is data and not a new engine branch.</b> {@code skill_effects.json} already says which parameter a damage
 * part reads ({@code damage_parts[].index}, added for 1415's two-part row); a row whose single share is not the first
 * parameter is the same fact stated once.
 *
 * <p><b>The two levels are both pinned on purpose.</b> The memosprite spec gives its skills level 10 (row {@code #2} = 2.1),
 * while the capture's own numbers are the level-6 row (1.5 - {@code 0.6 × 10422.8964 = 6253.7378} for 1415's servant in the
 * same fight follows the same convention). Pinning only one level would leave "did it read index 2, or did it read a
 * hard-coded 2.1?" unanswerable.
 */
public class SummerSongbirdSkillOneTest {

    private static final int ROBIN = 1512;

    /** {@code memosprites/1512.json}'s {@code servant_id}; a memosprite's skills are keyed by the SERVANT's cid. */
    private static final int SERVANT_ID = 11512;

    private static final int SKILL_SLOT = 1;

    /**
     * The row's {@code #2} at the level the memosprite spec declares (10).
     */
    @Test
    public void theShareIsTheRowsSecondParameter() {
        assertPerEnemyBase(10, 2.1,
                "Lv10's #2 is 2.1; the row's #1 is 0 at every level, so reading #1 settles nothing");
    }

    /**
     * The same index at the level the capture's numbers were taken at (6) - the arithmetic that produced 256638.88.
     */
    @Test
    public void theCaptureLevelReadsTheSameIndex() {
        assertPerEnemyBase(6, 1.5,
                "Lv6's #2 is 1.5 - the capture's own share for cast 17 (1.5 × 5249.7811 = 7874.6716)");
    }

    /**
     * Casts the skill at {@code level} against two enemies and asserts every instance's base.
     *
     * <p>The base is read off {@link Damage#breakdown()} rather than the settled number: zones (defence, resistance,
     * toughness reduction) would otherwise decide whether the reading is right.
     */
    private static void assertPerEnemyBase(int level, double share, String why) {
        Character robin = CharacterFactory.create(ROBIN, 80);
        Battle battle = new Battle(List.of(robin),
                List.of(BattleFixture.dummy(), BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        // Note: 1512's talent summons this memosprite on a condition, not at battle start, so the judge puts it on the field
        // through the same seam the engine uses (idempotent per summoner - a second call refreshes, it does not duplicate).
        Summon songbird = battle.summonServant(robin);
        Assertions.assertNotNull(songbird, "precondition: 「晴空乐手」 is on the field");

        Skill skill = new DefaultSkill(SERVANT_ID, SKILL_SLOT, level);
        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        SkillExecutor.execute(battle, skill, songbird, List.of(battle.enemies().getFirst()));
        battle.processRequests();

        Assertions.assertEquals(2, settled.size(),
                "the row is an AoE attack: one instance per living enemy, not one per cast");
        // ⚠ The multiplier applies to the SUMMONER's Max HP, not the servant's own (which is 70% of it). This judge was
        // written against the servant's sheet one turn before the recorded fight settled the question - 1.5 × 5249.7811
        // is 知更鸟•晴歌's panel, and ServantConfig keeps the servant's Max HP synced with it (Summon.damageBase).
        double expected = share * robin.getMaxHp();
        for (Damage hit : settled) {
            Assertions.assertEquals(expected, hit.breakdown().get("base"), 1e-6,
                    "every enemy's instance is #2 × the summoner's Max HP (" + why + ")");
        }
        Assertions.assertNotEquals(songbird.getMaxHp(), robin.getMaxHp(), 1e-6,
                "「晴空乐手」's own sheet is 70% of hers, so the two candidates are different numbers");
    }
}
