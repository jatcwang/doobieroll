package oru

import oru.impl.{Accum, UngroupedParentVisitor, UngroupedVisitor}
import shapeless.{::, HList, HNil}

import scala.collection.immutable.ArraySeq
import scala.collection.{mutable, MapView}

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
      override private[oru] def makeVisitor(
        accum: Accum,
        idx: Int
      ): (Int, UngroupedVisitor[A, Option[ADb] :: RestDb]) = {
        val v = new UngroupedVisitor[A, Option[ADb] :: RestDb] {
          val (size, underlying) = self.makeVisitor(accum, idx)

          override def recordAsChild(parentId: Any, dbs: ArraySeq[Any]): Unit =
            dbs(idx).asInstanceOf[Option[ADb]].foreach { adb =>
              // FIXME: mutation :(
              underlying.recordAsChild(parentId, dbs.updated(idx, adb))
            }

          override def assemble(): MapView[Any, Vector[Either[EE, A]]] = underlying.assemble()
        }
        (v.size, v)
      }
    }
  }
}

object UngroupedAssembler {

  trait UngroupedParentAssembler[A, Dbs <: HList] extends UngroupedAssembler[A, Dbs] { self =>
    override private[oru] def makeVisitor(
      accum: Accum,
      idx: Int
    ): (Int, UngroupedParentVisitor[A, Dbs])

    final override def optional[ADb, RestDb <: HList](
      implicit ev: (ADb :: RestDb) =:= Dbs
    ): UngroupedParentAssembler[A, Option[ADb] :: RestDb] = {
      new UngroupedParentAssembler[A, Option[ADb] :: RestDb] {
        override private[oru] def makeVisitor(
          accum: Accum,
          idx: Int
        ): (Int, UngroupedParentVisitor[A, Option[ADb] :: RestDb]) = {
          val visitor = new UngroupedParentVisitor[A, Option[ADb] :: RestDb] {
            val (size, underlying) = self.makeVisitor(accum, idx)

            override def recordTopLevel(dbs: ArraySeq[Any]): Unit =
              dbs(idx).asInstanceOf[Option[ADb]].foreach { adb =>
                underlying.recordTopLevel(dbs.updated(idx, adb))
              }

            override def assembleTopLevel(): Vector[Either[EE, A]] =
              underlying.assembleTopLevel()

            override def recordAsChild(parentId: Any, dbs: ArraySeq[Any]): Unit =
              dbs(idx).asInstanceOf[Option[ADb]].foreach { adb =>
                underlying.recordAsChild(parentId, dbs.updated(idx, adb))
              }

            override def assemble(): MapView[Any, Vector[Either[EE, A]]] =
              underlying.assemble()
          }
          visitor.size -> visitor
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
