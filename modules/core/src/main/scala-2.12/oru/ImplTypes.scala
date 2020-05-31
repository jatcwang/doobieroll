package oru

private[oru] object ImplTypes {
  type LazyMap[K, +V] = scala.collection.Map[K, V]
}
