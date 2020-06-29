---
layout: docs
title:  "FAQ"
permalink: docs/faq
---

# Frequently Asked Questions

## Help! The IDE can't figure out what the error type should be!

We provide a helper method `errorIs` which you can use to annotate
the expected error type, which will help your IDE infer the error type of the
following calls.

Note that the `Coproduct` instance of a `sealed trait` is alphabetically ordered!
```:invisible
import doobieroll.implicits._
import shapeless._
```

```:silent
sealed trait Sealed
case class C() extends Sealed
case class B() extends Sealed
case class A() extends Sealed
```
```
val either: Either[Sealed, Unit] = Left(C())

either.errorAsCoproduct.errorIs[A :+: B :+: C :+: CNil]

// Or for errorAsCoproduct specifically..
either.errorAsCoproduct[A :+: B :+: C :+: CNil]
```

## How does the ZIO / Cats Effect integration work? Why do I not need an extra `doobieroll-zio` dependency and an import?

To improve user ergonomics, this project puts all typeclass instances inside the companion object.
Do not worry! With optional dependencies like `zio` and `cats-effect` marked as `optional`, your project will not
incur a dependency on them if you don't already have it in your classpath!

To read more about this technique, see ["No More Orphans" from 7minds.io]([https://blog.7mind.io/no-more-orphans.html])

## Known issues / Caveats?

* Compile time - due to the heavy use of shapeless (implicits), compile time of your project may suffer.
  It's recommended that you use doobieroll where it matters, and use sealed traits when it is sufficient.
