package doobieroll

private[doobieroll] object ImplTypes {
  type LazyMap[K, +V] = scala.collection.MapView[K, V]
}
