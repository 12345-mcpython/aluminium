# aluminium

a game like HSR written by java

## generator

You can use [this](https://gist.github.com/12345-mcpython/7a4032165da00a74dbc7bccdbe3d9500#file-generate_data-py) Python script and [turnbasedgamedata](https://gitlab.com/Dimbreath/turnbasedgamedata) to generate data.

The generated files must be placed under **`src/main/resources/game/data/`** (that directory is the
target of the `/game/data/` classpath prefix that `JSONReader` reads from). Placing them in
`src/main/resources/` itself will not work.

From the repository root (the script writes into `src/main/resources/game/data/`):

```
python /path/to/generate_data.py
```