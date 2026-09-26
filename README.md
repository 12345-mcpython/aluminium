# aluminium

a game like HSR written by java

## docs

| 文件 | 内容 |
|---|---|
| `engine.md` | **引擎机制说明**：从代码推导的当前行为（属性 / 伤害乘区 / 事件 / 行动条 / 回合流程 / 韧性击破 / 超击破 / 能量 / Buff / 面板构建 / 敌人 AI）。明确区分"已实现 / 未接线 / 占位 / 未实现" |
| `ROADMAP.md` | **开发路线图**：设计原则（引擎不认角色 / 三分法）+ 待办任务（每个自带验收标准）+ 踩坑记录 + **§12 遗留缺陷登记** |

> 2026-09-26 删掉了 `CODE_REVIEW.md` 与 `DOC_VS_CODE.md`（一次性的审查/不一致分析快照，已多处腐烂），
> 2026-09-27 又删掉了 v2 存档 `docs/archive/ROADMAP-v2-历史.md`（2446 行纯历史）。
> 前两者的**未修条目**全部并进了 `ROADMAP.md` §12；已修条目不保留。
> **三份文件的全文都在 git 历史里**（`git log --diff-filter=D --name-only`），需要查"当初为什么这么做"时去那里翻。
> 旧文档里"`ROADMAP` 第 N 行指向 v2"的行号说法随之彻底作废 —— 现在只用任务编号（`P3-4` 等）引用。

> 游戏机制的**权威公式**在外部文档 `HSR.md`（"崩坏：星穹铁道 战斗机制总结"）里，**它不在本仓库**。
> 遇到公式疑义时以它为准；`engine.md` 的 §18 记了它与本实现的差异。

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
`skill_effects.json` and `property_names.json`. The Java engine reads 12 of the generated files
(plus 2 hand-maintained ones); `engine.md` §14.1 registers which are loaded and which are
generated-but-unused.

> ⚠️ The gist linked above is **behind the local copy**: the `skill_effects.json` export
> (the per-skill scale/parameter table the engine reads through `SkillEffects`) exists only in the
> local `E:\code\python\generate_data.py` and has no published history, so a change to it leaves no
> trace anywhere. Push it back to the gist before editing it.

> **This step is required before anything can run.** Most game data is not committed:
> `src/main/resources/data/` is listed in `.gitignore`, and only two files there are tracked —
> the patch file `monster_attack_modify_ratio.json` and the hand-written `enemy_skills.json`.
> A fresh `git clone` therefore has none of the **28 generated data files**
> (`skills.json`, `monster_config.json`, `stage.json`, …), and **every test fails**.

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
