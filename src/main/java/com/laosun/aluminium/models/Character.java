package com.laosun.aluminium.models;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.CharacterData;
import com.laosun.aluminium.beans.Translate;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.exceptions.CharacterException;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString(callSuper = true)
public class Character extends CanHit {
    private CharacterSkills characterSkills;

    public Character(String name, Camp camp, double health, double defence, double attack, double speed) {
        super(name, camp, health, defence, attack, speed);
    }

    public Character(Translate name, Camp camp, double health, double defence, double attack, double speed) {
        super(name.english(), camp, health, defence, attack, speed);
    }

    public static Character build(int cid, int level, Camp camp) {
        CharacterData characterData = Constant.CHARACTERS.get(cid);
        if (characterData == null) {
            throw new CharacterException.CharacterNotFoundException(String.format("Character '%s' not found", cid));
        }
        // 此处可扩展：根据等级调整属性（如 health = baseHealth * level * 0.1）
        return new Character(characterData.name(), camp, characterData.health(), characterData.defence(), characterData.attack(), characterData.speed());
    }
}