package oru.syntax

import oru.UngroupedAssembler.UngroupedParentAssembler
import oru.impl.{
  Accum,
  UngroupedAtomVisitorImpl,
  UngroupedParentVisitor,
  UngroupedParentVisitorImpl,
  UngroupedVisitor
}
import oru.{Atom, EE, Par, UngroupedAssembler}
import shapeless._

import scala.annotation.tailrec
import cats.implicits._

trait UnorderedSyntax {

  implicit class AtomExtension[A, ADb](atom: Atom[A, ADb :: HNil]) {
    def asUnordered: UngroupedAssembler[A, ADb :: HNil] = {
      new UngroupedAssembler[A, ADb :: HNil] {
        private[oru] override def makeVisitor(
          accum: Accum,
          idx: Int
        ): UngroupedVisitor[A, ADb :: HNil] =
          new UngroupedAtomVisitorImpl[A, ADb](
            atom,
            accum,
            idx
          )
      }

    }
  }

  import shapeless.ops.hlist._
  import shapeless._

  type Flattener[HL <: HList, Out <: HList] = FlatMapper.Aux[HListIdentity.type, HL, Out]

  object HListIdentity extends Poly1 {
    implicit def caseHList[HL <: HList] = at[HL](identity)
  }

  private def mkParentUngrouped[A, ADb, CDbs <: HList, CDbsFlattened <: HList](
    par: Par[A, ADb],
    assemblers: Vector[UngroupedAssembler[Any, HList]],
    flattener: Flattener[CDbs, CDbsFlattened]
  ): UngroupedParentAssembler[A, ADb :: CDbsFlattened] = {
    val _ = flattener // unused. For type inference only

    new UngroupedParentAssembler[A, ADb :: CDbsFlattened] {

      private[oru] override def makeVisitor(
        accum: Accum,
        idx: Int
      ): UngroupedParentVisitor[A, ADb :: CDbsFlattened] =
        new UngroupedParentVisitorImpl[A, ADb, CDbsFlattened](
          par,
          accum,
          idx,
          assemblers
        )
    }
  }

  @inline
  private def eraseAssemblerType[A, HL <: HList](
    assembler: UngroupedAssembler[A, HL]
  ): UngroupedAssembler[Any, HList] =
    assembler.asInstanceOf[UngroupedAssembler[Any, HList]]

  implicit class ParentExtension[A, ADb, Cs <: HList](par: Par.Aux[A, ADb, Cs]) {

    def asUnordered[C0, C0Dbs <: HList](
      c0Assembler: UngroupedAssembler[C0, C0Dbs]
    ): UngroupedParentAssembler[A, ADb :: C0Dbs] =
      mkParentUngrouped(
        par,
        Vector(eraseAssemblerType(c0Assembler)),
        implicitly[Flattener[C0Dbs :: HNil, C0Dbs]]
      )

    def asUnordered[C0, C1, C0Dbs <: HList, C1Dbs <: HList, CDbs <: HList](
      c0Assembler: UngroupedAssembler[C0, C0Dbs],
      c1Assembler: UngroupedAssembler[C1, C1Dbs]
    )(
      implicit flattener: Flattener[C0Dbs :: C1Dbs :: HNil, CDbs]
    ): UngroupedParentAssembler[A, ADb :: CDbs] =
      mkParentUngrouped(
        par,
        Vector(eraseAssemblerType(c0Assembler), eraseAssemblerType(c1Assembler)),
        flattener
      )
  }
}

private[oru] object UnorderedSyntax {

  def seqToHList[HL <: HList](orig: Vector[Any]): HL = {

    @tailrec def impl(acc: HList, rest: Vector[Any]): HL =
      rest match {
        case Vector()  => acc.asInstanceOf[HL]
        case i +: rest => impl(i :: acc, rest)
      }

    // Reverse so we can use ::
    impl(HNil, orig.reverse)
  }

  @tailrec def collectSuccess(
    accum: Vector[Vector[Any]],
    results: Vector[Vector[Either[EE, Any]]]
  ): Either[EE, Vector[Vector[Any]]] = {
    results match {
      case Vector() => Right(accum)
      case init +: rest =>
        init.sequence match {
          case l @ Left(_) => l.rightCast
          case Right(r)    => collectSuccess(accum :+ r, rest)
        }
    }
  }

}
