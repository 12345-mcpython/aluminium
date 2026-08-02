package com.laosun.aluminium.models;

/**
 * A 忆灵 skill (忆灵技) driven by servant_skills.json data, sharing the same
 * execution engine as character skills.
 *
 * @param servantId the servant ID (e.g. 11402 for Garmentmaker)
 * @param skillIndex the servant skill index (1 = 忆灵普攻, 2 = 忆灵技, 3 = 忆灵终结技)
 */
public class ServantSkill extends DataSkill {

    public ServantSkill(int servantId, int skillIndex, int level) {
        super(servantId, skillIndex, level, ServantSkillData.init(servantId, skillIndex));
    }

    @Override
    public String toString() {
        return "ServantSkill[" + cid + "_" + skillId + "@" + level + "]";
    }
}
