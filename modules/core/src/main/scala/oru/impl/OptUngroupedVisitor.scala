package oru.impl

import oru.UngroupedAssembler
import shapeless.{::, HList}

import scala.collection.MapView
import scala.collection.immutable.ArraySeq

private[oru] final class OptUngroupedVisitor[F[_], A, ADb, RestDb <: HList](
  underlying: UngroupedVisitor[F, A, ADb :: RestDb],
) extends UngroupedVisitor[F, A, Option[ADb] :: RestDb] {

  override val startIdx: Int = underlying.startIdx
  override val nextIdx: Int = underlying.nextIdx

  override def recordAsChild(parentId: Any, dbs: ArraySeq[Any]): Unit =
    dbs(startIdx).asInstanceOf[Option[_]].foreach { adb =>
      underlying.recordAsChild(parentId, dbs.updated(startIdx, adb))
    }

  override def assemble(): MapView[Any, Vector[F[A]]] = underlying.assemble()
}

private[oru] object OptUngroupedVisitor {

  def fromAssembler[F[_], A, ADb, RestDb <: HList](
    assembler: UngroupedAssembler[F, A, ADb :: RestDb],
    accum: Accum,
    startIdx: Int
  ): UngroupedVisitor[F, A, Option[ADb] :: RestDb] = {
    new OptUngroupedVisitor(assembler.makeVisitor(accum, startIdx))
  }
}
