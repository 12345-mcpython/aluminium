package com.laosun.aluminium.models;

import com.laosun.aluminium.enums.Camp;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * A summoned entity that participates in combat.
 *
 * <p>Summons are typically aligned with {@link Camp#PLAYER} and may inherit
 * stats from their summoner.
 *
 * <p>🚧 <b>骨架，尚未接入</b>（P9-4 忆灵/召唤物）：全项目**没有任何地方
 * {@code new Summon(...)}**，`SkillEffectType.SUMMON`（数据里 8 条）也没有分派。
 * 与"忆灵"相关的机制（独立行动条、面板快照、连携攻击）都还没做。
 */
@Getter
@Setter
@ToString(callSuper = true)
public class Summon extends CanHit {
    /**
     * Constructs a summoned entity.
     *
     * @param name       display name
     * @param camp       faction alignment (typically {@link Camp#PLAYER})
     * @param attributes pre-computed attribute array
     */
    public Summon(String name, Camp camp, DoubleValue[] attributes) {
        super(name, camp, attributes);
    }
}
