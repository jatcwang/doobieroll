package oru.impl
import oru.impl.Accum.AnyKeyMultiMap
import oru.{EE, Par, UngroupedAssembler}
import oru.syntax.UnorderedSyntax.{collectSuccess, seqToHList}
import shapeless._

import scala.collection.immutable.ArraySeq
import scala.collection.{MapView, mutable}

private[oru] final class UngroupedParentVisitorImpl[A, ADb, CDbs <: HList](
  par: Par[A, ADb],
  accum: Accum,
  override val startIdx: Int,
  assemblers: Vector[UngroupedAssembler[Any, HList]],
) extends UngroupedParentVisitor[A, ADb :: CDbs] {

  private val thisRawLookup: AnyKeyMultiMap[ADb] = accum.getRawLookup[ADb](startIdx)
  private val childStartIdx: Int = startIdx + 1
  private val (idxForNext, visitors) =
    assemblers.foldLeft((childStartIdx, Vector.empty[UngroupedVisitor[A, ADb :: CDbs]])) {
      case ((currIdx, visitorsAccum), thisAssembler) =>
        val vis = thisAssembler.makeVisitor(accum, currIdx)
        (
          vis.nextIdx,
          visitorsAccum :+ vis.asInstanceOf[UngroupedVisitor[A, ADb :: CDbs]]
        )
    }

  val nextIdx: Int = idxForNext

  override def recordAsChild(parentId: Any, d: ArraySeq[Any]): Unit = {
    val adb = d(startIdx).asInstanceOf[ADb]
    val buf = thisRawLookup.getOrElseUpdate(parentId, mutable.ArrayBuffer.empty[ADb])
    buf += adb
    val id = par.getId(adb)
    visitors.foreach(v => v.recordAsChild(id, d))
  }

  override def recordTopLevel(dbs: ArraySeq[Any]): Unit = {
    val adb = dbs(startIdx).asInstanceOf[ADb]
    val thisId = par.getId(adb)
    accum.addToTopLevel(thisId, adb)
    visitors.foreach(v => v.recordAsChild(parentId = thisId, dbs))
  }

  override def assemble(): collection.MapView[Any, Vector[Either[EE, A]]] = {
    thisRawLookup.view.mapValues { values =>
      val childValues: Vector[MapView[Any, Vector[Either[EE, Any]]]] =
        visitors.map(v => v.assemble())
      values.distinct.toVector.map { adb =>
        val thisId = par.getId(adb)
        val childValuesEither =
          childValues.map(childLookupByParent => childLookupByParent.getOrElse(thisId, Vector.empty)
          )
        for {
          successChildren <- collectSuccess(accum = Vector.empty, results = childValuesEither)
          a <- par.constructWithChild(adb, seqToHList[par.ChildVecs](successChildren))
        } yield a
      }
    }
  }

  override def assembleTopLevel(): Vector[Either[EE, A]] = {
    accum.getTopLevel[ADb].map { adb =>
      val childValues: Vector[MapView[Any, Vector[Either[EE, Any]]]] =
        visitors.map(v => v.assemble())
      val thisId = par.getId(adb)
      val childValuesEither = childValues
        .map(childLookupByParent => childLookupByParent.getOrElse(thisId, Vector.empty))
      for {
        successChildren <- collectSuccess(accum = Vector.empty, childValuesEither)
        a <- par.constructWithChild(adb, seqToHList[par.ChildVecs](successChildren))
      } yield a
    }
  }.toVector
}
