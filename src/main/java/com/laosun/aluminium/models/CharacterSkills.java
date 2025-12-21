package com.laosun.aluminium.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class CharacterSkills {
    @NonNull
    public Skill common;
    @NonNull
    public Skill skill;
    @NonNull
    public Skill ultra;
    // talent should use "event listener"
    @NonNull
    public Skill talent;
    @NonNull
    public Skill summonTalent;
    @NonNull
    public Skill summonSkill;
}
