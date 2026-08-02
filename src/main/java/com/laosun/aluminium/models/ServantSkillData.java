package com.laosun.aluminium.models;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.Skill;
import com.laosun.aluminium.beans.Translate;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Skill data for 忆灵 (memosprite) skills from servant_skills.json.
 */
public class ServantSkillData extends SkillData {

    public ServantSkillData(int maxLevel, String skillType, String skillEffect, List<List<Double>> skills,
                            Skill.StanceList stanceList, Translate skillIntroduction) {
        super(maxLevel, skillType, skillEffect, skills, stanceList, skillIntroduction);
    }

    public static ServantSkillData init(int servantId, int skillIndex) {
        Map<Integer, Skill> skillMap = Constant.SERVANT_SKILLS.get(servantId);
        if (skillMap == null || !skillMap.containsKey(skillIndex)) {
            return new ServantSkillData(0, "", "", List.of(), new Skill.StanceList(0, 0, 0),
                    new Translate("", ""));
        }
        Skill skill = skillMap.get(skillIndex);
        return new ServantSkillData(skill.maxLevel(), skill.attackType(), skill.skillEffect(), skill.paramList(),
                skill.stanceList(), skill.skillIntroduction());
    }
}
