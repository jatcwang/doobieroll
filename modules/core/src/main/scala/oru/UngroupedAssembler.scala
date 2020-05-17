package oru

import cats.implicits._
import oru.impl.{Accum, UngroupedParentVisitor, UngroupedVisitor}
import shapeless.ops.hlist.Prepend
import shapeless.ops.nat.ToInt
import shapeless.{::, HList, HNil, Nat}

import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq
import scala.collection.{mutable, MapView}

trait UngroupedAssembler[A, Dbs <: HList] { self =>
  // Given an offset index, returns the visitor instance which has been bound to the state accumulator,
  // as well as the size of input this visitor consumes
  private[oru] def makeVisitor(
    accum: Accum,
    idx: Int
  ): (Int, UngroupedVisitor[A, Dbs])

  def optional[ADb, RestDb <: HList](
    implicit ev: (ADb :: RestDb) =:= Dbs
  ): UngroupedAssembler[A, Option[ADb] :: RestDb] = {
    new UngroupedAssembler[A, Option[ADb] :: RestDb] {
      override private[oru] def makeVisitor(
        accum: Accum,
        idx: Int
      ): (Int, UngroupedVisitor[A, Option[ADb] :: RestDb]) = {
        val v = new UngroupedVisitor[A, Option[ADb] :: RestDb] {
          val (size, underlying) = self.makeVisitor(accum, idx)

          override def recordAsChild(parentId: Any, dbs: ArraySeq[Any]): Unit =
            dbs(idx).asInstanceOf[Option[ADb]].foreach { adb =>
              // FIXME: mutation :(
              underlying.recordAsChild(parentId, dbs.updated(idx, adb))
            }

          override def assemble(): MapView[Any, Vector[Either[EE, A]]] = underlying.assemble()
        }
        (v.size, v)
      }
    }
  }
}

object UngroupedAssembler {
  implicit def forParentOf1[A, ADb, C, RestDb <: HList](
    implicit mker: Par.Aux[A, ADb, C :: HNil, Vector[C] :: HNil],
    childUnorderedAssembler: UngroupedAssembler[C, RestDb]
  ): UnorderedParentAssembler[A, ADb :: RestDb] = new UnorderedParentAssembler[A, ADb :: RestDb] {

    override private[oru] def makeVisitor(
      accum: Accum,
      idx: Int
    ): (Int, UngroupedParentVisitor[A, ADb :: RestDb]) = {
      val v = new UngroupedParentVisitor[A, ADb :: RestDb] {

        val (childSize, visChild) = childUnorderedAssembler.makeVisitor(accum, idx + 1)

        val thisRawLookup: mutable.MultiDict[Any, ADb] = accum.getRawLookup[ADb](idx)

        override def recordAsChild(parentId: Any, d: ArraySeq[Any]): Unit = {
          val adb = d(idx).asInstanceOf[ADb]
          thisRawLookup.addOne(parentId -> adb)
          val id = mker.getId(adb)
          visChild.recordAsChild(parentId = id, d)
        }

        override def recordTopLevel(dbs: ArraySeq[Any]): Unit = {
          val adb = dbs(idx).asInstanceOf[ADb]
          val thisId = mker.getId(adb)
          accum.addToTopLevel(thisId, adb)
          visChild.recordAsChild(parentId = thisId, dbs)
        }

        override def assemble(): collection.MapView[Any, Vector[Either[EE, A]]] = {
          thisRawLookup.sets.view.mapValues { valueSet =>
            val childValues = visChild.assemble()
            valueSet.toVector.map { v =>
              val thisId = mker.getId(v)
              for {
                thisChildren <- childValues.getOrElse(thisId, Vector.empty).sequence
                a <- mker.constructWithChild(v, thisChildren :: HNil)
              } yield a
            }
          }
        }

        override def assembleTopLevel(): Vector[Either[EE, A]] = {
          accum.getTopLevel[ADb].map { adb =>
            val childValues = visChild.assemble()
            val thisId = mker.getId(adb)
            for {
              thisChildren <- childValues.getOrElse(thisId, Vector.empty).sequence
              a <- mker.constructWithChild(adb, thisChildren :: HNil)
            } yield a
          }
        }.toVector
      }

      ((v.childSize + 1) -> v)
    }
  }

  import shapeless.ops.hlist._

  private def convertToVisitor[HL <: HList, AnyDbs <: HList](
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

//  implicit def forParentOf2[A, ADb, C0, C1, C0Db <: HList, C1Db <: HList, RestDb <: HList](
//    implicit mker: Par.Aux[A, ADb, C0 :: C1 :: HNil, Vector[C0] :: Vector[C1] :: HNil],
//    childUnorderedAssembler0: UngroupedAssembler[C0, C0Db],
//    childUnorderedAssembler1: UngroupedAssembler[C1, C1Db],
//    prependDb: Prepend.Aux[C0Db, C1Db, RestDb]
//  ): UnorderedParentAssembler[A, ADb :: RestDb] = {
//    val _ = prependDb
//    new UnorderedParentAssembler[A, ADb :: RestDb] {
//
//      override private[oru] def makeVisitor(
//        accum: Accum,
//        catKey: String,
//        idx: Int
//      ): (Int, UngroupedParentVisitor[A, ADb :: RestDb]) = {
//        val v = new UngroupedParentVisitor[A, ADb :: RestDb] {
//
//          val assemblers = childUnorderedAssembler0 :: childUnorderedAssembler1 :: HNil
//          private val childStartIdx: Int = idx + 1
//          val (lastIdx, visitors) = convertToVisitor(Vector.empty, accum, childStartIdx, assemblers)
//
//          val thisRawLookup: mutable.MultiDict[Any, ADb] = accum.getRawLookup[ADb](idx.toString)
//
//          override def recordAsChild(parentId: Any, d: ArraySeq[Any]): Unit = {
//            val adb = d(idx).asInstanceOf[ADb]
//            thisRawLookup.addOne(parentId -> adb)
//            val id = mker.getId(adb)
//            visitors.foreach(v => v.recordAsChild(id, d))
//          }
//
//          override def recordTopLevel(dbs: ArraySeq[Any]): Unit = {
//            val adb = dbs(idx).asInstanceOf[ADb]
//            val thisId = mker.getId(adb)
//            accum.addToTopLevel(thisId, adb)
//            visitors.foreach(v => v.recordAsChild(parentId = thisId, dbs))
//          }
//
//          override def assemble(): collection.MapView[Any, Vector[Either[EE, A]]] = {
//            thisRawLookup.sets.view.mapValues { valueSet =>
//              val childValues: Vector[MapView[Any, Vector[Either[EE, Any]]]] =
//                visitors.map(v => v.assemble())
//              valueSet.toVector.map { adb =>
//                val thisId = mker.getId(adb)
//                val childValuesEither = childValues.map(childLookupByParent =>
//                  childLookupByParent.getOrElse(thisId, Vector.empty)
//                )
//                for {
//                  successChildren <- gogo(Vector.empty, childValuesEither)
//                  a <- mker.constructWithChild(adb, seqToHList[Vector[C0] :: Vector[C1] :: HNil](successChildren))
//                } yield a
//              }
//            }
//          }
//
//          override def assembleTopLevel(): Vector[Either[EE, A]] = {
//            accum.getTopLevel[ADb].map { adb =>
//              val childValues: Vector[MapView[Any, Vector[Either[EE, Any]]]] =
//                visitors.map(v => v.assemble())
//              val thisId = mker.getId(adb)
//              val childValuesEither = childValues.map(childLookupByParent =>
//                childLookupByParent.getOrElse(thisId, Vector.empty)
//              )
//              for {
//                successChildren <- gogo(accum = Vector.empty, childValuesEither)
//                a <- mker.constructWithChild(adb, seqToHList[Vector[C0] :: Vector[C1] :: HNil](successChildren))
//              } yield a
//            }
//          }.toVector
//        }
//
//        ((v.lastIdx + 1) -> v)
//      }
//    }
//  }

  private def seqToHList[HL <: HList](orig: Vector[Any]): HL = {

    @tailrec def impl(acc: HList, rest: Vector[Any]): HL =
      rest match {
        case Vector() => acc.asInstanceOf[HL]
        case i +: rest    => impl(i :: acc, rest)
      }

    // Reverse so we can use ::
    impl(HNil, orig.reverse)
  }

  @tailrec private def gogo(
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


  implicit def forAtom[A, ADb](
    implicit atom: Atom[A, ADb :: HNil]
  ): UngroupedAssembler[A, ADb :: HNil] = {
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
        1 -> v
      }
    }
  }

  implicit def toOptionalAssembler[A, ADb, RestDb <: HList](
    implicit mkvis: UngroupedAssembler[A, ADb :: RestDb]
  ): UngroupedAssembler[A, Option[ADb] :: RestDb] =
    mkvis.optional

  implicit def toOptionalParentAssembler[A, ADb, RestDb <: HList](
    implicit mkvis: UnorderedParentAssembler[A, ADb :: RestDb]
  ): UnorderedParentAssembler[A, Option[ADb] :: RestDb] =
    mkvis.optional

  trait UnorderedParentAssembler[A, Dbs <: HList] extends UngroupedAssembler[A, Dbs] { self =>
    override private[oru] def makeVisitor(
      accum: Accum,
      idx: Int
    ): (Int, UngroupedParentVisitor[A, Dbs])

    final override def optional[ADb, RestDb <: HList](
      implicit ev: (ADb :: RestDb) =:= Dbs
    ): UnorderedParentAssembler[A, Option[ADb] :: RestDb] = {
      new UnorderedParentAssembler[A, Option[ADb] :: RestDb] {
        override private[oru] def makeVisitor(
          accum: Accum,
          idx: Int
        ): (Int, UngroupedParentVisitor[A, Option[ADb] :: RestDb]) = {
          val v = new UngroupedParentVisitor[A, Option[ADb] :: RestDb] {
            val (size, underlying) = self.makeVisitor(accum, idx)

            override def recordTopLevel(dbs: ArraySeq[Any]): Unit =
              dbs(idx).asInstanceOf[Option[ADb]].foreach { adb =>
                underlying.recordTopLevel(dbs.updated(idx, adb))
              }

            override def assembleTopLevel(): Vector[Either[EE, A]] =
              underlying.assembleTopLevel()

            override def recordAsChild(parentId: Any, dbs: ArraySeq[Any]): Unit =
              dbs(idx).asInstanceOf[Option[ADb]].foreach { adb =>
                underlying.recordAsChild(parentId, dbs.updated(idx, adb))
              }

            override def assemble(): MapView[Any, Vector[Either[EE, A]]] =
              underlying.assemble()
          }
          v.size -> v
        }
      }
    }
  }
  def hlistToArraySeq[Dbs <: HList, N <: Nat](
    h: Dbs
  )(
    implicit length: Length.Aux[Dbs, N],
    toInt: ToInt[N]
  ): ArraySeq[Any] = {
    val _ = length

    val arr = new Array[Any](toInt())

    @scala.annotation.tailrec
    def impl(i: Int, h: HList): Unit = {
      h match {
        case x :: r => {
          arr.update(i, x)
          impl(i + 1, r)
        }
        case HNil => ()
      }
    }

    impl(0, h)

    ArraySeq.from(arr)
  }

  def assembleUngrouped[A, Dbs <: HList, N <: Nat](
    rows: Vector[Dbs],
  )(
    implicit ungroupedParentAssembler: UnorderedParentAssembler[A, Dbs],
    length: Length.Aux[Dbs, N],
    toInt: ToInt[N]
  ): Vector[Either[EE, A]] = {
    if (rows.isEmpty) return Vector.empty
    val accum = Accum.mkEmpty()

    val (_, parVis) = ungroupedParentAssembler.makeVisitor(accum, 0)

    rows.foreach { dbs =>
      parVis.recordTopLevel(hlistToArraySeq(dbs))
    }

    parVis.assembleTopLevel()
  }

}
