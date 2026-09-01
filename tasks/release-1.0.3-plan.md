# Release 1.0.3 — execution plan

Two artifacts ship from one commit and one tag: the **preview launcher jar** and the
**IntelliJ plugin**. This plan is the order to do them in, why that order, and what has to
be true at each gate. Written 2026-09-01 against `master` at `e05fd87`.

The launcher half already has a full procedure in
[`doc/release-launcher-procedure.md`](../doc/release-launcher-procedure.md); the plugin half
is in [`README.md` § Release Process](../README.md). This plan does not restate them — it
sequences them, and records the three things that are true this time and are not in either
document.

---

## 1. What is actually being released

One tag, `v1.0.3`, feeds three consumers:

```
                    master @ e05fd87
                          │
              ┌───────────┴────────────┐
              │                        │
      tag v1.0.3 pushed          ./gradlew publishPlugin
              │                        │
   release-launcher.yml           zkidea-1.0.3.zip
              │                        │
   GitHub Release v1.0.3          JetBrains Marketplace
   ├─ zk-preview-launcher-1.0.3.jar    │
   └─ …jar.sha256                      └─ bundles lib/zk-preview-launcher.jar
              │                            (same bytes, built from the same commit)
              ▼
   zkoss-demo/agent-skill
   preview-zul.py pins VERSION + SHA256
```

**The plugin does not consume the GitHub Release.** `prepareSandbox` in `build.gradle`
copies `:zk-preview-launcher:jar`'s output into `<plugin>/lib/zk-preview-launcher.jar`, so
the plugin ZIP carries its own copy. Verified for this build — all three copies are
byte-identical:

| Copy | SHA-256 |
|---|---|
| `zk-preview-launcher/build/libs/zk-preview-launcher.jar` | `cdf469c9…71e5c5` |
| `zk-preview-launcher/build/release/zk-preview-launcher-1.0.3.jar` | `cdf469c9…71e5c5` |
| `zkidea-1.0.3.zip → zkidea/lib/zk-preview-launcher.jar` | `cdf469c9…71e5c5` |

and the bundled jar carries the #71 fix (`RenderResult.notServed` / `isNotServed` present
in the packaged class).

> That local digest is for **cross-checking these three copies against each other only**.
> It is not the value that goes into `preview-zul.py` — see step 5 and
> the launcher procedure's § 3.1: CI and macOS produce different bytes for the same
> commit, proven on 1.0.2.

### What is in it

| Issue | Change |
|---|---|
| [#70](https://github.com/zkoss/zkidea/issues/70) | The preview serves regular files from the webapp docroot (images, CSS, JS). |
| [#71](https://github.com/zkoss/zkidea/issues/71) | A `.zul` with no file behind it answers `404` with a diagnostic, not an empty `200`. |

Both already have `<change-notes>` entries under `1.0.3` in `plugin.xml`.

---

## 2. Pre-flight state, measured

| Check | Required | Actual |
|---|---|---|
| `build.gradle` version | `1.0.3` | ✅ `1.0.3` |
| `zk-preview-launcher/build.gradle` version | `1.0.3` | ✅ `1.0.3` |
| tag `v1.0.3` local | absent | ✅ absent |
| tag `v1.0.3` on origin | absent | ✅ absent |
| working tree | clean | ✅ clean (only untracked `.vscode/`, `doc/potential-ideas.md`) |
| `master` vs `origin/master` | — | ⚠️ ahead 6, **not pushed** |
| `git push` credential | push allowed | ✅ SSH dry-run accepted `c87ed05..e05fd87` |
| `gh` active account | push allowed | ❌ **`hawkhero` has `push: false`** (see § 3) |
| `intellijPublishToken` | set | ✅ present in `~/.gradle/gradle.properties` |
| `./gradlew build` | green | ✅ 341 launcher + 383 plugin tests, 0 failed |
| `./gradlew verifyPlugin` | green | ✅ |
| `./gradlew runPluginVerifier` | — | ⏭️ **waived for this release** (see § 3.2) |

---

## 3. Two things to settle before starting

### 3.1 Blocker: the wrong `gh` account is active

The launcher procedure's § 4 warns that the git identity and the `gh` identity are
not necessarily the same. On this machine, right now, they are not:

| Account | `zkoss/zkidea` permissions |
|---|---|
| `hawkhero` (**active**) | `pull` only — `push: false` |
| `hawkchen` | `push: true`, `triage: true` |

`git push` is fine (it goes over SSH as a separate credential — dry-run accepted). What
breaks is **`gh workflow run`**, which needs push. That is not a corner case: pushing the
tag did **not** trigger the workflow for 1.0.2, and the manual trigger was the path that
actually shipped it.

Fix before step 2, either:

```bash
gh auth switch --user hawkchen
```

or per-command:

```bash
GH_TOKEN=$(gh auth token -u hawkchen) gh workflow run …
```

### 3.2 Decision: `runPluginVerifier` is waived, JetBrains verifies instead

Decided 2026-09-01. The local verifier run against IC-2023.3 / IU-2025.3 / IU-2026.2 is
skipped for this release; the Marketplace's own compatibility check on the uploaded build
is the gate instead.

What changes: the Marketplace runs its verification **after** the upload, so an API problem
surfaces as a published version being flagged or held rather than as a local failure before
the tag exists. The plugin's own 383 tests and `verifyPlugin` still run in step 0, so this
waiver covers exactly one class of risk — API breakage on IDE builds newer than the 2023.3
SDK this compiles against — on a plugin that only uses long-stable XML/DOM/completion
extension points. Recovery, if the Marketplace does flag it, is to release 1.0.4; nothing
about the launcher release is affected.

---

## 4. Order, and why

**All local gates run before the tag.** Once `v1.0.3` is pushed and the launcher jar is
published, the tag cannot be re-cut — the workflow uploads with `--clobber`, which replaces
bytes at a URL the agent skill may already have pinned. The launcher procedure's § 3.2
calls re-running a released tag "a deliberate pin-breaker: bump the version instead."
So anything that could still fail must fail *before* the tag exists.

> **Note a conflict with the README.** `README.md` § 4 "Post-Release" says to create the git
> tag *after* `publishPlugin`. That ordering predates the launcher: the workflow builds the
> jar *from the tag*, so tagging last would publish the plugin before the launcher jar
> exists, and would leave a window where the marketplace ZIP and the tagged commit could
> diverge. Follow the order below, and fix the README afterwards (§ 8).

```
0. build + verifyPlugin       ← the last thing that can still say "no"
1. push master, push tag      ← the point of no return
2. launcher release (CI)      ← workflow, or manual trigger with the right account
3. verify the release assets
4. publishPlugin              ← marketplace, effectively one-way
5. pin the digest in agent-skill
6. verify the download path end to end
```

---

## 5. Steps

### Step 0 — the last local gate

```bash
cd "$ZKIDEA"
withjdk.sh 17 ./gradlew clean build verifyPlugin
```

`runPluginVerifier` is deliberately not in this line — see § 3.2.

**Gate:** `BUILD SUCCESSFUL`, 341 launcher + 383 plugin tests green, `verifyPlugin` clean.

### Step 1 — push master, then the tag

```bash
git push origin master
git tag v1.0.3
git push origin v1.0.3
```

master first, so the Release is built from a commit that is genuinely on master and the
manual trigger can select it.

### Step 2 — the launcher release

```bash
gh run list --repo zkoss/zkidea --workflow "Release zk-preview-launcher" --limit 3
```

Empty list → trigger it by hand (this is the documented primary fallback, not an exception),
**with the account that has push**:

```bash
gh auth switch --user hawkchen
gh workflow run release-launcher.yml --repo zkoss/zkidea --ref master -f tag=v1.0.3
gh run watch <RUN_ID> --repo zkoss/zkidea --exit-status --interval 15
```

### Step 3 — confirm both assets exist

```bash
gh release view v1.0.3 --repo zkoss/zkidea --json assets --jq '.assets[].name'
```

**Gate:** both `zk-preview-launcher-1.0.3.jar` **and** `zk-preview-launcher-1.0.3.jar.sha256`.
Missing sidecar → stop; it is the only authoritative digest source.

### Step 4 — publish the plugin

```bash
cd "$ZKIDEA"
git status --porcelain          # must be clean
git describe --tags --exact-match   # must print v1.0.3 — publish the tagged commit, nothing
                                    # else. --tags is required: `git tag` makes a lightweight
                                    # tag, which --exact-match alone does not consider.
withjdk.sh 17 ./gradlew publishPlugin
```

`publishPlugin` runs `buildPlugin` itself. The token is already in
`~/.gradle/gradle.properties`.

**Gate:** upload accepted; the new version appears at
[plugins.jetbrains.com/plugin/7855](https://plugins.jetbrains.com/plugin/7855) (JetBrains
may hold it briefly for automated checks).

### Step 5 — pin the published digest in the agent skill

Follow `release-launcher-procedure.md` steps 4–8 verbatim. The one rule that matters:

```bash
gh release download v1.0.3 --repo zkoss/zkidea --dir . \
  --pattern 'zk-preview-launcher-1.0.3.jar' --pattern 'zk-preview-launcher-1.0.3.jar.sha256'
shasum -a 256 -c zk-preview-launcher-1.0.3.jar.sha256    # must print OK
```

Take `LAUNCHER_SHA256` from **that** file. Never from a local rebuild — 1.0.2 proved CI and
macOS differ byte-for-byte for the same commit.

### Step 6 — verify the download path

Cache cleared, and **without** `--launcher-jar` (with it, nothing about the pin is tested):

```bash
rm -rf ~/.cache/zul-writer/launcher/1.0.3
uv run skills/zul-writer/scripts/preview-zul.py --width 1280 --out /tmp/verify.png \
  zulwriter-showcase/src/main/webapp/preview-fixtures/healthy-page.zul
```

**Gate:** `STATUS: ok`; `LAUNCHER: 1.0.3 (downloaded)`; **no** `is not the pinned launcher`
warning; then `python3 test/run-preview-tests.py` → `0 failed`, exit 0. The check count grows
with the suite (20 at 1.0.2, 29 at 1.0.3), so the verdict is the criterion, not the number.

---

## 6. Acceptance checklist

- [ ] `v1.0.3` exists on `zkoss/zkidea`, pointing at `e05fd87` (or its descendant)
- [ ] Release `v1.0.3` carries both the `.jar` and the `.jar.sha256`
- [ ] `shasum -a 256 -c` on the downloaded jar prints `OK`
- [ ] Plugin 1.0.3 live on the marketplace, change-notes showing #70 and #71
- [ ] A marketplace install renders a preview with a docroot image (#70) and shows the
      `404` diagnostic for a bad path (#71)
- [ ] `preview-zul.py` pins the **published** digest; only that one file committed
- [ ] Cache-cleared download path: `STATUS: ok`, no pin warning
- [ ] CLI contract suite `0 failed`, exit 0
- [ ] **Marketplace compatibility check came back clean** (stands in for the waived local
      verifier — check the version's status on the vendor page, do not assume)
- [ ] **Release `v1.0.3` title and notes edited by hand** to cover the plugin as well as the
      launcher (decision D6a)

---

## 7. What cannot be undone

| Action | Reversible? |
|---|---|
| `git push origin master` | Yes, but only by force-push — treat as final |
| `git push origin v1.0.3` | Deletable *only* while no Release has been cut from it |
| Launcher Release published | **No.** Re-running the tag clobbers pinned bytes → release 1.0.4 instead |
| `publishPlugin` | **Effectively no.** A published version can be hidden, not withdrawn |
| Agent-skill pin commit | Yes — local commit, not pushed by this procedure |

Anything that goes wrong after step 2 is fixed by releasing **1.0.4**, never by re-cutting
`v1.0.3`.

---

## 8. Follow-ups after the release

1. **Fix `README.md` § Release Process ordering** — it still says tag after publish, which
   is wrong now that the launcher workflow builds from the tag (§ 4 above).
2. **Consider a plugin release procedure doc** to sit beside
   `release-launcher-procedure.md`; the README section predates the launcher and the
   marketplace-media step, and this plan is currently the only place the two halves are
   sequenced together.
3. **Edit the `v1.0.3` Release title and notes (decided: D6a).** The workflow creates it as
   "zk-preview-launcher 1.0.3" with launcher-only notes, but the tag is the plugin's too.
   Rewrite it to cover both, naming #70 and #71. This is in the acceptance checklist, not
   optional.
