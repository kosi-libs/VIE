# VIE agent skill

An [Agent Skill](https://code.claude.com/docs/en/skills) that teaches a coding agent VIE's
conventions: what good VIE code looks like, how to spot bad VIE code on review, and how to
scaffold a new screen's contract, ViewModel and view. It is not specific to Claude Code — `SKILL.md`
is the open Agent Skill format, so any agent/tool that reads it can use it.

It is meant to be installed into *projects that consume* the `org.kodein.arch.vie:vie` artifact,
not into this repository.

## Install

From the consuming project's root:

```bash
mkdir -p .claude/skills
ln -s "/path/to/VIE/ai-skill" .claude/skills/vie
```

Or copy the `ai-skill/` folder directly if you'd rather not symlink across repositories.

## Layout

```
SKILL.md                      # entry point: API cheat-sheet, routes to the sections below
references/
  good-practices.md           # the conventions, with rationale
  bad-practices.md            # anti-pattern → smell → grep hint → why → fix
templates/
  XxxVIE.kt.template           # the screen's contract
  XxxVM.kt.template            # its ViewModel
  Xxx.kt.template              # its view (wrapper + VIEViewScope extension + preview)
```
