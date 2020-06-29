---
layout: docs
title:  "Why Doobieroll"
permalink: docs/why
---

## What does doobieroll do?

Doobieroll is a library that aims to help you handle errors in a type-safe way, and 
making it as easy as possible.

It helps you:

* Specify precisely what errors can happen for a piece of code
* Handle error cases exhaustively, just like sealed traits
* Handle error cases partially, bubbling unhandled errors in the resulting type
* Combine errors from multiple steps (e.g. in a for comprehension), even when each step
  may return a different set of errors

## But why should I put errors in my type?

Using types help you:

- Document the input-output of a piece of code
- Catch mistakes in implementation as well as usage
- Help you to reason about the expected behaviour locally

Error handling is an important concern for most programs, doesn't it make sense to 
accurately use types to describe what could go wrong in a piece of code?

## I'm using sealed traits to represent possible errors, how does doobieroll help?

Using sealed trait for error handling is great! You get:

* Exhaustive matching
* Good type inference (if your effect/container type is covariant like `ZIO`)

However, it comes with a few limitations

* You're only allowed to extend a sealed trait in the same file (and you need to modify the class you want to make part of a sealed trait)
* You cannot freely add or remove an element of a sealed trait. When used to represent error cases it often ends up in
  * **Boilerplate** - define a new sealed trait (and new classes) for every different usecase
  * **Imprecision** - reusing sealed traits often means being inaccurate about what errors a function can actually return.
    For the client, this leads to either **over-handling** (dead code) or **under-handling** (missed error cases due to wildcard pattern matching)

doobieroll uses **Shapeless Coproducts**, which you can think of as arbitrary union of different types.
(and unlike sealed traits, you do not need to own/modify the types you use)

For example, the following two functions each have two error cases, and `Unauthorized` is an error both of them may return.
```:invisible
import doobieroll._
case class Unauthorized()
case class NotFound()
case class QuotaExceeded()
```
```:compile-only
def func1: Either[OneOf2[Unauthorized, NotFound], String] = ???
def func2: Either[OneOf2[Unauthorized, QuotaExceeded], String] = ???
```

For those familiar with shapeless, `OneOf2` is a type alias for `A1 :+: A2 :+: CNil`


