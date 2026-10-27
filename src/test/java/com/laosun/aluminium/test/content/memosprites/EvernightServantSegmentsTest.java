package com.laosun.aluminium.test.content.memosprites;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1413 长夜月's memosprite 「长夜」, its data skill 1 「追忆，蹁跹，如雨」 (SkillID 1141301), settles <b>six instances</b> - the
 * split the game's ability states and the game's own max-hit column confirms.
 *
 * <p><b>Where the six come from (measured in the upstream ability, not guessed).</b>
 * {@code Config/ConfigAbility/Servant/Servant_EvernightServant_00_Ability.json}'s
 * {@code Servant_EvernightServant_00_Skill01_Phase02} carries <b>six</b> {@code DamageByAttackProperty} tasks on the
 * same target, with {@code HitSplitRatio} = 0.1, 0.1, 0.1, 0.1, 0.1, <b>0.5</b> (sum 1.0; the last one is the heavy hit,
 * {@code HitAnimation: Hit_H}). {@code skill_max_hits.json} says the same count
 * ({@code servants.Servant_EvernightServant_00_Config.Skill01 = 6}).
 *
 * <p><b>The capture agrees, once its grouping artefact is understood.</b> In {@code veritas-085418.json} the cast shows
 * up as 177.60 x 5 then 888.01 x 1 - i.e. 0.06 x 5 + 0.3, exactly the same 5 x 0.1 + 0.5 of one share (0.6). It only
 * looks like "5 segments" because {@code capture-flow} groups hit groups by their unmodified base, and the 0.5-split hit
 * has a base five times the others - so it is reported as its own cast.
 *
 * <p>⚠ <b>What this does NOT cover.</b> The sentence also says 「长夜月当前每有 #3 点【忆质】，则额外造成 #2% 伤害」; the game folds
 * that into the six instances' percentage (two casts in the capture total 0.6 and 0.9 - the same skill), it does not add
 * instances. The engine has no such reading yet - registered in {@code AGENT.md} §3.7h, not approximated here.
 *
 * <p>Also note: the demo's summon turn still swings the memosprite spec's single-hit attack ({@code COMMON}); this judge
 * is about the servant's <b>data skill</b> path, which is what the game's own damage events follow.
 */
public class EvernightServantSegmentsTest {

    private static final int EVERNIGHT = 1413;
    private static final int SKILL_SLOT = 1;

    /** 1141301's Lv10 row: #1 = 0.7 (the memosprite spec gives its skills level 10). */
    private static final double LV10_SHARE = 0.7;

    /** The ability's HitSplitRatio list, in order. */
    private static final double[] SPLITS = {0.1, 0.1, 0.1, 0.1, 0.1, 0.5};

    @Test
    public void theServantsSkillSettlesTheAbilitysSixSplitsInOrder() {
        Character evernight = CharacterFactory.create(EVERNIGHT, 80);
        Battle battle = new Battle(List.of(evernight),
                List.of(BattleFixture.dummy(), BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Summon evey = battle.memospriteOf(evernight);
        Assertions.assertNotNull(evey, "precondition: her BATTLE_START talent summons 长夜");
        Skill skill = evey.skillAt(SKILL_SLOT);
        Assertions.assertNotNull(skill, "the servant carries its data slot 1");

        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        SkillExecutor.execute(battle, skill, evey, List.of(battle.enemies().get(0)));
        battle.processRequests();

        List<Damage> hits = settled.stream().filter(d -> d.getType() == DamageType.NORMAL).toList();
        Assertions.assertEquals(SPLITS.length, hits.size(),
                "the ability states six damage tasks (Skill01 Phase02), not one");
        // ⚠ The base is the SUMMONER's Max HP, not the servant's own (which is half of it). This case used to multiply
        // by `evey.getMaxHp()`; the recorded fight turned that around on 2026-10-27 - its six instances are
        // 0.06 × 2960.0287 five times plus 0.30 × 2960.0287 once, i.e. 长夜月's panel, and the game's ServantConfig
        // keeps the servant's Max HP synced with hers (Summon.damageBase).
        double maxHp = evernight.getMaxHp();
        for (int i = 0; i < SPLITS.length; i++) {
            Damage hit = hits.get(i);
            Assertions.assertEquals(SPLITS[i] * LV10_SHARE * maxHp, hit.breakdown().get("base"), 1e-6,
                    "instance " + (i + 1) + " carries HitSplitRatio " + SPLITS[i] + " of #1 (Lv10 = 0.7) × the "
                            + "summoner's Max HP");
            Assertions.assertSame(battle.enemies().get(0), hit.getDefender(),
                    "all six are the single-target skill's own target");
        }
        Assertions.assertNotEquals(evey.getMaxHp(), maxHp, 1e-6,
                "长夜's own sheet is half of hers, so the two candidates are different numbers");
        // The 30-point toughness is ONE number for the cast, spread over the instances (the engine's documented
        // convention for a multi-instance single-target skill): 6 x 5 = 30.
        double stance = hits.stream().mapToDouble(Damage::getStance).sum();
        Assertions.assertEquals(30, stance, 1e-6, "30 toughness spread over the six instances (5 each)");
    }
}
