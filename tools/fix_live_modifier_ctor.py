"""Fix: `Modifier` is annotated `@AllArgsConstructor`, so the new `live` field changed the generated signature.

Replaced with an explicit private constructor over the original four fields, which is what every existing factory calls.
"""
import io
import sys

PATH = "src/main/java/com/laosun/aluminium/models/DoubleValue.java"
text = io.open(PATH, encoding="utf-8").read()


def patch(old, new, label, count=1):
    global text
    found = text.count(old)
    if found != count:
        sys.stderr.write("REFUSING %s: the anchor appears %d times\n" % (label, found))
        raise SystemExit(1)
    text = text.replace(old, new, count)
    print("ok   %s" % label)


patch("    @AllArgsConstructor\n    @Getter\n", "    @Getter\n", "the generated all-args constructor is gone")

patch(
    "    public static final class Modifier implements Cloneable {\n",
    "    public static final class Modifier implements Cloneable {\n\n"
    "        /**\n"
    "         * The four fields every factory fills. Written out rather than generated: the class also carries the optional\n"
    "         * {@code live} supplier (2026-10-02), and a generated all-args constructor would silently require it too.\n"
    "         */\n"
    "        private Modifier(ModifierType modifierType, double value, ModifierSource source, int sourceRoleId) {\n"
    "            this.modifierType = modifierType;\n"
    "            this.value = value;\n"
    "            this.source = source;\n"
    "            this.sourceRoleId = sourceRoleId;\n"
    "        }\n",
    "the explicit four-argument constructor",
)

io.open(PATH, "w", encoding="utf-8", newline="\n").write(text)
print("done")
