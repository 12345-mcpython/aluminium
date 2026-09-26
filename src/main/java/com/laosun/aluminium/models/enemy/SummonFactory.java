package com.laosun.aluminium.models.enemy;

import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Summon;

/**
 * Builds a {@link Summon} from <b>real monster data</b> (P9-4).
 *
 * <pre>
 * SummonFactory.create(1002040, 90, 1, Camp.ENEMY)
 *   → monster_config[1002040] × monster_template_config[1002040] × hard_level_group[1][90]
 *   → Summon (attributes / level / skill)
 * </pre>
 *
 * <p><b>Why it is a separate factory and not a method on {@link EnemyFactory}.</b> The two produce
 * different types, and the difference is the point: from the same resolved data an {@code EnemyFactory}
 * fills in the monster-only columns (resistances, weaknesses, toughness, phase table) and a
 * {@code SummonFactory} does not, because a {@link Summon} has nowhere to put them. The lookup and the
 * scaling <b>are</b> shared ({@link EnemyFactory#resolve}), so the parts that must agree — which config,
 * which template, what it scales to, and how a missing id is reported — cannot drift.
 *
 * <p>⚠ Deviates from the roadmap's original wording ({@code utils/SummonFactory}): it lives next to
 * {@code EnemyFactory} because it needs the package-private {@code resolve} / {@code statSheet} /
 * {@code enemySkillFor} helpers, and duplicating those in {@code utils} is exactly how two copies of the
 * stat rules start to differ.
 *
 * <p>A summon's camp is <b>passed in</b> rather than assumed: the same data builds an enemy minion or, one
 * day, a friendly memosprite. What the caller may <em>place</em> is a separate question, and
 * {@code Battle.summon} answers it.
 */
public final class SummonFactory {

    private SummonFactory() {
    }

    /**
     * Builds a summon from "monster id + level + level group".
     *
     * @param monsterId      a key of {@code monster_config.json} — the <b>summon's</b> own id, not its
     *                       master's (e.g. 1002040 银鬃近卫 for 银鬃尉官 1003010)
     * @param level          the stage level (the same one the rest of the battle uses)
     * @param hardLevelGroup the stage's hard level group; explicit, because a monster's own
     *                       {@code hard_level_group} is almost always 1 and the stage is what decides
     *                       difficulty — so there is nothing here to infer it from
     * @param camp           who the summon fights for (normally its master's camp)
     * @return a summon with a real stat sheet and an installed skill
     * @throws IllegalArgumentException if the id / level-group level is unknown (loudly, by the same
     *                                  messages {@code EnemyFactory.create} uses — a summon id that names
     *                                  no monster is a data error, not something to skip)
     */
    public static Summon create(int monsterId, int level, int hardLevelGroup, Camp camp) {
        if (camp == null) {
            throw new IllegalArgumentException("A summon must have a camp (summon " + monsterId + ")");
        }
        EnemyFactory.Resolved resolved = EnemyFactory.resolve(monsterId, level, hardLevelGroup);

        Summon summon = new Summon(displayName(resolved), camp,
                EnemyFactory.statSheet(resolved.stats()).build());
        summon.setLevel(level);                                  // enters the defence zone like anything else
        // The summon acts with its *own* monster data, so a summon whose id has an entry in
        // enemy_skills.json uses it; everything else falls back to the ordinary default attack.
        summon.setSkill(SkillType.COMMON, EnemyFactory.enemySkillFor(monsterId, resolved.template()));
        return summon;
    }

    private static String displayName(EnemyFactory.Resolved resolved) {
        return resolved.config().name() == null
                ? "Summon#" + resolved.monsterId()
                : resolved.config().name().chinese();
    }
}
