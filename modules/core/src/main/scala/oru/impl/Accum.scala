package oru.impl;
import scala.collection.mutable
import Accum._

import scala.collection.immutable.ListMap

private[oru] class Accum() {

  val seenRootDbItem: mutable.Set[Any] = mutable.Set.empty
  val rootDbItems: mutable.ArrayBuffer[Any] = mutable.ArrayBuffer.empty[Any]
  // For storing raw parent DB values because all child isn't available yet
  val rawLookup: LookupByIdx[Any] = mutable.Map.empty[Int, AnyKeyMultiMap[Any]]

  def addRootDbItem(k: Any, v: Any): Unit = {
    if (seenRootDbItem.add(k)) {
      rootDbItems += v
    }
  }

  def getRootDbItems[A]: Iterator[A] =
    rootDbItems.iterator.asInstanceOf[Iterator[A]]

  def getRawLookup[A](
    idx: Int
  ): AnyKeyMultiMap[A] =
    rawLookup
      .getOrElseUpdate(idx, mutable.Map.empty[Any, mutable.ArrayBuffer[Any]])
      .asInstanceOf[AnyKeyMultiMap[A]]

}

private[oru] object Accum {

  type LookupByIdx[A] = mutable.Map[Int, AnyKeyMultiMap[A]]
  type AnyKeyMultiMap[A] = mutable.Map[Any, mutable.ArrayBuffer[A]]

}
