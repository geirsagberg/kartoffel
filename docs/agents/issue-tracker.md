# Issue tracker: GitHub

Issues and specs for this repo live as GitHub issues. Use the `gh` CLI for all operations.

This repository is public. Do not include secrets, API keys, signing keys, private location data, or local configuration values in issues.

## Conventions

- **Create an issue**: `gh issue create --title "..." --body "..."`.
- **Read an issue**: `gh issue view <number> --comments`, also fetching labels as needed.
- **List issues**: `gh issue list --state open --json number,title,body,labels,comments` with appropriate label and state filters.
- **Comment on an issue**: `gh issue comment <number> --body "..."`.
- **Apply or remove labels**: `gh issue edit <number> --add-label "..."` / `--remove-label "..."`.
- **Close**: `gh issue close <number> --comment "..."`.

Infer the repository from `git remote -v`; `gh` does this automatically inside the clone.

## Pull requests as a triage surface

**PRs as a request surface: no.**

GitHub shares one number space across issues and PRs, so resolve ambiguous references before acting.

## When a skill says “publish to the issue tracker”

Create a GitHub issue.

## When a skill says “fetch the relevant ticket”

Run `gh issue view <number> --comments`.

## Wayfinding operations

Used by `/wayfinder`. The **map** is a single issue with **child** issues as tickets.

- **Map**: an issue labelled `wayfinder:map`, holding Destination, Notes, Decisions so far, Not yet specified, and Out of scope.
- **Child ticket**: an issue linked to the map as a GitHub sub-issue using `gh api`. If sub-issues are unavailable, add the child to a task list in the map and put `Part of #<map>` at the top of its body. Apply one `wayfinder:<type>` label.
- **Blocking**: use GitHub's native issue dependencies. Add an edge through `repos/<owner>/<repo>/issues/<child>/dependencies/blocked_by`, using the blocker's numeric database ID. If dependencies are unavailable, use a `Blocked by: #<n>` line in the child body.
- **Frontier query**: list the map's open children, then exclude tickets with open blockers or assignees. The first remaining ticket in map order wins.
- **Claim**: `gh issue edit <n> --add-assignee @me`; this is the session's first write.
- **Resolve**: post the answer as a comment, close the ticket, then append a linked gist to the map's Decisions-so-far.
