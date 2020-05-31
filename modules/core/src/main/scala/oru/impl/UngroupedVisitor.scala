package oru.impl
import oru.ImplTypes.LazyMap
import shapeless._

import scala.collection.immutable.Vector

private[oru] trait UngroupedVisitor[F[_], A, Dbs <: HList] {

  def startIdx: Int
  def nextIdx: Int

  def recordAsChild(parentId: Any, d: Vector[Any]): Unit

  def assemble(): LazyMap[Any, Vector[F[A]]]
}

private[oru] trait UngroupedParentVisitor[F[_], A, Dbs <: HList]
    extends UngroupedVisitor[F, A, Dbs] {
  def recordTopLevel(dbs: Vector[Any]): Unit
  def assembleTopLevel(): Vector[F[A]]
}
