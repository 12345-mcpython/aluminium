package com.laosun.aluminium.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class CharacterSkills {
    public Skill common;
    public Skill skill;
    public Skill ultra;
    public Skill talent;
    public Skill summonTalent;
    public Skill summonSkill;
}
