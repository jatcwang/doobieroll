package doobieroll

import shapeless.{::, HList}
import doobieroll.impl.{ParentVisitor, Accum, OptParentVisitor}

trait ParentAssembler[F[_], A, Dbs <: HList] extends Assembler[F, A, Dbs] {
  self =>
  private[doobieroll] override def makeVisitor(
    accum: Accum,
    idx: Int,
  ): ParentVisitor[F, A, Dbs]

  final override def optional[ADb, RestDb <: HList](
    implicit ev: (ADb :: RestDb) =:= Dbs,
  ): ParentAssembler[F, A, Option[ADb] :: RestDb] = {
    new ParentAssembler[F, A, Option[ADb] :: RestDb] {
      private[doobieroll] override def makeVisitor(
        accum: Accum,
        idx: Int,
      ): ParentVisitor[F, A, Option[ADb] :: RestDb] =
        OptParentVisitor.fromAssembler(
          self.asInstanceOf[ParentAssembler[F, A, ADb :: RestDb]],
          accum,
          idx,
        )
    }
  }
}

