package com.laosun.aluminium.models.enemy;

import com.laosun.aluminium.beans.MemospriteSpec;
import com.laosun.aluminium.data.Memosprites;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.utils.AttributeBuilder;

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

    /**
     * Builds a character's <b>memosprite</b> (忆灵) with a panel derived from its summoner (P9-4).
     *
     * <pre>
     * SummonFactory.memosprite(阿格莱雅 at level 80)
     *   → memosprites/1402.json  HEALTH = 0.66 × her Max HP + 720, SPEED = 0.35 × her SPD
     *   → Summon (Camp.PLAYER)
     * </pre>
     *
     * <p><b>A second data path, deliberately in the same class.</b> The monster path above reads
     * {@code monster_config.json} and takes a level group; this one reads the <b>summoner's own sheet</b> and
     * takes nothing but the summoner. They share only the return type — but "the one place that constructs a
     * {@link Summon}" is worth keeping, because a summon built anywhere else is a summon whose master link,
     * camp and panel were decided somewhere nobody looks.
     *
     * <p>⚠ <b>The panel is read from the summoner's <em>resolved</em> sheet</b> ({@code getAttribute(...)}),
     * which is what the documents mean by 「等同于阿格莱雅生命上限」: the number her relics, light cone, traces
     * and buffs have already produced. A memosprite therefore follows its summoner's equipment without any of
     * it being repeated here.
     *
     * <p>Only what the spec states is set. Everything else stays at the builder's default of 0 — see
     * {@link com.laosun.aluminium.beans.MemospriteSpec} for why that is the honest choice and which gaps it
     * leaves.
     *
     * @param master the summoning character (a {@link Character}: the spec is keyed by its cid)
     * @return the memosprite, with a panel derived from {@code master} and no skill installed yet
     * @throws IllegalArgumentException when the character has no memosprite spec — a loud failure naming the
     *                                  file to write, rather than an unnamed 0-HP unit
     */
    public static Summon memosprite(Character master) {
        if (master == null) {
            throw new IllegalArgumentException("A memosprite needs a summoner");
        }
        MemospriteSpec spec = Memosprites.of(master.getCid());
        if (spec == null) {
            throw new IllegalArgumentException(
                    "Character " + master.getName() + " (" + master.getCid() + ") has no memosprite spec: "
                            + "add resources/" + Memosprites.DIR + "/" + master.getCid() + ".json describing "
                            + "its name and how its panel derives from the summoner");
        }
        return memosprite(master, spec);
    }

    /**
     * Builds a memosprite from an already-loaded spec.
     *
     * <p>Split out so the panel derivation can be exercised on its own — including the ratio-attribute
     * spelling, which no shipped spec uses yet but the builder supports. Without this seam that branch could
     * only be reached by inventing a memosprite in the shipped data, which is exactly the kind of fabricated
     * content this project refuses.
     *
     * @param master the summoning character, whose resolved sheet the panel is derived from
     * @param spec   the validated spec
     * @return the memosprite, with no skill installed yet
     */
    public static Summon memosprite(Character master, MemospriteSpec spec) {
        if (master == null) {
            throw new IllegalArgumentException("A memosprite needs a summoner");
        }
        if (spec == null) {
            throw new IllegalArgumentException("A memosprite needs a spec");
        }
        AttributeBuilder panel = new AttributeBuilder();
        for (MemospriteSpec.Panel entry : spec.panel()) {
            AttributeType attribute = AttributeType.fromString(entry.attribute());
            double share = entry.percent() == null ? 0 : entry.percent();
            double flat = entry.flat() == null ? 0 : entry.flat();
            double value = share * master.getAttribute(attribute).get() + flat;
            // The builder's own convention for the two kinds of attribute (see AttributeBuilder): a base
            // attribute is set outright, and a ratio attribute -- whose base is literally 0 -- is given a
            // percentage-point modifier, which is exactly `ModifyAttr`'s rule for the same situation.
            if (attribute.isPercent) {
                panel.addPercentPoint(attribute, value, DoubleValue.Modifier.ModifierSource.BASE);
            } else {
                panel.setBase(attribute, value);
            }
        }
        Summon summon = new Summon(spec.name(), Camp.PLAYER, panel.build());
        summon.setLevel(master.getLevel());
        return summon;
    }
}