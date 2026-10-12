# aluminium

a game like HSR written by java

## docs

| 文件 | 放什么 |
|---|---|
| `engine.md` | **引擎机制说明**：从代码推导的当前行为（属性 / 伤害乘区 / 事件 / 行动条 / 回合流程 / 韧性击破 / 超击破 / 能量 / Buff / 面板构建 / 敌人 AI）。明确区分"已实现 / 未接线 / 占位 / 未实现" |
| `ROADMAP.md` | **开发路线图**：设计原则（引擎不认角色 / 三分法）+ 待办任务（每个自带验收标准）+ 踩坑记录 + **§12 遗留缺陷登记** |
| `HANDOFF.md` | **纪律手册 ＋ 当前实测状态** —— 动手前读它；历史不在这里 |
| `REFACTOR.md` | **结构重构**：已完成的 god-class 拆分、量刀口的工具、剩余刀口在哪 |
| `GAPS.md` | **未出货的登记**：每一条写着"缺什么 / 读者是谁 / 前置是什么" |
| `EXPRESSION.md` | **「完全表达」判定清单** —— ⚠ 被 `ExpressionChecklistTest` 机器校验，不能凭印象改 |
| `HSR.md` | 游戏机制的**权威公式**（正典副本在仓库外） |

> 2026-09-26 删掉了 `CODE_REVIEW.md` 与 `DOC_VS_CODE.md`（一次性的审查/不一致分析快照，已多处腐烂），
> 2026-09-27 又删掉了 v2 存档 `docs/archive/ROADMAP-v2-历史.md`（2446 行纯历史）。
> 前两者的**未修条目**全部并进了 `ROADMAP.md` §12；已修条目不保留。
>
> ⚠ **2026-10-12 又删掉两份**：`GAPS_LOG.md`（36361 行逐轮台账）与 `tools/gaps_section4.md`
> （已作废的 1415 受阻快照 —— 它说"受阻"的 4 条**都已出货**）。
> `GAPS.md` 也同时从 **4082 行**收敛成**只列未出货项**的登记册。
> ⇒ **需要查"当初为什么这么做"时去 git 历史里翻**：
> `git log --diff-filter=D --name-only` 找出删除提交，再 `git show <commit>:<path>` 按 blob 取回。
> ⚠ **不要用 `>` 重定向**（Windows PowerShell 下写 UTF-16）。
>
> ⚠ **本仓库没有 `HISTORY.md`，也没有 `HISTORICAL.md`** —— 两者都从未存在过。
> 需要历史时用 git 历史。引用任何文件前先 `Test-Path` 一下。

> 游戏机制的**权威公式**在 `HSR.md`（"崩坏：星穹铁道 战斗机制总结"）里，**正典位置在仓库外**（`E:\code\blog\hsr\HSR.md`）。
> 遇到公式疑义时以它为准；`engine.md` 的 §18 记了它与本实现的差异。
> ⚠ 仓库根目录另有一份**逐字节相同**的副本（`HSR.md`，SHA256 一致，2026-10-07 纳管）：它让规格在**会话内可读**（此前它在工作区未跟踪，
> 结果本会话要靠作者两次发图才拿到里面本来就写着的欢愉公式 ✗）。⚠ 两份**会漂移**：改动请以仓库外那份为准，并同步这一份。

## generator

You can use [this](https://gist.github.com/12345-mcpython/7a4032165da00a74dbc7bccdbe3d9500#file-generate_data-py) Python script and [turnbasedgamedata](https://gitlab.com/Dimbreath/turnbasedgamedata) to generate data.

The generated files must be placed under **`src/main/resources/data/`** (that directory is the
target of the `/data/` classpath prefix that `JSONReader` reads from). Placing them in
`src/main/resources/` itself will not work.

From either the repository root or `src/main/resources/`:

```
python /path/to/generate_data.py
```

The script looks for `data/data_path.txt` (which holds the `turnbasedgamedata` checkout path) in
this order — the current directory's `data/`, then the current directory's
`src/main/resources/data/`, then the script's own `data/` — **and writes its output into whichever
`data/` it found**. So running it from the repository root writes straight into
`src/main/resources/data/`, and running it from your data-tooling directory writes there. Only if
none of those exist will it prompt for the path (and then store it next to the script).

> ⚠️ The "current directory first" order matters: an earlier version of the script preferred the
> script's own directory, which meant regenerating from the repo root silently refreshed a *different*
> copy and left the committed-under-gitignore data stale.

The current script emits **28 files** — including `eidolons.json`, `relic_sets.json`, `growth.json`,
`materials.json`, `recommend.json`, `enhanced_skills.json`, `global_buffs.json`,
`skill_effects.json` and `property_names.json`.

The Java engine reads **17** of the files in `data/` — **14 generated**, the 2 hand-maintained ones
(`enemy_skills.json`, `monster_attack_modify_ratio.json`), and `dump_data.json` (read only by
`Benchmark`). `engine.md` §14.1 registers which are loaded and which are generated-but-unused; the
live count is reproducible with `python tools/audit_data_reads.py`, which also lists the 13 files
nothing reads.

> ⚠️ **These counts drift.** Both sentences above were wrong before 2026-10-12: the engine was said
> to read 12 (of 28) and only 2 files were said to be tracked. Re-measure with the commands named
> here rather than trusting a number in this file.

> ⚠️ The gist linked above is **behind the local copy**: the `skill_effects.json` export
> (the per-skill scale/parameter table the engine reads through `SkillEffects`) exists only in the
> local `E:\code\python\generate_data.py` and has no published history, so a change to it leaves no
> trace anywhere. Push it back to the gist before editing it.

> **This step is required before anything can run.** Most game data is not committed:
> `src/main/resources/data/` is listed in `.gitignore`, and only **four** files there are tracked —
> the patch file `monster_attack_modify_ratio.json`, the hand-written `enemy_skills.json`, and
> `skills.json` / `skill_effects.json`. A fresh `git clone` therefore has none of the
> **28 generated data files** (`monster_config.json`, `stage.json`, …), and **every test fails**.
> Check the current list with `git ls-files src/main/resources/data/`.

### What a missing data file looks like

If the data has not been generated, the failure is an `ExceptionInInitializerError` caused by an
`IllegalStateException` naming the missing file — `Constant` loads all data in its static
initializer, so the first test that touches it brings down the rest:

```
java.lang.ExceptionInInitializerError
  ... Caused by: java.lang.IllegalStateException: 缺少数据文件 /data/skills.json
      （应位于 src/main/resources/data/）...
```

Fix it by generating the data; it is not a code or environment problem.
