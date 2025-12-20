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
    private CharacterSkills characterSkills = new CharacterSkills(null, null, null, null, null, null);;

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
        return new Character(characterData.name(), camp, characterData.health(), characterData.defence(), characterData.attack(), characterData.speed());
    }
}