"""STATE_ENDED, step 3a: a name-carrying firing entry on Battle (2026-10-02).

Why a field + a dedicated method rather than threading the name through the fireTriggers overload chain: the chain is
four overloads deep and every one of them would grow a parameter for a fact exactly ONE event carries. The project
already solved the same shape this way (lastUltEnergySpent), and its comment records the hazard that pattern needs
guarding: the field must be cleared right after the firing, or every later event inherits it.

The save/restore in `finally` is that guard, and it also keeps a nested firing (a rule that removes another state while
handling this one) from clobbering the outer name.

Purely additive: nothing calls it yet. The full suite is the judge.
ASCII only.
"""
import io
import sys

PATH = "src/main/java/com/laosun/aluminium/Battle.java"
ANCHOR = "    public int fireTriggers(TriggerEvent event) {\n"
NEW = ("    /**\n"
       "     * The name of the state that just left a unit, for the duration of a {@link TriggerEvent#STATE_ENDED}\n"
       "     * firing (2026-10-02).\n"
       "     *\n"
       "     * <p><b>Why a field and not a context component.</b> The firing chain is four overloads deep, and a name\n"
       "     * would be a parameter on every one of them for a fact that exactly one event carries. State also has to ride\n"
       "     * <i>here</i> rather than be read off the carrier: by the time the event fires the state is already gone.\n"
       "     */\n"
       "    private String lastStateEndedName;\n"
       "\n"
       "    /** The state named by the {@link TriggerEvent#STATE_ENDED} currently being fired, or {@code null}. */\n"
       "    public String getLastStateEndedName() {\n"
       "        return lastStateEndedName;\n"
       "    }\n"
       "\n"
       "    /**\n"
       "     * Fires {@link TriggerEvent#STATE_ENDED} for a state that has just left {@code carrier}.\n"
       "     *\n"
       "     * <p>\u26a0 Save/restore, not a plain clear: a rule handling this event may itself remove another state, and the\n"
       "     * outer name must survive that. This is the guard {@code lastUltEnergySpent}'s comment warns about.\n"
       "     */\n"
       "    public void fireStateEnded(CanHit carrier, String stateName) {\n"
       "        String previous = lastStateEndedName;\n"
       "        lastStateEndedName = stateName;\n"
       "        try {\n"
       "            fireTriggers(TriggerEvent.STATE_ENDED, carrier, carrier, 0, 0);\n"
       "        } finally {\n"
       "            lastStateEndedName = previous;\n"
       "        }\n"
       "    }\n"
       "\n")

text = io.open(PATH, encoding="utf-8").read()
if "fireStateEnded" in text:
    print("skip: already there")
    raise SystemExit(0)
if text.count(ANCHOR) != 1:
    print("FAIL: fireTriggers(event) anchor matched %d times" % text.count(ANCHOR))
    sys.exit(1)
io.open(PATH, "w", encoding="utf-8", newline="").write(text.replace(ANCHOR, NEW + ANCHOR))
print("ok   Battle.fireStateEnded + the name field")
