---
layout: docs
title:  "Introduction"
permalink: docs/intro
---

## What does doobieroll do?

[![Release](https://img.shields.io/nexus/r/com.github.jatcwang/doobieroll_2.13?server=https%3A%2F%2Foss.sonatype.org)](https://oss.sonatype.org/content/repositories/releases/com/github/jatcwang/doobieroll_2.13/)

Doobieroll is a collection of utilities to help you work with [Doobie](https://tpolecat.github.io/doobie/) / SQL better.

```
// SBT
"com.github.jatcwang" %% "doobieroll" % "{{ site.version }}" 

// Mill
ivy"com.github.jatcwang::doobieroll:{{ site.version }}" 
```

- [TableColumns](tablecolumns) - Ensure fields in your SQL are consistently named and ordered.
- [Assembler](assembler) - Assemble SQL query results into hierarchical domain models



