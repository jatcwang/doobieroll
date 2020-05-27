package oru.impl

import oru.UngroupedAssembler.UngroupedParentAssembler
import shapeless.{::, HList}

import scala.collection.MapView
import scala.collection.immutable.ArraySeq

private[oru] final class OptUngroupedParentVisitor[F[_], A, ADb, RestDb <: HList](
  underlying: UngroupedParentVisitor[F, A, ADb :: RestDb],
) extends UngroupedParentVisitor[F, A, Option[ADb] :: RestDb] {

  override val startIdx: Int = underlying.startIdx
  override val nextIdx: Int = underlying.nextIdx

  override def recordTopLevel(dbs: ArraySeq[Any]): Unit =
    dbs(startIdx).asInstanceOf[Option[ADb]].foreach { adb =>
      underlying.recordTopLevel(dbs.updated(startIdx, adb))
    }

  override def assembleTopLevel(): Vector[F[A]] =
    underlying.assembleTopLevel()

  override def recordAsChild(parentId: Any, dbs: ArraySeq[Any]): Unit =
    dbs(startIdx).asInstanceOf[Option[ADb]].foreach { adb =>
      underlying.recordAsChild(parentId, dbs.updated(startIdx, adb))
    }

  override def assemble(): MapView[Any, Vector[F[A]]] = underlying.assemble()
}

private[oru] object OptUngroupedParentVisitor {
  def fromAssembler[F[_], A, ADb, RestDb <: HList](
    assembler: UngroupedParentAssembler[F, A, ADb :: RestDb],
    accum: Accum,
    startIdx: Int,
  ): UngroupedParentVisitor[F, A, Option[ADb] :: RestDb] =
    new OptUngroupedParentVisitor(assembler.makeVisitor(accum, startIdx))
}
