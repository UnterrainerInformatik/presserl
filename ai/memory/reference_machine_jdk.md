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

Verify which JDK the Quarkus backend actually targets once it is scaffolded and update this
entry (and add `reference_build_and_test.md` with the real build/test/dev commands).
