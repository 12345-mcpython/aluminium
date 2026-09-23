package com.laosun.aluminium.enums;

/**
 * The category of a character's skill.
 *
 * <p>Corresponds to the different types of abilities in Honkai: Star Rail.
 *
 * <p>按"什么时候进入战斗模型"分两类，这个区分很重要（见 {@link #isIntrinsic()}）：
 * <ul>
 *   <li><b>常驻</b>：{@link #COMMON} / {@link #SKILL} / {@link #ULTRA} / {@link #TALENT}
 *       —— 角色一造出来就该有，由 {@code Character.Builder.build()} 装配；</li>
 *   <li><b>战斗开场附加</b>：{@link #MAZE} / {@link #TECHNIQUE}
 *       ——地图上用的东西，由 {@code Battle.startBattle()} 附加，不常驻在角色身上。</li>
 * </ul>
 */
public enum SkillType {
    /**
     * Basic attack.
     */
    COMMON,
    /**
     * Skill (consumes skill points).
     */
    SKILL,
    /**
     * Ultimate (energy-based).
     */
    ULTRA,
    /**
     * Passive talent.
     */
    TALENT,
    /**
     * 地图普攻（技能槽位 6）：在大地图上打怪、以及"进入战斗时削弱对应属性韧性"那一下。
     *
     * <p>数据里的攻击类型是 {@code MazeNormal}、效果 {@code MazeAttack}。
     * 它**不是**战斗内的普攻（那是 {@link #COMMON}）。
     */
    MAZE,
    /**
     * 秘技（技能槽位 7）：地图上主动施放的强化效果（数据里攻击类型 {@code Maze}）。
     *
     * <p>多数角色的秘技效果是"下一场战斗开始时生效"（例如景元 +3 段【神君】），
     * 所以它的**触发时机**是战斗开始 —— 详见 ROADMAP P8-6 的事件补齐。
     */
    TECHNIQUE,
    /**
     * Active skill of a summoned entity.
     */
    SUMMON_SKILL,
    /**
     * Passive talent of a summoned entity.
     */
    SUMMON_TALENT;

    /**
     * 这个槽位是不是**角色常驻**技能（一造出来就该有）。
     *
     * <p>{@link #MAZE} / {@link #TECHNIQUE} 返回 {@code false}：它们是战斗开场才附加的，
     * 所以 {@code CharacterFactory} 造出来的角色身上**没有**它们
     * （装配点见 {@code Constant.SKILL_SLOT} 与 {@code Battle#startBattle()}）。
     *
     * <p>召唤物的两个槽位也返回 {@code false}：它们属于忆灵/召唤物，不是角色自己的技能
     * （P9-4）。
     */
    public boolean isIntrinsic() {
        return this == COMMON || this == SKILL || this == ULTRA || this == TALENT;
    }
}
