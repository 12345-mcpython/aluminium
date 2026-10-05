package com.laosun.aluminium.models.enemy;

import com.laosun.aluminium.beans.MemospriteSpec;
import com.laosun.aluminium.data.Memosprites;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillEffectType;
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

    /**
     * ⭐ Registers the resources a summon's SPEC declares on the summon itself (2026-10-02).
     *
     * <p>A resource is only usable where it is declared, and until this existed a summon had nowhere to declare one: `GAIN_RESOURCE{target: "summon"}` was accepted by the loader and then
     * silently granted nothing (measured -- it is why 1141526's 【故事】 could not land).
     */
    private static void declareResources(Summon summon, MemospriteSpec spec) {
        if (spec.resources() == null) {
            return;
        }
        for (com.laosun.aluminium.beans.ResourceSpec declared : spec.resources()) {
            if (declared == null || declared.id() == null || declared.id().isBlank()) {
                throw new IllegalStateException(
                        "a memosprite spec declares a resource without an id; the id is what a rule names");
            }
            if (declared.max() == null) {
                throw new IllegalStateException("the memosprite resource '" + declared.id()
                        + "' states no \"max\"; a resource needs a cap (Integer.MAX_VALUE means \"the data states none\")");
            }
            summon.getResources().register(declared.id(), declared.max(),
                    declared.initial() == null ? 0 : declared.initial(),
                    declared.overflow() == null ? 0 : declared.overflow());
        }
    }

    /**
     * ⭐ Installs the skills a spec states on the summon (2026-10-02).
     *
     * <p>One helper for BOTH paths, because they were not symmetric: `servantWith` installed them and `memospriteWith` did not, and since both make the same unit, which one a caller used
     * silently decided whether the memosprite had any skills of its own (measured: `skillsByDataSlot()` was empty on the memosprite path).
     *
     * <p>Each stated row becomes a real `Skill`, just like a character's, so `SkillEffects.forSkill`, `SkillExecutor.canDeliver` and every op that takes a skill work on a memosprite's skill
     * with no special case.
     */
    private static void installSpecSkills(Summon summon, MemospriteSpec spec, Character master) {
        if (spec.skills() == null || spec.skills().isEmpty()) {
            return;
        }
        int servantCid = spec.servantId() == null ? master.getCid() : spec.servantId();
        for (MemospriteSpec.SkillRow row : spec.skills()) {
            if (row == null || row.slot() == null) {
                continue;
            }
            int level = row.level() == null ? 1 : row.level();
            summon.setSkillAt(row.slot(),
                    new com.laosun.aluminium.models.skill.DefaultSkill(servantCid, row.slot(), level));
        }
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
     * @return the memosprite, with a panel derived from {@code master} and, when the spec states one, its
     *         attack installed
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

    /** The same, for a panel that derives from a battle-level RESOURCE (see {@link #panelOf}). */
    public static Summon memosprite(Character master, java.util.function.ToIntFunction<String> resourceValue) {
        MemospriteSpec spec = Memosprites.of(master.getCid());
        if (spec == null) {
            // The same text as the 1-arg overload: a caller must not be able to tell which entry point it used
            // from the message alone (SummonOpTest pins the wording).
            throw new IllegalArgumentException(
                    "Character " + master.getName() + " (" + master.getCid() + ") has no memosprite spec: "
                            + "add resources/" + Memosprites.DIR + "/" + master.getCid() + ".json describing "
                            + "its name and how its panel derives from the summoner");
        }
        return memosprite(master, spec, resourceValue);
    }

    public static Summon servant(Character master) {
        if (master == null) {
            throw new IllegalArgumentException("A memosprite needs a summoner");
        }
        MemospriteSpec spec = Memosprites.of(master.getCid(), Memosprites.SERVANT_DIR);
        if (spec == null) {
            throw new IllegalArgumentException(
                    "Character " + master.getName() + " (" + master.getCid() + ") has no memosprite spec: "
                            + "add resources/" + Memosprites.DIR + "/" + master.getCid() + ".json describing "
                            + "its name and how its panel derives from the summoner");
        }
        return servant(master, spec);
    }

    /** The same, for a panel that derives from a battle-level RESOURCE (see {@link #panelOf}). */
    public static Summon servant(Character master, java.util.function.ToIntFunction<String> resourceValue) {
        MemospriteSpec spec = Memosprites.of(master.getCid(), Memosprites.SERVANT_DIR);
        if (spec == null) {
            // The same text as the 1-arg overload (SummonOpTest pins the wording).
            throw new IllegalArgumentException(
                    "Character " + master.getName() + " (" + master.getCid() + ") has no memosprite spec: "
                            + "add resources/" + Memosprites.DIR + "/" + master.getCid() + ".json describing "
                            + "its name and how its panel derives from the summoner");
        }
        return servant(master, spec, resourceValue);
    }

    /**
     * The panel every summon shares: each entry is a share of the <b>master's attribute</b> plus a flat term
     * -- or, when it names {@code resource:<name>}, a share of a <b>battle-level resource</b> (2026-10-02;
     * reader: 1407/1415's dead dragon).
     *
     * <p>⚠ The resource reader is handed in rather than reached for: resources live on the battle, and this
     * derivation was deliberately split out to be exercised on its own. A {@code resource:} panel with no reader
     * is refused loudly -- deriving 0 would be a number that looks plausible and is wrong.
     */
    private static AttributeBuilder panelOf(Character master, MemospriteSpec spec,
                                            java.util.function.ToIntFunction<String> resourceValue) {
        AttributeBuilder panel = new AttributeBuilder();
        for (MemospriteSpec.Panel entry : spec.panel()) {
            AttributeType attribute = AttributeType.fromString(entry.attribute());
            double share = entry.percent() == null ? 0 : entry.percent();
            double flat = entry.flat() == null ? 0 : entry.flat();
            double value;
            if (entry.source() != null && entry.source().startsWith("attr:")) {
                // ⭐ 「等同于召唤者生命上限的 X%」 (2026-10-02): a share of ANOTHER of the
                // master's attributes. The plain branch below reads `master.getAttribute(attribute)` -- the
                // SAME attribute the entry names -- which is why the 景元-style trick (「神君」 = 66% of his
                // ATTACK, carried in the ATTACK slot) works but "40% of the summoner's Max HP" did not.
                String other = entry.source().substring("attr:".length()).trim();
                value = share * master.getAttribute(AttributeType.fromString(other)).get() + flat;
            } else if (entry.source() != null && entry.source().startsWith("resource:")) {
                String name = entry.source().substring("resource:".length()).trim();
                if (resourceValue == null) {
                    throw new IllegalStateException(
                            "the panel of \"" + spec.name() + "\" derives from resource \"" + name
                                    + "\", but no resource reader was handed in: use the overload that takes one "
                                    + "(a missing reader would silently derive 0)");
                }
                value = share * resourceValue.applyAsInt(name) + flat;
            } else {
                value = share * master.getAttribute(attribute).get() + flat;
            }
            // The builder's own convention for the two kinds of attribute (see AttributeBuilder): a base
            // attribute is set outright, and a ratio attribute -- whose base is literally 0 -- is given a
            // percentage-point modifier, which is exactly `ModifyAttr`'s rule for the same situation.
            if (attribute.isPercent) {
                panel.addPercentPoint(attribute, value, DoubleValue.Modifier.ModifierSource.BASE);
            } else {
                panel.setBase(attribute, value);
            }
        }
        return panel;
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
     * @return the memosprite, with its attack installed when the spec states one
     */
    /** The same, for a panel that derives from a battle-level RESOURCE (see {@link #panelOf}). */
    public static Summon memosprite(Character master, MemospriteSpec spec, java.util.function.ToIntFunction<String> resourceValue) {
        return memospriteWith(master, spec, resourceValue);
    }

    public static Summon memosprite(Character master, MemospriteSpec spec) {
        return memospriteWith(master, spec, null);
    }

    private static Summon memospriteWith(Character master, MemospriteSpec spec, java.util.function.ToIntFunction<String> resourceValue) {
        if (master == null) {
            throw new IllegalArgumentException("A memosprite needs a summoner");
        }
        if (spec == null) {
            throw new IllegalArgumentException("A memosprite needs a spec");
        }
        // Validated here too, not only on the load path: this overload is the seam a test uses, and a bad
        // spec handed to it must fail the same way a bad file does -- otherwise the checks in
        // Memosprites.validate could be bypassed by the one caller that is easiest to get wrong.
        Memosprites.validate(spec, "SummonFactory.memosprite(master, spec)");
        AttributeBuilder panel = panelOf(master, spec, resourceValue);
        Summon summon = new Summon(spec.name(), Camp.PLAYER, panel.build());
        summon.setLevel(master.getLevel());
        declareResources(summon, spec);
        if (spec.aggro() != null) {
            // The servant's own 仇恨 (「ServantID 11413 · 仇恨: 125」). Only stated when a document states it:
            // Battle.aggroOf answers its regular tier for anything left at 0, which is a different claim from
            // "the document says 100".
            summon.setAggro((int) Math.round(spec.aggro()));
        }
        if (spec.attack() != null) {
            summon.setSkill(SkillType.COMMON, attackOf(spec));
        }
        installSpecSkills(summon, spec, master);
        return summon;
    }

    /** The same, for a panel that derives from a battle-level RESOURCE (see {@link #panelOf}). */
    public static Summon servant(Character master, MemospriteSpec spec, java.util.function.ToIntFunction<String> resourceValue) {
        return servantWith(master, spec, resourceValue);
    }

    public static Summon servant(Character master, MemospriteSpec spec) {
        return servantWith(master, spec, null);
    }

    private static Summon servantWith(Character master, MemospriteSpec spec, java.util.function.ToIntFunction<String> resourceValue) {
        if (master == null) {
            throw new IllegalArgumentException("A memosprite needs a summoner");
        }
        if (spec == null) {
            throw new IllegalArgumentException("A memosprite needs a spec");
        }
        // Validated here too, not only on the load path: this overload is the seam a test uses, and a bad
        // spec handed to it must fail the same way a bad file does -- otherwise the checks in
        // Memosprites.validate could be bypassed by the one caller that is easiest to get wrong.
        Memosprites.validate(spec, "SummonFactory.servant(master, spec)");
        AttributeBuilder panel = panelOf(master, spec, resourceValue);
        Summon summon = new Summon(spec.name(), Camp.PLAYER, panel.build());
        summon.setLevel(master.getLevel());
        declareResources(summon, spec);
        if (spec.aggro() != null) {
            // The servant's own 仇恨 (「ServantID 11413 · 仇恨: 125」). Only stated when a document states it:
            // Battle.aggroOf answers its regular tier for anything left at 0, which is a different claim from
            // "the document says 100".
            summon.setAggro((int) Math.round(spec.aggro()));
        }
        if (spec.attack() != null) {
            summon.setSkill(SkillType.COMMON, attackOf(spec, DamageType.NORMAL));
        }
        // \u2b50 \u4ebf\u7075\u6280\u672c\u6765\u5c31\u8be5\u662f `Skill` (2026-10-02): each stated row becomes a real skill, addressed by (ServantID, slot) exactly
        // like a character's -- so `SkillEffects.forSkill`, `SkillExecutor.canDeliver` and every op that takes a skill work
        // on a memosprite's skill with no special case.
        installSpecSkills(summon, spec, master);
        return summon;
    }

    /**
     * Compiles the spec's {@code attack} block into the skill the memosprite acts with (P9-4 忆灵).
     *
     * <p>⚠ The spec was validated immediately above, so an unknown element / base / shape cannot reach this
     * point; the lookups are the plain {@code fromString} ones rather than a second set of checks, because
     * two copies of a rule is how the copies start to differ. (They would not agree on the failure mode
     * either: {@code DamageElement.fromString} and {@code SkillEffectType.fromString} answer {@code null} for
     * an unknown name, which the {@code EnemySkill} constructor turns into its documented default, while
     * {@code AttributeType.fromString} throws.)
     *
     * <p>The damage base is {@code base × percent} of the <b>memosprite's own</b> attribute, read fresh on
     * every hit (see {@code EnemySkill.strike}), so a buff that lands on the memosprite mid-battle is
     * reflected — the panel decides what it starts with, not what it is worth.
     */
    /** A memosprite's attack is memory damage; the two-argument form is what a servant uses. */
    private static EnemySkill attackOf(MemospriteSpec spec) {
        return attackOf(spec, DamageType.MEMORY);
    }

    private static EnemySkill attackOf(MemospriteSpec spec, DamageType type) {
        MemospriteSpec.Attack attack = spec.attack();
        return new EnemySkill(
                DamageElement.fromString(attack.element()),
                attack.percent(),
                attack.hits() == null ? 1 : attack.hits(),
                // ⚠ `type` is 忆灵伤害 (GLOSSARY.md), and a memosprite's own skill is exactly that -- not NORMAL.
                // The type has been declared since the table was written (its javadoc notes only some constants are in use);
                // labelling it here is what lets a rule scope a bonus to memosprite damage, and it is the game's own word.
                type,
                SkillEffectType.fromString(attack.shape()),
                AttributeType.fromString(attack.base()),
                attack.stance() == null ? 0 : attack.stance());
    }
}