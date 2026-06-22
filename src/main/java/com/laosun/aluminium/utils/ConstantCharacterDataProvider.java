package com.laosun.aluminium.utils;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.CharacterData;

/**
 * Default {@link CharacterDataProvider} implementation backed by the global
 * {@link Constant#CHARACTERS} map loaded from JSON.
 */
public class ConstantCharacterDataProvider implements CharacterDataProvider {
    @Override
    public CharacterData get(int cid) {
        return Constant.CHARACTERS.get(cid);
    }
}
