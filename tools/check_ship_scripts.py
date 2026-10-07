"""Parse-check the shipping scripts, and flag any JSON-literal line that carries an inner straight quote.

The trap this guards: a Chinese note written with a straight `"` inside a `"..."` Python literal is a syntax error, and
one already got through once in this session. Chinese quotes 「」 are the project's convention and never collide.
"""
import ast
import pathlib
import sys

TARGETS = [
    "tools/ship_pearl_charge_counter.py",
    "tools/ship_pearl_technique_archetype.py",
    "tools/sweep_rule_coverage.py",
    "tools/sweep_worktrees.py",
]

problems = 0
for name in TARGETS:
    path = pathlib.Path(name)
    if not path.exists():
        print(f"MISSING {name}")
        problems += 1
        continue
    text = path.read_text(encoding="utf-8")
    try:
        ast.parse(text)
        verdict = "parses OK"
    except SyntaxError as error:
        verdict = f"SYNTAX ERROR line {error.lineno}: {error.msg}"
        problems += 1
    print(f"{verdict:52s} {name}")

    # An inner straight quote: more than four on a quoted-key line is the tell.
    for number, line in enumerate(text.splitlines(), 1):
        stripped = line.strip()
        if stripped.startswith(('"note"', '"source"')) and stripped.count('"') > 4:
            print(f"    SUSPECT inner quote at line {number}: {stripped[:100]}")
            problems += 1

print("problems:", problems)
sys.exit(1 if problems else 0)
