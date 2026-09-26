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
 */
public record MemospriteSpec(@SerializedName("name") String name,
                             @SerializedName("source") String source,
                             @SerializedName("note") String note,
                             @SerializedName("panel") List<Panel> panel) {

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
     */
    public record Panel(@SerializedName("attribute") String attribute,
                        @SerializedName("percent") Double percent,
                        @SerializedName("flat") Double flat) {
    }
}
