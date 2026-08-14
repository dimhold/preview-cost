# Repo notes

Notes for publishing this repository. Not part of the artifact.

## One-line description

A paired, forked, interleaved benchmark of two ways to build a live preview: transaction rollback lost in 100% of 4,500 pairs, and the penalty stayed near 6 to 7 ms regardless of how heavy the calculation got.

Short form for the GitHub "About" field (350 char limit):

> What a live preview costs if you build it on a transaction rollback. Java 25, no framework, 4,500 paired runs on Postgres. Rollback lost every pair, and the overhead is a constant rather than a share of the work.

## Suggested GitHub topics

- `benchmark`
- `postgresql`
- `jdbc`
- `java`
- `performance-testing`
- `transactions`

## Publishing checklist

- [ ] `docs/hero.png` renders on the repo landing page
- [ ] Social preview image set to `docs/hero.png` in repo settings
- [ ] Topics applied
- [ ] Description applied
- [ ] `build/` is gitignored and absent from the commit
- [ ] No remote or push happened before review
