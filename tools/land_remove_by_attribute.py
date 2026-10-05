"""`REMOVE_STATE` can name an ATTRIBUTE, so a modifier can be taken off by what it modifies (2026-10-02).

Reader: 1415's memosprite skill 12 「献予「浪漫」之诗」 -- 「阿格莱雅与衣匠造成的伤害提高 #2% 并无视目标 #3% 的防御，<b>持续至阿格莱雅退出【至高之姿】状态</b>」.
The engine's durations are `turns` or `permanent`, and neither says "until that other state ends"; the half that CAN say it is a companion rule on
`STATE_ENDED` (`self state_ended 至高之姿`) that takes the two modifiers off again.

What is already there, all measured:
  * `BuffManager.isNamed(AbstractBuff, String stateName, AttributeType attribute)` ALREADY answers by attribute:
    `if (attribute != null) return buff instanceof StatModifierBuff m && m.getAttribute() == attribute;`
  * `REMOVE_STATE`'s worker calls `target.getBuffManager().removeState(effect.getBuff())` -- a NAME;
  * and its load-time check is `requireBuff`, which demands that name.
So the single missing piece is the op's spelling: let it state `attribute` instead of `buff`, and pass it to the by-attribute sweep the manager
already has (asserted below -- if there is no such sweep, this script refuses rather than inventing one).
"""
import io
import re
import sys

T = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
M = "src/main/java/com/laosun/aluminium/models/buff/BuffManager.java"

interp = io.open(T, encoding="utf-8").read()
mgr = io.open(M, encoding="utf-8").read()

# ---------- phase 1: does the manager really have a by-attribute sweep? ----------
overloads = re.findall(r"public int removeState\(([^)]*)\)", mgr)
print("removeState overloads: %s" % overloads)
if not any("AttributeType" in o for o in overloads):
    sys.exit("REFUSING: BuffManager has no removeState(AttributeType) -- the by-attribute sweep does not exist, so nothing is written")

OLD_VALIDATE = """                   requireBuff(effect, op, spec);
                   requireNoDuration(effect, op, spec);
                   requireNoStackArguments(effect, op, spec);"""
NEW_VALIDATE = """                   // ⭐ `buff` (the state's name) OR `attribute` (what a modifier modifies) -- exactly one (2026-10-02). The by-attribute
                   // form is what makes 「持续至…状态结束」 expressible: the state that ends is one thing, the modifiers that must
                   // come off with it may be several, and naming them by attribute is the only handle a rule has on them.
                   boolean byAttribute = effect.getAttribute() != null && !effect.getAttribute().isBlank();
                   if (byAttribute) {
                       requireAttribute(effect, op, spec);
                   } else {
                       requireBuff(effect, op, spec);
                   }
                   requireNoDuration(effect, op, spec);
                   requireNoStackArguments(effect, op, spec);"""
OLD_WORKER = """    private static void removeState(EffectSpec effect, TriggerContext ctx) {
        for (CanHit target : resolveTargets(ctx.battle(), effect, ctx)) {
            target.getBuffManager().removeState(effect.getBuff());
        }
    }"""
NEW_WORKER = """    private static void removeState(EffectSpec effect, TriggerContext ctx) {
        // ⭐ A NAME or an ATTRIBUTE (2026-10-02): the second is how 「持续至【至高之姿】状态结束」 takes its own modifiers off.
        AttributeType byAttribute = effect.getAttribute() == null || effect.getAttribute().isBlank()
                ? null
                : AttributeType.fromString(effect.getAttribute().trim());
        for (CanHit target : resolveTargets(ctx.battle(), effect, ctx)) {
            if (byAttribute == null) {
                target.getBuffManager().removeState(effect.getBuff());
            } else {
                target.getBuffManager().removeState(byAttribute);
            }
        }
    }"""

for old, label in ((OLD_VALIDATE, "the REMOVE_STATE validation"), (OLD_WORKER, "the removeState worker")):
    n = interp.count(old)
    print("anchor %-30s : %d" % (label, n))
    if n != 1:
        sys.exit("REFUSING: %s occurs %d times -- nothing written" % (label, n))

interp = interp.replace(OLD_VALIDATE, NEW_VALIDATE).replace(OLD_WORKER, NEW_WORKER)
io.open(T, "w", encoding="utf-8", newline="\n").write(interp)
print("ok   REMOVE_STATE takes a name or an attribute now")
