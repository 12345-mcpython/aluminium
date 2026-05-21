package com.laosun.aluminium.utils;

import com.laosun.aluminium.beans.CharacterData;

public interface CharacterDataProvider {
    CharacterData get(int cid);
}