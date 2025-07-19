# Android vs JVM targets

The default KMP
["hierarchy template"](https://www.jetbrains.com/help/kotlin-multiplatform-dev/multiplatform-hierarchy.html#see-the-full-hierarchy-template)
configures `androidMain` and `jvmMain` to be entirely separate targets, even though Android *can*
be made to be a child of JVM. Changing this requires completely wiring up all targets ourselves
though, so for now we've left them separate to simplify gradle config. If there ends up being too
much code duplication, we can either make `androidMain` a child of `jvmMain`, or introduce a new
shared target that includes both of them. Compose, for example, uses a structure where `jvm` is the
shared parent of both `android` and `desktop`.
