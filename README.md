# tc-agent

Point it at screenshots / PDFs / notes, it returns a full test suite:
priorities, types, edge cases, preconditions, numbered steps, expected
results — in your naming convention, plus a `testcases.json` that drops
straight into the n8n → GitHub Projects 24 automation. GUI or CLI, same
engine, same output.

Zero dependencies. JDK 17+ and nothing else (Swing ships with the JDK).

Works with six providers — paste in any key and it just works: the provider
is guessed from the key's shape.

| provider | key looks like | notes |
|---|---|---|
| Anthropic | `sk-ant-...` | native image + PDF support |
| Gemini | `AIza...` | native image + PDF support |
| OpenRouter | `sk-or-...` | one gateway to hundreds of models, e.g. `deepseek/deepseek-v4-pro`, `google/gemini-3.8-flash` |
| xAI (Grok) | `xai-...` | image only |
| OpenAI | `sk-...` (default guess) | native image + PDF support |
| DeepSeek | `sk-...` (looks identical to OpenAI) | image only — pass `--provider deepseek` (CLI) or pick it in the GUI's Provider dropdown, since the key alone can't tell it apart from OpenAI |

Override the guess any time with `--provider anthropic|openai|gemini|deepseek|xai|openrouter`.

Each provider's key is saved to its own file under `~/.tc-agent/keys/` (e.g.
`gemini.key`, `deepseek.key`) - switching the GUI's Provider dropdown loads the
matching one automatically, so keys never overwrite each other.

## Build

```bash
export ANTHROPIC_API_KEY=sk-ant-...      # or OPENAI_ / GEMINI_ / DEEPSEEK_ / XAI_ / OPENROUTER_API_KEY
./build.sh                                # or: mvn -q package  (build.bat on Windows)
```

## Run — GUI

```bash
java -jar tc-agent.jar          # no arguments opens the GUI
```

On Windows, double-click **`TC-Agent.bat`** instead — no console window.

Drag screenshots, PDFs and notes onto the file list (or use Browse — it takes
files and whole folders), fill in **Module** and any known rules in **Notes**,
click **Generate**. It then asks for a name for this run's output folder -
that's created under **Output folder** (Advanced options, default `out`), so
each run gets its own subfolder instead of overwriting the last one. The log
pane streams progress; when it finishes, **Open output folder** jumps straight
to that new subfolder's `testcases.json` / `.md` / `.csv`.

The API key field is pre-filled from any of the `*_API_KEY` env vars above if
one is set; otherwise paste any provider's key in — a small label shows which
one it detected. If it guesses wrong (only possible for OpenAI vs. DeepSeek),
set it explicitly in the **Provider** dropdown. Each provider's key is saved
locally to its own file under `~/.tc-agent/keys/` (plaintext, never sent
anywhere but that provider's API), so switching providers swaps in the right
key automatically and none of them overwrite each other.

The rest of the session (dropped files, Module, Notes, and every Advanced
option except the key) is likewise saved to `~/.tc-agent/session.json` and
restored the next time you open the app - close it mid-work and reopening
picks up right where you left off, instead of a blank form.

## Run — CLI

```bash
java -jar tc-agent.jar --in ./inputs --module Categories --start 1 --out ./out
```

Put everything you have into `./inputs`: screenshots (`.png .jpg .webp .gif`),
BRDs (`.pdf`), and notes (`.md .txt .csv .json .html`). File names matter — the
agent cites them in the `evidence` field of each case, so name them
`categories-list.png`, `add-category-modal.png`, etc.

### Options

| flag | meaning |
|---|---|
| `--module Forms` | title prefix, e.g. `Forms \| 16- ... ` |
| `--code FF` | internal ID prefix (`FF-001`); derived from module if omitted |
| `--start 64` | continue numbering from an existing suite |
| `--count 40` | target size (it won't pad) |
| `--notes "..."` / `--notes-file brd.md` | known rules, TBDs already answered, what to skip |
| `--project 24` | project number written into `testcases.json` |
| `--provider gemini` | force a backend instead of guessing it from the key (anthropic\|openai\|gemini\|deepseek\|openrouter) |
| `--model claude-sonnet-5` | or `claude-opus-5-5` / `gpt-5.5` / `gemini-3.8-flash` / `deepseek-v4-pro` / `grok-4.7` / `anthropic/claude-sonnet-5` (OpenRouter's `author/slug` form), etc. |
| `--max-tokens 16000` | raise for 80+ case suites |
| `--dry-run` | builds the request, writes `request-preview.json`, calls nothing |
| `--debug` | also writes `raw-response.json` |

## Outputs (`--out`)

- **`testcases.json`** — the n8n contract: `projectNumber` + `testCases[]` with
  `id, title, module, type, priority, preconditions, steps[], expected`. Drop it
  in the watched Drive/local folder and the board fills itself.
- **`testcases.md`** — review copy, grouped by section, TBDs flagged.
- **`testcases.csv`** — UTF-8 BOM, opens straight in Excel / imports to Jira.

## What it enforces

Numbering and titles are composed **in Java, not by the model** — the model only
returns the title text, so `Module | N- Title (Type)(Priority)` is always right
and numbering is continuous across sections within one prefix.

The model is held to: Positive / Negative / Edge case / Validation, P1 / P2 / P3,
one assertion per case, no `OR`-style expectations, and `TBD - confirm with dev
whether ...` instead of inventing undefined behaviour. The coverage checklist it
works through includes bilingual AR/EN asymmetry, RTL, boundaries, paste-bypass,
unsaved changes, concurrency, cascade-on-delete, and permission enforcement at
the route and API level rather than just a hidden button.

## Typical loop

```bash
# first pass
java -jar tc-agent.jar --in ./inputs/categories --module Categories --count 35 --out ./out/cat
# read out/cat/testcases.md, fix the TBDs with dev, then re-run with the answers
java -jar tc-agent.jar --in ./inputs/categories --module Categories --start 1 \
     --notes "Duplicate names ARE rejected. In-use categories cannot be deleted." \
     --out ./out/cat
# ship to the board
cp out/cat/testcases.json ~/n8n-watched/
```

## Notes

- One API call per run. Big screenshot folders are fine; ~20 images per run keeps
  latency and cost sane — split by sub-module if you have more.
- If a run hits the output token limit the tool warns you and the suite is
  truncated: lower `--count` or raise `--max-tokens`.
- PDFs are only sent as-is to Anthropic, Gemini, and OpenAI; DeepSeek, xAI, and
  OpenRouter get the screenshots and text notes but not the raw PDF bytes (their
  chat-completions endpoints have no file-input type) - the "DOCUMENT: name"
  label still reaches the model either way.
- Nothing is uploaded anywhere except whichever single provider's API you picked.
