"""Two fixes to the ElationDamage branch, both from the compiler.

  1. `double totalDamage = 0;` must be declared BEFORE the branch -- the branch accumulates into it, and the first version
     left the declaration below (errors: cannot find symbol, twice);
  2. `data.getSkillType()` returns a STRING, not the enum (error: incomparable types String and SkillType), and the data's
     own spelling for these rows is `attack_type: "ElationDamage"`. The branch now accepts that spelling or the enum's
     name, so it cannot depend on which of the two the loader kept.
"""
import io

PATH = "src/main/java/com/laosun/aluminium/models/skill/SkillExecutor.java"

text = io.open(PATH, encoding="utf-8").read()

OLD_DECL = """        CanHit mainTarget = targets.getFirst();

        // ⭐ An ElationDamage row reads its params as [hits, per-hit share, final split share]"""
NEW_DECL = """        CanHit mainTarget = targets.getFirst();

        double totalDamage = 0;

        // ⭐ An ElationDamage row reads its params as [hits, per-hit share, final split share]"""
if text.count(OLD_DECL) != 1:
    raise SystemExit("REFUSING: the declaration anchor appears %d times" % text.count(OLD_DECL))
text = text.replace(OLD_DECL, NEW_DECL, 1)

OLD_TAIL = """            return;
        }

        double totalDamage = 0;
"""
NEW_TAIL = """            return;
        }
"""
if text.count(OLD_TAIL) != 1:
    raise SystemExit("REFUSING: the trailing declaration appears %d times" % text.count(OLD_TAIL))
text = text.replace(OLD_TAIL, NEW_TAIL, 1)

OLD_IF = "        if (data.getSkillType() == com.laosun.aluminium.enums.SkillType.ELATION_SKILL) {"
NEW_IF = ("        // ⚠ `getSkillType()` hands back the DATA's own spelling, which for these rows is `ElationDamage`; the enum's name is\n"
          "        // accepted too, so the branch does not depend on which of the two the loader kept (the first version compared a String\n"
          "        // to the enum and did not compile).\n"
          "        String elationKind = data.getSkillType();\n"
          "        if (elationKind != null && (\"ElationDamage\".equalsIgnoreCase(elationKind)\n"
          "                || \"ELATION_SKILL\".equalsIgnoreCase(elationKind))) {")
if text.count(OLD_IF) != 1:
    raise SystemExit("REFUSING: the type test appears %d times" % text.count(OLD_IF))
text = text.replace(OLD_IF, NEW_IF, 1)

io.open(PATH, "w", encoding="utf-8", newline="\n").write(text)
print("ok   declaration moved above the branch, and the type test matches the data's own spelling")
