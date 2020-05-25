package oru.syntax

import oru.UngroupedAssembler.UngroupedParentAssembler
import oru.impl.{Accum, UngroupedParentVisitor, UngroupedVisitor}
import oru.{Atom, EE, Par, UngroupedAssembler}
import shapeless._

import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq
import scala.collection.{mutable, MapView}
import cats.implicits._

// FIXME: fix reflexive calls
trait UnorderedSyntax {
  import UnorderedSyntax._

  implicit class AtomExtension[A, ADb](atom: Atom[A, ADb :: HNil]) {
    def asUnordered: UngroupedAssembler[A, ADb :: HNil] = {
      new UngroupedAssembler[A, ADb :: HNil] {
        override private[oru] def makeVisitor(
          accum: Accum,
          idx: Int
        ): (Int, UngroupedVisitor[A, ADb :: HNil]) = {
          val v = new UngroupedVisitor[A, ADb :: HNil] {
            val thisRawLookup = accum.getRawLookup[ADb](idx)

            override def recordAsChild(parentId: Any, d: ArraySeq[Any]): Unit =
              thisRawLookup.addOne(parentId -> d(idx).asInstanceOf[ADb])

            override def assemble(): collection.MapView[Any, Vector[Either[EE, A]]] =
              thisRawLookup.sets.view
                .mapValues(valueSet => valueSet.toVector.map(v => atom.construct(v :: HNil)))
          }
          (idx + 1) -> v
        }
      }

    }
  }

  import shapeless.ops.hlist._
  import shapeless._

  // FIXME: extract to separate file
  type Flattener[HL <: HList, Out <: HList] = FlatMapper.Aux[HListIdentity.type, HL, Out]

  object HListIdentity extends Poly1 {
    implicit def caseHList[HL <: HList] = at[HL](identity)
  }

  private def mkParentUngrouped[A, ADb, CDbs <: HList, CDbsFlattened <: HList](
    par: Par[A, ADb],
    assemblers: Vector[UngroupedAssembler[Any, HList]],
    flattener: Flattener[CDbs, CDbsFlattened]
  ): UngroupedParentAssembler[A, ADb :: CDbsFlattened] = {
    val _ = flattener // unused. For type inference only

    new UngroupedParentAssembler[A, ADb :: CDbsFlattened] {

      override private[oru] def makeVisitor(
        accum: Accum,
        idx: Int
      ): (Int, UngroupedParentVisitor[A, ADb :: CDbsFlattened]) = {
        val v = new UngroupedParentVisitor[A, ADb :: CDbsFlattened] {

          val thisRawLookup: mutable.MultiDict[Any, ADb] = accum.getRawLookup[ADb](idx)
          val childStartIdx: Int = idx + 1
          val (idxForNext, visitors) =
            assemblers.foldLeft((childStartIdx, Vector.empty[UngroupedVisitor[A, ADb :: CDbsFlattened]])) {
              case ((currIdx, visitorsAccum), thisAssembler) =>
                val (nextIdx, vis) = thisAssembler.makeVisitor(accum, currIdx)
                (
                  nextIdx,
                  visitorsAccum :+ vis.asInstanceOf[UngroupedVisitor[A, ADb :: CDbsFlattened]]
                )
            }

          override def recordAsChild(parentId: Any, d: ArraySeq[Any]): Unit = {
            val adb = d(idx).asInstanceOf[ADb]
            thisRawLookup.addOne(parentId -> adb)
            val id = par.getId(adb)
            visitors.foreach(v => v.recordAsChild(id, d))
          }

          override def recordTopLevel(dbs: ArraySeq[Any]): Unit = {
            val adb = dbs(idx).asInstanceOf[ADb]
            val thisId = par.getId(adb)
            accum.addToTopLevel(thisId, adb)
            visitors.foreach(v => v.recordAsChild(parentId = thisId, dbs))
          }

          override def assemble(): collection.MapView[Any, Vector[Either[EE, A]]] = {
            thisRawLookup.sets.view.mapValues { valueSet =>
              val childValues: Vector[MapView[Any, Vector[Either[EE, Any]]]] =
                visitors.map(v => v.assemble())
              valueSet.toVector.map { adb =>
                val thisId = par.getId(adb)
                val childValuesEither = childValues.map(childLookupByParent =>
                  childLookupByParent.getOrElse(thisId, Vector.empty)
                )
                for {
                  successChildren <- collectSuccess(Vector.empty, childValuesEither)
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

        (v.idxForNext -> v)
      }
    }
  }

  @inline
  private def eraseAssemblerType[A, HL <: HList](
    assembler: UngroupedAssembler[A, HL]
  ): UngroupedAssembler[Any, HList] =
    assembler.asInstanceOf[UngroupedAssembler[Any, HList]]

  implicit class ParentExtension[A, ADb, Cs <: HList](par: Par.Aux[A, ADb, Cs]) {

    def asUnordered[C0, C0Dbs <: HList](
      c0Assembler: UngroupedAssembler[C0, C0Dbs]
    ): UngroupedParentAssembler[A, ADb :: C0Dbs] =
      mkParentUngrouped(
        par,
        Vector(eraseAssemblerType(c0Assembler)),
        implicitly[Flattener[C0Dbs :: HNil, C0Dbs]]
      )

    def asUnordered[C0, C1, C0Dbs <: HList, C1Dbs <: HList, CDbs <: HList](
      c0Assembler: UngroupedAssembler[C0, C0Dbs],
      c1Assembler: UngroupedAssembler[C1, C1Dbs]
    )(
      implicit flattener: Flattener[C0Dbs :: C1Dbs :: HNil, CDbs]
    ): UngroupedParentAssembler[A, ADb :: CDbs] =
      mkParentUngrouped(
        par,
        Vector(eraseAssemblerType(c0Assembler), eraseAssemblerType(c1Assembler)),
        flattener
      )
  }
}

private[oru] object UnorderedSyntax {

  def seqToHList[HL <: HList](orig: Vector[Any]): HL = {

    @tailrec def impl(acc: HList, rest: Vector[Any]): HL =
      rest match {
        case Vector()  => acc.asInstanceOf[HL]
        case i +: rest => impl(i :: acc, rest)
      }

    // Reverse so we can use ::
    impl(HNil, orig.reverse)
  }

  @tailrec def collectSuccess(
    accum: Vector[Vector[Any]],
    results: Vector[Vector[Either[EE, Any]]]
  ): Either[EE, Vector[Vector[Any]]] = {
    results match {
      case Vector() => Right(accum)
      case init +: rest =>
        init.sequence match {
          case l @ Left(_) => l.rightCast
          case Right(r)    => collectSuccess(accum :+ r, rest)
        }
    }
  }

}
