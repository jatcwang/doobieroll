---
layout: docs
title:  "FAQ"
permalink: docs/faq
---

# Frequently Asked Questions

## What problem is DoobieRoll trying to solve?

This library aims to reduce the boring, boilerplatey things when working with Doobie / SQL databases.

Similar to Doobie's philosophy, it doesn't try to hide the underlying SQL from you. 
For example [snippets](snippets) functions maps directly to common SQL fragments - 
their purpose is to reduce boilerplate and avoid typos.

## I want to use Assembler but I have a list of tuples instead of HLists!

If you are using Doobie, you can query directly into HLists
(see Assembler's "Usage with Doobie" section).

If that's not possible, shapeless can convert tuples to HLists:

```scala mdoc
import shapeless.syntax.std.tuple._
import shapeless._

val listOfTuples: List[(Int, String)] = List((1, "one"), (2, "two"))

val listOfHList: List[Int :: String :: HNil] = listOfTuples.map(_.productElements)
```
