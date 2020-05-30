package oru.syntax

import cats.Monad
import cats.implicits._
import oru.UngroupedAssembler.UngroupedParentAssembler
import oru.impl._
import oru.{LeafDef, ParentDef, UngroupedAssembler}
import shapeless._

import scala.annotation.tailrec

trait UnorderedSyntax {

  implicit class AtomExtension[F[_], A, ADb](atom: LeafDef[F, A, ADb :: HNil]) {
    def forUnordered: UngroupedAssembler[F, A, ADb :: HNil] = {
      new UngroupedAssembler[F, A, ADb :: HNil] {
        private[oru] override def makeVisitor(
          accum: Accum,
          idx: Int,
        ): UngroupedVisitor[F, A, ADb :: HNil] =
          new UngroupedLeafVisitorImpl[F, A, ADb](
            atom,
            accum,
            idx,
          )
      }

    }
  }

  import shapeless._
  import shapeless.ops.hlist._

  type Flattener[HL <: HList, Out <: HList] = FlatMapper.Aux[HListIdentity.type, HL, Out]

  object HListIdentity extends Poly1 {
    implicit def caseHList[HL <: HList] = at[HL](identity)
  }

  private def mkParentUngrouped[F[_], A, ADb, CDbs <: HList, CDbsFlattened <: HList](
    par: ParentDef[F, A, ADb],
    assemblers: Vector[UngroupedAssembler[F, Any, HList]],
    flattener: Flattener[CDbs, CDbsFlattened],
    FMonad: Monad[F],
  ): UngroupedParentAssembler[F, A, ADb :: CDbsFlattened] = {
    val _ = flattener // unused. For type inference only

    new UngroupedParentAssembler[F, A, ADb :: CDbsFlattened] {

      private[oru] override def makeVisitor(
        accum: Accum,
        idx: Int,
      ): UngroupedParentVisitor[F, A, ADb :: CDbsFlattened] =
        new UngroupedParentVisitorImpl[F, A, ADb, CDbsFlattened](
          par,
          accum,
          idx,
          assemblers,
          FMonad,
        )
    }
  }

  @inline
  private def eraseAssemblerType[F[_], A, HL <: HList](
    assembler: UngroupedAssembler[F, A, HL],
  ): UngroupedAssembler[F, Any, HList] =
    assembler.asInstanceOf[UngroupedAssembler[F, Any, HList]]

  implicit class ParentExtension[F[_], A, ADb, Cs <: HList](par: ParentDef.Aux[F, A, ADb, Cs]) {

    def forUnordered[C0, C0Dbs <: HList](
      c0Assembler: UngroupedAssembler[F, C0, C0Dbs],
    )(implicit monadF: Monad[F]): UngroupedParentAssembler[F, A, ADb :: C0Dbs] =
      mkParentUngrouped(
        par,
        Vector(eraseAssemblerType(c0Assembler)),
        implicitly[Flattener[C0Dbs :: HNil, C0Dbs]],
        monadF,
      )

    def forUnordered[C0, C1, C0Dbs <: HList, C1Dbs <: HList, CDbs <: HList](
      c0Assembler: UngroupedAssembler[F, C0, C0Dbs],
      c1Assembler: UngroupedAssembler[F, C1, C1Dbs],
    )(
      implicit monadF: Monad[F],
      flattener: Flattener[C0Dbs :: C1Dbs :: HNil, CDbs],
    ): UngroupedParentAssembler[F, A, ADb :: CDbs] =
      mkParentUngrouped(
        par,
        Vector(eraseAssemblerType(c0Assembler), eraseAssemblerType(c1Assembler)),
        flattener,
        monadF,
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

  def collectSuccess[F[_]](
    results: Vector[Vector[F[Any]]],
  )(implicit M: Monad[F]): F[Vector[Vector[Any]]] = results.map(_.sequence).sequence

}
