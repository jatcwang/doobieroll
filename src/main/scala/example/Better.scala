package example

import shapeless.{Id => _, _}

import scala.collection.{mutable, MapView}
import cats.implicits._

object Better {

  // Error type when a Db type to domain type conversion failed
  case class EE(msg: String)

  trait ParentDef[A, IdType, ThisDb] {
    def name: String
    def getId(dbs: ThisDb): IdType
  }

  sealed trait DbDesc[A, Dbs] {}

  trait Parent1[A, Dbs <: HList] extends DbDesc[A, Dbs] {
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

  object DbDesc {
    implicit class DbDescOps[A, Dbs](val dbDesc: DbDesc[A, Dbs]) extends AnyVal {
      def contramapDbs[NewDbs <: HList](
        parentName: String,
        extractCDb: NewDbs => Dbs
      ): DbDesc[A, NewDbs] = {
        dbDesc match {
          case desc: Parent1[A, Dbs] =>
            new Parent1[A, NewDbs] {
              override type Id = desc.Id
              override type ThisDb = desc.ThisDb
              override type Child = desc.Child

              override val name: String = parentName + "." + desc.name

              override def getThisDb(dbs: NewDbs): ThisDb = desc.getThisDb(extractCDb(dbs))

              override def c1DbDesc: DbDesc[Child, NewDbs] =
                desc.c1DbDesc.contramapDbs(parentName, extractCDb)

              override def getId(cols: NewDbs): desc.Id = desc.getId(extractCDb(cols))

              override def getIdFromThisDb(t: ThisDb): Id = desc.getIdFromThisDb(t)

              override def construct(
                thisDb: ThisDb,
                c1: Vector[Child]
              ): Either[EE, A] =
                desc.construct(thisDb, c1)
            }

          case atom: Atom[A, Dbs] =>
            new Atom[A, NewDbs] {
              override val name: String = parentName + "." + atom.name
              override def construct(db: NewDbs): Either[EE, A] =
                atom.construct(extractCDb(db))
            }
        }
      }
    }
  }

  object Parent1 {
    type Aux[A0, Dbs0 <: HList, C0] = Parent1[A0, Dbs0] {
      type Child = C0
    }

    implicit class Parent1Ops[C, CDb <: HList](val child1: Parent1[C, CDb]) extends AnyVal {
      def forParent[A, Idd, Adb](
        parentDef: ParentDef[A, Idd, Adb],
        constructA: (Adb, Vector[C]) => Either[EE, A]
      ): Parent1[A, Adb :: CDb] = new Parent1[A, Adb :: CDb] {
        override type Id = Idd
        override type ThisDb = Adb
        override type Child = C

        override val name: String = parentDef.name + "." + child1.name

        override def getThisDb(dbs: Adb :: CDb): ThisDb = dbs.head

        override val c1DbDesc: DbDesc[Child, Adb :: CDb] =
          child1.contramapDbs[Adb :: CDb](parentDef.name, _.tail)

        override def getId(cols: Adb :: CDb): Id = parentDef.getId(cols.head)

        override def getIdFromThisDb(t: Adb): Id = parentDef.getId(t)

        override def construct(thisDb: Adb, c1: Vector[C]): Either[EE, A] = constructA(thisDb, c1)
      }
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

          override val name: String = parentDef.name + "." + atom.name

          override def getThisDb(dbs: ADb :: CDb :: HNil): ThisDb = dbs.head

          override def c1DbDesc: DbDesc[C, ADb :: CDb :: HNil] =
            new Atom[C, ADb :: CDb :: HNil] {

              override val name: String = parentDef.name + "." + atom.name

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

  def assembleUnordered[A, Dbs <: HList](
    dbDesc: Parent1[A, Dbs]
  )(
    rows: Vector[Dbs]
  ): Vector[Either[EE, A]] = {

    if (rows.isEmpty) return Vector.empty

    val accum = Accum.mkEmpty()

    // FIXME:
//    val processors: Vector[Dbs => Unit] = mkRecorderFuncForParent(
//      getParentIdOpt = None,
//      dbDesc = dbDesc
//    ).map(f => f(accum)) // bind all functions to our accumulator
//
//    rows.foreach { row =>
//      processors.foreach(f => f(row))
//    }

    constructItTop(
      accum,
      dbDesc
    )
  }

  private def constructItTop[A, Dbs <: HList](
    accum: Accum,
    dbDesc: Parent1[A, Dbs]
  ): Vector[Either[EE, A]] = {
    val childLookup = constructIt(
      accum,
      dbDesc.c1DbDesc
    )

    accum
      .getTopLevel[dbDesc.ThisDb]
      .map { thisDb =>
        val childrenE = childLookup.getOrElse(dbDesc.getIdFromThisDb(thisDb), Vector.empty).sequence

        childrenE.flatMap { children =>
          dbDesc.construct(thisDb, children)
        }

      }
      .toVector

  }

  private def constructIt[A, Dbs](
    accum: Accum,
    dbDesc: DbDesc[A, Dbs]
  ): MapView[Any, Vector[Either[EE, A]]] =
    dbDesc match {
      case thisDbDesc: Parent1[A, Dbs] => {
        val childLookupById = constructIt(
          accum,
          thisDbDesc.c1DbDesc
        )

        accum.getRawIterator(thisDbDesc.name).foreach {
          case (parentId, thisRaw) =>
            val thisDb = thisRaw.asInstanceOf[thisDbDesc.ThisDb]
            val id = thisDbDesc.getIdFromThisDb(thisDb)

            val childrenE: Either[EE, Vector[thisDbDesc.Child]] =
              childLookupById.getOrElse(id, Vector.empty).sequence

            val constructResult = childrenE.flatMap { children =>
              thisDbDesc.construct(thisDb, children)
            }

            accum.addConverted(thisDbDesc.name, parentId, constructResult)

        }

        accum.getConvertedLookupView[A](thisDbDesc.name)
      }
      case thisDbDesc: Atom[A, Dbs] => {
        accum.getConvertedLookupView[A](thisDbDesc.name)
      }
    }
// FIXME:
//  private def mkRecordFuncForAtom[A, Dbs <: HList](
//    getParentId: Dbs => Any,
//    dbDesc: Atom[A, Dbs]
//  ): (Accum) => Dbs => Unit = {
//    def f(accum: Accum)(row: Dbs): Unit =
//      accum.addConverted(dbDesc.name, getParentId(row), dbDesc.construct(row))
//
//    f
//  }
//
//  private def mkRecorderFuncForParent[A, Dbs <: HList](
//    getParentIdOpt: Option[Dbs => Any],
//    dbDesc: Parent1[A, Dbs]
//  ): Vector[Accum => Dbs => Unit] = {
//    val f = getParentIdOpt match {
//      case Some(getParentId) =>
//        (accum: Accum) =>
//          (row: Dbs) => {
//            accum.addRaw(
//              dbDesc.name,
//              getParentId(row),
//              dbDesc.getThisDb(row)
//            )
//          }
//      case None =>
//        (accum: Accum) =>
//          (row: Dbs) => {
//            accum.addToTopLevel(
//              dbDesc.getId(row),
//              dbDesc.getThisDb(row)
//            )
//          }
//    }
//
//    val getParentIdForChild: Dbs => Any = (row: Dbs) => dbDesc.getId(row)
//
//    val otherFuncs = dbDesc.c1DbDesc match {
//      case parent: Parent1[_, Dbs] =>
//        mkRecorderFuncForParent(
//          getParentIdOpt = Some(getParentIdForChild),
//          dbDesc = parent
//        )
//      case atom: Atom[_, Dbs] =>
//        Vector(
//          mkRecordFuncForAtom(
//            getParentId = getParentIdForChild,
//            dbDesc = atom
//          )
//        )
//    }
//
//    f +: otherFuncs
//
//  }

  type AnyKeyMultiDict[A] = mutable.MultiDict[Any, A]
  def mkEmptyIdMap[A](): mutable.MultiDict[Any, A] = mutable.MultiDict.empty[Any, A]
  type LookupByCatKey[A] = mutable.Map[String, mutable.MultiDict[Any, A]]

  class Accum private (
    topLevelDbItem: mutable.Map[Any, Any],
    rawLookup: LookupByCatKey[Any], // For storing raw parent DB values because all child isn't availble yet
    convertedLookup: LookupByCatKey[Either[EE, Any]] // For storing converted values
  ) {
    def addToTopLevel(k: Any, v: Any): Unit =
      topLevelDbItem.update(k, v)

    def getTopLevel[A]: Iterator[A] =
      topLevelDbItem.iterator.map(_._2).asInstanceOf[Iterator[A]]

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
      val idMap =
        convertedLookup.getOrElse(catKey, sys.error(s"ConvertedLookup for $catKey doesn't exist"))
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

  object Accum {
    def mkEmpty(): Accum = new Accum(
      topLevelDbItem = mutable.Map.empty[Any, Any],
      rawLookup = mutable.Map.empty[String, AnyKeyMultiDict[Any]],
      convertedLookup = mutable.Map.empty[String, AnyKeyMultiDict[Either[EE, Any]]] // FIXME: probably don't want to use multidict for this, as we sometimes don't want to get rid of duplicates
    )
  }

}
