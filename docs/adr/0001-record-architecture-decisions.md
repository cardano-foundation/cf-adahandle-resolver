# 1. Record architecture decisions

- Status: Accepted
- Date: 2026-11-03

## Context

The resolver's design decisions so far live in pull request descriptions and code comments: which tokens count as
handles, how rollbacks are repaired, why the database must be resynced for 1.0.0, how the native image is licensed.
That is hard to find, and easy to contradict by accident.

CIP-25, CIP-67 and CIP-68 define the tokens behind ADA Handles, but not which of them are handles,
whom a handle resolves to, or what a burn means. Those rules exist only in Kora Labs' implementation. We need a place
to write down the rules we follow and why.

## Decision

We keep Architecture Decision Records in `docs/adr/`, one Markdown file per decision, numbered in order:
`NNNN-short-title.md`, with a status, a date, the context, the decision, its consequences and references.

An accepted ADR is not edited to change its decision; a new ADR supersedes it and says so, and the old one is marked
`Superseded by NNNN`. Small corrections (links, typos) are fine.

[ADR 2](0002-ada-handle-resolution-rules.md) is the closest thing this project has to a specification of ADA Handles.

## Consequences

- A pull request that changes one of these decisions adds or supersedes an ADR.
- Reviewers can check a change against the recorded rules, and the regression tests against
  [Kora Labs' API](0002-ada-handle-resolution-rules.md) have a written reference.
