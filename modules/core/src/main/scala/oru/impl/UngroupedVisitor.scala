package oru.impl
import oru.EE
import shapeless._

import scala.collection.immutable.ArraySeq

private[oru] trait UngroupedVisitor[A, Dbs <: HList] {

  def startIdx: Int
  def nextIdx: Int

  def recordAsChild(parentId: Any, d: ArraySeq[Any]): Unit

  def assemble(): collection.MapView[Any, Vector[Either[EE, A]]]
}

private[oru] trait UngroupedParentVisitor[A, Dbs <: HList] extends UngroupedVisitor[A, Dbs] {
  def recordTopLevel(dbs: ArraySeq[Any]): Unit
  def assembleTopLevel(): Vector[Either[EE, A]]
}
