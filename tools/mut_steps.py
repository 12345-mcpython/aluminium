import pathlib, subprocess, sys
SRC = pathlib.Path("src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java")
def swap(old, new):
    t = SRC.read_text(encoding="utf-8")
    if t.count(old) != 1:
        raise SystemExit("anchor hit %d" % t.count(old))
    SRC.write_text(t.replace(old, new), encoding="utf-8")
def main():
    if len(sys.argv) == 2 and sys.argv[1] == "restore":
        subprocess.run(["git", "checkout", "--", str(SRC)], check=True)
        print("restored with git checkout"); return 0
    v = sys.argv[1] if len(sys.argv) == 2 else ""
    if v == "round_instead_of_floor":
        swap("return shareOf(effect, ctx) * Math.floor(value / step)", "return shareOf(effect, ctx) * Math.round(value / step)")
    elif v == "no_positive_check":
        swap('if (!(step > 0)) {', 'if (false) {')
    else:
        print("unknown variant:", v); return 2
    print("applied", v); return 0
sys.exit(main())
