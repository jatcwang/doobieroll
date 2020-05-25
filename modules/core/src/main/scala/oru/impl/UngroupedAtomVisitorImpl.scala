package oru.impl

import oru.{Atom, EE}
import shapeless._

import scala.collection.immutable.ArraySeq

private[oru] final class UngroupedAtomVisitorImpl[A, ADb](atom: Atom[A, ADb :: HNil], accum: Accum, idx: Int) extends UngroupedVisitor[A, ADb :: HNil] {

  private val thisRawLookup = accum.getRawLookup[ADb](idx)

  def nextIdx = idx + 1

  override def recordAsChild(parentId: Any, d: ArraySeq[Any]): Unit =
    thisRawLookup.addOne(parentId -> d(idx).asInstanceOf[ADb])

  override def assemble(): collection.MapView[Any, Vector[Either[EE, A]]] =
    thisRawLookup.sets.view
      .mapValues(valueSet => valueSet.toVector.map(v => atom.construct(v :: HNil)))

}
