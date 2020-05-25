package oru.impl

import oru.EE
import oru.UngroupedAssembler.UngroupedParentAssembler
import shapeless.{::, HList}

import scala.collection.MapView
import scala.collection.immutable.ArraySeq

private[oru] final class OptUngroupedParentVisitor[A, ADb, RestDb <: HList](
  underlying: UngroupedParentVisitor[A, ADb :: RestDb],
) extends UngroupedParentVisitor[A, Option[ADb] :: RestDb] {

  override val startIdx: Int = underlying.startIdx
  override val nextIdx: Int = underlying.nextIdx

  override def recordTopLevel(dbs: ArraySeq[Any]): Unit =
    dbs(startIdx).asInstanceOf[Option[ADb]].foreach { adb =>
      underlying.recordTopLevel(dbs.updated(startIdx, adb))
    }

  override def assembleTopLevel(): Vector[Either[EE, A]] =
    underlying.assembleTopLevel()

  override def recordAsChild(parentId: Any, dbs: ArraySeq[Any]): Unit =
    dbs(startIdx).asInstanceOf[Option[ADb]].foreach { adb =>
      underlying.recordAsChild(parentId, dbs.updated(startIdx, adb))
    }

  override def assemble(): MapView[Any, Vector[Either[EE, A]]] = underlying.assemble()
}

private[oru] object OptUngroupedParentVisitor {
  def fromAssembler[A, ADb, RestDb <: HList](
    assembler: UngroupedParentAssembler[A, ADb :: RestDb],
    accum: Accum,
    startIdx: Int
  ): UngroupedParentVisitor[A, Option[ADb] :: RestDb] = {
    new OptUngroupedParentVisitor(assembler.makeVisitor(accum, startIdx))
  }
}
