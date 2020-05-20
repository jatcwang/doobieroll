package oru.syntax

import oru.UngroupedAssembler.UngroupedParentAssembler
import oru.impl.{Accum, UngroupedParentVisitor, UngroupedVisitor}
import oru.{Atom, EE, Par, UngroupedAssembler}
import shapeless._

import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq
import scala.collection.{MapView, mutable}
import cats.implicits._

trait UnorderedExtensions {
  import UnorderedExtensions._

  implicit class AtomExtension[A, ADb](atom: Atom[A, ADb :: HNil]) {
    def asUnordered: UngroupedAssembler[A, ADb :: HNil] = UngroupedAssembler.forAtom(atom)
  }

  implicit class ParentExtension[A, ADb, Cs <: HList](par: Par.Aux[A, ADb, Cs]) {

    def asUnordered[C0, C0Dbs <: HList](c0Assembler: UngroupedAssembler[C0, C0Dbs]): UngroupedParentAssembler[A, ADb :: C0Dbs] = {
      new UngroupedParentAssembler[A, ADb :: C0Dbs] {

        override private[oru] def makeVisitor(
          accum: Accum,
          idx: Int
        ): (Int, UngroupedParentVisitor[A, ADb :: C0Dbs]) = {
          val v = new UngroupedParentVisitor[A, ADb :: C0Dbs] {

            val assemblers = c0Assembler :: HNil
            private val childStartIdx: Int = idx + 1
            val (lastIdx, visitors) = convertToVisitor(Vector.empty, accum, childStartIdx, assemblers)

            val thisRawLookup: mutable.MultiDict[Any, ADb] = accum.getRawLookup[ADb](idx)

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
                    successChildren <- gogo(Vector.empty, childValuesEither)
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
                val childValuesEither = childValues.map(childLookupByParent =>
                  childLookupByParent.getOrElse(thisId, Vector.empty)
                )
                println(childValues)
                println(childValuesEither)
                for {
                  successChildren <- gogo(accum = Vector.empty, childValuesEither)
                  a <- par.constructWithChild(adb, seqToHList[par.ChildVecs](successChildren))
                } yield a
              }
            }.toVector
          }

          ((v.lastIdx + 1) -> v)
        }
      }
    }
  }
}

private[oru] object UnorderedExtensions {

   def convertToVisitor[HL <: HList, AnyDbs <: HList](
    accumVisitors: Vector[UngroupedVisitor[Any, AnyDbs]],
    accum: Accum,
    startIdx: Int,
    assemblers: HL
  ): (Int, Vector[UngroupedVisitor[Any, AnyDbs]]) = {
    assemblers match {
      case HNil => (startIdx, Vector.empty)
      case i :: rest => {
        val (nextStartIdx, vis) = i
          .asInstanceOf[UngroupedAssembler[Any, AnyDbs]]
          .makeVisitor(
            accum,
            startIdx
          )
        convertToVisitor(accumVisitors :+ vis, accum, nextStartIdx, rest)
      }
    }
  }

  def seqToHList[HL <: HList](orig: Vector[Any]): HL = {

    @tailrec def impl(acc: HList, rest: Vector[Any]): HL =
      rest match {
        case Vector() => acc.asInstanceOf[HL]
        case i +: rest    => impl(i :: acc, rest)
      }

    // Reverse so we can use ::
    impl(HNil, orig.reverse)
  }

  @tailrec def gogo(
    accum: Vector[Vector[Any]],
    results: Vector[Vector[Either[EE, Any]]]
  ): Either[EE, Vector[Vector[Any]]] = {
    results match {
      case Vector() => Right(accum)
      case init +: rest =>
        init.sequence match {
          case l @ Left(_) => l.rightCast
          case Right(r)    => gogo(accum :+ r, rest)
        }
    }
  }


}
