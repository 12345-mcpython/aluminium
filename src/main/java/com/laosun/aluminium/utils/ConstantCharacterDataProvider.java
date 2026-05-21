package com.laosun.aluminium.utils;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.CharacterData;

public class ConstantCharacterDataProvider implements CharacterDataProvider {
    @Override
    public CharacterData get(int cid) {
        return Constant.CHARACTERS.get(cid);
    }
}
