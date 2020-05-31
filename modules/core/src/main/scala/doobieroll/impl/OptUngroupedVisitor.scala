package doobieroll.impl

import doobieroll.ImplTypes.LazyMap
import doobieroll.UngroupedAssembler
import shapeless.{::, HList}

import scala.collection.immutable.Vector

private[doobieroll] final class OptUngroupedVisitor[F[_], A, ADb, RestDb <: HList](
  underlying: UngroupedVisitor[F, A, ADb :: RestDb],
) extends UngroupedVisitor[F, A, Option[ADb] :: RestDb] {

  override val startIdx: Int = underlying.startIdx
  override val nextIdx: Int = underlying.nextIdx

  override def recordAsChild(parentId: Any, dbs: Vector[Any]): Unit =
    dbs(startIdx).asInstanceOf[Option[_]].foreach { adb =>
      underlying.recordAsChild(parentId, dbs.updated(startIdx, adb))
    }

  override def assemble(): LazyMap[Any, Vector[F[A]]] = underlying.assemble()
}

private[doobieroll] object OptUngroupedVisitor {

  def fromAssembler[F[_], A, ADb, RestDb <: HList](
    assembler: UngroupedAssembler[F, A, ADb :: RestDb],
    accum: Accum,
    startIdx: Int,
  ): UngroupedVisitor[F, A, Option[ADb] :: RestDb] =
    new OptUngroupedVisitor(assembler.makeVisitor(accum, startIdx))
}
