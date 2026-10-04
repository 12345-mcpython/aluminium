"""A narrow, opt-in spelling: this effect may coexist with others of the same kind (2026-10-02, round 1692).

Measured last round: making DIFFERENT RULES coexist broke seven shipped readings (three cones, Jade's stacks, Kephale's
eidolon), so eviction is load-bearing and a blanket change is wrong. Measured the round before: `StatModifierBuff.isSameKind` is
`(attribute, modifierType, sourceRole)`, so 1408's trace 「进入战斗或变身结束时攻击力提高 50%」 and her transformation's 「变身期间攻击力
提高 80%」 evict each other although the documents have both in effect (+130% together).

So the behaviour change is confined to the ONE effect that asks for it:

  * `EffectSpec.coexist` (JSON `coexist`) -- "do not evict, and do not be the reason another is evicted";
  * `AbstractBuff.keepsSiblings()` carries it onto the buff;
  * `BuffManager.addBuff`'s replace loop skips the eviction when the NEW buff asks for it.

Everything that does not state the flag behaves exactly as before -- which is the property the seven readings were protecting.
"""
import io
import sys

BUFF = "src/main/java/com/laosun/aluminium/models/buff/AbstractBuff.java"
SPEC = "src/main/java/com/laosun/aluminium/beans/EffectSpec.java"
MANAGER = "src/main/java/com/laosun/aluminium/models/buff/BuffManager.java"
INTERP = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"


def patch(path, old, new, label, count=1):
    text = io.open(path, encoding="utf-8").read()
    found = text.count(old)
    if found != count:
        sys.stderr.write("REFUSING %s: the anchor appears %d times\n" % (label, found))
        raise SystemExit(1)
    io.open(path, "w", encoding="utf-8", newline="\n").write(text.replace(old, new, count))
    print("ok   %s" % label)


patch(
    BUFF,
    '    protected String ruleId = "";',
    '    protected String ruleId = "";\n\n'
    '    /**\n'
    '     * \u2b50 Whether this buff asks NOT to evict others of its kind (2026-10-02). Measured: `StatModifierBuff` identity is\n'
    '     * `(attribute, modifierType, sourceRole)`, so two rules granting the same attribute evict each other -- while some\n'
    '     * documents have both in effect at once. \u26a0 Opt-in on purpose: making every different rule coexist broke seven shipped\n'
    '     * readings, so eviction stays the default and only the effect that says so is exempt.\n'
    '     */\n'
    '    private boolean keepsSiblings;\n\n'
    '    /** Whether this buff asks not to evict others of its kind. */\n'
    '    public boolean keepsSiblings() {\n'
    '        return keepsSiblings;\n'
    '    }\n\n'
    '    /** Sets the ask (see {@link #keepsSiblings()}). */\n'
    '    public void setKeepsSiblings(boolean keeps) {\n'
    '        this.keepsSiblings = keeps;\n'
    '    }',
    "AbstractBuff.keepsSiblings",
)

patch(
    SPEC,
    '    @SerializedName("stackable")\n    private Boolean stackable;',
    '    @SerializedName("stackable")\n    private Boolean stackable;\n\n'
    '    /**\n'
    '     * \u2b50 {@code "coexist": true} -- this effect must not evict another effect of the same kind (2026-10-02; reader: 1408\'s\n'
    '     * trace \u300c\u8fdb\u5165\u6218\u6597\u6216\u53d8\u8eab\u7ed3\u675f\u65f6\u653b\u51fb\u529b\u63d0\u9ad8 50%\u300d, which is in effect together with her transformation\'s +80%).\n'
    '     */\n'
    '    @SerializedName("coexist")\n    private Boolean coexist;',
    "EffectSpec.coexist",
)

patch(
    SPEC,
    "        copy.stackable = this.stackable;",
    "        copy.stackable = this.stackable;\n        copy.coexist = this.coexist;",
    "EffectSpec.copy carries it",
)

patch(
    MANAGER,
    """        for (int i = buffs.size() - 1; i >= 0; i--) {
            AbstractBuff existed = buffs.get(i);
            if (existed.isSameKind(buff)) {
                removeBuff(existed);
            }
        }""",
    """        // \u2b50 An effect may ask NOT to evict (2026-10-02; reader: 1408's trace, whose +50% must live beside her transformation's
        // +80%). Measured, eviction is load-bearing for three cones and two other kits, so this is opt-in and the default is
        // untouched.
        for (int i = buffs.size() - 1; i >= 0 && !buff.keepsSiblings(); i--) {
            AbstractBuff existed = buffs.get(i);
            if (existed.isSameKind(buff)) {
                removeBuff(existed);
            }
        }""",
    "addBuff honours the ask",
)

patch(
    INTERP,
    """            if (Boolean.TRUE.equals(effect.getPerStackLive())) {""",
    """            // \u2b50 The ask travels onto the buff (2026-10-02), so `BuffManager` can honour it without knowing about effects.
            if (Boolean.TRUE.equals(effect.getCoexist())) {
                buff.setKeepsSiblings(true);
            }
            if (Boolean.TRUE.equals(effect.getPerStackLive())) {""",
    "modifyAttr stamps the ask",
)
