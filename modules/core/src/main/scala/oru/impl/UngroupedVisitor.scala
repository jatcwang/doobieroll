package oru.impl
import shapeless._

import scala.collection.immutable.ArraySeq

private[oru] trait UngroupedVisitor[F[_], A, Dbs <: HList] {

  def startIdx: Int
  def nextIdx: Int

  def recordAsChild(parentId: Any, d: ArraySeq[Any]): Unit

  def assemble(): collection.MapView[Any, Vector[F[A]]]
}

private[oru] trait UngroupedParentVisitor[F[_], A, Dbs <: HList] extends UngroupedVisitor[F, A, Dbs] {
  def recordTopLevel(dbs: ArraySeq[Any]): Unit
  def assembleTopLevel(): Vector[F[A]]
}
