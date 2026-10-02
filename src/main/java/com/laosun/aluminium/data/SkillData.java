package com.laosun.aluminium.data;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.Skill;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillEffectType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Runtime view of a skill loaded from {@code skills.json}, resolved for a
 * specific character + skill-id combination.
 *
 * <p>Provides the per-level parameter list {@link #getSkills()}, the attack
 * element {@link #getElement()} and the effect classification
 * {@link #getEffect()}. Access is immutable read-only; resolve lazily via
 * {@link #init(int, int)} which caches nothing but reads from
 * {@link Constant#SKILLS}.
 *
 * <p><b>Non-damaging skills</b> (heal / support / summon / ...) may have
 * {@code element == null} (the raw data uses {@code "Unknown"}); their
 * {@code effect} tells the executor which pipeline to run.
 *
 * @see SkillEffectType
 * @see DamageElement
 */
@Getter
@AllArgsConstructor
public class SkillData {
    private static final List<List<Double>> EMPTY_PARAMS = Collections.emptyList();
    private static final Skill.StanceList EMPTY_STANCE = new Skill.StanceList(0, 0, 0);

    /**
     * Fallback skill data returned when the requested character / skill id is not found
     * in the loaded data set.
     */
    private static final SkillData EMPTY = new SkillData(0, "", EMPTY_PARAMS, EMPTY_STANCE,
            DamageElement.PHYSICAL, SkillEffectType.ENHANCE, null, null, null);

    /**
     * Is this a real row, or the {@link #EMPTY} placeholder the loader hands back for an id the data does not have?
     *
     * <p><b>Why this question needs a name.</b> {@code Character.Builder} fills every intrinsic slot with a
     * {@code DefaultSkill}, and a slot whose character has no such row still ends up <b>present in the map</b> —
     * holding this placeholder. So "the slot is there" and "the character has that skill" are different facts, and
     * the difference is invisible to a null check: {@code getData()} answers non-null either way. Measured
     * (2026-10-02): 姬子 carries a {@code ELATION_SKILL} key whose row is this placeholder, while 8010's is a real
     * one — the first version of the {@code has_skill} condition asked about the key and answered "yes" for every
     * character in the game.
     *
     * <p>⚠ The marker is {@code maxLevel}: a real row has at least one level of parameters, the placeholder has
     * none ({@code 0}). It is the one field {@code EMPTY} is constructed with that no real row can share — which is
     * why the test that uses this also prints both sides.
     */
    public boolean isLoaded() {
        return maxLevel > 0;
    }

    /**
     * Maximum possible skill level (length of {@link #skills}).
     */
    private final int maxLevel;
    /**
     * Attack category string from the data, e.g. {@code "Normal"}, {@code "BPSkill"},
     * {@code "Ultra"}, {@code "Maze"}.
     */
    private final String skillType;
    /**
     * Per-level parameter lists, indexed by {@code level - 1}.
     */
    private final List<List<Double>> skills;
    /**
     * Toughness-reduction per-target counts: single / all / spread.
     */
    private final Skill.StanceList stanceList;
    /**
     * Damage element of this skill, or {@code null} for non-damaging skills.
     */
    private final DamageElement element;
    /**
     * Effect classification of this skill (attack / heal / buff / ...).
     */
    private final SkillEffectType effect;
    /**
     * Ultimate activation threshold (the {@code sp_need} of {@code skills.json}): **only ultimates
     * have a value**, the rest are {@code null}.
     *
     * <p>It is exactly tbgd's {@code AvatarSkillConfig.SPNeed}. ⚠ **It is NOT equal to the energy
     * cap**: of the 93 characters, 5 have a 2:1 ratio between the two (Yunli 240/120, Argenti
     * 180/90, 绯英 480/240, Feixiao 12/6, Cyrene (昔涟) 24/12).
     * See also {@link #spBase}.
     */
    private final Double spNeed;
    /**
     * **How much energy** is gained by casting this skill (the {@code sp_base} of
     * {@code skills.json}): tbgd's {@code AvatarSkillConfig.SPBase}.
     *
     * <p>Regular tiers: basic attack 20 / skill 30 / ultimate 5 — consistent with
     * {@code Constant.ENERGY_GAIN_*}.
     *
     * <p>⚠ <b>This field does not drive energy gain at present</b>
     * ({@link com.laosun.aluminium.models.energy.StandardEnergyProvider} still uses the constants).
     * The reason is that in the data the {@code sp_base} of **multi-hit / bouncing skills is a
     * "per-hit" value** (Asta / Sampo / Anaxa / Harmony Trailblazer 6, Welt 10), and it would have
     * to be multiplied by the hit count to be correct — but multiplying by hit count depends on
     * the ability config's {@code SPHitRatio} (absent from this project's data). The constants
     * happen to give the **correct total** instead.
     * The proper data-driven path is ROADMAP P3-4 (aggregate {@code SPHitRatio} first).
     *
     * <p>Value of keeping the read: it is the input for aggregating {@code SPHitRatio}, and it is
     * also the raw fact of "which skills grant no energy gain" ({@code null} = that skill grants
     * no energy gain).
     */
    private final Double spBase;

    /**
     * The skill's Chinese description ({@code skill_introduction.chinese}), tags and all — or
     * {@code null} when the data has none.
     *
     * <p><b>Why a description is a first-class field.</b> The data states some numbers <b>only in
     * prose</b>: which {@code param_list} slot holds a debuff's base chance is written as
     * "有#4%的基础概率" and nowhere else. The generator already relies on this ({@code skill_effects.json}
     * records {@code source: "SkillDesc 占位符"}), so the engine reading it too is not a hack — it is
     * the only way to answer the question at all. Kept raw rather than pre-parsed so that each
     * question (chance now, magnitudes later) can be answered without regenerating the data.
     */
    private final String description;

    /**
     * A {@code #N[...]} placeholder in a description; {@code N} is 1-based onto {@code param_list}.
     */
    private static final java.util.regex.Pattern PLACEHOLDER =
            java.util.regex.Pattern.compile("#(\\d+)\\[[^\\]]*\\]");
    /**
     * Markup that may sit between a placeholder and the words after it.
     */
    private static final java.util.regex.Pattern MARKUP =
            java.util.regex.Pattern.compile("<[^>]*>|\\\\n");

    /**
     * Resolves the skill data of a character skill from the global data set.
     *
     * @param cid     character id (key of {@code skills.json})
     * @param skillID skill slot id (e.g. 1 = normal attack, 2 = basic skill,
     *                3 = ultimate, 4 = talent, 6 = maze attack, 7 = technique)
     * @return the skill data for the requested skill
     * @throws IllegalArgumentException if the character / skill id exists but its
     *                                  {@code skill_effect} is missing or unknown
     */
    public static SkillData init(int cid, int skillID) {
        Map<Integer, Skill> skillMap = Constant.SKILLS.get(cid);
        if (skillMap == null) {
            return EMPTY;
        }
        Skill skill = skillMap.get(skillID);
        if (skill == null) {
            return EMPTY;
        }
        if (skill.skillEffect() == null) {
            throw new IllegalArgumentException("Missing skill_effect: cid=" + cid + ", skillID=" + skillID);
        }
        SkillEffectType effect = SkillEffectType.fromString(skill.skillEffect());
        if (effect == null) {
            throw new IllegalArgumentException(
                    "Unknown skill_effect: " + skill.skillEffect() + " (cid=" + cid + ", skillID=" + skillID + ")");
        }
        return new SkillData(skill.maxLevel(), skill.attackType(), skill.paramList(), skill.stanceList(),
                skill.element(), effect, skill.spNeed(), skill.spBase(),
                skill.skillIntroduction() == null ? null : skill.skillIntroduction().chinese());
    }

    /**
     * The debuff's base chance, read out of the description, or {@code null} when the skill states none.
     *
     * <h2>Which parameter it is (P10-6)</h2>
     * ⚠ <b>Not a fixed slot.</b> The plan for this task said "the chance is the 3rd {@code param_list}
     * entry"; measured against the real data that is wrong for <b>every</b> skill examined, and wrong
     * in the worst way — for Himeko's technique (1003/7) index 3 holds {@code 15}, so the "chance"
     * would be 1500%, clamped to 1.0 by {@link com.laosun.aluminium.Battle#hitChance}, i.e. "always lands" and nothing
     * would look broken.
     *
     * <p>The description <b>says</b> which index it is: the placeholder that directly precedes the
     * words 基础概率 / 固定概率. Five measured anchors:
     *
     * <table border="1">
     *   <caption>skill, the text, and where the number really lives</caption>
     *   <tr><th>skill</th><th>text</th><th>param index</th><th>value</th></tr>
     *   <tr><td>1003/7 姬子 不完全燃烧</td><td>{@code 有#1%的<u>基础概率</u>}</td><td>0</td><td>1.0</td></tr>
     *   <tr><td>1004/7 瓦尔特 画地为牢</td><td>{@code 有#1%的<u>基础概率</u>}</td><td>0</td><td>1.0</td></tr>
     *   <tr><td>1108/7 桑博 你最闪亮</td><td>{@code 有#2%<u>固定概率</u>} (no 的)</td><td><b>1</b></td><td>1.0</td></tr>
     *   <tr><td>1006/4 银狼 等待程序响应…</td><td>{@code 有#4%的<u>基础概率</u>}</td><td><b>3</b></td><td>0.6</td></tr>
     *   <tr><td>1307/4 黑天鹅 无端命运的机杼</td><td>{@code 有#2%的<u>基础概率</u>}</td><td><b>1</b></td><td>0.5</td></tr>
     * </table>
     *
     * <p>So the rule is textual, and the two wordings are <b>not</b> interchangeable in game terms:
     * 基础概率 is scaled by the caster's effect hit rate and reduced by the target's resistance
     * (what {@link com.laosun.aluminium.Battle#hitChance} computes), while 固定概率 is applied as-is. Both are returned
     * here, because both are "the chance this skill states"; a caller that feeds a 固定概率 through
     * {@code hitChance} will over-apply it. Distinguishing them is left to whoever builds the
     * Impair dispatch — the data needed to know <i>which</i> debuff is applied is still missing
     * (see ROADMAP P10-6), so an API for it here would have no caller.
     *
     * <p>Matching stops at the first hit, which is what the data needs: when a description names the
     * chance twice (黑天鹅 1307/4) both spellings point at the same placeholder.
     *
     * @return the chance ({@code param_list} of level 1), or {@code null} when the description states
     * no chance at all — which is a real answer, not a failure: 14 of the 28 {@code Impair}
     * skills (e.g. 1315/2 波提欧's 【绝命对峙】) apply their effect unconditionally
     */
    public Double debuffChance() {
        if (description == null || skills.isEmpty()) {
            return null;
        }
        java.util.regex.Matcher matcher = PLACEHOLDER.matcher(description);
        while (matcher.find()) {
            int end = Math.min(description.length(), matcher.end() + 60);
            String after = MARKUP.matcher(description.substring(matcher.end(), end)).replaceAll("");
            int i = 0;
            // java.lang.Character spelled out: this package has a Character class of its own (the combatant).
            while (i < after.length()
                    && (after.charAt(i) == '%' || java.lang.Character.isWhitespace(after.charAt(i)))) {
                i++;
            }
            if (i < after.length() && after.charAt(i) == '的') {
                i++;                                     // 1003/7 writes "的<u>基础概率</u>", 1108/7 does not
            }
            String tail = after.substring(i);
            if (!tail.startsWith("基础概率") && !tail.startsWith("固定概率")) {
                continue;
            }
            int index = Integer.parseInt(matcher.group(1)) - 1;   // placeholders are 1-based
            List<Double> level1 = skills.getFirst();
            return index >= 0 && index < level1.size() ? level1.get(index) : null;
        }
        return null;
    }

    /**
     * The enum parsed from the data's {@code attack_type} — **use this to branch**, do not take the
     * bare string from {@link #getSkillType()} and {@code switch}/{@code equals} on it.
     *
     * <p>The problem with the bare string: when the data side changes the spelling or adds a new
     * type it **fails to match silently** (it falls into {@code default} and is swallowed).
     * Going through the enum means "data value → engine semantics" is defined in exactly one place
     * ({@link SkillCategory#fromString}), and when a new type is added the compiler forces every
     * {@code switch} to take a position.
     *
     * @return never {@code null}; {@link SkillCategory#UNSPECIFIED} when the data is empty, and
     * {@link SkillCategory#UNKNOWN} when the data value is not recognized
     */
    public SkillCategory getCategory() {
        return SkillCategory.fromString(skillType);
    }

    /**
     * The toughness <b>one hit</b> of this skill removes — the single place that maps an attack shape onto
     * {@link Skill.StanceList}'s three columns ({@code single} / {@code all} / {@code spread}).
     *
     * <p><b>Why it lives here rather than in the executor.</b> Two callers need the same answer and must not
     * drift: {@code SkillExecutor} (a skill's own cast) and the trigger interpreter's {@code COMMAND_SUMMON}
     * (a <b>commanded</b> attack, which is the same swing delivered by a summon). ⚠ It used to be split three
     * ways — AOE and BLAST read {@code getStanceList()} themselves while only BOUNCE went through a helper — and
     * a mutant that zeroed the helper's AOE arm survived the whole suite (2026-09-27), which is what a dead
     * branch looks like from the outside.
     *
     * <p>{@code mainTarget} is what separates BLAST's centre ({@code single}) from its neighbours ({@code spread}).
     * A skill with no stance data ({@code StanceList(0, 0, 0)}) answers {@code 0} everywhere, which is the
     * documented "this skill removes no toughness".
     *
     * @param mainTarget whether the hit is on the skill's main target (only BLAST distinguishes)
     * @return the toughness to remove per hit
     */
    public double stanceFor(boolean mainTarget) {
        return switch (getEffect()) {
            case AOE_ATTACK -> getStanceList().all();
            case BLAST -> mainTarget ? getStanceList().single() : getStanceList().spread();
            default -> getStanceList().single();
        };
    }

    /**
     * A Bounce skill's damage share, taken from the description (2026-09-29).
     *
     * <p>Bounce rows do not agree on layout: 1009 is `[0.25]` (its 「额外造成4次」 is prose only), 1108 is `[4, 0.28]` (count first) and 1004 is
     * `[0.36, 0.65, 0.1, 2]` (count last). The only reliable map is the description: the share is the parameter a `#N[i]` placeholder
     * references immediately before a `%`, read the same way {@link #debuffChance()} reads its own.
     *
     * @return the level-1 share, or {@code null} when the description does not state one
     */
    public Double bounceDamageShare() {
        if (description == null || skills.isEmpty()) {
            return null;
        }
        java.util.regex.Matcher matcher = PLACEHOLDER.matcher(description);
        while (matcher.find()) {
            int end = Math.min(description.length(), matcher.end() + 12);
            String after = MARKUP.matcher(description.substring(matcher.end(), end)).replaceAll("");
            if (!after.startsWith("%")) {
                continue;
            }
            int index = Integer.parseInt(matcher.group(1)) - 1;   // placeholders are 1-based
            List<Double> level1 = skills.getFirst();
            return index >= 0 && index < level1.size() ? level1.get(index) : null;
        }
        return null;
    }

    /**
     * The number of ADDITIONAL hits a Bounce skill's description states: the integer parameter that is not the damage share
     * (2026-09-29). 1004's `2` and 1108's `4` are such numbers; 1009 states its count in prose, so this returns {@code null} for it.
     */
    public Integer bounceAdditionalHits(Double damageShare) {
        if (skills.isEmpty()) {
            return null;
        }
        for (Double value : skills.getFirst()) {
            if (value == null || value.equals(damageShare)) {
                continue;
            }
            double raw = value;
            if (raw >= 1 && raw == Math.rint(raw)) {
                return (int) raw;
            }
        }
        return null;
    }

    /**
     * Which of the caster's attributes a damage clause scales off (2026-09-29).
     *
     * <p>The description names it: 「等同于砂金100%防御力」 is DEF, 「等同于风堇50%生命上限」 is Max HP, and everything else is ATK. 18 documents scale a damage
     * clause off Max HP and 3 off DEF, while the executor used to multiply {@code ATTACK} unconditionally — so those skills dealt the wrong damage.
     */
    public com.laosun.aluminium.enums.AttributeType damageBaseAttribute() {
        if (description != null) {
            String text = MARKUP.matcher(description).replaceAll("");
            if (text.contains("\u9632\u5fa1\u529b")) {
                return com.laosun.aluminium.enums.AttributeType.DEFENCE;
            }
            if (text.contains("\u751f\u547d\u4e0a\u9650")) {
                return com.laosun.aluminium.enums.AttributeType.HEALTH;
            }
        }
        return com.laosun.aluminium.enums.AttributeType.ATTACK;
    }
}
