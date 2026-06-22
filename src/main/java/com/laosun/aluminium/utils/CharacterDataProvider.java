package com.laosun.aluminium.utils;

import com.laosun.aluminium.beans.CharacterData;

/**
 * Provider interface for character base data.
 *
 * <p>Allows decoupling character data access from the static {@link com.laosun.aluminium.Constant}
 * store, enabling alternative data sources for testing.
 */
public interface CharacterDataProvider {
    /**
     * Retrieves the base data for a character by its game ID.
     *
     * @param cid the character's numeric ID
     * @return the character data, or {@code null} if not found
     */
    CharacterData get(int cid);
}
