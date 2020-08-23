---
layout: home
title:  "Home"
section: "home"
position: 1
---

DoobieRoll is a collection of utilities to make working with [Doobie](https://tpolecat.github.io/doobie/) / SQL even easier.

[![Release](https://img.shields.io/nexus/r/com.github.jatcwang/doobieroll_2.13?server=https%3A%2F%2Foss.sonatype.org)](https://oss.sonatype.org/content/repositories/releases/com/github/jatcwang/doobieroll-core_2.13/)
[![(https://badges.gitter.im/gitterHQ/gitter.png)](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/jatcwang/doobieroll)

- [TableColumns](docs/tablecolumns) - Ensure fields in your SQL are consistently named and ordered.
- [Assembler](docs/assembler) - Assemble SQL query results into hierarchical domain models.

**Assembler** does not depend on Doobie, so check it out even if you don't use Doobie!

# Installation

```
// SBT
"com.github.jatcwang" %% "doobieroll" % "{{ site.version }}" 

// Mill
ivy"com.github.jatcwang::doobieroll:{{ site.version }}" 
```
