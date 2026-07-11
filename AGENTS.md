# Technical Overview
- Kotlin 2.3 - Programming Language
- Spring Boot 4.x - Main Framework
- openFeign QueryDSL 7.x - Database Querying
- Konvert - Object Mapping
- Liquibase - Database Migration and extension
- jUnit 5, kotlin.test - Unit Testing

# Agent Instructions

This repository keeps canonical AI instructions in `.ai/`.

Before making code changes:

1. Read `.ai/README.md`.
2. Follow the progressive loading rules from that file.
3. Load only the instruction, skill, and harness files relevant to the current task.
4. Always follow `.ai/instructions/coding-guidelines.md` for Kotlin code.
5. Always follow `.ai/instructions/testing.md` when creating, changing, or reviewing tests.
