"""The key set must follow GSON's real rule, not "annotated fields only" (round 1688).

The first version collected `@SerializedName` values only, and the suite immediately showed why that is wrong: shipped files use
`amountFromEvent` / `amountFromAttr`, which are FIELD names -- Gson uses those when there is no annotation. So the rule is:

    annotated field  -> the annotation's value is the JSON key (`max_stacks`, which is why `maxStacks` vanished)
    plain field      -> the field's own name is the JSON key (`amountFromEvent`)

Both are collected now. That also keeps last round's finding intact: `maxStacks` is still refused, because `maxStacks` IS
annotated and its key is `max_stacks`.
"""
import io
import sys

PATH = "src/main/java/com/laosun/aluminium/data/TriggerTables.java"
text = io.open(PATH, encoding="utf-8").read()
OLD = """            com.google.gson.annotations.SerializedName name =
                    field.getAnnotation(com.google.gson.annotations.SerializedName.class);
            if (name != null) {
                keys.add(name.value());
            }"""
NEW = """            com.google.gson.annotations.SerializedName name =
                    field.getAnnotation(com.google.gson.annotations.SerializedName.class);
            // ⭐ Gson's own rule (2026-10-02): an annotated field is keyed by the annotation, a plain one by the field's
            // name. Collecting only the annotated ones rejected `amountFromEvent` and `amountFromAttr` -- keys the shipped
            // files use and Gson maps -- which the suite showed at once.
            keys.add(name != null ? name.value() : field.getName());"""
if text.count(OLD) != 1:
    sys.exit("REFUSING: the anchor appears %d times" % text.count(OLD))
io.open(PATH, "w", encoding="utf-8", newline="\n").write(text.replace(OLD, NEW, 1))
print("ok   the key set follows Gson's rule for annotated and plain fields")
