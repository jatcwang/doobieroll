package oru.impl

import oru.impl.Accum.AnyKeyMultiMap
import oru.{Atom, EE}
import shapeless._

import scala.collection.immutable.ArraySeq
import scala.collection.mutable

private[oru] final class UngroupedAtomVisitorImpl[A, ADb](
  atom: Atom[A, ADb :: HNil],
  accum: Accum,
  override val startIdx: Int
) extends UngroupedVisitor[A, ADb :: HNil] {

  private val thisRawLookup: AnyKeyMultiMap[ADb] = accum.getRawLookup[ADb](startIdx)

  override val nextIdx: Int = startIdx + 1

  override def recordAsChild(parentId: Any, d: ArraySeq[Any]): Unit = {
    val buf = thisRawLookup.getOrElseUpdate(parentId, mutable.ArrayBuffer.empty[ADb])
    buf += d(startIdx).asInstanceOf[ADb]
  }


  override def assemble(): collection.MapView[Any, Vector[Either[EE, A]]] =
    thisRawLookup.view
      .mapValues(values => values.distinct.toVector.map(v => atom.construct(v :: HNil)))

}


