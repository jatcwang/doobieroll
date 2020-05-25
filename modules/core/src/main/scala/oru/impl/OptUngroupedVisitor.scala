package oru.impl

import oru.{EE, UngroupedAssembler}
import shapeless.{::, HList}

import scala.collection.MapView
import scala.collection.immutable.ArraySeq

private[oru] final class OptUngroupedVisitor[A, ADb, RestDb <: HList](
  accum: Accum,
  startIdx: Int,
  underlyingAssembler: UngroupedAssembler[A, ADb :: RestDb]
) extends UngroupedVisitor[A, Option[ADb] :: RestDb] {

  private val (nextIndexInner, underlying) = underlyingAssembler.makeVisitor(accum, startIdx)

  val nextIndex: Int = nextIndexInner

  override def recordAsChild(parentId: Any, dbs: ArraySeq[Any]): Unit =
    dbs(startIdx).asInstanceOf[Option[_]].foreach { adb =>
      underlying.recordAsChild(parentId, dbs.updated(startIdx, adb))
    }

  override def assemble(): MapView[Any, Vector[Either[EE, A]]] = underlying.assemble()
}
