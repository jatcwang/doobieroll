package oru.hlist

import shapeless._
import shapeless.ops.hlist.Mapper

/** Make all elements of an HList wrapped in Option, if it weren't already wrapped */
trait AllOptional[HL <: HList] {
  type Out <: HList
}

object AllOptional {

  type Aux[HL <: HList, Out0 <: HList] = AllOptional[HL] {
    type Out = Out0
  }

  object AllOptionalImpl extends AllOptionalImplLowerPrio {
    implicit def caseOpt[H <: Option[_]] = at[H](identity)
  }

  trait AllOptionalImplLowerPrio extends Poly1 {
    implicit def caseNonOptional[H]: Case.Aux[H, Option[H]] = at[H](h => Some(h))
  }

  implicit def allOptionalForHList[HL <: HList, Out0 <: HList](implicit mapped: Mapper.Aux[AllOptionalImpl.type, HL, Out0]): AllOptional.Aux[HL, Out0] = new AllOptional[HL] {
    type Out = Out0
  }

}


