# aluminium

a game like HSR written by java

## 先读 `AGENT.md`

**[`AGENT.md`](AGENT.md) 是这个仓库唯一的台账**（2026-10-16 由 `ENGINE.md` / `ROADMAP.md` /
`AGENT.md` / `CONDUCT.md` 合并而成，理由见它 §2）。

| 你是 | 读哪节 |
|---|---|
| 改引擎的 | `AGENT.md` §三 —— 红线、三层结构、**四道验证闸门** |
| 做项目 / 写内容的 | `AGENT.md` §四 —— 阶段、待办、为什么这样排 |
| 收尾 / 交接的 | `AGENT.md` §五 —— 提交纪律、要说清"没做什么" |
| 谁都该读 | `AGENT.md` §一（怎么做事）· §二（台账自己的规矩） |

触发器 JSON 格式在 [`TRIGGER.md`](TRIGGER.md)。游戏侧公式在 `E:\code\blog\hsr\HSR.md`（仓库外）。
**"现在是什么"的权威是代码**，不是文档。

## generator

You can use [this](https://gist.github.com/12345-mcpython/7a4032165da00a74dbc7bccdbe3d9500#file-generate_data-py) Python script and [turnbasedgamedata](https://gitlab.com/Dimbreath/turnbasedgamedata) to generate data.

The generated files must be placed under **`src/main/resources/game/data/`** (that directory is the
target of the `/game/data/` classpath prefix that `JSONReader` reads from). Placing them in
`src/main/resources/` itself will not work.

From the repository root (the script writes into `src/main/resources/game/data/`):

```
python /path/to/generate_data.py
```