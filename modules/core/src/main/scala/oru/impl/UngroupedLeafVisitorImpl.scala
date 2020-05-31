package oru.impl

import oru.ImplTypes.LazyMap
import oru.LeafDef
import oru.impl.Accum.AnyKeyMultiMap
import shapeless._

import scala.annotation.nowarn
import scala.collection.mutable
import scala.collection.immutable.Vector

private[oru] final class UngroupedLeafVisitorImpl[F[_], A, ADb](
  leafDef: LeafDef[F, A, ADb :: HNil],
  accum: Accum,
  override val startIdx: Int,
) extends UngroupedVisitor[F, A, ADb :: HNil] {

  private val thisRawLookup: AnyKeyMultiMap[ADb] = accum.getRawLookup[ADb](startIdx)

  override val nextIdx: Int = startIdx + 1

  override def recordAsChild(parentId: Any, d: Vector[Any]): Unit = {
    val buf = thisRawLookup.getOrElseUpdate(parentId, mutable.ArrayBuffer.empty[ADb])
    buf += d(startIdx).asInstanceOf[ADb]
  }

  @nowarn("msg=method mapValues.*deprecated")
  override def assemble(): LazyMap[Any, Vector[F[A]]] = {
    // Note: call to mapValues is intentional for view-like behaviour
    // In 2.12 We want MappedValues, while in 2.13 we want MapView
    // By using strict Map the performance completely tanks
    thisRawLookup
      .mapValues(values => values.distinct.toVector.map(v => leafDef.construct(v :: HNil)))
  }

}
