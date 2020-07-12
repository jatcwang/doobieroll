package doobieroll.impl

import cats.Monad
import doobieroll.impl.Accum.AnyKeyMultiMap
import doobieroll.{ParentDef, Assembler}
import doobieroll.syntax.ToAssemblerSyntax.{collectSuccess, seqToHList}
import shapeless._

import scala.collection.immutable.Vector
import scala.collection.mutable
import doobieroll.ImplTypes.LazyMap

import scala.annotation.nowarn

private[doobieroll] final class ParentVisitorImpl[F[_], A, ADb, CDbs <: HList](
  par: ParentDef[F, A, ADb],
  accum: Accum,
  override val startIdx: Int,
  assemblers: Vector[Assembler[F, Any, HList]],
  FMonad: Monad[F],
) extends ParentVisitor[F, A, ADb :: CDbs] {

  private val thisRawLookup: AnyKeyMultiMap[ADb] = accum.getRawLookup[ADb](startIdx)
  private val childStartIdx: Int = startIdx + 1
  private val (idxForNext, visitors) =
    assemblers.foldLeft((childStartIdx, Vector.empty[Visitor[F, Any, ADb :: CDbs]])) {
      case ((currIdx, visitorsAccum), thisAssembler) =>
        val vis = thisAssembler.makeVisitor(accum, currIdx)
        (
          vis.nextIdx,
          visitorsAccum :+ vis.asInstanceOf[Visitor[F, Any, ADb :: CDbs]],
        )
    }

  val nextIdx: Int = idxForNext

  override def recordAsChild(parentId: Any, d: Vector[Any]): Unit = {
    val adb = d(startIdx).asInstanceOf[ADb]
    val buf = thisRawLookup.getOrElseUpdate(parentId, mutable.ArrayBuffer.empty[ADb])
    buf += adb
    val id = par.getId(adb)
    visitors.foreach(v => v.recordAsChild(id, d))
  }

  override def recordTopLevel(dbs: Vector[Any]): Unit = {
    val adb = dbs(startIdx).asInstanceOf[ADb]
    val thisId = par.getId(adb)
    accum.addRootDbItem(thisId, adb)
    visitors.foreach(v => v.recordAsChild(parentId = thisId, dbs))
  }

  @nowarn("msg=method mapValues.*deprecated")
  override def assemble(): LazyMap[Any, Vector[F[A]]] = {
    // Note: call to mapValues is intentional for view-like behaviour
    // In 2.12 We want MappedValues, while in 2.13 we want MapView
    // By using strict Map the performance completely tanks
    thisRawLookup.mapValues { values =>
      val childValues: Vector[LazyMap[Any, Vector[F[Any]]]] =
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
      val childValues: Vector[LazyMap[Any, Vector[F[Any]]]] =
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
