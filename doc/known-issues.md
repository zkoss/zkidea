# Known Issues

## `ConcurrentModificationException` During `./gradlew build`

**Status:** Not actionable — IntelliJ Platform internal issue.

### Symptom

Running `./gradlew build` (especially after `clean`) prints `SEVERE` log entries like:

```
SEVERE - #c.i.o.a.i.CoroutineExceptionHandlerImpl - Unhandled exception in [...coroutine#N..., Dispatchers.Default]
java.util.ConcurrentModificationException
    at java.util.ArrayList$Itr.checkForComodification (ArrayList.java:1013)
    at com.b.I.b.b.s.b (s.java:191)   ← obfuscated IntelliJ internal
    ...
    at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.run
```

### Cause

The exception originates inside IntelliJ Platform's own coroutine infrastructure during the **test sandbox cold startup**. The sandbox unpacks and initializes a real IntelliJ IDE instance; during that startup, multiple coroutines race to initialize services and one of them iterates an internal `ArrayList` while another modifies it. All affected classes (`com.b.I.b.b.s`) are obfuscated Gradle/IDE internals — none of the stack frames belong to plugin code.

This does **not** happen on a warm `./gradlew test` run because the sandbox is already initialized and the task is skipped.

A secondary unrelated error (`Cannot init component state: GradleJvmSupportMatrix`) also appears and is similarly an IntelliJ Platform internal bug.

### Impact

None. The build reports `BUILD SUCCESSFUL` and all tests pass. The exception is caught and logged by IntelliJ's `CoroutineExceptionHandlerImpl`; it does not abort the build or affect test results.

### Resolution

No plugin-side fix is needed. If the log noise becomes a CI concern, upgrading the `intellij.version` in `build.gradle` to a newer platform version may resolve it.
