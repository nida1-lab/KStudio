# Bundled build tools

The on-device builder is implemented in KStudio. It does not embed another IDE or its build engine.

| Component | Version/source | License |
| --- | --- | --- |
| Kotlin compiler, standard library, reflection, script runtime, daemon | JetBrains Kotlin 2.2.10, Maven Central | Apache-2.0 |
| kotlinx.coroutines | 1.8.0, Maven Central | Apache-2.0 |
| JetBrains annotations | 23.0.0, Maven Central | Apache-2.0 |
| StAX API | stax:stax-api:1.0.1, Maven Central | Apache-2.0 |
| D8/R8 | com.android.tools:r8:8.7.18 | BSD-3-Clause and upstream notices |
| APK signer | com.android.tools.build:apksig:8.7.3 | Apache-2.0 |
| Android API definitions | Android SDK Platform 35, installed SDK's android.jar | Android SDK / component notices supplied with the platform |
| aapt2 for Android arm64 and x86_64 | AOSP build distributed in sanjarAmin/AIDE-OS at a236fb318c96ea772e1432b2f3082681bb4030f6 | Apache-2.0 and AOSP dependency notices |
| ASM (build-time transformation only) | 9.7.1 | BSD-3-Clause |

The aapt2 binary source/build provenance is documented at
https://github.com/sanjarAmin/AIDE-OS/blob/a236fb318c96ea772e1432b2f3082681bb4030f6/tools/aapt2/FINDINGS.md .
Its Git blob IDs are checked during packaging. AIDE-OS's GPL IDE sources and compiler shims are not incorporated.

KStudio's compiler compatibility adapter is in `tools/local/`. It maps optional JVM management telemetry to ART-compatible adapters, provides atomic fetch-and-add using ART's volatile read and compare-and-swap operations, and adapts optional native-buffer/byte-array copying.
The original Kotlin artifacts are checksum verified. Their resources, service descriptors and license records accompany the compiler DEX archive.
The Android platform and build-tools notices are also included in the APK's toolchain assets.

Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0
Kotlin source: https://github.com/JetBrains/kotlin/tree/v2.2.10
R8 source and license: https://r8.googlesource.com/r8/
apksig source: https://android.googlesource.com/platform/tools/apksig/
Android source: https://source.android.com/
ASM source and license: https://gitlab.ow2.org/asm/asm
