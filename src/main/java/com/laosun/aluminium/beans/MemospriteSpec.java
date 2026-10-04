package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * A character's memosprite (忆灵) as data: its name, and how its panel is <b>derived from its
 * summoner</b>.
 *
 * <p>Loaded from {@code resources/memosprites/<ownerCid>.json} by {@link com.laosun.aluminium.data.Memosprites}.
 *
 * <h2>Why the panel is stated as an inheritance rather than as numbers</h2>
 * Every memosprite in the documents is described that way, and the ratios differ per character:
 *
 * <pre>
 * 1402 阿格莱雅 · 衣匠   「等同于阿格莱雅#1[i]%速度的速度以及等同于阿格莱雅#2[i]%生命上限+#3[i]的生命上限」
 * 1413 长夜月   · 「长夜」 「初始拥有#1[i]点速度，生命上限为长夜月的#2[i]%」
 * 1512 知更鸟·晴歌 · 晴空乐手 「等同于…#1[i]%生命上限的生命上限和等同于…#2[i]%速度的速度」
 * 8007/8 开拓者  · 迷迷   「初始拥有#1[i]点速度和等同于开拓者#2[i]%生命上限+#3[i]的生命上限」
 * </pre>
 *
 * <p>So a memosprite's panel is <b>not</b> a stat block of its own — it is a function of the summoner's
 * current sheet. Writing absolute numbers would be wrong for every level, every light cone and every relic
 * the summoner wears, and it would go stale the moment any of them changed.
 *
 * <p>⚠ A panel entry <b>replaces</b> the attribute: nothing this file does not mention is inherited. An
 * attribute left out stays at {@code AttributeBuilder}'s default of 0 — which is why {@code HEALTH} and
 * {@code SPEED} are required (see {@code Memosprites.validate}): 0 max HP is a unit that dies to a tick, and
 * 0 speed is a unit the action bar cannot schedule. The other columns (ATTACK, DEFENCE, the resistances)
 * have no number in any document and no memosprite stat table in this data set, so they are deliberately
 * left at 0 and registered as a gap rather than guessed at.
 *
 * @param name   the memosprite's name as the text writes it (e.g. 衣匠)
 * @param source where the panel numbers come from — the document and rule, with the placeholders and the
 *               parameter list, so a number can be traced back
 * @param note   free-form note for the next reader (may be absent)
 * @param panel  one entry per attribute this memosprite takes from its summoner
 * @param attack the memosprite's own attack, or {@code null} when no document states one (P9-4 忆灵)
 * @param aggro  the servant's 仇恨 weight, or {@code null} to leave the engine's regular tier (100). Every
 *               document that describes a servant states one (「ServantID 11413 · 仇恨: 125」), so {@code null}
 *               means "no document says", not "it is an ordinary unit"
 */
public record MemospriteSpec(@SerializedName("name") String name,
                             @SerializedName("source") String source,
                             @SerializedName("note") String note,
                             @SerializedName("panel") List<Panel> panel,
                             @SerializedName("attack") Attack attack,
                             @SerializedName("aggro") Double aggro) {

    /**
     * A spec with a panel and <b>no attack</b> — the ordinary case, since a document states an attack for
     * only some memosprites.
     */
    public MemospriteSpec(String name, String source, String note, List<Panel> panel) {
        this(name, source, note, panel, null, null);
    }

    /** The same, with an attack and no aggro — the shape most specs have. */
    public MemospriteSpec(String name, String source, String note, List<Panel> panel, Attack attack) {
        this(name, source, note, panel, attack, null);
    }

    /**
     * One attribute of the panel, expressed against the summoner: {@code value = percent × summoner + flat}.
     *
     * <p>Either term may be absent, which is what lets the same entry shape cover "35% of the summoner's
     * speed" ({@code percent} only), "160 speed" ({@code flat} only) and "66% of the summoner's Max HP plus
     * 720" ({@code percent} and {@code flat}).
     *
     * @param attribute the {@link com.laosun.aluminium.enums.AttributeType} name, spelled the way the rule
     *                  files spell it (e.g. {@code HEALTH}, {@code SPEED})
     * @param percent   the share of the summoner's own value ({@code 0.35} = 35%), or {@code null} for none
     * @param flat      a flat addition after the share, or {@code null} for none
     * @param source    {@code "resource:<name>"} to derive from a battle-level RESOURCE instead of an attribute, or
     *                  {@code null} for the attribute behaviour every shipped panel uses (2026-10-02)
     */
    public record Panel(@SerializedName("attribute") String attribute,
                        @SerializedName("percent") Double percent,
                        @SerializedName("flat") Double flat,
                        @SerializedName("source") String source,
                        @SerializedName("by_ability") Boolean byAbility) {

        /**
         * The attribute-derived panel, which is every panel shipped before 2026-10-02: no {@code source}.
         */
        public Panel(String attribute, Double percent, Double flat) {
            this(attribute, percent, flat, null, null);
        }

        /** The resource-derived panel (2026-10-02), with nothing said about abilities. */
        public Panel(String attribute, Double percent, Double flat, String source) {
            this(attribute, percent, flat, source, null);
        }
    }

    /**
     * What the memosprite does when it gets a turn: {@code base × percent}, {@code hits} times, in
     * {@code shape}.
     *
     * <p><b>Why this lives here and not in a rule file.</b> A memosprite's attack is not a trigger — nothing
     * in the battle fires it, a turn does — so it is not a {@code TriggerSpec}. It also cannot live on the
     * summoning character, because the numbers are stated against the <em>memosprite</em>: 长夜月's 「长夜」
     * hits for 「等同于「长夜」200%生命上限」, which is the memosprite's own Max HP, not the summoner's.
     * Reading it off the summoner would produce a number 2× too large (the panel gives 长夜 half of 长夜月's
     * HP) — a wrong number that looks plausible, which is the worst kind.
     *
     * <p>⚠ {@code base} names an attribute of the <b>memosprite</b>. Where a document instead scales off the
     * <em>summoner</em> (景元's 「神君」 hits for 「等同于景元攻击力66%」), the panel carries the share and the
     * attack then scales off the memosprite's own ATTACK: the same number, stated the same way every other
     * inherited attribute is, and consistent with the panel's snapshot semantics.
     *
     * @param element the {@link com.laosun.aluminium.enums.DamageElement} spelling, as the data files write
     *                it ({@code "Ice"})
     * @param base    the {@link com.laosun.aluminium.enums.AttributeType} name the share applies to, which
     *                must be an attribute the panel actually gives this memosprite ({@code "HEALTH"})
     * @param percent the share of that attribute per hit ({@code 2.0} = 200%)
     * @param hits    how many segments land on each target, or {@code null} for one
     * @param shape   the {@link com.laosun.aluminium.enums.SkillEffectType} spelling ({@code "AoEAttack"}),
     *                or {@code null} for a single target
     */
    public record Attack(@SerializedName("element") String element,
                         @SerializedName("base") String base,
                         @SerializedName("percent") Double percent,
                         @SerializedName("hits") Integer hits,
                         @SerializedName("shape") String shape,
                         @SerializedName("stance") Double stance) {

        /** The same without a toughness value -- an attack that does not touch the bar, the default. */
        public Attack(String element, String base, Double percent, Integer hits, String shape) {
            this(element, base, percent, hits, shape, null);
        }
    }
}
