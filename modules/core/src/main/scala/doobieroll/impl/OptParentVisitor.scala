package doobieroll.impl

import doobieroll.ImplTypes.LazyMap
import doobieroll.Assembler.ParentAssembler
import shapeless.{::, HList}

import scala.collection.immutable.Vector

private[doobieroll] final class OptParentVisitor[F[_], A, ADb, RestDb <: HList](
  underlying: ParentVisitor[F, A, ADb :: RestDb],
) extends ParentVisitor[F, A, Option[ADb] :: RestDb] {

  override val startIdx: Int = underlying.startIdx
  override val nextIdx: Int = underlying.nextIdx

  override def recordTopLevel(dbs: Vector[Any]): Unit =
    dbs(startIdx).asInstanceOf[Option[ADb]].foreach { adb =>
      underlying.recordTopLevel(dbs.updated(startIdx, adb))
    }

  override def assembleTopLevel(): Vector[F[A]] =
    underlying.assembleTopLevel()

  override def recordAsChild(parentId: Any, dbs: Vector[Any]): Unit =
    dbs(startIdx).asInstanceOf[Option[ADb]].foreach { adb =>
      underlying.recordAsChild(parentId, dbs.updated(startIdx, adb))
    }

  override def assemble(): LazyMap[Any, Vector[F[A]]] = underlying.assemble()
}

private[doobieroll] object OptParentVisitor {
  def fromAssembler[F[_], A, ADb, RestDb <: HList](
    assembler: ParentAssembler[F, A, ADb :: RestDb],
    accum: Accum,
    startIdx: Int,
  ): ParentVisitor[F, A, Option[ADb] :: RestDb] =
    new OptParentVisitor(assembler.makeVisitor(accum, startIdx))
}
