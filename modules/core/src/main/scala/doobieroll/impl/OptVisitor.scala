package doobieroll.impl

import doobieroll.ImplTypes.LazyMap
import doobieroll.Assembler
import shapeless.{::, HList}

import scala.collection.immutable.Vector

private[doobieroll] final class OptVisitor[F[_], A, ADb, RestDb <: HList](
  underlying: Visitor[F, A, ADb :: RestDb],
) extends Visitor[F, A, Option[ADb] :: RestDb] {

  override val startIdx: Int = underlying.startIdx
  override val nextIdx: Int = underlying.nextIdx

  override def recordAsChild(parentId: Any, dbs: Vector[Any]): Unit =
    dbs(startIdx).asInstanceOf[Option[_]].foreach { adb =>
      underlying.recordAsChild(parentId, dbs.updated(startIdx, adb))
    }

  override def assemble(): LazyMap[Any, Vector[F[A]]] = underlying.assemble()
}

private[doobieroll] object OptVisitor {

  def fromAssembler[F[_], A, ADb, RestDb <: HList](
    assembler: Assembler[F, A, ADb :: RestDb],
    accum: Accum,
    startIdx: Int,
  ): Visitor[F, A, Option[ADb] :: RestDb] =
    new OptVisitor(assembler.makeVisitor(accum, startIdx))
}
