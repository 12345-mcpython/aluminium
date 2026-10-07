"""Current-project figures, measured rather than recalled.

Counts the corpus pages, the shipped character files, the rules, the wired operation vocabulary and the judge files, so
"how is it going" is answered from the tree in front of us.
"""
import pathlib
import re

CORPUS = pathlib.Path(r"E:\code\blog\hsr")
CHARS = pathlib.Path("src/main/resources/characters")
INTERPRETER = pathlib.Path("src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java")
TESTS = pathlib.Path("src/test/java/com/laosun/aluminium/test")


def main():
    pages = {p.stem.split("_")[0] for p in CORPUS.glob("*.md") if re.match(r"^\d{4}_", p.name)}
    files = {p.stem for p in CHARS.glob("*.json")}
    print(f"corpus pages           : {len(pages)}")
    print(f"shipped character files: {len(files)}")
    print(f"not shipped            : {sorted(pages - files)}")
    print(f"shipped but no page    : {sorted(files - pages)}")

    total_rules = 0
    for path in CHARS.glob("*.json"):
        import json
        data = json.loads(path.read_text(encoding="utf-8"))
        rules = data["rules"] if isinstance(data, dict) else data
        total_rules += len(rules)
    print(f"rules across characters: {total_rules}")

    text = INTERPRETER.read_text(encoding="utf-8")
    wired = re.search(r"private static final Set<String> WIRED = Set\.of\((.*?)\);", text, re.S)
    print(f"wired ops              : {len(re.findall(chr(34) + r'[A-Z_]+' + chr(34), wired.group(1)))}")

    for folder, label in (("engine", "engine judges"), ("content", "content judges")):
        count = len(list((TESTS / folder).rglob("*.java")))
        print(f"{label:22s}: {count}")
    print(f"judge files total      : {len(list(TESTS.rglob('*.java')))}")


if __name__ == "__main__":
    main()
