package oru

private[oru] object ImplTypes {
  type LazyMap[K, +V] = scala.collection.MapView[K, V]
}
