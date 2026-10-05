import io, sys
P = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
txt = io.open(P, encoding="utf-8").read()
old = """            return effect.getPercent() * subject.getAttribute(from).get()
                    + (effect.getAmount() == null ? 0 : effect.getAmount());"""
if txt.count(old) != 1:
    sys.exit("REFUSING: the actor_attr return occurs %d times" % txt.count(old))
new = """            // ⚠⚠ The share, not `percent` raw: this branch was written before `shareOf` existed, and left reading `percent`
            // directly -- so a rule whose share is `percent_from_cast_param` crashed on a null here. Same fix as its sibling below.
            return shareOf(effect, ctx) * subject.getAttribute(from).get()
                    + (effect.getAmount() == null ? 0 : effect.getAmount());"""
io.open(P, "w", encoding="utf-8", newline="\n").write(txt.replace(old, new))
print("ok   the actor_attr branch takes the share")
