package oru

import oru.impl.{
  Accum,
  OptUngroupedParentVisitor,
  OptUngroupedVisitor,
  UngroupedParentVisitor,
  UngroupedVisitor
}
import shapeless.{::, HList, HNil}

import scala.collection.immutable.ArraySeq
import scala.collection.mutable

trait UngroupedAssembler[A, Dbs <: HList] { self =>
  // Given an offset index, returns the visitor instance which has been bound to the state accumulator,
  // as well as the size of input this visitor consumes
  private[oru] def makeVisitor(
    accum: Accum,
    idx: Int
  ): (Int, UngroupedVisitor[A, Dbs])

  def optional[ADb, RestDb <: HList](
    implicit ev: (ADb :: RestDb) =:= Dbs
  ): UngroupedAssembler[A, Option[ADb] :: RestDb] = {
    new UngroupedAssembler[A, Option[ADb] :: RestDb] {
      private[oru] override def makeVisitor(
        accum: Accum,
        idx: Int
      ): (Int, UngroupedVisitor[A, Option[ADb] :: RestDb]) = {
        val v = new OptUngroupedVisitor[A, ADb, RestDb](
          accum,
          idx,
          self.asInstanceOf[UngroupedAssembler[A, ADb :: RestDb]]
        )
        (v.nextIndex, v)
      }
    }
  }
}

object UngroupedAssembler {

  trait UngroupedParentAssembler[A, Dbs <: HList] extends UngroupedAssembler[A, Dbs] { self =>
    private[oru] override def makeVisitor(
      accum: Accum,
      idx: Int
    ): (Int, UngroupedParentVisitor[A, Dbs])

    final override def optional[ADb, RestDb <: HList](
      implicit ev: (ADb :: RestDb) =:= Dbs
    ): UngroupedParentAssembler[A, Option[ADb] :: RestDb] = {
      new UngroupedParentAssembler[A, Option[ADb] :: RestDb] {
        private[oru] override def makeVisitor(
          accum: Accum,
          idx: Int
        ): (Int, UngroupedParentVisitor[A, Option[ADb] :: RestDb]) = {
          val v = new OptUngroupedParentVisitor[A, ADb, RestDb](
            accum,
            idx,
            self.asInstanceOf[UngroupedParentAssembler[A, ADb :: RestDb]]
          )
          (v.nextIndex, v)
        }
      }
    }
  }

  def hlistToArraySeq[Dbs <: HList](
    h: Dbs
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

  def assembleUngrouped[A, Dbs <: HList](
    ungroupedParentAssembler: UngroupedParentAssembler[A, Dbs],
  )(
    rows: Vector[Dbs],
  ): Vector[Either[EE, A]] = {
    if (rows.isEmpty) return Vector.empty
    val accum = Accum.mkEmpty()

    val (_, parVis) = ungroupedParentAssembler.makeVisitor(accum, 0)

    rows.foreach { dbs =>
      parVis.recordTopLevel(hlistToArraySeq(dbs))
    }

    parVis.assembleTopLevel()
  }

}
