package com.laosun.aluminium.test;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.utils.CharacterFactory;

/**
 * A real character with <b>no trigger file of their own</b> - the "unregistered" control that the trigger-table
 * suites are built on.
 *
 * <p><b>Why it is looked up rather than named.</b> It used to be Himeko (姬子) (1003), spelled as a constant in
 * {@code TriggerTableTest} and {@code RelicTriggerTableTest}, until her kit was authored on 2026-09-2- which
 * turned four unrelated assertions into failures: "no file to empty table", "a character without relics behaves as
 * before", and a {@code BATTLE_START} count that went from 1 to 2 because the control itself had acquired a rule.
 * None of those claims is about Himeko; they are all about <em>having no rules</em>. Which character that is, is a
 * fact about the <b>content</b> and changes every time a kit is data-ised (93 characters, a handful written), so
 * the control asks for the lowest character id that has no file instead of naming one.
 *
 * <p>Note: Characters that do not use conventional energy ({@code CharacterFactory.usesSpecialResource}) are skipped:
 * a control is also used for arithmetic like "a basic attack credits 20 energy  x  ERR", which such a character
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
     * @throws IllegalStateException when every character in the data has a trigger file - the control cannot
     *                               exist any more, and a test silently using a character <em>with</em> rules
     *                               would be asserting the wrong thing
     */
    static int withoutTriggerFile() {
        // Note: PINNED ON PURPOSE (2026-09-29, round 209/240). This used to return "the lowest id with no trigger
        // file" -- a MOVING TARGET: shipping 1112 (round 20/240) handed every caller a DIFFERENT character, and
        // a suite measuring HP numbers went red far from the change. The id below is a key of
        // data/character_data.json with no content file; if it ever gains one, the guard fails loudly.
        int cid = 1506;   // 2026-09-30: the pin moved because 1502 Yao Guang was SHIPPED. 1506 (Silver Wolf LV.999 (银狼LV.999)) is now
        // the last key of character_data.json without a content file. Note: Its max energy is 0, which is why the suite that
        // asserted "an empty table still recovers energy" no longer uses this control -- it builds an empty table itself. // 2026-09-30: the pin moved because 1505 绯英 was SHIPPED -- the guard failed loudly
        // exactly as designed. 1502 Yao Guang is still a key of character_data.json with no content file. Note: Note what the
        // failure taught: this control is used as a PARTY MEMBER in some suites, so its data (element, speed, whether slot 1
        // is single-target) can move their numbers. `HimekoChargeTest` now names its own single-target ally for that reason.
        if (TriggerTables.exists(cid)) {
            throw new IllegalStateException(
                    "Character " + cid + " now has a trigger file, so the \"unregistered character\" control is gone. "
                            + "Pick another key of character_data.json that is deliberately left un-data-ised "
                            + "and update this constant.");
        }
        return cid;
    }
}
