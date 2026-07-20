# Plugin Version Compatibility (`intellij.version` vs `since/untilBuild`)

## Q1: Is `intellij { version = '2023.3' }` being "2023" (not 2026) a problem?

**No — it is correct and intentional.** These two settings mean different things:

| Setting | Meaning | Value now |
|---|---|---|
| `intellij.version` | The **IntelliJ Platform SDK you COMPILE against** (which API is available at build time) | `2023.3` (branch `233`) |
| `patchPluginXml.sinceBuild` | Minimum IDE build the plugin **declares** it runs on | `233.2` |
| `patchPluginXml.untilBuild` | Maximum IDE build the plugin **declares** it runs on | `262.*` (= 2026.2) |

- `version` is a **compile-time** target. `untilBuild` is a **runtime compatibility declaration**. They are independent.
- Best practice is to **compile against the OLDEST version you support** (here 2023.3 → `sinceBuild = 233`). That guarantees you don't accidentally call an API that doesn't exist in older IDEs. The compiled binary then runs *forward* on newer IDEs (2024.x, 2025.x, 2026.x) as long as the stable APIs it uses still exist.
- So `version = '2023.3'` matching `sinceBuild = '233.2'` is exactly right. Bumping `version` to 2026 would give you *newer* APIs but would risk breaking compatibility with 2023.3 users — the opposite of what you want.

**Why you had to edit `untilBuild`:** the Gradle IntelliJ plugin auto-derives `untilBuild` from the SDK branch. Building against 2023.3 defaults it to `233.*`, which blocks the plugin on 2024+ IDEs. That default is the reason a manual bump is needed each time — not the `version` value.

## Q2: Can I avoid bumping `untilBuild` every release?

**Yes.** Remove the upper bound so the plugin is compatible with all future builds. With `gradle-intellij-plugin` 1.17.2 (this project):

```groovy
patchPluginXml {
    sinceBuild = '233.2'
    untilBuild = provider { null }   // omit the until-build attribute entirely
}
```

`provider { null }` overrides the auto-default and causes **no `until-build` attribute** to be written into `plugin.xml`. Result: `<idea-version since-build="233.2"/>` → compatible with 233 and every later build, forever, with no per-release edits.

> If you later migrate to IntelliJ Platform Gradle Plugin 2.x, the equivalent is:
> ```
> intellijPlatform { pluginConfiguration { ideaVersion {
>     sinceBuild = "233"; untilBuild = provider { null }
> } } }
> ```

### Trade-off (the important part)

| | Bounded `untilBuild` (current) | No `untilBuild` (open-ended) |
|---|---|---|
| Per-release maintenance | Must bump every new IDE | None |
| If a future IDE breaks an API you use | Plugin simply **won't load** (fail-safe) | Plugin **loads and may crash/misbehave** for users |
| Marketplace listing | Capped at declared max | Shown compatible with all future IDEs |

Removing the cap trades a safety net for convenience. It is a **good fit for this plugin** because it relies on very stable extension points (XML/DOM/completion), which JetBrains rarely breaks.

### Recommended mitigation if you drop the cap

Run the **Plugin Verifier** against new/EAP builds periodically instead of relying on `untilBuild`:

```bash
./gradlew runPluginVerifier
```

This catches API-breakage on newer IDEs *before* users hit it — giving you the early warning that `untilBuild` used to provide, without the per-release edits. JetBrains Marketplace will also auto-flag a version as incompatible if a future IDE breaks it, protecting users.

### Middle ground (if you want *some* cap but less churn)

Set a far-future cap, e.g. `untilBuild = '999.*'` — practically unbounded but still an explicit ceiling. Cleaner is just `provider { null }`.
