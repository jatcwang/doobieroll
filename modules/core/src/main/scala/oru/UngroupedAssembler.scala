package oru

import cats.implicits._
import oru.impl.{Accum, UngroupedParentVisitor, UngroupedVisitor}
import shapeless.ops.hlist.Length
import shapeless.ops.nat.ToInt
import shapeless.{::, HList, HNil, Nat}

import scala.collection.immutable.ArraySeq
import scala.collection.{MapView, mutable}

trait UngroupedAssembler[A, Dbs <: HList] { self =>
  // Given an offset index, returns the visitor instance which has been bound to the state accumulator,
  // as well as the size of input this visitor consumes
  private[oru] def makeVisitor(accum: Accum, catKey: String, idx: Int): (Int, UngroupedVisitor[A, Dbs])

  def optional[ADb, RestDb <: HList](
    implicit ev: (ADb :: RestDb) =:= Dbs
  ): UngroupedAssembler[A, Option[ADb] :: RestDb] = {
    new UngroupedAssembler[A, Option[ADb] :: RestDb] {
      private[oru] override def makeVisitor(
        accum: Accum,
        catKey: String,
        idx: Int
      ): (Int, UngroupedVisitor[A, Option[ADb] :: RestDb]) = {
        val v = new UngroupedVisitor[A, Option[ADb] :: RestDb] {
          val (size, underlying) = self.makeVisitor(accum, catKey, idx)

          override def recordAsChild(parentId: Any, dbs: ArraySeq[Any]): Unit =
            dbs(idx).asInstanceOf[Option[ADb]].foreach { adb =>
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
  implicit def forParent[A, ADb, C, RestDb <: HList](
    implicit mker: Par.Aux[A, ADb, C],
    childUnorderedAssembler: UngroupedAssembler[C, RestDb]
  ): UnorderedParentAssembler[A, ADb :: RestDb] = new UnorderedParentAssembler[A, ADb :: RestDb] {

    private[oru] override def makeVisitor(accum: Accum, catKey: String, idx: Int): (Int, UngroupedParentVisitor[A, ADb :: RestDb]) = {
      val v = new UngroupedParentVisitor[A, ADb :: RestDb] {

        val childCatKey = s"$catKey.0"
        val (childSize, visChild) = childUnorderedAssembler.makeVisitor(accum, childCatKey, idx + 1)

        val thisRawLookup: mutable.MultiDict[Any, ADb] = accum.getRawLookup[ADb](catKey)

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
                a <- mker.constructWithChild(v, thisChildren)
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
              a <- mker.constructWithChild(adb, thisChildren)
            } yield a
          }
        }.toVector
      }

      ((v.childSize + 1) -> v)
    }
  }

  implicit def forAtom[A, ADb](
    implicit atom: Atom[A, ADb :: HNil]
  ): UngroupedAssembler[A, ADb :: HNil] = {
    new UngroupedAssembler[A, ADb :: HNil] {
      private[oru] override def makeVisitor(accum: Accum, catKey: String, idx: Int): (Int, UngroupedVisitor[A, ADb :: HNil]) = {
        val v = new UngroupedVisitor[A, ADb :: HNil] {
          val thisRawLookup = accum.getRawLookup[ADb](catKey)

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
    private[oru] override def makeVisitor(accum: Accum, catKey: String, idx: Int): (Int, UngroupedParentVisitor[A, Dbs])

    final override def optional[ADb, RestDb <: HList](
      implicit ev: (ADb :: RestDb) =:= Dbs
    ): UnorderedParentAssembler[A, Option[ADb] :: RestDb] = {
      new UnorderedParentAssembler[A, Option[ADb] :: RestDb] {
        private[oru] override def makeVisitor(
          accum: Accum,
          catKey: String,
          idx: Int
        ): (Int, UngroupedParentVisitor[A, Option[ADb] :: RestDb]) = {
          val v = new UngroupedParentVisitor[A, Option[ADb] :: RestDb] {
            val (size, underlying) = self.makeVisitor(accum, catKey, idx)

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

    @scala.annotation.tailrec def impl(i: Int, h: HList): Unit = {
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

  def assembleUngrouped[A, Dbs <: HList,N <: Nat](
    rows: Vector[Dbs],
  )(implicit ungroupedParentAssembler: UnorderedParentAssembler[A, Dbs],
    length: Length.Aux[Dbs, N],
    toInt: ToInt[N]
  ): Vector[Either[EE, A]] = {
    if (rows.isEmpty) return Vector.empty
    val accum = Accum.mkEmpty()
    val catKey = "t"

    val (_, parVis) = ungroupedParentAssembler.makeVisitor(accum, catKey, 0)

    rows.foreach { dbs =>
      parVis.recordTopLevel(hlistToArraySeq(dbs))
    }

    parVis.assembleTopLevel()
  }

}
