---
name: reference_machine_jdk
description: Default JDK on Gerald's machine is now 21 (Lombok-safe); JDK 26 is installed but not default
metadata:
  type: reference
---

On Gerald's CachyOS machine the default JDK is 21 (`archlinux-java` default
`java-21-openjdk`, verified 2026-09-26); JDK 26 is installed but not default, the IntelliJ
JBRs are 25. Lombok broke on anything past 21 in java-overmind-server, so plain `./mvnw`
works; pin `JAVA_HOME=/usr/lib/jvm/java-21-openjdk` only if the default changes again. Node is managed via fnm/volta (`openspec` is on the volta path).

The backend targets `maven.compiler.release=21` (Quarkus 3.33 LTS) and builds and tests on
the default JDK 21 (verified 2026-09-26); the image uses Temurin 21. Commands:
[[reference_build_and_test]].
