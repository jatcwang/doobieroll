package oru

import oru.impl.{
  Accum,
  OptUngroupedParentVisitor,
  OptUngroupedVisitor,
  UngroupedParentVisitor,
  UngroupedVisitor,
}
import shapeless.{::, HList, HNil}

import scala.collection.immutable.ArraySeq
import scala.collection.mutable

trait UngroupedAssembler[F[_], A, Dbs <: HList] { self =>
  // Given an offset index, returns the visitor instance which has been bound to the state accumulator,
  // as well as the size of input this visitor consumes
  private[oru] def makeVisitor(
    accum: Accum,
    idx: Int,
  ): UngroupedVisitor[F, A, Dbs]

  def optional[ADb, RestDb <: HList](
    implicit ev: (ADb :: RestDb) =:= Dbs,
  ): UngroupedAssembler[F, A, Option[ADb] :: RestDb] = {
    new UngroupedAssembler[F, A, Option[ADb] :: RestDb] {
      private[oru] override def makeVisitor(
        accum: Accum,
        idx: Int,
      ): UngroupedVisitor[F, A, Option[ADb] :: RestDb] =
        OptUngroupedVisitor.fromAssembler(
          self.asInstanceOf[UngroupedAssembler[F, A, ADb :: RestDb]],
          accum,
          idx,
        )
    }
  }
}

object UngroupedAssembler {

  trait UngroupedParentAssembler[F[_], A, Dbs <: HList] extends UngroupedAssembler[F, A, Dbs] {
    self =>
    private[oru] override def makeVisitor(
      accum: Accum,
      idx: Int,
    ): UngroupedParentVisitor[F, A, Dbs]

    final override def optional[ADb, RestDb <: HList](
      implicit ev: (ADb :: RestDb) =:= Dbs,
    ): UngroupedParentAssembler[F, A, Option[ADb] :: RestDb] = {
      new UngroupedParentAssembler[F, A, Option[ADb] :: RestDb] {
        private[oru] override def makeVisitor(
          accum: Accum,
          idx: Int,
        ): UngroupedParentVisitor[F, A, Option[ADb] :: RestDb] =
          OptUngroupedParentVisitor.fromAssembler(
            self.asInstanceOf[UngroupedParentAssembler[F, A, ADb :: RestDb]],
            accum,
            idx,
          )
      }
    }
  }

  def assembleUngrouped[F[_], A, Dbs <: HList](
    ungroupedParentAssembler: UngroupedParentAssembler[F, A, Dbs],
  )(
    rows: Vector[Dbs],
  ): Vector[F[A]] = {
    if (rows.isEmpty) return Vector.empty
    val accum = new Accum()

    val parVis = ungroupedParentAssembler.makeVisitor(accum, 0)

    rows.foreach { dbs =>
      parVis.recordTopLevel(hlistToArraySeq(dbs))
    }

    parVis.assembleTopLevel()
  }

  private def hlistToArraySeq[Dbs <: HList](
    h: Dbs,
  ): ArraySeq[Any] = {
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
    ArraySeq.from(arr)
  }

}
