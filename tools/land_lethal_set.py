import io, sys

def insert_before(path, anchor, lines, label, indent_from_anchor=True):
    txt = io.open(path, encoding="utf-8").read()
    if txt.count(anchor) != 1:
        sys.exit("REFUSING %s : %d" % (label, txt.count(anchor)))
    i = txt.index(anchor)
    ls = txt.rfind("\n", 0, i) + 1
    indent = txt[ls:i] if indent_from_anchor else ""
    add = "".join((indent + l).rstrip() + "\n" if l.strip() else "\n" for l in lines.split("\n"))
    io.open(path, "w", encoding="utf-8", newline="\n").write(txt[:ls] + add + txt[ls:])
    print("ok   %s" % label)

B = "src/main/java/com/laosun/aluminium/Battle.java"
insert_before(B, "private void processAddRequests() {", """/**
 * The allies a lethal blow has landed on during the CURRENT action (2026-10-02).
 *
 * <p>\\u2b50 Reader: 1407 \\u6708\\u8309\\u4e4b\\u5e87, \\u300c\\u5728**\\u4e00\\u6b21\\u884c\\u52a8**\\u4e2d\\u53d7\\u5230\\u81f4\\u547d\\u653b\\u51fb\\u7684**\\u5168\\u4f53**\\u300d. The set is what makes that ONE clause rather
 * than one save per blow: the same action can land a lethal blow on several allies, and the effect reaches all of them.
 *
 * <p>\\u26a0 Cleared when an action starts ({@code TURN_START}, which brackets {@code performAction} and its settlement), so
 * "this action" is the action boundary the engine already had -- not a new one.
 */
private final List<CanHit> lethallyHitThisAction = new ArrayList<>();

/** The allies a lethal blow has landed on since this action began, in the order it happened. */
public List<CanHit> lethallyHitThisAction() {
    return List.copyOf(lethallyHitThisAction);
}""", "the per-action lethal set")
insert_before(B, "fireTriggersForAlly(TriggerEvent.LETHAL_DAMAGE, target, target, 0);",
              "if (!lethallyHitThisAction.contains(target)) {\n    lethallyHitThisAction.add(target);\n}",
              "recording the victim")
insert_before(B, "fireTriggers(TriggerEvent.TURN_START, actor, actor, 0, 0);",
              "// \\u2b50 \\u300c\\u4e00\\u6b21\\u884c\\u52a8\\u4e2d\\u300d begins here: the action boundary already existed (this event brackets\n"
              "// `performAction` and its settlement), so the lethal set is cleared at it.\nlethallyHitThisAction.clear();",
              "clearing it at the action boundary")

T = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
insert_before(T, '"random_ally_below_half_energy",',
              '// \\u2b50 \\u300c\\u5728**\\u4e00\\u6b21\\u884c\\u52a8**\\u4e2d\\u53d7\\u5230\\u81f4\\u547d\\u653b\\u51fb\\u7684**\\u5168\\u4f53**\\u300d (1407 \\u6708\\u8309\\u4e4b\\u5e87): the allies a lethal blow has landed on\n'
              '// since this action began. A SET, not a unit -- hence the `all_` prefix, like `all_allies`.\n'
              '"all_allies_lethally_hit_this_action",', "the selector name")
insert_before(T, "if (TARGET_ALL_ALLIES.contains(selector) || TARGET_OTHER_ALLIES.equals(selector)) {",
              'if ("all_allies_lethally_hit_this_action".equals(selector)) {\n'
              '    if (battle == null) {\n'
              '        throw new IllegalStateException(\n'
              '                "Effect targets \\"all_allies_lethally_hit_this_action\\" but no battle was supplied to read its lethal set");\n'
              '    }\n'
              '    return battle.lethallyHitThisAction();\n'
              '}', "the resolver branch")
