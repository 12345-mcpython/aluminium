package com.laosun.aluminium.test;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.utils.CharacterFactory;

/**
 * A real character with <b>no trigger file of their own</b> — the "unregistered" control that the trigger-table
 * suites are built on.
 *
 * <p><b>Why it is looked up rather than named.</b> It used to be 姬子 (1003), spelled as a constant in
 * {@code TriggerTableTest} and {@code RelicTriggerTableTest}, until her kit was authored on 2026-09-27 — which
 * turned four unrelated assertions into failures: 「no file → empty table」, 「a character without relics behaves as
 * before」, and a {@code BATTLE_START} count that went from 1 to 2 because the control itself had acquired a rule.
 * None of those claims is about Himeko; they are all about <em>having no rules</em>. Which character that is, is a
 * fact about the <b>content</b> and changes every time a kit is data-ised (93 characters, a handful written), so
 * the control asks for the lowest character id that has no file instead of naming one.
 *
 * <p>⚠ Characters that do not use conventional energy ({@code CharacterFactory.usesSpecialResource}) are skipped:
 * a control is also used for arithmetic like 「a basic attack credits 20 energy × ERR」, which such a character
 * does not follow. The question being asked is "an ordinary character with nothing of their own", and that is what
 * this answers.
 */
final class TestCharacters {

    private TestCharacters() {
    }

    /**
     * The lowest character id whose kit is not data-ised yet.
     *
     * @return the cid, e.g. 1002 today
     * @throws IllegalStateException when every character in the data has a trigger file — the control cannot
     *                               exist any more, and a test silently using a character <em>with</em> rules
     *                               would be asserting the wrong thing
     */
    static int withoutTriggerFile() {
        return Constant.CHARACTERS.keySet().stream()
                .sorted()
                .filter(cid -> !TriggerTables.exists(cid))
                .filter(cid -> !CharacterFactory.usesSpecialResource(cid))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Every character in the data now has a trigger file, so there is no "
                                + "\"unregistered character\" control left. Pick a different control (or a "
                                + "character built without a table) for the suites that need one."));
    }
}
