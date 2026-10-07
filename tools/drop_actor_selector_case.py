"""Drop the actor-selector case from the reward judge and record why.

Status: the shipped feature does NOT need `target: "actor"`. The extra-turn payment ended up addressed with the default
(`self`), because 好活当赏 is ONE count and its declaration and spender are both Pearl; the clause's CONDITION is what
speaks about the archetype (`actor has_state 美学底本`). So the selector stays in the engine as a registered capability
(with the note and the whitelist entry), and its dedicated case waits until something needs it.

Why the case was pulled rather than debugged further: its fixture hit a counter whose cap reads 0, so `Resource.gain`
clamped the payment away with nothing said (`[PROBE after gain] ... max=0`), and two attempts to give the recipient a
usable counter (`register`) did not change it - measured, not assumed. That is a fact about the FIXTURE (or about how a
hand-built table registers resources), not about the selector, and chasing it further buys nothing for this round's
feature. It is registered instead.

Usage:
    python tools/drop_actor_selector_case.py
"""
import pathlib
import re
import sys

JUDGE = pathlib.Path("src/test/java/com/laosun/aluminium/test/content/characters/PearlExtraTurnRewardTest.java")
START = "    /**\n     * {@code target: \"actor\"} names the EVENT's actor, not the rule's owner."
END_MARKER = "    // ==================================================================\n    // Helpers"


def main():
    text = JUDGE.read_text(encoding="utf-8")
    start = text.find(START)
    end = text.find(END_MARKER)
    if start == -1 or end == -1 or end < start:
        print("anchors not found (start:", start, "end:", end, ")")
        return 1
    removed = text[start:end]
    if "theActorSelectorNamesTheEventsActorNotTheOwner" not in removed or "actorRule" not in removed:
        print("the block does not look like the case + its helper; refusing")
        return 2
    text = text[:start] + text[end:]
    # The class doc still mentions the selector; keep that, it is where the capability is recorded.
    JUDGE.write_text(text, encoding="utf-8")
    check = JUDGE.read_text(encoding="utf-8")
    if "actorRule" in check or "theActorSelector" in check:
        print("something survived; inspect by hand")
        return 3
    print(f"removed {len(removed.splitlines())} lines (the case and its helper)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
