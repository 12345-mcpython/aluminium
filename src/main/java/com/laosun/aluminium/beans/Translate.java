package com.laosun.aluminium.beans;

/**
 * A bilingual text record with Chinese and English translations.
 *
 * <p>Used for character names, weapon names, skill descriptions, etc.
 * in the game data files.
 */
public record Translate(String chinese, String english) {
}
