import io, sys
P = "src/main/java/com/laosun/aluminium/beans/EffectSpec.java"
txt = io.open(P, encoding="utf-8").read()
anchor = "        copy.damageParam = this.damageParam;"
if txt.count(anchor) != 1:
    sys.exit("REFUSING: the copy anchor occurs %d times" % txt.count(anchor))
le = txt.index("\n", txt.index(anchor)) + 1
txt = txt[:le] + "        copy.percentFromCastParam = this.percentFromCastParam;\n" + txt[le:]
io.open(P, "w", encoding="utf-8", newline="\n").write(txt)
print("ok   copy() carries the new field")
