package oru.impl;
import scala.collection.mutable
import Accum._

private[oru] class Accum private (
  topLevelDbItem: mutable.Map[Any, Any],
  // For storing raw parent DB values because all child isn't availble yet
  rawLookup: LookupByCatKey[Any],
) {
  def addToTopLevel(k: Any, v: Any): Unit =
    topLevelDbItem.update(k, v)

  def getTopLevel[A]: Iterator[A] =
    topLevelDbItem.iterator.map(_._2).asInstanceOf[Iterator[A]]

  def getRawLookup[A](
    catKey: String
  ): mutable.MultiDict[Any, A] =
    rawLookup
      .getOrElseUpdate(catKey, mutable.MultiDict.empty[Any, Any])
      .asInstanceOf[mutable.MultiDict[Any, A]]

}

private[oru] object Accum {
  def mkEmptyIdMap[A](): mutable.MultiDict[Any, A] = mutable.MultiDict.empty[Any, A]
  type LookupByCatKey[A] = mutable.Map[String, mutable.MultiDict[Any, A]]
  type AnyKeyMultiDict[A] = mutable.MultiDict[Any, A]

  def mkEmpty(): Accum = new Accum(
    topLevelDbItem = mutable.Map.empty[Any, Any],
    rawLookup = mutable.Map.empty[String, AnyKeyMultiDict[Any]]
  )
}
