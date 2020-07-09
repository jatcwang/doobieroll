package doobieroll

import doobieroll.impl.{
  Accum,
  OptVisitor,
  Visitor,
}
import shapeless.{::, HList, HNil}

import scala.collection.mutable

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

object Assembler {

  def assemble[F[_], A, Dbs <: HList](
    parentAssembler: ParentAssembler[F, A, Dbs],
  )(
    rows: Vector[Dbs],
  ): Vector[F[A]] = {
    if (rows.isEmpty) return Vector.empty
    val accum = new Accum()

    val parVis = parentAssembler.makeVisitor(accum, 0)

    rows.foreach { dbs =>
      parVis.recordTopLevel(hlistToArraySeq(dbs))
    }

    parVis.assembleTopLevel()
  }

  private def hlistToArraySeq[Dbs <: HList](
    h: Dbs,
  ): Vector[Any] = {
    val arr = mutable.ArrayBuffer.empty[Any]

    @scala.annotation.tailrec
    def impl(h: HList): Unit = {
      h match {
        case x :: r => {
          arr += x
          impl(r)
        }
        case HNil => ()
      }
    }

    impl(h)
    arr.toVector
  }

}
