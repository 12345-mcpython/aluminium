# tools/ —— 这里的脚本是什么，以及**该跑哪一个**

> ⚠ 先读这一页，再决定跑什么。这个目录里 **101 个跟踪文件里只有约 10 个是"日常会再跑"的工具** ✓，
> 其余是**历史证据**（被 `GAPS.md`／`GAPS_LOG.md`／`engine.md` **按文件名引用** ✓）。

## 0. 两条规则（本目录存在的原因）

1. ⭐ **被文档按名字引用的脚本 = 证据，不是垃圾** ✓。文档里写「`tools/mut_steps.py` 变异 2/2 全红」✓、
   「`tools/probe_1506_roll.py` 导出了权重表」✓ —— 那只脚本就是那句话的**可复现依据** ✓。⇒ **别删** ✓。
2. ⚠ **"这个文件被引用了吗"这个问题，只允许一份扫描器** ✓。用两种写法各查一遍、结果不一致，
   就会删掉一条**被当作证据引用**的脚本（本目录真的发生过 ✗，见提交 `6f51e770` 前后的记录 ✓）。

## 1. ⭐ 日常工具（真的会再跑的）

| 脚本 | 干什么 | 怎么用 / 看什么 |
|---|---|---|
| `sweep_worktrees.py` | **规则覆盖率扫描**：逐条删掉一条规则 → 跑全量 → 看是否变红；6 个 `git worktree` 真并行 | ≈ **32 分钟** ✓；输出"哪些规则**没有判据把守**"✓。⭐ 单进程要 ~3 小时 ✓，别用串行 |
| `sweep_rule_coverage.py`／`sweep_readers.py`／`sweep_tbgd.py`／`sweep_parallel.py`／`sweep_progress.py` | 同族的扫描／进度／阅读者普查 | 同上；`sweep_readers.py` 用来**数某个机制的读者有几处** ✓ |
| `rank_characters_by_page.py` | **下一个做谁**：按"语料页 KB ÷ 规则数"排序 | 取**最低**者 ✓ —— 1506 就是这么找出来的（全库倒数第一 ✓）|
| `rank_characters.py` | 同上，另一种口径 | 交叉验证 ✓ |
| `cone_registry.py` | 光锥**登记表**：哪些光锥还没写、为什么 | 出货一个就少一个 ✓ |
| `gate_after_ship.py` | **出货闸门**：全量测试 ＋ 两个 demo | 基线 **302 / 45** ✓（行数）|
| `check_ship_scripts.py` | 检查"落地脚本与文档"是否一致 | 出货前跑 ✓ |
| `audit_content_lines.py`／`audit_equivalent_to_current.py`／`audit_note_contradictions.py`／`audit_op_vocabulary.py` | 内容自检：行数、等价性、注记自相矛盾、op 词表 | 改过一批内容之后跑 ✓ |
| `verify_comments.py`／`name_modifiers.py`／`control.py` | 注释与命名的一致性 | 同上 ✓ |
| `measure.py`／`show_failures.py`／`show_failure_messages.py` | 看数字／看失败 | ⚠ 失败信息**优先从 `build/test-results/test/*.xml` 读** ✓（`-i` 管道会因 broken pipe 返回假的 exit 1 ✗）|
| `decode_1506_expr.py` ＋ `probe_1506_hashes.py` | ⭐ **能力脚本解码器**：把上游栈式 VM（`GetFixed/GetGlobal/Add/Mul/…`）反成公式；哈希 → 语义 | **可复用资产** ✓ —— 别的角色的技能脚本同样要解 ✓（加权三选一的权重就是这么来的 ✓）|
| `package_tests.py`／`rename_tests.py` | 测试分组／改名 | 结构性改动时 ✓ |
| `build_glossary.py` | 从上游构建术语表 | 术语疑问时 ✓ |
| `breadth_chars_relics.py`／`breadth_cones.py` | 广度普查（角色/遗器/光锥的覆盖）| 决定下一批做什么 ✓ |
| `mutate.py`／`mut_*.py` | ⚠ **变异驱动**（历史断言靠它们）| ⚠ **已由 pitest 接替**（`build.gradle` 里 `info.solidsoft.pitest` ✓，增量分析已开 ✓）⇒ 新工作用 pitest ✓，这些退居**证据** ✓ |
| `content_classes.tsv`／`content_names.tsv` | 表格 | 生成物，一般只读 ✓ |
| `gaps_section4.md` | ⚠⚠ **已作废的 1415 受阻快照**（2026-10-05）—— 其中 4 条"受阻"结论**都已出货**；文件头已写清作废原因。**待办读 `GAPS.md`，不要读它** ✓ |

## 2. 历史证据（**别删、也别当工具用**）

`ship_*`／`add_*`／`patch_*`／`fix_*`／`land_*`／`sync_expression_*`／`wire_*`／`probe_*`／`diag_*`／`import_*`／`drop_*`／
`withdraw_*`／`rollback_*`／`restore_*` 等 ✓ —— 每个负责**把某一句话写进某个 JSON**（或引擎 ✓），**只跑过一次** ✓。

它们的价值是：**`GAPS.md` 说"这句话是这么落地的"时，那只脚本能重放当时那次编辑** ✓ —— 比散文精确 ✓。

## 3. 临时产物（`_*`，已被 `.gitignore` 忽略）

`_1506_page.md`（语料页快照 ✓）、`_1506_roll_template.json`（导出的脚本片段 ✓）、`_sweep/`（扫描前的备份 ✓）、
`__pycache__/` ✓ —— **可以随时删** ✓，不影响仓库 ✓。

## 4. ⚠⚠ 命令级纪律（都是踩过的坑）

1. ⚠⚠ **先提交，再变异** —— 变异脚本的 `restore` 走 `git checkout` ✓，它会**连带退回你尚未提交的修复** ✗
   （症状：全量变红、而内容"看起来没动过"✗。本会话踩过两次 ✗）。
2. ⚠ **变异必须能编译** —— 编译失败的红是**假红** ✗（什么都没证明 ✓）。
3. ⚠ **不要在 PowerShell 里用内联 `python -c`** —— 引号会被吃掉 ✗ ⇒ "变异"根本没生效 ✓ 而结果看起来是绿的 ✗。
   一律写**脚本文件** ✓，或用 PowerShell **here-string** ✓。
4. ⚠ **`>` 重定向在 Windows PowerShell 下写 UTF-16** ✗（带 null 字节 ⇒ `py_compile` 直接拒绝 ✓）。
   要恢复文件一律 **`git checkout <commit> -- <path>`** ✓ —— 按 blob 原样写 ✓。
5. ⚠ **手发 `TURN_START`／`TURN_END` 不驱动 buff 时钟** ✗ —— 过真回合要走队列 ✓（写"行为判据"前先测这一点 ✓）。
6. ⚠ **星魂门是规则上的字段**（`minEidolon` ✓），**不是装载期过滤** ✗ —— 别用"E0 查表查不到"来断言 ✓。
7. ⚠ 删／改脚本前**先扫引用** ✓，且**只用一个扫描器** ✓（见 §0 第 2 条 ✓）。
