package oru

import cats.implicits._
import oru.impl.{Accum, UngroupedParentVisitor, UngroupedVisitor}
import shapeless.{::, HList, HNil}

import scala.collection.{MapView, mutable}

trait UngroupedAssembler[A, Dbs <: HList] { self =>
  private[oru] def makeVisitor(accum: Accum, catKey: String): UngroupedVisitor[A, Dbs]

  def optional[ADb, RestDb <: HList](
    implicit ev: (ADb :: RestDb) =:= Dbs
  ): UngroupedAssembler[A, Option[ADb] :: RestDb] = {
    new UngroupedAssembler[A, Option[ADb] :: RestDb] {
      override def makeVisitor(
        accum: Accum,
        catKey: String
      ): UngroupedVisitor[A, Option[ADb] :: RestDb] = {
        new UngroupedVisitor[A, Option[ADb] :: RestDb] {
          val underlying: UngroupedVisitor[A, Dbs] = self.makeVisitor(accum, catKey)

          override def recordAsChild(parentId: Any, dbs: Option[ADb] :: RestDb): Unit =
            dbs.head.foreach { adb =>
              underlying.recordAsChild(parentId, ev.apply(adb :: dbs.tail))
            }

          override def assemble(): MapView[Any, Vector[Either[EE, A]]] = underlying.assemble()
        }
      }
    }
  }
}


object UngroupedAssembler {
  implicit def forParent[A, ADb, C, RestDb <: HList](
    implicit mker: Par.Aux[A, ADb, C],
    childUnorderedAssembler: UngroupedAssembler[C, RestDb]
  ): UnorderedParentAssembler[A, ADb :: RestDb] = new UnorderedParentAssembler[A, ADb :: RestDb] {

    override def makeVisitor(accum: Accum, catKey: String): UngroupedParentVisitor[A, ADb :: RestDb] =
      new UngroupedParentVisitor[A, ADb :: RestDb] {

        val childCatKey = s"$catKey.0"
        val visChild = childUnorderedAssembler.makeVisitor(accum, childCatKey)

        val thisRawLookup: mutable.MultiDict[Any, ADb] = accum.getRawLookup[ADb](catKey)

        override def recordAsChild(parentId: Any, d: ADb :: RestDb): Unit = {
          val adb :: cdb = d
          thisRawLookup.addOne(parentId -> adb)
          val id = mker.getId(adb)
          visChild.recordAsChild(parentId = id, cdb)
        }

        override def recordTopLevel(dbs: ADb :: RestDb): Unit = {
          val adb :: cdb = dbs
          val thisId = mker.getId(adb)
          accum.addToTopLevel(thisId, adb)
          visChild.recordAsChild(parentId = thisId, cdb)
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
  }

  implicit def forAtom[A, ADb](
    implicit atom: Atom[A, ADb :: HNil]
  ): UngroupedAssembler[A, ADb :: HNil] = {
    new UngroupedAssembler[A, ADb :: HNil] {
      override def makeVisitor(accum: Accum, catKey: String): UngroupedVisitor[A, ADb :: HNil] =
        new UngroupedVisitor[A, ADb :: HNil] {
          val thisRawLookup = accum.getRawLookup[ADb](catKey)

          override def recordAsChild(parentId: Any, d: ADb :: HNil): Unit =
            thisRawLookup.addOne(parentId -> d.head)

          override def assemble(): collection.MapView[Any, Vector[Either[EE, A]]] =
            thisRawLookup.sets.view
              .mapValues(valueSet => valueSet.toVector.map(v => atom.construct(v :: HNil)))
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
    private[oru] override def makeVisitor(accum: Accum, catKey: String): UngroupedParentVisitor[A, Dbs]

    final override def optional[ADb, RestDb <: HList](
      implicit ev: (ADb :: RestDb) =:= Dbs
    ): UnorderedParentAssembler[A, Option[ADb] :: RestDb] = {
      new UnorderedParentAssembler[A, Option[ADb] :: RestDb] {
        override def makeVisitor(
          accum: Accum,
          catKey: String
        ): UngroupedParentVisitor[A, Option[ADb] :: RestDb] =
          new UngroupedParentVisitor[A, Option[ADb] :: RestDb] {
            val underlying: UngroupedParentVisitor[A, Dbs] = self.makeVisitor(accum, catKey)

            override def recordTopLevel(dbs: Option[ADb] :: RestDb): Unit =
              dbs.head.foreach { adb =>
                underlying.recordTopLevel(ev.apply(adb :: dbs.tail))
              }

            override def assembleTopLevel(): Vector[Either[EE, A]] =
              underlying.assembleTopLevel()

            override def recordAsChild(parentId: Any, dbs: Option[ADb] :: RestDb): Unit =
              dbs.head.foreach { adb =>
                underlying.recordAsChild(parentId, ev.apply(adb :: dbs.tail))
              }

            override def assemble(): MapView[Any, Vector[Either[EE, A]]] =
              underlying.assemble()
          }
      }
    }
  }

  def assembleUngrouped[A, Dbs <: HList](
    rows: Vector[Dbs],
  )(implicit ungroupedParentAssembler: UnorderedParentAssembler[A, Dbs]): Vector[Either[EE, A]] = {
    if (rows.isEmpty) return Vector.empty
    val accum = Accum.mkEmpty()
    val catKey = "t"

    val parVis = ungroupedParentAssembler.makeVisitor(accum, catKey)

    rows.foreach { dbs =>
      parVis.recordTopLevel(dbs)
    }

    parVis.assembleTopLevel()
  }

}
