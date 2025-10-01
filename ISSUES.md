# Cannot init component state (componentName=GradleJvmSupportMatrix, componentClass=GradleJvmSupportMatrix)
## Steps to reproduce
* run gradle task "runIde"

## Current result
IDE starts with errors:
```text
2025-09-30 22:49:43,706 [   5009] SEVERE - #c.i.c.ComponentStoreImpl - Cannot init component state (componentName=GradleJvmSupportMatrix, componentClass=GradleJvmSupportMatrix) [Plugin: com.intellij.gradle]
com.intellij.diagnostic.PluginException: Cannot init component state (componentName=GradleJvmSupportMatrix, componentClass=GradleJvmSupportMatrix) [Plugin: com.intellij.gradle]
	at com.intellij.configurationStore.ComponentStoreImpl.initComponent(ComponentStoreImpl.kt:174)
	at com.intellij.configurationStore.ComponentStoreWithExtraComponents.initComponent(ComponentStoreWithExtraComponents.kt:48)
	at com.intellij.serviceContainer.ComponentManagerImpl.initializeComponent$intellij_platform_serviceContainer(ComponentManagerImpl.kt:931)
	at com.intellij.serviceContainer.ServiceInstanceInitializer.createInstance$suspendImpl(ServiceInstanceInitializer.kt:41)
	at com.intellij.serviceContainer.ServiceInstanceInitializer.createInstance(ServiceInstanceInitializer.kt)
	at com.intellij.platform.instanceContainer.internal.LazyInstanceHolder$initialize$1$1.invokeSuspend(LazyInstanceHolder.kt:162)
	at com.intellij.platform.instanceContainer.internal.LazyInstanceHolder$initialize$1$1.invoke(LazyInstanceHolder.kt)
	at com.intellij.platform.instanceContainer.internal.LazyInstanceHolder$initialize$1$1.invoke(LazyInstanceHolder.kt)
	at kotlinx.coroutines.intrinsics.UndispatchedKt.startUndispatchedOrReturn(Undispatched.kt:78)
	at kotlinx.coroutines.BuildersKt__Builders_commonKt.withContext(Builders.common.kt:167)
	at kotlinx.coroutines.BuildersKt.withContext(Unknown Source)
	at com.intellij.platform.instanceContainer.internal.LazyInstanceHolder$initialize$1.invokeSuspend(LazyInstanceHolder.kt:160)
	at com.intellij.platform.instanceContainer.internal.LazyInstanceHolder$initialize$1.invoke(LazyInstanceHolder.kt)
	at com.intellij.platform.instanceContainer.internal.LazyInstanceHolder$initialize$1.invoke(LazyInstanceHolder.kt)
	at kotlinx.coroutines.intrinsics.UndispatchedKt.startCoroutineUndispatched(Undispatched.kt:44)
	at kotlinx.coroutines.CoroutineStart.invoke(CoroutineStart.kt:112)
	at kotlinx.coroutines.AbstractCoroutine.start(AbstractCoroutine.kt:126)
	at kotlinx.coroutines.BuildersKt__Builders_commonKt.launch(Builders.common.kt:56)
	at kotlinx.coroutines.BuildersKt.launch(Unknown Source)
	at com.intellij.platform.instanceContainer.internal.LazyInstanceHolder.initialize(LazyInstanceHolder.kt:145)
	at com.intellij.platform.instanceContainer.internal.LazyInstanceHolder.access$initialize(LazyInstanceHolder.kt:13)
	at com.intellij.platform.instanceContainer.internal.LazyInstanceHolder.tryInitialize(LazyInstanceHolder.kt:135)
	at com.intellij.platform.instanceContainer.internal.LazyInstanceHolder.getInstance(LazyInstanceHolder.kt:95)
	at com.intellij.platform.instanceContainer.internal.LazyInstanceHolder.getInstanceInCallerContext$suspendImpl(LazyInstanceHolder.kt:87)
	at com.intellij.platform.instanceContainer.internal.LazyInstanceHolder.getInstanceInCallerContext(LazyInstanceHolder.kt)
	at com.intellij.serviceContainer.ComponentManagerImplKt$getOrCreateInstanceBlocking$3.invokeSuspend(ComponentManagerImpl.kt:2337)
	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:108)
	at kotlinx.coroutines.EventLoopImplBase.processNextEvent(EventLoop.common.kt:280)
	at kotlinx.coroutines.BlockingCoroutine.joinBlocking(Builders.kt:85)
	at kotlinx.coroutines.BuildersKt__BuildersKt.runBlocking(Builders.kt:59)
	at kotlinx.coroutines.BuildersKt.runBlocking(Unknown Source)
	at com.intellij.serviceContainer.ComponentManagerImplKt$runBlockingInitialization$1.invoke(ComponentManagerImpl.kt:2406)
	at com.intellij.serviceContainer.ComponentManagerImplKt$runBlockingInitialization$1.invoke(ComponentManagerImpl.kt:2397)
	at com.intellij.openapi.progress.ContextKt.prepareThreadContext(context.kt:83)
	at com.intellij.serviceContainer.ComponentManagerImplKt.runBlockingInitialization(ComponentManagerImpl.kt:2397)
	at com.intellij.serviceContainer.ComponentManagerImplKt.getOrCreateInstanceBlocking(ComponentManagerImpl.kt:2336)
	at com.intellij.serviceContainer.ComponentManagerImpl.doGetService(ComponentManagerImpl.kt:1057)
	at com.intellij.serviceContainer.ComponentManagerImpl.getService(ComponentManagerImpl.kt:988)
	at org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix$Companion.getInstance(GradleJvmSupportMatrix.kt:220)
	at org.jetbrains.plugins.gradle.jvmcompat.GradleCompatibilitySupportUpdater.<init>(GradleCompatibilitySupportUpdater.kt:14)
	at java.base/jdk.internal.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)
	at java.base/jdk.internal.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:45)
	at java.base/java.lang.reflect.Constructor.newInstanceWithCaller(Constructor.java:499)
	at java.base/java.lang.reflect.Constructor.newInstance(Constructor.java:480)
	at com.intellij.platform.instanceContainer.instantiation.InstantiateKt$instantiate$4.invoke(instantiate.kt:74)
	at com.intellij.platform.instanceContainer.instantiation.InstantiateKt$instantiate$4.invoke(instantiate.kt:72)
	at com.intellij.platform.instanceContainer.instantiation.InstantiateKt.instantiate(instantiate.kt:278)
	at com.intellij.platform.instanceContainer.instantiation.InstantiateKt.instantiate(instantiate.kt:72)
	at com.intellij.serviceContainer.InstantiateKt.instantiateWithContainer(instantiate.kt:19)
	at com.intellij.serviceContainer.ServiceInstanceInitializer.createInstance$suspendImpl(ServiceInstanceInitializer.kt:26)
	at com.intellij.serviceContainer.ServiceInstanceInitializer.createInstance(ServiceInstanceInitializer.kt)
	at com.intellij.platform.instanceContainer.internal.LazyInstanceHolder$initialize$1$1.invokeSuspend(LazyInstanceHolder.kt:162)
	at com.intellij.platform.instanceContainer.internal.LazyInstanceHolder$initialize$1$1.invoke(LazyInstanceHolder.kt)
	at com.intellij.platform.instanceContainer.internal.LazyInstanceHolder$initialize$1$1.invoke(LazyInstanceHolder.kt)
	at kotlinx.coroutines.intrinsics.UndispatchedKt.startUndispatchedOrReturn(Undispatched.kt:78)
	at kotlinx.coroutines.BuildersKt__Builders_commonKt.withContext(Builders.common.kt:167)
	at kotlinx.coroutines.BuildersKt.withContext(Unknown Source)
	at com.intellij.platform.instanceContainer.internal.LazyInstanceHolder$initialize$1.invokeSuspend(LazyInstanceHolder.kt:160)
	at com.intellij.platform.instanceContainer.internal.LazyInstanceHolder$initialize$1.invoke(LazyInstanceHolder.kt)
	at com.intellij.platform.instanceContainer.internal.LazyInstanceHolder$initialize$1.invoke(LazyInstanceHolder.kt)
	at kotlinx.coroutines.intrinsics.UndispatchedKt.startCoroutineUndispatched(Undispatched.kt:44)
	at kotlinx.coroutines.CoroutineStart.invoke(CoroutineStart.kt:112)
	at kotlinx.coroutines.AbstractCoroutine.start(AbstractCoroutine.kt:126)
	at kotlinx.coroutines.BuildersKt__Builders_commonKt.launch(Builders.common.kt:56)
	at kotlinx.coroutines.BuildersKt.launch(Unknown Source)
	at com.intellij.platform.instanceContainer.internal.LazyInstanceHolder.initialize(LazyInstanceHolder.kt:145)
	at com.intellij.platform.instanceContainer.internal.LazyInstanceHolder.access$initialize(LazyInstanceHolder.kt:13)
	at com.intellij.platform.instanceContainer.internal.LazyInstanceHolder.tryInitialize(LazyInstanceHolder.kt:135)
	at com.intellij.platform.instanceContainer.internal.LazyInstanceHolder.getInstance(LazyInstanceHolder.kt:95)
	at com.intellij.platform.instanceContainer.internal.LazyInstanceHolder.getInstanceInCallerContext$suspendImpl(LazyInstanceHolder.kt:87)
	at com.intellij.platform.instanceContainer.internal.LazyInstanceHolder.getInstanceInCallerContext(LazyInstanceHolder.kt)
	at com.intellij.serviceContainer.ComponentManagerImplKt$getOrCreateInstanceBlocking$3.invokeSuspend(ComponentManagerImpl.kt:2337)
	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:108)
	at kotlinx.coroutines.EventLoopImplBase.processNextEvent(EventLoop.common.kt:280)
	at kotlinx.coroutines.BlockingCoroutine.joinBlocking(Builders.kt:85)
	at kotlinx.coroutines.BuildersKt__BuildersKt.runBlocking(Builders.kt:59)
	at kotlinx.coroutines.BuildersKt.runBlocking(Unknown Source)
	at com.intellij.serviceContainer.ComponentManagerImplKt$runBlockingInitialization$1.invoke(ComponentManagerImpl.kt:2406)
	at com.intellij.serviceContainer.ComponentManagerImplKt$runBlockingInitialization$1.invoke(ComponentManagerImpl.kt:2397)
	at com.intellij.openapi.progress.ContextKt.prepareThreadContext(context.kt:86)
	at com.intellij.serviceContainer.ComponentManagerImplKt.runBlockingInitialization(ComponentManagerImpl.kt:2397)
	at com.intellij.serviceContainer.ComponentManagerImplKt.getOrCreateInstanceBlocking(ComponentManagerImpl.kt:2336)
	at com.intellij.serviceContainer.ComponentManagerImpl.doGetService(ComponentManagerImpl.kt:1057)
	at com.intellij.serviceContainer.ComponentManagerImpl.getService(ComponentManagerImpl.kt:988)
	at org.jetbrains.plugins.gradle.jvmcompat.GradleCompatibilitySupportUpdater$Companion.getInstance(GradleCompatibilitySupportUpdater.kt:26)
	at org.jetbrains.plugins.gradle.service.project.GradleVersionUpdateStartupActivity.execute(GradleVersionUpdateStartupActivity.kt:12)
	at com.intellij.ide.startup.impl.StartupManagerImplKt$launchActivity$1.invokeSuspend(StartupManagerImpl.kt:482)
	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:108)
	at kotlinx.coroutines.scheduling.CoroutineScheduler.runSafely(CoroutineScheduler.kt:584)
	at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.executeTask(CoroutineScheduler.kt:793)
	at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.runWorker(CoroutineScheduler.kt:697)
	at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.run(CoroutineScheduler.kt:684)
Caused by: java.lang.IllegalArgumentException: 25
	at com.intellij.util.lang.JavaVersion.parse(JavaVersion.java:307)
	at org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix$getCompatibilityRanges$1$javaRange$1.invoke(GradleJvmSupportMatrix.kt:44)
	at org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix$getCompatibilityRanges$1$javaRange$1.invoke(GradleJvmSupportMatrix.kt:44)
	at org.jetbrains.plugins.gradle.jvmcompat.IdeVersionedDataParser$Companion.parseVersion(IdeVersionedDataParser.kt:21)
	at org.jetbrains.plugins.gradle.jvmcompat.IdeVersionedDataParser$Companion.parseRange(IdeVersionedDataParser.kt:31)
	at org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix.getCompatibilityRanges(GradleJvmSupportMatrix.kt:44)
	at org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix.applyState(GradleJvmSupportMatrix.kt:28)
	at org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix.onStateChanged(GradleJvmSupportMatrix.kt:50)
	at org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix.onStateChanged(GradleJvmSupportMatrix.kt:13)
	at org.jetbrains.plugins.gradle.jvmcompat.IdeVersionedDataStorage.loadState(IdeVersionedDataStorage.kt:38)
	at org.jetbrains.plugins.gradle.jvmcompat.IdeVersionedDataStorage.loadState(IdeVersionedDataStorage.kt:7)
	at com.intellij.configurationStore.ComponentStoreImpl.doInitComponent(ComponentStoreImpl.kt:490)
	at com.intellij.configurationStore.ComponentStoreImpl.initComponent(ComponentStoreImpl.kt:409)
	at com.intellij.configurationStore.ComponentStoreImpl.initComponent(ComponentStoreImpl.kt:131)
	... 95 more
```

## Root cause
The IntelliJ IDEA version you're using has a Gradle plugin that doesn't recognize Java 25. The error happens when:

The GradleJvmSupportMatrix component tries to initialize
It attempts to parse Java version compatibility data
The parser encounters "25" (Java 25) but the JavaVersion.parse() method doesn't recognize it as a valid version

Why this happens:

Java 25 is a relatively new version (early access/preview)
Your IntelliJ IDEA version was released before Java 25 support was added
The Gradle plugin's compatibility matrix includes Java 25, but the version parser hasn't been updated to handle it

## Solution
Upgrade the IntelliJ platform version in `build.gradle`
```gradle
   intellij {
       version = '2024.2' // or later
       plugins = ['maven', 'com.intellij.java']
   }
```
but it also needs to adjust the compatible version range:

```xml
   <patchPluginXml>
       sinceBuild = '241' // Start from 2024.1
       untilBuild = '253.*'
   </patchPluginXml>
```

That might affect some users who are on older versions of IntelliJ IDEA, so consider the trade-offs.