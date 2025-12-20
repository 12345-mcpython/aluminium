package com.laosun.aluminium.models;


import com.laosun.aluminium.beans.Translate;
import com.laosun.aluminium.enums.SkillAttackType;
import com.laosun.aluminium.enums.SkillEffectType;
import com.laosun.aluminium.enums.SkillType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class SkillRequest {
    public Translate name;
    public int level;
    public SkillEffectType effect;
    public SkillType skill;
    public SkillAttackType skillAttackType;
}