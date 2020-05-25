package oru.impl;
import scala.collection.mutable
import Accum._

private[oru] class Accum private (
  topLevelDbItem: mutable.Map[Any, Any],
  // For storing raw parent DB values because all child isn't availble yet
  rawLookup: LookupByIdx[Any]
) {
  def addToTopLevel(k: Any, v: Any): Unit =
    topLevelDbItem.update(k, v)

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

  def mkEmpty(): Accum = new Accum(
    topLevelDbItem = mutable.Map.empty[Any, Any],
    rawLookup = mutable.Map.empty[Int, AnyKeyMultiMap[Any]]
  )
}
