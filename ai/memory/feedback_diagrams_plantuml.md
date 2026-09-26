---
name: feedback_diagrams_plantuml
description: Diagrams are always PlantUML (.puml source + rendered SVG), never Mermaid/ASCII/images
metadata:
  type: feedback
---

Every diagram is written as PlantUML: `.puml` source under `docs/diagrams/`, rendered SVG committed next to it.

**Why:** Gerald asked explicitly ("diagramme bitte immer als plantuml") on 2026-09-26.

**How to apply:** Never produce Mermaid, ASCII art or hand-drawn images as diagrams in the repo. Render via
`curl -sS -L -X POST -H 'Content-Type: text/plain; charset=utf-8' --data-binary @x.puml https://plantuml.unterrainer.info/plantuml/svg -o x.svg`
(`-L` because the server redirects with 307; the charset is needed or non-ASCII characters break). Check the rendering visually before committing.
