"""Two rules' modifiers on one attribute can coexist (2026-10-02, round 1691).

Measured: `StatModifierBuff.isSameKind` is `(attribute, modifierType, sourceRole)` -- so 1408's trace 「进入战斗或变身结束时攻击力提高
50%」 and her transformation's 「变身期间攻击力提高 80%」 are the SAME KIND and evict each other, while the documents say both are
in effect (a total of +130%). The pinned reading made it visible: `expected base x 1.5 x 1.8 but was base x 1.8`.

The fix keeps every existing meaning and only splits by RULE:
  * the same rule re-applied  -> same kind -> refreshes, exactly as before;
  * two DIFFERENT rules       -> different kind -> both apply, which is what the documents describe;
  * a buff with no rule id (factory-made, e.g. `percentBuff`) -> the old tuple decides, so nothing else moves at all.

That last clause is deliberate: this is a behaviour change, and confining it to rules is the smallest one that can be stated.
"""
import io
import sys

PATH = "src/main/java/com/laosun/aluminium/models/buff/StatModifierBuff.java"
text = io.open(PATH, encoding="utf-8").read()
OLD = """    @Override
    public boolean isSameKind(AbstractBuff other) {
        if (!(other instanceof StatModifierBuff that)) {
            return false;
        }
        return this.attribute == that.attribute
                && this.modifierType == that.modifierType
                && this.sourceRole == that.sourceRole;
    }"""
NEW = """    @Override
    public boolean isSameKind(AbstractBuff other) {
        if (!(other instanceof StatModifierBuff that)) {
            return false;
        }
        if (this.attribute != that.attribute
                || this.modifierType != that.modifierType
                || this.sourceRole != that.sourceRole) {
            return false;
        }
        // \u2b50 Two DIFFERENT rules are two different effects (2026-10-02). Measured: 1408's trace \u300c\u8fdb\u5165\u6218\u6597\u6216\u53d8\u8eab\u7ed3\u675f\u65f6
        // \u653b\u51fb\u529b\u63d0\u9ad8 50%\u300d and her transformation's \u300c\u53d8\u8eab\u671f\u95f4\u653b\u51fb\u529b\u63d0\u9ad8 80%\u300d were the same kind, so the second evicted the first --
        // while the documents say both are in effect (a total of +130%). \u26a0 The split is confined to RULES: a buff with no rule
        // id (every factory-made one) keeps the old tuple as its whole identity, so nothing else moves.
        boolean thisFromRule = this.ruleId != null && !this.ruleId.isBlank();
        boolean thatFromRule = that.ruleId != null && !that.ruleId.isBlank();
        if (thisFromRule && thatFromRule) {
            return this.ruleId.equals(that.ruleId);
        }
        return true;
    }"""
if text.count(OLD) != 1:
    sys.exit("REFUSING: the anchor appears %d times" % text.count(OLD))
io.open(PATH, "w", encoding="utf-8", newline="\n").write(text.replace(OLD, NEW, 1))
print("ok   two rules' modifiers can coexist; everything else keeps its identity")
