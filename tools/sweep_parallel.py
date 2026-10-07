"""Run sweep_rule_coverage.py across several processes, one shard of characters each.

Why sharded by CHARACTER rather than by rule: the sweep EDITS a character file to remove a rule and restores it
afterwards, so two workers must never touch the same file. Sharding by character gives each worker its own files and
nothing to coordinate beyond the shared results file (which the sweep rewrites atomically enough for this purpose, and
which is only read at the end).

Each worker still runs the FULL suite for every removal - that is what makes a cross-character guard visible - so the
speedup is linear in the number of workers and bounded by the machine.

Usage:
    python tools/sweep_parallel.py [workers]        # default 4
"""
import json
import pathlib
import subprocess
import sys
import time

CHARS = pathlib.Path("src/main/resources/characters")
RESULTS = pathlib.Path("tools/_rule_coverage.json")


def main():
    workers = int(sys.argv[1]) if len(sys.argv) > 1 else 4
    done = json.loads(RESULTS.read_text(encoding="utf-8")) if RESULTS.exists() else {}

    cids = []
    for path in sorted(CHARS.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        rules = data["rules"] if isinstance(data, dict) else data
        pending = 0
        for index, rule in enumerate(rules):
            has_id = isinstance(rule, dict) and rule.get("id")
            on = rule.get("on") if isinstance(rule, dict) else "?"
            label = rule["id"] if has_id else "#%d:%s" % (index, on)
            if "%s:%s" % (path.stem, label) not in done:
                pending += 1
        if pending:
            cids.append(path.stem)

    print(f"pending characters: {len(cids)} ; workers: {workers}")
    # Longest-first is not known in advance; interleaving keeps the shards roughly even in count.
    shards = [cids[i::workers] for i in range(workers)]
    for index, shard in enumerate(shards):
        print(f"  worker {index}: {len(shard)} character(s)")

    processes = []
    for index, shard in enumerate(shards):
        if not shard:
            continue
        command = [sys.executable, "tools/sweep_rule_coverage.py"] + shard
        log = open(f"tools/_sweep_worker_{index}.log", "w", encoding="utf-8")
        processes.append((subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, cwd="."), log))

    started = time.time()
    for process, log in processes:
        process.wait()
        log.close()
    print(f"all workers finished in {time.time() - started:.0f}s")


if __name__ == "__main__":
    main()
