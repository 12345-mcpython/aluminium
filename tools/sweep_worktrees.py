"""Sweep rule coverage in PARALLEL, using one git worktree per worker.

Why worktrees rather than threads: every removal EDITS a character file and runs `gradlew test`, and Gradle serialises
builds that share a build directory behind a file lock - so N processes in the same checkout take as long as one. A
worktree per worker gives each its own `build/`, and the shards can then really run at once.

Each worker:
  * checks out a detached worktree under tools/_sweep/wN,
  * is handed a slice of the pending rules as `cid:label` keys,
  * for each, rewrites that character's file with the rule removed, runs the full suite, and records the verdict,
  * reports one line per rule to tools/_sweep_worker_N.log and the verdict to tools/_rule_coverage_partN.json,
  * removes its worktree at the end.

Usage:
    python tools/sweep_worktrees.py [workers]     # default 6
    python tools/sweep_worktrees.py report        # merge the parts into the report
"""
import json
import pathlib
import shutil
import subprocess
import sys
import time

ROOT = pathlib.Path(".").resolve()
CHARS = ROOT / "src/main/resources/characters"
SWEEP = ROOT / "tools/_sweep"
RESULTS = ROOT / "tools/_rule_coverage.json"


def pending_keys():
    done = json.loads(RESULTS.read_text(encoding="utf-8")) if RESULTS.exists() else {}
    keys = []
    for path in sorted(CHARS.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        rules = data["rules"] if isinstance(data, dict) else data
        for index, rule in enumerate(rules):
            label = rule["id"] if isinstance(rule, dict) and rule.get("id") else "#%d:%s" % (
                index, rule.get("on") if isinstance(rule, dict) else "?")
            key = "%s:%s" % (path.stem, label)
            if key not in done:
                keys.append(key)
    return keys


def worker_script():
    """The code each worker runs, written into its own worktree so it edits ITS copy of the files."""
    return '''import json, pathlib, subprocess, sys, time

keys = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
out = pathlib.Path(sys.argv[2])
chars = pathlib.Path("src/main/resources/characters")

def rules(data):
    return data["rules"] if isinstance(data, dict) else data

def label_of(rule, index):
    return rule["id"] if isinstance(rule, dict) and rule.get("id") else "#%d:%s" % (
        index, rule.get("on") if isinstance(rule, dict) else "?")

verdicts = {}
started = time.time()
for position, key in enumerate(keys, start=1):
    cid, _, wanted = key.partition(":")
    path = chars / (cid + ".json")
    original = path.read_text(encoding="utf-8")
    data = json.loads(original)
    body = rules(data)
    keep = [r for i, r in enumerate(body) if label_of(r, i) != wanted]
    if len(keep) == len(body):
        verdicts[key] = None
        print("[%d/%d] SKIP %s" % (position, len(keys), key), flush=True)
        continue
    if isinstance(data, dict):
        data["rules"] = keep
    else:
        data = keep
    try:
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\\n", encoding="utf-8")
        red = subprocess.run([".\\\\gradlew.bat", "test", "--console=plain", "-q"],
                             capture_output=True, text=True).returncode != 0
    finally:
        path.write_text(original, encoding="utf-8")
    verdicts[key] = red
    out.write_text(json.dumps(verdicts, ensure_ascii=False), encoding="utf-8")
    print("[%d/%d] %s %s (%.0fs)" % (position, len(keys), "GUARDED  " if red else "UNGUARDED",
                                     key, time.time() - started), flush=True)
'''


def run(workers):
    SWEEP.mkdir(parents=True, exist_ok=True)
    # A safety net, because the risk is real: each worker writes a rule OUT of a character file and restores it after the
    # suite, and a worker killed inside that window leaves the file short a rule - with the suite still GREEN, because
    # the rule that vanished is exactly one that nothing was guarding. Measured, not imagined: the first serial attempt
    # was cancelled and left 1005.json with 17 lines fewer. So every file is copied first, and compared afterwards.
    backup = SWEEP / "backup"
    if backup.exists():
        shutil.rmtree(backup)
    backup.mkdir(parents=True)
    for path in sorted(CHARS.glob("*.json")):
        shutil.copyfile(path, backup / path.name)

    keys = pending_keys()
    print("pending rules: %d ; workers: %d" % (len(keys), workers))
    if not keys:
        return 0
    shards = [keys[i::workers] for i in range(workers)]

    processes = []
    for index, shard in enumerate(shards):
        if not shard:
            continue
        tree = SWEEP / ("w%d" % index)
        if tree.exists():
            subprocess.run(["git", "worktree", "remove", "--force", str(tree)], cwd=ROOT,
                           capture_output=True, text=True)
        result = subprocess.run(["git", "worktree", "add", "--detach", str(tree), "HEAD"],
                                cwd=ROOT, capture_output=True, text=True)
        if result.returncode != 0:
            print("worktree %d failed: %s" % (index, result.stderr.strip()))
            continue
        (tree / "tools").mkdir(exist_ok=True)
        (tree / "tools/_keys.json").write_text(json.dumps(shard, ensure_ascii=False), encoding="utf-8")
        (tree / "tools/_worker.py").write_text(worker_script(), encoding="utf-8")
        log = open(ROOT / ("tools/_sweep_worker_%d.log" % index), "w", encoding="utf-8")
        process = subprocess.Popen([sys.executable, "tools/_worker.py", "tools/_keys.json",
                                    "tools/_part.json"], cwd=tree, stdout=log, stderr=subprocess.STDOUT)
        processes.append((index, tree, log, process))
        print("  worker %d: %d rule(s) in %s" % (index, len(shard), tree.name))

    started = time.time()
    for index, tree, log, process in processes:
        process.wait()
        log.close()
        shutil.copyfile(tree / "tools/_part.json", ROOT / ("tools/_rule_coverage_part%d.json" % index))
        print("  worker %d finished" % index)
    print("all workers done in %.0fs" % (time.time() - started))
    verify_restored(backup)
    return 0


def verify_restored(backup):
    """Restores any file a worker left short, and says so.

    <p>Note: A file left short is INVISIBLE to the suite - the missing rule is one nothing guards, which is the whole
    reason it was being swept - so this is the only check that would catch it.
    """
    restored = []
    for path in sorted(CHARS.glob("*.json")):
        saved = backup / path.name
        if not saved.exists():
            continue
        if path.read_bytes() != saved.read_bytes():
            shutil.copyfile(saved, path)
            restored.append(path.name)
    if restored:
        print("RESTORED from the backup (a worker left these short): " + ", ".join(restored))
    else:
        print("every character file matches the backup: nothing was left short")
    return restored


def report():
    merged = json.loads(RESULTS.read_text(encoding="utf-8")) if RESULTS.exists() else {}
    for part in sorted(ROOT.glob("tools/_rule_coverage_part*.json")):
        merged.update({k: v for k, v in json.loads(part.read_text(encoding="utf-8")).items() if v is not None})
    RESULTS.write_text(json.dumps(merged, ensure_ascii=False, indent=1), encoding="utf-8")

    by_character = {}
    for key, guarded in merged.items():
        cid, _, rule = key.partition(":")
        by_character.setdefault(cid, []).append((rule, guarded))
    lines = []
    unguarded = 0
    for cid in sorted(by_character):
        bad = [rule for rule, guarded in by_character[cid] if not guarded]
        unguarded += len(bad)
        if bad:
            lines.append("%s: %d of %d unguarded" % (cid, len(bad), len(by_character[cid])))
            for rule in bad:
                lines.append("    UNGUARDED " + rule)
    lines.append("")
    lines.append("checked %d ; unguarded %d" % (len(merged), unguarded))
    (ROOT / "tools/_rule_coverage_report.txt").write_text("\\n".join(lines), encoding="utf-8")
    print("\\n".join(lines))
    return 0


def main():
    if len(sys.argv) > 1 and sys.argv[1] == "report":
        return report()
    return run(int(sys.argv[1]) if len(sys.argv) > 1 else 6)


if __name__ == "__main__":
    sys.exit(main())
