package com.laosun.aluminium.models;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.Skill;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@AllArgsConstructor
@Getter
public class SkillData {
    private int maxLevel;
    private String skillType;
    private List<List<Double>> skills;
    private Skill.StanceList stanceList;

    public static SkillData init(int cid, int skillID) {
        Skill skill = Constant.SKILLS.get(cid).get(skillID);
        return new SkillData(skill.maxLevel(), skill.attackType(), skill.paramList(), skill.stanceList());
    }
}
