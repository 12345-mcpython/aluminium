package com.laosun.aluminium.models;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.CharacterData;
import com.laosun.aluminium.beans.Translate;
import com.laosun.aluminium.exceptions.CharacterException;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString(callSuper = true)
public class Character extends CanHit {
    public Character(String name, double health, double defence, double attack, double speed) {
        super(name, health, defence, attack, speed);
    }

    public Character(Translate name, double health, double defence, double attack, double speed) {
        super(name.english(), health, defence, attack, speed);
    }

    public static Character build(int cid, int level) {
        CharacterData characterData = Constant.CHARACTERS.get(cid);
        if (characterData == null) {
            throw new CharacterException.CharacterNotFoundException(String.format("Character '%s' not found", cid));
        }
        return new Character(characterData.name(), characterData.health(), characterData.defence(), characterData.attack(), characterData.speed());
    }
}
