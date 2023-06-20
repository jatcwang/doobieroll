package doobieroll.syntax

import cats.Monad
import cats.syntax.all._
import doobieroll.impl._
import doobieroll.{Assembler, ParentDef, LeafDef, ParentAssembler}
import shapeless._

import scala.annotation.{tailrec, nowarn}

trait ToAssemblerSyntax {

  implicit class LeafDefExtension[F[_], A, ADb](leafDef: LeafDef[F, A, ADb]) {
    def toAssembler: Assembler[F, A, ADb :: HNil] = {
      new Assembler[F, A, ADb :: HNil] {
        private[doobieroll] override def makeVisitor(
          accum: Accum,
          idx: Int,
        ): Visitor[F, A, ADb :: HNil] =
          new LeafVisitorImpl[F, A, ADb](
            leafDef = leafDef,
            accum = accum,
            startIdx = idx,
          )
      }

    }
  }

  import shapeless._
  import shapeless.ops.hlist._

  final type Flattener[HL <: HList, Out <: HList] = FlatMapper.Aux[HListIdentity.type, HL, Out]

  object HListIdentity extends Poly1 {
    implicit def caseHList[HL <: HList]: HListIdentity.Case.Aux[HL, HL] = at[HL](identity)
  }

  private def mkParent[F[_], A, ADb, CDbs <: HList, CDbsFlattened <: HList](
    par: ParentDef[F, A, ADb],
    assemblers: Vector[Assembler[F, Any, HList]],
    flattener: Flattener[CDbs, CDbsFlattened],
    FMonad: Monad[F],
  ): ParentAssembler[F, A, ADb :: CDbsFlattened] = {
    val _ = flattener // unused. For type inference only

    new ParentAssembler[F, A, ADb :: CDbsFlattened] {

      private[doobieroll] override def makeVisitor(
        accum: Accum,
        idx: Int,
      ): ParentVisitor[F, A, ADb :: CDbsFlattened] =
        new ParentVisitorImpl[F, A, ADb, CDbsFlattened](
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
    assembler: Assembler[F, A, HL],
  ): Assembler[F, Any, HList] =
    assembler.asInstanceOf[Assembler[F, Any, HList]]

  implicit class ParentExtension[F[_], A, ADb, Cs <: HList](par: ParentDef.Aux[F, A, ADb, Cs]) {

    def toAssembler[C0, C0Dbs <: HList](
      c0Assembler: Assembler[F, C0, C0Dbs],
    )(implicit monadF: Monad[F]): ParentAssembler[F, A, ADb :: C0Dbs] =
      mkParent(
        par,
        Vector(eraseAssemblerType(c0Assembler)),
        implicitly[Flattener[C0Dbs :: HNil, C0Dbs]],
        monadF,
      )

    def toAssembler[C0, C1, C0Dbs <: HList, C1Dbs <: HList, CDbs <: HList](
      c0Assembler: Assembler[F, C0, C0Dbs],
      c1Assembler: Assembler[F, C1, C1Dbs],
    )(implicit
      monadF: Monad[F],
      flattener: Flattener[C0Dbs :: C1Dbs :: HNil, CDbs],
    ): ParentAssembler[F, A, ADb :: CDbs] =
      mkParent(
        par,
        Vector(eraseAssemblerType(c0Assembler), eraseAssemblerType(c1Assembler)),
        flattener,
        monadF,
      )

    def toAssembler[C0, C1, C2, C0Dbs <: HList, C1Dbs <: HList, C2Dbs <: HList, CDbs <: HList](
      c0Assembler: Assembler[F, C0, C0Dbs],
      c1Assembler: Assembler[F, C1, C1Dbs],
      c2Assembler: Assembler[F, C2, C2Dbs],
    )(implicit
      monadF: Monad[F],
      flattener: Flattener[C0Dbs :: C1Dbs :: C2Dbs :: HNil, CDbs],
    ): ParentAssembler[F, A, ADb :: CDbs] =
      mkParent(
        par,
        Vector(
          eraseAssemblerType(c0Assembler),
          eraseAssemblerType(c1Assembler),
          eraseAssemblerType(c2Assembler),
        ),
        flattener,
        monadF,
      )

    def toAssembler[
      C0,
      C1,
      C2,
      C3,
      C0Dbs <: HList,
      C1Dbs <: HList,
      C2Dbs <: HList,
      C3Dbs <: HList,
      CDbs <: HList,
    ](
      c0Assembler: Assembler[F, C0, C0Dbs],
      c1Assembler: Assembler[F, C1, C1Dbs],
      c2Assembler: Assembler[F, C2, C2Dbs],
      c3Assembler: Assembler[F, C3, C3Dbs],
    )(implicit
      monadF: Monad[F],
      flattener: Flattener[C0Dbs :: C1Dbs :: C2Dbs :: C3Dbs :: HNil, CDbs],
    ): ParentAssembler[F, A, ADb :: CDbs] =
      mkParent(
        par,
        Vector(
          eraseAssemblerType(c0Assembler),
          eraseAssemblerType(c1Assembler),
          eraseAssemblerType(c2Assembler),
          eraseAssemblerType(c3Assembler),
        ),
        flattener,
        monadF,
      )

    def toAssembler[
      C0,
      C1,
      C2,
      C3,
      C4,
      C0Dbs <: HList,
      C1Dbs <: HList,
      C2Dbs <: HList,
      C3Dbs <: HList,
      C4Dbs <: HList,
      CDbs <: HList,
    ](
      c0Assembler: Assembler[F, C0, C0Dbs],
      c1Assembler: Assembler[F, C1, C1Dbs],
      c2Assembler: Assembler[F, C2, C2Dbs],
      c3Assembler: Assembler[F, C3, C3Dbs],
      c4Assembler: Assembler[F, C4, C4Dbs],
    )(implicit
      monadF: Monad[F],
      flattener: Flattener[C0Dbs :: C1Dbs :: C2Dbs :: C3Dbs :: C4Dbs :: HNil, CDbs],
    ): ParentAssembler[F, A, ADb :: CDbs] =
      mkParent(
        par,
        Vector(
          eraseAssemblerType(c0Assembler),
          eraseAssemblerType(c1Assembler),
          eraseAssemblerType(c2Assembler),
          eraseAssemblerType(c3Assembler),
          eraseAssemblerType(c4Assembler),
        ),
        flattener,
        monadF,
      )

  }
}

private[doobieroll] object ToAssemblerSyntax {

  def seqToHList[HL <: HList](orig: Vector[Any]): HL = {

    @nowarn("msg=.*may not be exhaustive.*") // https://github.com/scala/bug/issues/12232
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
