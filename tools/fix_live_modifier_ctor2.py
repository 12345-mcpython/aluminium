"""The generated constructor was public (StatModifierBuff calls it), so the explicit one has to be too."""
import io

PATH = "src/main/java/com/laosun/aluminium/models/DoubleValue.java"
text = io.open(PATH, encoding="utf-8").read()
OLD = "        private Modifier(ModifierType modifierType, double value, ModifierSource source, int sourceRoleId) {"
NEW = "        public Modifier(ModifierType modifierType, double value, ModifierSource source, int sourceRoleId) {"
if text.count(OLD) != 1:
    raise SystemExit("REFUSING: the anchor appears %d times" % text.count(OLD))
io.open(PATH, "w", encoding="utf-8", newline="\n").write(text.replace(OLD, NEW, 1))
print("ok   the constructor is public, like the generated one was")
