package oru.impl;
import scala.collection.mutable
import Accum._

import scala.collection.immutable.ListMap

private[oru] class Accum() {

  var topLevelDbItem: ListMap[Any, Any] = ListMap.empty
  // For storing raw parent DB values because all child isn't availble yet
  val rawLookup: LookupByIdx[Any] = mutable.Map.empty[Int, AnyKeyMultiMap[Any]]


  def addToTopLevel(k: Any, v: Any): Unit = {
    if (!topLevelDbItem.contains(k))
      topLevelDbItem += k -> v
  }

  def getTopLevel[A]: Iterator[A] =
    topLevelDbItem.iterator.map(_._2).asInstanceOf[Iterator[A]]

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
