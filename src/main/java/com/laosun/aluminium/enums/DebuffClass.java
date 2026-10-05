package com.laosun.aluminium.enums;

import java.util.Locale;

/**
 * The <b>class</b> of a negative state (负面状态类), as the documents group them:
 * "抵抗<b>控制类</b>负面状态的概率提高35%" and "抵抗<b>持续伤害类</b>负面状态的概率提高50%".
 *
 * <p><b>Why a class and not just the specific resistance key.</b> The engine already has a per-state resistance
 * ({@code STAT_CTRL_Frozen} and friends, read by {@code Battle.hitChance} from a monster's own data), which answers
 * "is this unit resistant to <i>this</i> state". The sentences above are about a <b>family</b>: 克拉拉's 守护 makes
 * her 35% more likely to resist <b>any</b> control, and 长夜月's 忆灵 "长夜" is outright <b>immune</b> to the whole
 * class ("'长夜'免疫控制类负面状态"). That cannot be written as a list of specific keys - a new control state
 * would silently fall outside the list.
 *
 * <p>Note: <b>Only the two classes the corpus names exist.</b> "免疫<b>负面效果</b>" (all of them) is a different
 * sentence with a reader whose character file does not exist yet (1409 小伊卡), so it is registered rather than
 * guessed at; and a state that belongs to no class ({@code null}, the answer every other buff gives) is simply not
 * affected by any class resistance.
 */
public enum DebuffClass {

    /**
     * 控制类: a state that takes the victim's turn away - 冻结 / 纠缠 / 禁锢, and the engine's plain act lock.
     */
    CONTROL("control"),

    /**
     * 持续伤害类: the four damage-over-time states (灼烧 / 触电 / 裂伤 / 风化).
     */
    DOT("dot");

    private final String value;

    DebuffClass(String value) {
        this.value = value;
    }

    /**
     * The spelling a rule uses for this class in {@code RESIST_DEBUFF}'s {@code "kind"} ({@code "control"}).
     */
    public String value() {
        return value;
    }

    /**
     * The class a rule named, or {@code null} when the name is not one of them.
     *
     * @param raw the raw {@code "kind"} value ({@code "control"} / {@code "dot"}, case-insensitive)
     */
    public static DebuffClass fromString(String raw) {
        if (raw == null) {
            return null;
        }
        String wanted = raw.trim().toLowerCase(Locale.ROOT);
        for (DebuffClass kind : values()) {
            if (kind.value.equals(wanted)) {
                return kind;
            }
        }
        return null;
    }

    /**
     * The names a rule may write, for an error message that tells the author what to fix it with.
     */
    public static java.util.List<String> names() {
        return java.util.Arrays.stream(values()).map(DebuffClass::value).sorted().toList();
    }
}