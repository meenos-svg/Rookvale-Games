# Notes for Claude

- The owner works from a phone. Keep instructions short and phone-friendly; no steps that need a computer.
- Merge automatically: after opening a PR, wait for the unit-test check to pass, then merge it into `main`
  yourself (REST: `gh api -X PUT repos/meenos-svg/rookvale-games/pulls/N/merge`). Don't ask first.
  If tests fail, fix them before merging. Re-run once if the failure is a Maven/network blip (e.g. HTTP 429).
- Every push to `main` builds and publishes `Rookvale-latest.apk` on the `latest` release.
- Changes under `supabase/` must also be run by the owner in the Supabase SQL Editor; tell them when that's needed.
- Never commit secrets (.env, keystores, passwords).
