package oru

import oru.hlist.AllOptional
import oru.impl.{Accum, UngroupedParentVisitor, UngroupedVisitor}
import shapeless.{::, HList, HNil}

import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq
import scala.collection.{MapView, mutable}

trait UngroupedAssembler[A, Dbs <: HList] { self =>
  // Given an offset index, returns the visitor instance which has been bound to the state accumulator,
  // as well as the start index for the next column group
  private[oru] def makeVisitor(
    accum: Accum,
    idx: Int
  ): (Int, UngroupedVisitor[A, Dbs])

  def optional[Out <: HList](
    implicit allOptional: AllOptional.Aux[Dbs, Out]
  ): UngroupedAssembler[A, Out] = {
    new UngroupedAssembler[A, Out] {
      override private[oru] def makeVisitor(
        accum: Accum,
        idx: Int
      ): (Int, UngroupedVisitor[A, Out]) = {
        val v = new UngroupedVisitor[A, Out] {
          val (nextIdx, underlying) = self.makeVisitor(accum, idx)

          override def recordAsChild(parentId: Any, dbs: ArraySeq[Any]): Unit =
            dbs(idx).asInstanceOf[Option[Any]].foreach { adb =>
              underlying.recordAsChild(parentId, dbs.updated(idx, adb))
            }

          override def assemble(): MapView[Any, Vector[Either[EE, A]]] = underlying.assemble()
        }
        (v.nextIdx, v)
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

    final override def optional[Out <: HList](
      implicit allOptional: AllOptional.Aux[Dbs, Out]
    ): UngroupedParentAssembler[A, Out] = {
      new UngroupedParentAssembler[A, Out] {
        override private[oru] def makeVisitor(
          accum: Accum,
          idx: Int
        ): (Int, UngroupedParentVisitor[A, Out]) = {
          val visitor = new UngroupedParentVisitor[A, Out] {
            val (nextIdx, underlying) = self.makeVisitor(accum, idx)

            override def recordTopLevel(dbs: ArraySeq[Any]): Unit =
              dbs(idx).asInstanceOf[Option[Any]].foreach { adb =>
                underlying.recordTopLevel(dbs.updated(idx, adb))
              }

            override def assembleTopLevel(): Vector[Either[EE, A]] =
              underlying.assembleTopLevel()

            override def recordAsChild(parentId: Any, dbs: ArraySeq[Any]): Unit =
              dbs(idx).asInstanceOf[Option[Any]].foreach { adb =>
                underlying.recordAsChild(parentId, dbs.updated(idx, adb))
              }

            override def assemble(): MapView[Any, Vector[Either[EE, A]]] =
              underlying.assemble()
          }
          visitor.nextIdx -> visitor
        }
      }
    }
  }

  def hlistToArraySeq[Dbs <: HList](
    h: Dbs
  ): ArraySeq[Any] = {
    val arr = mutable.ArrayBuffer.empty[Any]

    @tailrec
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
