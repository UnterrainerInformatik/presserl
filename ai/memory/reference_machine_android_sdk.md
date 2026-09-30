---
name: reference_machine_android_sdk
description: Android SDK on Gerald's machine comes from his Android Studio at /home/psilo/Android/Sdk; never install a second one
metadata:
  type: reference
---

Gerald has Android Studio (used for school) and its SDK lives at `/home/psilo/Android/Sdk`
(installed 2026-09-30). Use that SDK for the admin app's Android target — point
`ANDROID_HOME` or `admin/local.properties` `sdk.dir` there and add missing components (emulator,
system images) with its `sdkmanager`; do not install a separate SDK. `/dev/kvm` is available for
the emulator. Build commands: [[reference_build_and_test]]; JDK: [[reference_machine_jdk]].
