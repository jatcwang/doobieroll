package oru.impl

import cats.Monad
import oru.impl.Accum.AnyKeyMultiMap
import oru.{ParentDef, UngroupedAssembler}
import oru.syntax.UnorderedSyntax.{collectSuccess, seqToHList}
import shapeless._

import scala.collection.immutable.ArraySeq
import scala.collection.{mutable, MapView}

private[oru] final class UngroupedParentVisitorImpl[F[_], A, ADb, CDbs <: HList](
  par: ParentDef[F, A, ADb],
  accum: Accum,
  override val startIdx: Int,
  assemblers: Vector[UngroupedAssembler[F, Any, HList]],
  FMonad: Monad[F],
) extends UngroupedParentVisitor[F, A, ADb :: CDbs] {

  private val thisRawLookup: AnyKeyMultiMap[ADb] = accum.getRawLookup[ADb](startIdx)
  private val childStartIdx: Int = startIdx + 1
  private val (idxForNext, visitors) =
    assemblers.foldLeft((childStartIdx, Vector.empty[UngroupedVisitor[F, Any, ADb :: CDbs]])) {
      case ((currIdx, visitorsAccum), thisAssembler) =>
        val vis = thisAssembler.makeVisitor(accum, currIdx)
        (
          vis.nextIdx,
          visitorsAccum :+ vis.asInstanceOf[UngroupedVisitor[F, Any, ADb :: CDbs]],
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
    accum.addRootDbItem(thisId, adb)
    visitors.foreach(v => v.recordAsChild(parentId = thisId, dbs))
  }

  override def assemble(): collection.MapView[Any, Vector[F[A]]] = {
    thisRawLookup.view.mapValues { values =>
      val childValues: Vector[MapView[Any, Vector[F[Any]]]] =
        visitors.map(v => v.assemble())
      values.distinct.toVector.map { adb =>
        val thisId = par.getId(adb)
        val childValuesF: Vector[Vector[F[Any]]] =
          childValues.map(childLookupByParent =>
            childLookupByParent.getOrElse(thisId, Vector.empty),
          )
        FMonad.flatMap(collectSuccess(childValuesF)(FMonad)) { successChildren =>
          par.constructWithChild(adb, seqToHList[par.ChildVecs](successChildren))
        }
      }
    }
  }

  override def assembleTopLevel(): Vector[F[A]] = {
    accum.getRootDbItems[ADb].map { adb =>
      val childValues: Vector[MapView[Any, Vector[F[Any]]]] =
        visitors.map(v => v.assemble())
      val thisId = par.getId(adb)
      val childValuesF: Vector[Vector[F[Any]]] = childValues
        .map(childLookupByParent => childLookupByParent.getOrElse(thisId, Vector.empty))
      FMonad.flatMap(collectSuccess(childValuesF)(FMonad)) { successChildren =>
        par.constructWithChild(adb, seqToHList[par.ChildVecs](successChildren))
      }
    }
  }.toVector
}
