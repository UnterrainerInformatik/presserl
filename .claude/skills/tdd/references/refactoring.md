---
name: "Refactor Candidates"
description: "Common refactor opportunities to look for after a green TDD cycle: duplication, long methods, shallow modules, feature envy, primitive obsession."
tags: [tdd, refactoring, code-smells, duplication, modules, solid]
---

# Refactor Candidates

After TDD cycle, look for:

- **Duplication** → Extract function/class
- **Long methods** → Break into private helpers (keep tests on public interface)
- **Shallow modules** → Combine or deepen
- **Feature envy** → Move logic to where data lives
- **Primitive obsession** → Introduce value objects
- **Existing code** the new code reveals as problematic
