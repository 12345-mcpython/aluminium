"""Move the test classes into layered packages (2026-10-02). DRY by default; pass --apply.

Run AFTER rename_tests.py. Classification:
  support   the three shared helpers (TriggerSpecs, TestTurns, TestCharacters);
  data      loader / data-consistency / census tests;
  trigger   trigger-table, expression and condition tests;
  content   per-entity tests -- characters, lightcones, relics, memosprites, enemies -- where the entity kind comes from the rename map (tbgd-resolved) or from the class name;
  engine    everything else: battle flow, damage, stats, resources, buffs and states.

Each moved file gets its `package` line rewritten, plus an import for every test class it mentions that lives in another package (the helpers are the common case: TriggerSpecs is used by 161 files).
Refusals instead of guesses: it stops if a file is missing, if the target already exists, or if a class would land in two packages.
"""
import io
import os
import re
import subprocess
import sys

APPLY = "--apply" in sys.argv
ROOTPKG = "com.laosun.aluminium.test"

KEYWORDS = [
    ("data", r"(Data|JSON|Load|Registry|Sync|Census|Mapping|Convention|Rule|Every|MonsterData)"),
    ("trigger", r"(Trigger|Expression|Condition|Op[A-Z]|Table|SkillParam|SkillSlot|SkillCategory|SkillLevel|SkillAttribution|TargetSelector|TargetFilter|CastTarget|CastSkill|CastSetup|UltCast|BasicAttack|TalentTest)"),
    ("content.memosprite", r"(Memosprite|Servant|Summon)"),
    ("content.enemies", r"(Enemy|Monster|Elite)"),
]
KIND_PKG = {"character": "content.characters", "lightcone": "content.lightcones",
            "relic": "content.relics", "servant": "content.memosprites", "memosprite": "content.memosprites"}
HELPERS = {"TriggerSpecs", "TestTurns", "TestCharacters"}

# entity names from the game data, longest first, so `AglaeaMemospriteTest` cannot be mistaken for a shorter one
ENTITIES = []
for ln in io.open("tools/content_classes.tsv", encoding="utf-8").read().split("\n"):
    parts = ln.split("\t")
    if len(parts) == 2 and not ln.startswith("#"):
        ENTITIES.append((parts[1], parts[0]))
ENTITIES.sort(key=lambda kv: -len(kv[0]))

# kind per class, from the rename map (whose identities were resolved against tbgd)
kind = {}
renames = {}
for ln in io.open("tools/_rename_map2.txt", encoding="utf-8").read().split("\n"):
    m = re.match(r"^\s+(\S+)\s+([a-z,]+)\s+\S*\s*-> (\S+)$", ln)
    if m:
        k = m.group(2).split(",")[0]
        kind[m.group(3)] = k          # post-rename name
        renames[m.group(1)] = m.group(3)


def package_of(name):
    if name in HELPERS:
        return "support"
    # a test ABOUT a memosprite, an enemy or the trigger layer is more specific than the character it mentions
    for pkg, pat in (("content.memosprites", r"(Memosprite|Servant|Summon)"),
                     ("content.enemies", r"(Enemy|Monster|Elite)")):
        if re.search(pat, name):
            return pkg
    if name in kind:
        return KIND_PKG.get(kind[name], "content.characters")
    for entity, k in ENTITIES:                      # 84 characters, 170 light cones, 62 relic sets, 7 memosprites
        if entity in name:
            return KIND_PKG.get(k, "engine")
    for pkg, pat in KEYWORDS:
        if re.search(pat, name):
            return pkg
    return "engine"

tracked = subprocess.run(["git", "ls-files"], capture_output=True, text=True).stdout.split()
files = [f for f in tracked if f.startswith("src/test/java/") and f.endswith(".java")]


plan = {}
for f in files:
    name = os.path.basename(f)[:-5]
    plan[f] = package_of(name)

counts = {}
for f, p in plan.items():
    counts[p] = counts.get(p, 0) + 1
print("packages: %s" % ", ".join("%s=%d" % kv for kv in sorted(counts.items())))

targets = {}
problems = []
for f, pkg in plan.items():
    name = os.path.basename(f)
    dst = os.path.join("src", "test", "java", *ROOTPKG.split("."), *pkg.split("."), name)
    if os.path.exists(dst) and dst != f:
        problems.append("%s -> %s already exists" % (f, dst))
    targets.setdefault(dst, []).append(f)
for dst, srcs in targets.items():
    if len(srcs) > 1:
        problems.append("%s claimed by %s" % (dst, ", ".join(srcs)))
if problems:
    print("REFUSING (%d):" % len(problems))
    for p in problems[:15]:
        print("  " + p)
    sys.exit(1)

if not APPLY:
    for f, pkg in list(plan.items())[:10]:
        print("  %s -> test.%s" % (f, pkg))
    print("  ... DRY RUN, nothing written")
    sys.exit(0)

# where each class ends up, so imports can be added
home = {}
for f, pkg in plan.items():
    home[os.path.basename(f)[:-5]] = pkg

for f, pkg in plan.items():
    dst = os.path.join("src", "test", "java", *ROOTPKG.split("."), *pkg.split("."), os.path.basename(f))
    if os.path.dirname(dst) != os.path.dirname(f):
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        subprocess.run(["git", "mv", f, dst], check=True)
    text = io.open(dst, encoding="utf-8").read()
    text = re.sub(r"^package\s+[\w.]+;", "package %s.%s;" % (ROOTPKG, pkg), text, count=1, flags=re.M)
    # imports for any test class mentioned that now lives elsewhere
    needed = []
    for name, other in home.items():
        if other == pkg or name == os.path.basename(dst)[:-5]:
            continue
        if re.search(r"\b" + re.escape(name) + r"\b", text):
            imp = "import %s.%s.%s;" % (ROOTPKG, other, name)
            if imp not in text:
                needed.append(imp)
    if needed:
        m = re.search(r"^package\s+[\w.]+;\s*$", text, flags=re.M)
        text = text[:m.end()] + "\n\n" + "\n".join(sorted(needed)) + text[m.end():]
    io.open(dst, "w", encoding="utf-8", newline="").write(text)

print("moved %d files into %d packages" % (len(plan), len(counts)))
