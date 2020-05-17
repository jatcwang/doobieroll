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
  ): mutable.MultiDict[Any, A] =
    rawLookup
      .getOrElseUpdate(idx, mutable.MultiDict.empty[Any, Any])
      .asInstanceOf[mutable.MultiDict[Any, A]]

}

private[oru] object Accum {
  def mkEmptyIdMap[A](): mutable.MultiDict[Any, A] = mutable.MultiDict.empty[Any, A]
  type LookupByIdx[A] = mutable.Map[Int, mutable.MultiDict[Any, A]]
  type AnyKeyMultiDict[A] = mutable.MultiDict[Any, A]

  def mkEmpty(): Accum = new Accum(
    topLevelDbItem = mutable.Map.empty[Any, Any],
    rawLookup = mutable.Map.empty[Int, AnyKeyMultiDict[Any]]
  )
}
