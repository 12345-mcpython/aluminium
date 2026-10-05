"""A word for "this attack was a follow-up" (2026-10-02). Reader: 1415's ode of passage, second sentence.

「缇宝施放<b>追加攻击</b>触发缇宝的结界的附加伤害时，会额外造成 #1 次附加伤害。」

The engine's own notion of a follow-up attack is `Battle.applyAdditionalDamage` ("this is the engine's one and only notion of a follow-up attack"), and it fires
`TriggerEvent.FOLLOW_UP`. But a rule cannot ASK whether the instance it is looking at came from one:

  * `SkillCategory` has no `FOLLOW_UP` value (measured: NORMAL/BPSKILL/ULTRA/MAZE_NORMAL/MAZE/ASSIST/ELATION_DAMAGE/UNSPECIFIED/UNKNOWN);
  * nothing reads an instance's cast category as a condition.

So: the category gains the value, the DAMAGE op can STATE it, and a keyword can ASK it.  ⚠ The category rides on the INSTANCE because `applyAdditionalDamage`
fires `FOLLOW_UP` without one; stamping the damage is what lets the event say what it was.
"""
import io
import re
import sys

CAT = "src/main/java/com/laosun/aluminium/enums/SkillCategory.java"
DMG = "src/main/java/com/laosun/aluminium/models/Damage.java"
BAT = "src/main/java/com/laosun/aluminium/Battle.java"
SPEC = "src/main/java/com/laosun/aluminium/beans/EffectSpec.java"
INT = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
TAB = "src/main/java/com/laosun/aluminium/models/TriggerTable.java"

cat = io.open(CAT, encoding="utf-8").read()
dmg = io.open(DMG, encoding="utf-8").read()
bat = io.open(BAT, encoding="utf-8").read()
spec = io.open(SPEC, encoding="utf-8").read()
interp = io.open(INT, encoding="utf-8").read()
tab = io.open(TAB, encoding="utf-8").read()

# ---- 1) the category value ----
CAT_ANCHOR = '    ELATION_DAMAGE("ElationDamage"),'
CAT_NEW = '''    ELATION_DAMAGE("ElationDamage"),
    /**
     * ⭐ 「追加攻击」 (2026-10-02). The engine’s own notion of one is {@code Battle.applyAdditionalDamage} -- the method that fires
     * {@code TriggerEvent.FOLLOW_UP} -- but an instance could not SAY it was one, so a rule reading an instance could not ask. Reader: 1415's ode of passage,
     * 「缇宝施放**追加攻击**触发缇宝的结界的附加伤害时…」, where the follow-up half is load-bearing: an ordinary attack must
     * NOT satisfy it.
     */
    FOLLOW_UP("FollowUp"),'''

# ---- 2) a reader for the instance's category ----
DMG_ANCHOR = "    private final SkillCategory castCategory;"
DMG_NEW = '''    private final SkillCategory castCategory;

    /** @see #castCategory */
    public SkillCategory getCastCategory() {
        return castCategory;
    }'''

# ---- 3) the overload that states it; the old one delegates so there is still ONE settlement body ----
BAT_ANCHOR = """    public double applyAdditionalDamage(CanHit attacker, CanHit target, DamageElement element, double base,
                                        Double fixedCritRate, Double fixedCritDamage, DamageType type) {
        Damage extra = new Damage(attacker, target, element, type == null ? DamageType.ADDITIONAL : type, base);"""
BAT_NEW = """    public double applyAdditionalDamage(CanHit attacker, CanHit target, DamageElement element, double base,
                                        Double fixedCritRate, Double fixedCritDamage, DamageType type) {
        return applyAdditionalDamage(attacker, target, element, base, fixedCritRate, fixedCritDamage, type, null);
    }

    /**
     * The same, with the instance’s CAST CATEGORY stated (2026-10-02; reader: 1415's ode of passage, whose clause is about a 「追加攻击」). ⚠ The
     * body stays here and the six/seven-argument forms delegate to it -- a second way to build and settle an instance is exactly what the single-settlement
     * invariant exists to prevent, as the note above says.
     */
    public double applyAdditionalDamage(CanHit attacker, CanHit target, DamageElement element, double base,
                                        Double fixedCritRate, Double fixedCritDamage, DamageType type,
                                        com.laosun.aluminium.enums.SkillCategory castCategory) {
        Damage extra = new Damage(attacker, target, element, type == null ? DamageType.ADDITIONAL : type, base, castCategory);"""

# ---- 4) the effect can state it ----
SPEC_ANCHOR = '    @SerializedName("damage_type")'
SPEC_NEW = '''    /**
     * The cast category of the instance a DAMAGE effect produces, spelled as {@code SkillCategory} (2026-10-02).
     *
     * <p>Reader: 1415's ode of passage, 「缇宝施放**追加攻击**触发…时」 -- the follow-up half has to be stated somewhere, and
     * `applyAdditionalDamage` fires `FOLLOW_UP` without one.
     */
    @SerializedName("cast_category")
    private String castCategory;

    @SerializedName("damage_type")'''

SPEC_COPY = "        copy.percentFromSkillParam = this.percentFromSkillParam;"
SPEC_COPY_NEW = """        copy.percentFromSkillParam = this.percentFromSkillParam;
        copy.castCategory = this.castCategory;"""

# ---- 5) the DAMAGE worker passes it ----
INT_RE = re.compile(
    r"battle\.applyAdditionalDamage\(attacker, victim, skill == null.*?effect\.getCritRate\(\), effect\.getCritDamage\(\), damageType\);",
    re.DOTALL)
INT_ANCHOR = None
INT_NEW = """            battle.applyAdditionalDamage(attacker, victim, skill == null
                            ? DamageElement.fromString(effect.getElement().trim())
                            : elementOf(effect, skill), settledBase,
                    effect.getCritRate(), effect.getCritDamage(), damageType,
                    // ⭐ The rule may state that this instance is a 「追加攻击」 (2026-10-02): `applyAdditionalDamage` fires FOLLOW_UP without a
                    // category, so the instance has to carry it for a listener to be able to ask.
                    effect.getCastCategory() == null || effect.getCastCategory().isBlank()
                            ? null
                            : com.laosun.aluminium.enums.SkillCategory.fromString(effect.getCastCategory().trim()));"""

# ---- 6) the keyword ----
TAB_CONST_ANCHOR = '    static final String DAMAGE_IS_ADDITIONAL = "damage_is_additional";'
TAB_CONST_NEW = '''    static final String DAMAGE_IS_ADDITIONAL = "damage_is_additional";

    /**
     * The bare keyword {@code damage_is_follow_up}: "the instance being settled came from a 「追加攻击」" (2026-10-02).
     *
     * <p>⭐ The third of the family, and the one that had no spelling at all: `SkillCategory` did not even have a FOLLOW_UP value, so a rule could not ask the
     * question the ode of passage asks -- 「缇宝施放**追加攻击**触发…时」 -- and an ordinary attack had no way to be excluded.
     */
    static final String DAMAGE_IS_FOLLOW_UP = "damage_is_follow_up";'''

TAB_PARSE_ANCHOR = "            return new DamageIsAdditional(raw);\n        }"
TAB_PARSE_NEW = """            return new DamageIsAdditional(raw);
        }

        // ⭐ The third of the family (2026-10-02).
        if (text.trim().equalsIgnoreCase(DAMAGE_IS_FOLLOW_UP)) {
            TriggerEvent event = TriggerEvent.fromString(spec.getOn());
            if (event == null || !DAMAGE_CARRYING_EVENTS.contains(event)) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' asks whether the instance is a follow-up attack, but " + spec.getOn()
                                + " carries no damage instance; it belongs on an event that settles one "
                                + "(source: " + spec.getSource() + ")");
            }
            return new DamageIsFollowUp(raw);
        }"""

TAB_CLASS_ANCHOR = "    /** ⭐ The complement of {@link #DAMAGE_IS_ATTACK}: the instance is additional damage, not an ordinary attack. */"
TAB_CLASS_NEW = '''    /** ⭐ "The instance came from a 「追加攻击」" (2026-10-02): the instance’s own category, stamped where the rule states one. */
    private static final class DamageIsFollowUp implements Condition {
        private final String raw;

        DamageIsFollowUp(String raw) {
            this.raw = raw;
        }

        @Override
        public boolean test(TriggerContext ctx) {
            return ctx.damage() != null
                    && ctx.damage().getCastCategory() == com.laosun.aluminium.enums.SkillCategory.FOLLOW_UP;
        }

        @Override
        public String source() {
            return raw;
        }
    }

''' + TAB_CLASS_ANCHOR

checks = [(cat, CAT_ANCHOR, "the category value"), (dmg, DMG_ANCHOR, "the damage field"),
          (bat, BAT_ANCHOR, "the battle overload"), (spec, SPEC_ANCHOR, "the effect field"),
          (spec, SPEC_COPY, "the effect copy"), (interp, INT_RE, "the worker call"),
          (tab, TAB_CONST_ANCHOR, "the constant"), (tab, TAB_PARSE_ANCHOR, "the parse site"),
          (tab, TAB_CLASS_ANCHOR, "the class anchor")]
for body, old, label in checks:
    n = len(old.findall(body)) if hasattr(old, 'findall') else body.count(old)
    print("anchor %-22s : %d" % (label, n))
    if n != 1:
        sys.exit("REFUSING: %s occurs %d times -- nothing written" % (label, n))

io.open(CAT, "w", encoding="utf-8", newline="\n").write(cat.replace(CAT_ANCHOR, CAT_NEW))
io.open(DMG, "w", encoding="utf-8", newline="\n").write(dmg.replace(DMG_ANCHOR, DMG_NEW))
io.open(BAT, "w", encoding="utf-8", newline="\n").write(bat.replace(BAT_ANCHOR, BAT_NEW))
io.open(SPEC, "w", encoding="utf-8", newline="\n").write(spec.replace(SPEC_ANCHOR, SPEC_NEW).replace(SPEC_COPY, SPEC_COPY_NEW))
io.open(INT, "w", encoding="utf-8", newline="\n").write(INT_RE.sub(lambda m: INT_NEW, interp, count=1))
io.open(TAB, "w", encoding="utf-8", newline="\n").write(
    tab.replace(TAB_CONST_ANCHOR, TAB_CONST_NEW).replace(TAB_PARSE_ANCHOR, TAB_PARSE_NEW).replace(TAB_CLASS_ANCHOR, TAB_CLASS_NEW))
print("ok   FOLLOW_UP: category, instance reader, op spelling, keyword")
