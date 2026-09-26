---
name: reference_machine_jdk
description: Default javac on Gerald's machine is too new for Lombok — pin JAVA_HOME to JDK 21 (or the version the backend targets)
metadata:
  type: reference
---

On Gerald's CachyOS machine the default `javac` is JDK 26 and the IntelliJ JBRs are 25;
Lombok broke on anything past 21 in java-overmind-server. JDK 21 lives at
`/usr/lib/jvm/java-21-openjdk`. Run Maven as
`JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./mvnw ...` unless the backend pins a different
toolchain. Node is managed via fnm/volta (`openspec` is on the volta path).

Verify which JDK the Quarkus backend actually targets once it is scaffolded and update this
entry (and add `reference_build_and_test.md` with the real build/test/dev commands).
