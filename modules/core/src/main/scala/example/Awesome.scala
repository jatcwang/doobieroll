package example

import shapeless.{::, HList, HNil}
import cats.implicits._
import oru.{Atom, Par}

import scala.collection.{MapView, mutable}

object Awesome {

  trait Vis[A, Dbs <: HList] {
    def recordAsChild(parentId: Any, d: Dbs): Unit

    def assemble(): collection.MapView[Any, Vector[Either[EE, A]]]
  }

  trait ParVis[A, Dbs <: HList] extends Vis[A, Dbs] {
    def recordTopLevel(dbs: Dbs): Unit
    def assembleTopLevel(): Vector[Either[EE, A]]
  }

  trait MkVis[A, Dbs <: HList] { self =>
    def mkVis(accum: Accum, catKey: String): Vis[A, Dbs]

    def optional[ADb, RestDb <: HList](
      implicit ev: (ADb :: RestDb) =:= Dbs
    ): MkVis[A, Option[ADb] :: RestDb] = {
      new MkVis[A, Option[ADb] :: RestDb] {
        override def mkVis(accum: Accum, catKey: String): Vis[A, Option[ADb] :: RestDb] = {
          new Vis[A, Option[ADb] :: RestDb] {
            val underlying: Vis[A, Dbs] = self.mkVis(accum, catKey)

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

  object MkVis {

    implicit def forParent[A, ADb, C, RestDb <: HList](
      implicit mker: Par.Aux[A, ADb, C],
      mkVisChild: MkVis[C, RestDb]
    ): MkParVis[A, ADb :: RestDb] = new MkParVis[A, ADb :: RestDb] {

      override def mkVis(accum: Accum, catKey: String): ParVis[A, ADb :: RestDb] =
        new ParVis[A, ADb :: RestDb] {

          val childCatKey = s"$catKey.0"
          val visChild = mkVisChild.mkVis(accum, childCatKey)

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

    implicit def forAtom[A, ADb](implicit atom: Atom[A, ADb :: HNil]): MkVis[A, ADb :: HNil] = {
      new MkVis[A, ADb :: HNil] {
        override def mkVis(accum: Accum, catKey: String): Vis[A, ADb :: HNil] = new Vis[A, ADb :: HNil] {
          val thisRawLookup = accum.getRawLookup[ADb](catKey)

          override def recordAsChild(parentId: Any, d: ADb :: HNil): Unit =
            thisRawLookup.addOne(parentId -> d.head)

          override def assemble(): collection.MapView[Any, Vector[Either[EE, A]]] =
            thisRawLookup.sets.view
              .mapValues(valueSet => valueSet.toVector.map(v => atom.construct(v :: HNil)))
        }

      }
    }

    implicit def optionalMkVis[A, ADb, RestDb <: HList](implicit mkvis: MkVis[A, ADb :: RestDb]): MkVis[A, Option[ADb] :: RestDb] = {
      mkvis.optional
    }

    implicit def optionalParMkVis[A, ADb, RestDb <: HList](implicit mkvis: MkParVis[A, ADb :: RestDb]): MkParVis[A, Option[ADb] :: RestDb] = {
      mkvis.optional
    }
  }

  trait MkParVis[A, Dbs <: HList] extends MkVis[A, Dbs] { self =>
    override def mkVis(accum: Accum, catKey: String): ParVis[A, Dbs]

    final override def optional[ADb, RestDb <: HList](
      implicit ev: (ADb :: RestDb) =:= Dbs
    ): MkParVis[A, Option[ADb] :: RestDb] = {
      new MkParVis[A, Option[ADb] :: RestDb] {
        override def mkVis(accum: Accum, catKey: String): ParVis[A, Option[ADb] :: RestDb] =
          new ParVis[A, Option[ADb] :: RestDb] {
            val underlying: ParVis[A, Dbs] = self.mkVis(accum, catKey)

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

  def mkVisAtom[A, Dbs <: HList](
    dbDesc: Atom[A, Dbs]
  ): MkVis[A, Dbs] = new MkVis[A, Dbs] {

    override def mkVis(accum: Accum, catKey: String): Vis[A, Dbs] = new Vis[A, Dbs] {
      val thisRawLookup = accum.getRawLookup[Dbs](catKey)

      override def recordAsChild(parentId: Any, d: Dbs): Unit =
        thisRawLookup.addOne(parentId -> d)

      override def assemble(): collection.MapView[Any, Vector[Either[EE, A]]] =
        thisRawLookup
          .sets
          .view
          .mapValues(valueSet => valueSet.toVector.map(v => dbDesc.construct(v.asInstanceOf[Dbs])))
    }

  }

  def mkVisParent[A, Id, ADb, C, CDb <: HList](
    getId: ADb => Id,
    mkVisChild: MkVis[C, CDb],
    constructWithChild: (ADb, Vector[C]) => Either[EE, A]
  ): MkParVis[A, ADb :: CDb] = new MkParVis[A, ADb :: CDb] {

    override def mkVis(accum: Accum, catKey: String): ParVis[A, ADb :: CDb] =
      new ParVis[A, ADb :: CDb] {

        val childCatKey = s"$catKey.0"
        val visChild = mkVisChild.mkVis(accum, childCatKey)

        val thisRawLookup: mutable.MultiDict[Any, ADb] = accum.getRawLookup[ADb](catKey)

        override def recordAsChild(parentId: Any, d: ADb :: CDb): Unit = {
          val adb :: cdb = d
          thisRawLookup.addOne(parentId -> adb)
          val id = getId(adb)
          visChild.recordAsChild(parentId = id, cdb)
        }

        override def recordTopLevel(dbs: ADb :: CDb): Unit = {
          val adb :: cdb = dbs
          val thisId = getId(adb)
          accum.addToTopLevel(thisId, adb)
          visChild.recordAsChild(parentId = thisId, cdb)
        }

        override def assemble(): collection.MapView[Any, Vector[Either[EE, A]]] = {
          thisRawLookup.sets.view.mapValues { valueSet =>
            val childValues = visChild.assemble()
            valueSet.toVector.map { v =>
              val thisId = getId(v)
              for {
                thisChildren <- childValues.getOrElse(thisId, Vector.empty).sequence
                a <- constructWithChild(v, thisChildren)
              } yield a
            }
          }
        }

        override def assembleTopLevel(): Vector[Either[EE, A]] = {
          accum.getTopLevel[ADb].map { adb =>
            val childValues = visChild.assemble()
            val thisId = getId(adb)
            for {
              thisChildren <- childValues.getOrElse(thisId, Vector.empty).sequence
              a <- constructWithChild(adb, thisChildren)
            } yield a
          }
        }.toVector
      }

  }

  def assembleUnordered[A, Dbs <: HList](
    rows: Vector[Dbs],
  )(implicit mkParVis: MkParVis[A, Dbs]): Vector[Either[EE, A]] = {
    if (rows.isEmpty) return Vector.empty
    val accum = Accum.mkEmpty()
    val catKey = "t"

    val parVis = mkParVis.mkVis(accum, catKey)

    rows.foreach { dbs =>
      parVis.recordTopLevel(dbs)
    }

    parVis.assembleTopLevel()
  }

  class Accum private (
    topLevelDbItem: mutable.Map[Any, Any],
    // For storing raw parent DB values because all child isn't availble yet
    rawLookup: LookupByCatKey[Any],
  ) {
    def addToTopLevel(k: Any, v: Any): Unit =
      topLevelDbItem.update(k, v)

    def getTopLevel[A]: Iterator[A] =
      topLevelDbItem.iterator.map(_._2).asInstanceOf[Iterator[A]]

    def getRawLookup[A](
      catKey: String
    ): mutable.MultiDict[Any, A] =
      rawLookup.getOrElseUpdate(catKey, mutable.MultiDict.empty[Any, Any]).asInstanceOf[mutable.MultiDict[Any, A]]

  }

  // Error type when a Db type to domain type conversion failed
  case class EE(msg: String)

  type AnyKeyMultiDict[A] = mutable.MultiDict[Any, A]
  def mkEmptyIdMap[A](): mutable.MultiDict[Any, A] = mutable.MultiDict.empty[Any, A]
  type LookupByCatKey[A] = mutable.Map[String, mutable.MultiDict[Any, A]]

  object Accum {
    def mkEmpty(): Accum = new Accum(
      topLevelDbItem = mutable.Map.empty[Any, Any],
      rawLookup = mutable.Map.empty[String, AnyKeyMultiDict[Any]]
    )
  }

}
