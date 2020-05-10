package oru.impl
import oru.EE
import shapeless._

private[oru] trait UngroupedVisitor[A, Dbs <: HList] {
  def recordAsChild(parentId: Any, d: Dbs): Unit

  def assemble(): collection.MapView[Any, Vector[Either[EE, A]]]
}

private[oru] trait UngroupedParentVisitor[A, Dbs <: HList] extends UngroupedVisitor[A, Dbs] {
  def recordTopLevel(dbs: Dbs): Unit
  def assembleTopLevel(): Vector[Either[EE, A]]
}
