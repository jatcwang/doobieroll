package example

import shapeless._

import scala.collection.{MapView, mutable}
import cats.implicits._

object Better {

  // Error type when a Db type to domain type conversion failed
  case class EE(msg: String)

  sealed trait DbDesc[A, Dbs] {}

  object DbDesc {}

  trait ParentDef[A, IdType, ThisDb] {
    def name: String
    def getId(dbs: ThisDb): IdType
  }

  trait Parent1[A, Dbs] extends DbDesc[A, Dbs] {
    type Id
    type ThisDb
    type Child

    def name: String
    def getThisDb(dbs: Dbs): ThisDb
    def c1DbDesc: DbDesc[Child, Dbs]
    def getId(cols: Dbs): Id
    def getIdFromThisDb(t: ThisDb): Id

    def construct(thisDb: ThisDb, c1: Vector[Child]): Either[EE, A]
  }

  object Parent1 {
    type Aux[A0, Dbs0 <: HList, C0] = Parent1[A0, Dbs0] {
      type Child = C0
    }
  }

  trait Atom[A, Dbs] extends DbDesc[A, Dbs] {
    def name: String
    def construct(db: Dbs): Either[EE, A]
  }

  object Atom {

    implicit class AtomOps[C, CDb](val atom: Atom[C, CDb]) extends AnyVal {
      def forParent[A, Idd, ADb](
        parentDef: ParentDef[A, Idd, ADb],
        constructA: (ADb, Vector[C]) => Either[EE, A]
      ): Parent1[A, ADb :: CDb :: HNil] = {

        new Parent1[A, ADb :: CDb :: HNil] {
          override type Id = Idd
          override type ThisDb = ADb
          override type Child = C

          // FIXME: test if changing this to val improves performance
          override def name: String = parentDef.name

          override def getThisDb(dbs: ADb :: CDb :: HNil): ThisDb = dbs.head

          override def c1DbDesc: DbDesc[C, ADb :: CDb :: HNil] =
            new Atom[C, ADb :: CDb :: HNil] {
              // FIXME: we should be able to construct the namespaced name here
              override def name: String = atom.name

              override def construct(db: ADb :: CDb :: HNil): Either[EE, C] =
                atom.construct(db.tail.head)
            }

          override def getId(cols: ADb :: CDb :: HNil): Id =
            parentDef.getId(cols.head)

          override def construct(thisDb: ADb, c1: Vector[Child]): Either[EE, A] =
            constructA(thisDb, c1)

          override def getIdFromThisDb(t: ADb): Idd = parentDef.getId(t)
        }

      }
    }

  }

  def go[A, Dbs <: HList](
    rows: Vector[Dbs]
  )(implicit dbDesc: Parent1[A, Dbs]): Vector[Either[EE, A]] = {

    val accum = Accum.mkEmpty()

    val processors: Vector[Dbs => Unit] = mkRecorderFuncForParent(
      getParentIdOpt = None,
      parentCatKey = "",
      dbDesc
    ).map(f => f(accum)) // bind all functions to our accumulator

    rows.foreach { row =>
      processors.foreach(f => f(row))
    }

    constructItTop(
      accum,
      dbDesc
    )
  }

  private def constructItTop[A, Dbs](
    accum: Accum,
    dbDesc: Parent1[A, Dbs]
  ): Vector[Either[EE, A]] = {
    val childLookup = constructIt(
      dbDesc.name,
      accum,
      dbDesc.c1DbDesc
    )

    accum.getTopLevel[dbDesc.ThisDb].map { thisDb =>

      val childrenE = childLookup.getOrElse(dbDesc.getIdFromThisDb(thisDb), Vector.empty).sequence

      childrenE.flatMap { children =>
        dbDesc.construct(thisDb, children)
      }

    }.toVector

  }

  private def constructIt[A, Dbs](
    parentCatKey: String,
    accum: Accum,
    dbDesc: DbDesc[A, Dbs]
  ): MapView[Any, Vector[Either[EE, A]]] =
        dbDesc match {
      case thisDbDesc: Parent1[A, Dbs] => {
        val catKey = s"$parentCatKey.${thisDbDesc.name}"
        val childLookupById = constructIt(
          catKey,
          accum,
          thisDbDesc.c1DbDesc
        )

        accum.getRawIterator(catKey).foreach { case (parentId, thisRaw) =>
          val thisDb = thisRaw.asInstanceOf[thisDbDesc.ThisDb]
          val id = thisDbDesc.getIdFromThisDb(thisDb)

          val childrenE: Either[EE, Vector[thisDbDesc.Child]] = childLookupById.getOrElse(id, Vector.empty).sequence

          val constructResult = childrenE.flatMap { children =>
            thisDbDesc.construct(thisDb, children)
          }

          accum.addConverted(catKey, parentId, constructResult)

        }

        accum.getConvertedLookupView[A](catKey)
      }
      case thisDbDesc: Atom[A, Dbs] => {
        val catKey = s"$parentCatKey.${thisDbDesc.name}"
        accum.getConvertedLookupView[A](catKey)
      }
    }

  private def mkRecordFuncForAtom[A, Dbs <: HList](
    parentCatKey: String,
    getParentId: Dbs => Any,
    dbDesc: Atom[A, Dbs]
  ): (Accum) => Dbs => Unit = {
    val catKey = s"$parentCatKey.${dbDesc.name}"
    def f(accum: Accum)(row: Dbs): Unit =
      accum.addConverted(catKey, getParentId(row), dbDesc.construct(row))

    f
  }

  private def mkRecorderFuncForParent[A, Dbs <: HList](
    getParentIdOpt: Option[Dbs => Any],
    parentCatKey: String,
    dbDesc: Parent1[A, Dbs]
  ): Vector[Accum => Dbs => Unit] = {
    val catKey = s"$parentCatKey.${dbDesc.name}"

    val f = getParentIdOpt match {
      case Some(getParentId) =>
        (accum: Accum) =>
          (row: Dbs) => {
            accum.addRaw(
              catKey,
              getParentId(row),
              dbDesc.getThisDb(row)
            )
          }
      case None =>
        (accum: Accum) =>
          (row: Dbs) => {
            accum.addToTopLevel(
              dbDesc.getThisDb(row)
            )
          }
    }

    val getParentIdForChild: Dbs => Any = (row: Dbs) => dbDesc.getId(row)

    val otherFuncs = dbDesc.c1DbDesc match {
      case parent: Parent1[_, Dbs] =>
        mkRecorderFuncForParent(
          parentCatKey = catKey,
          getParentIdOpt = Some(getParentIdForChild),
          dbDesc = parent
        )
      case atom: Atom[_, Dbs] =>
        Vector(
          mkRecordFuncForAtom(
            parentCatKey = catKey,
            getParentId = getParentIdForChild,
            dbDesc = atom
          )
        )
    }

    f +: otherFuncs

  }

  type AnyKeyMultiDict[A] = mutable.MultiDict[Any, A]
  def mkEmptyIdMap[A](): mutable.MultiDict[Any, A] = mutable.MultiDict.empty[Any, A]
  type LookupByCatKey[A] = mutable.Map[String, mutable.MultiDict[Any, A]]

  private class Accum private (
    topLevelDbItem: mutable.ArrayBuffer[Any],
    rawLookup: LookupByCatKey[Any], // For storing raw parent DB values because all child isn't availble yet
    convertedLookup: LookupByCatKey[Either[EE, Any]] // For storing converted values
  ) {
    def addToTopLevel(a: Any): Unit =
      topLevelDbItem += a

    def getTopLevel[A]: Iterator[A] = {
      topLevelDbItem.iterator.asInstanceOf[Iterator[A]]
    }

    def addConverted(
      catKey: String,
      parentId: Any,
      value: Either[EE, Any]
    ): Unit = {
      val idMap = convertedLookup.getOrElseUpdate(
        catKey,
        mkEmptyIdMap()
      )
      idMap.addOne(parentId -> value)
    }

    def getConvertedLookupView[ValueType](
      catKey: String,
    ): MapView[Any, Vector[Either[EE, ValueType]]] = {
      val idMap = convertedLookup.getOrElse(catKey, sys.error(s"ConvertedLookup for ${catKey} doesn't exist"))
      idMap.sets.view.mapValues(_.toVector.asInstanceOf[Vector[Either[EE, ValueType]]])
    }

    def addRaw(
      catKey: String,
      id: Any,
      value: Any
    ): Unit = {
      val idMap = rawLookup.getOrElseUpdate(
        catKey,
        mkEmptyIdMap()
      )
      idMap.addOne(id -> value)
    }

    def getRawIterator(
      catKey: String
    ): Iterator[(Any, Any)] = {
      val idMap = rawLookup.getOrElse(catKey, sys.error(s"getRaw for $catKey not found"))
      idMap.iterator
    }


  }

  private object Accum {
    def mkEmpty(): Accum = new Accum(
      topLevelDbItem = mutable.ArrayBuffer.empty[Any],
      rawLookup = mutable.Map.empty[String, AnyKeyMultiDict[Any]],
      convertedLookup = mutable.Map.empty[String, AnyKeyMultiDict[Either[EE, Any]]] // FIXME: probably don't want to use multidict for this, as we sometimes don't want to get rid of duplicates
    )
  }

}
