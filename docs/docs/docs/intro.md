---
layout: docs
title:  "Quick Start"
permalink: docs/intro
---

# Quick Start

Add the following to your build.sbt:

[![Release](https://img.shields.io/nexus/r/com.github.jatcwang/doobieroll-core_2.13?server=https%3A%2F%2Foss.sonatype.org)](https://oss.sonatype.org/content/repositories/releases/com/github/jatcwang/doobieroll-core_2.13/)

```
libraryDependencies += "com.github.jatcwang" %% "doobieroll-core" % LATEST_VERSION
```

If you're using Scala 2.12, make sure you add `scalacOptions += "-Ypartial-unitification"` compiler flag

Reload your project, and start handling errors!

```scala
import doobieroll._
```



