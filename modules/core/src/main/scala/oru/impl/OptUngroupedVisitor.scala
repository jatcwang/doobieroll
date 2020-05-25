package oru.impl

import oru.{EE, UngroupedAssembler}
import shapeless.{::, HList}

import scala.collection.MapView
import scala.collection.immutable.ArraySeq

private[oru] final class OptUngroupedVisitor[A, ADb, RestDb <: HList](
  underlying: UngroupedVisitor[A, ADb :: RestDb],
) extends UngroupedVisitor[A, Option[ADb] :: RestDb] {

  override val startIdx: Int = underlying.startIdx
  override val nextIdx: Int = underlying.nextIdx

  override def recordAsChild(parentId: Any, dbs: ArraySeq[Any]): Unit =
    dbs(startIdx).asInstanceOf[Option[_]].foreach { adb =>
      underlying.recordAsChild(parentId, dbs.updated(startIdx, adb))
    }

  override def assemble(): MapView[Any, Vector[Either[EE, A]]] = underlying.assemble()
}

private[oru] object OptUngroupedVisitor {

  def fromAssembler[A, ADb, RestDb <: HList](
    assembler: UngroupedAssembler[A, ADb :: RestDb],
    accum: Accum,
    startIdx: Int
  ): UngroupedVisitor[A, Option[ADb] :: RestDb] = {
    new OptUngroupedVisitor(assembler.makeVisitor(accum, startIdx))
  }
}
