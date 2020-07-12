package doobieroll

import doobieroll.impl.{Accum, OptVisitor, Visitor}
import shapeless.{::, HList}

trait Assembler[F[_], A, Dbs <: HList] { self =>
  // Given an offset index, returns the visitor instance which has been bound to the state accumulator,
  // as well as the size of input this visitor consumes
  private[doobieroll] def makeVisitor(
    accum: Accum,
    idx: Int,
  ): Visitor[F, A, Dbs]

  def optional[ADb, RestDb <: HList](
    implicit ev: (ADb :: RestDb) =:= Dbs,
  ): Assembler[F, A, Option[ADb] :: RestDb] = {
    val _ = ev
    new Assembler[F, A, Option[ADb] :: RestDb] {
      private[doobieroll] override def makeVisitor(
        accum: Accum,
        idx: Int,
      ): Visitor[F, A, Option[ADb] :: RestDb] =
        OptVisitor.fromAssembler(
          self.asInstanceOf[Assembler[F, A, ADb :: RestDb]],
          accum,
          idx,
        )
    }
  }
}
