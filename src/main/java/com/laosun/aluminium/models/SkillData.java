package com.laosun.aluminium.models;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.Skill;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Getter
public class SkillData {
    private static final List<List<Double>> EMPTY_PARAMS = Collections.emptyList();
    private static final Skill.StanceList EMPTY_STANCE = new Skill.StanceList(0, 0, 0);

    private static final SkillData EMPTY = new SkillData(0, "", EMPTY_PARAMS, EMPTY_STANCE);

    private final int maxLevel;
    private final String skillType;
    private final List<List<Double>> skills;
    private final Skill.StanceList stanceList;

    public SkillData(int maxLevel, String skillType, List<List<Double>> skills, Skill.StanceList stanceList) {
        this.maxLevel = maxLevel;
        this.skillType = skillType;
        this.skills = skills;
        this.stanceList = stanceList;
    }

    public static SkillData init(int cid, int skillID) {
        IO.println(cid + "_" + skillID);
        Map<Integer, Skill> skillMap = Constant.SKILLS.get(cid);
        if (skillMap == null) {
            return EMPTY;
        }
        Skill skill = skillMap.get(skillID);
        if (skill == null) {
            return EMPTY;
        }
        return new SkillData(skill.maxLevel(), skill.attackType(), skill.paramList(), skill.stanceList());
    }
}
