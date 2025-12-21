package com.laosun.aluminium.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

// TODO: finish it!
@AllArgsConstructor
@Getter
@Setter
public class EnemySkills {
    public int cursor;

    public List<Skill> skills;
}
