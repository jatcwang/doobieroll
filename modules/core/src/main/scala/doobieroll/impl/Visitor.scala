package doobieroll.impl
import doobieroll.ImplTypes.LazyMap
import shapeless._

import scala.collection.immutable.Vector

private[doobieroll] trait Visitor[F[_], A, Dbs <: HList] {

  def startIdx: Int
  def nextIdx: Int

  def recordAsChild(parentId: Any, d: Vector[Any]): Unit

  def assemble(): LazyMap[Any, Vector[F[A]]]
}

private[doobieroll] trait ParentVisitor[F[_], A, Dbs <: HList] extends Visitor[F, A, Dbs] {
  def recordTopLevel(dbs: Vector[Any]): Unit
  def assembleTopLevel(): Vector[F[A]]
}
