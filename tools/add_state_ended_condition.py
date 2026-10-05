"""STATE_ENDED, step 4a: the `state_ended <name>` condition (2026-10-02).

Modelled on `has_state`, which the file already spells out: a boundary-guarded keyword (so a state NAME containing the
keyword is not mistaken for the operator), a subject from STATE_SUBJECTS, and a Condition implementation. The one
difference is WHERE the answer comes from: `has_state` reads the unit's buff manager, while this reads the name the
event carries -- the state is already gone by the time it fires.

Written `target state_ended 生息` for 「【生息】结束时」: the subject is the CARRIER (the event's target), not the actor.
Compile-checked: the caller reverts on a red suite.
ASCII only, except the two state names in the doc comment.
"""
import io
import sys

PATH = "src/main/java/com/laosun/aluminium/models/TriggerTable.java"
text = io.open(PATH, encoding="utf-8").read()

if "STATE_ENDED_KEYWORD" in text:
    print("skip: already there")
    raise SystemExit(0)

# 1) the keyword pattern, beside the others ---------------------------------------------------------------
PATTERN_ANCHOR = "    private static final Pattern HAS_STATE =\n"
PATTERN_NEW = ("    /**\n"
               "     * The keyword of the \"the named state just left that unit\" condition (2026-10-02).\n"
               "     *\n"
               "     * <p>Boundary-guarded for the same reason {@link #HAS_STATE} is: a state whose NAME contains the\n"
               "     * keyword must not be mistaken for the operator. The answer comes from the event\n"
               "     * ({@code TriggerContext.battle().getLastStateEndedName()}) rather than from the unit, because the state\n"
               "     * is already gone by the time it fires -- which is exactly why the event carries the name.\n"
               "     */\n"
               "    private static final Pattern STATE_ENDED_KEYWORD =\n"
               "            Pattern.compile(\"(?<![\\\\w])state_ended(?![\\\\w])\", Pattern.CASE_INSENSITIVE);\n"
               "\n")

# 2) the parse branch, before has_state's (same shape, same guards) ---------------------------------------
PARSE_ANCHOR = "        Matcher hasState = HAS_STATE.matcher(text);\n"
PARSE_NEW = ("        Matcher stateEnded = STATE_ENDED_KEYWORD.matcher(text);\n"
             "        if (stateEnded.find()) {\n"
             "            String subject = normalize(text.substring(0, stateEnded.start()));\n"
             "            String state = text.substring(stateEnded.end()).trim();\n"
             "            return new StateEnded(requireCarriedParty(requireStateSubject(subject, raw, spec), raw, spec),\n"
             "                    state, raw, spec);\n"
             "        }\n"
             "\n")

# 3) the condition itself, beside HasState ----------------------------------------------------------------
CLASS_ANCHOR = "    private static final class HasState implements Condition, PartyCondition {\n"
CLASS_NEW = ("    /**\n"
             "     * 「【X】结束时」: the state NAMED X has just left the subject (2026-10-02).\n"
             "     *\n"
             "     * <p>⚠ It cannot be written as {@code has_state}: the removal happens first, so by the time this event\n"
             "     * fires the carrier no longer has it. The name rides on the event instead.\n"
             "     */\n"
             "    private static final class StateEnded implements Condition, PartyCondition {\n"
             "\n"
             "        private final String subject;\n"
             "        private final String state;\n"
             "        private final String raw;\n"
             "\n"
             "        StateEnded(String subject, String state, String raw, TriggerSpec spec) {\n"
             "            if (state.isEmpty()) {\n"
             "                throw new IllegalArgumentException(\n"
             "                        \"Condition '\" + raw + \"' names no state after \\\"state_ended\\\" \"\n"
             "                                + \"(source: \" + spec.getSource() + \")\");\n"
             "            }\n"
             "            this.subject = subject;\n"
             "            this.state = state;\n"
             "            this.raw = raw;\n"
             "        }\n"
             "\n"
             "        @Override\n"
             "        public CanHit partyOf(TriggerContext ctx) {\n"
             "            return switch (subject) {\n"
             "                case \"self\" -> ctx.owner();\n"
             "                case \"actor\" -> ctx.actor();\n"
             "                case \"target\" -> ctx.target();\n"
             "                default -> null;\n"
             "            };\n"
             "        }\n"
             "\n"
             "        @Override\n"
             "        public boolean test(TriggerContext ctx) {\n"
             "            return ctx.battle() != null && state.equals(ctx.battle().getLastStateEndedName());\n"
             "        }\n"
             "\n"
             "        @Override\n"
             "        public String source() {\n"
             "            return raw;\n"
             "        }\n"
             "    }\n"
             "\n")

for anchor, replacement, label in ((PATTERN_ANCHOR, PATTERN_NEW, "pattern"),
                                   (PARSE_ANCHOR, PARSE_NEW, "parse branch"),
                                   (CLASS_ANCHOR, CLASS_NEW, "condition class")):
    if text.count(anchor) != 1:
        print("FAIL %s: anchor matched %d times" % (label, text.count(anchor)))
        sys.exit(1)
    text = text.replace(anchor, replacement + anchor)

io.open(PATH, "w", encoding="utf-8", newline="").write(text)
print("ok   state_ended: keyword + parse branch + StateEnded condition")
