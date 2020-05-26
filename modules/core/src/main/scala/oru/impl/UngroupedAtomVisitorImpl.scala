package oru.impl

import oru.LeafDef
import oru.impl.Accum.AnyKeyMultiMap
import shapeless._

import scala.collection.immutable.ArraySeq
import scala.collection.mutable

private[oru] final class UngroupedAtomVisitorImpl[F[_], A, ADb](
  atom: LeafDef[F, A, ADb :: HNil],
  accum: Accum,
  override val startIdx: Int
) extends UngroupedVisitor[F, A, ADb :: HNil] {

  private val thisRawLookup: AnyKeyMultiMap[ADb] = accum.getRawLookup[ADb](startIdx)

  override val nextIdx: Int = startIdx + 1

  override def recordAsChild(parentId: Any, d: ArraySeq[Any]): Unit = {
    val buf = thisRawLookup.getOrElseUpdate(parentId, mutable.ArrayBuffer.empty[ADb])
    buf += d(startIdx).asInstanceOf[ADb]
  }


  override def assemble(): collection.MapView[Any, Vector[F[A]]] =
    thisRawLookup.view
      .mapValues(values => values.distinct.toVector.map(v => atom.construct(v :: HNil)))

}


