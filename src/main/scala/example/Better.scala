package example

import shapeless._
import scala.collection.mutable

object Better {

  // Error type when a Db type to domain type conversion failed
  case class EE(msg: String)

  sealed trait DbDesc[A, Dbs <: HList] {}

  object DbDesc {}

  trait Parent1[A, Dbs <: HList] extends DbDesc[A, Dbs] {
    type Id
    type ThisDb
    type Child

    def name: String
    def getThisDb(dbs: Dbs): ThisDb
    def c1DbDesc: DbDesc[Child, Dbs]
    def getId(cols: Dbs): Id

    def construct(thisDb: ThisDb, c1: Vector[Child]): Either[EE, A]
  }

  trait Atom[A, Dbs <: HList] extends DbDesc[A, Dbs] {
    def name: String
    def construct(db: Dbs): Either[EE, A]
  }

  def go[A, Dbs <: HList](
    rows: Vector[Dbs]
  )(implicit dbDesc: Parent1[A, Dbs]): Either[EE, Vector[A]] = {

    val accum = Accum.mkEmpty()

    val processors: Vector[Dbs => Unit] = mkRecorderFuncForParent(
      getParentIdOpt = None,
      parentCatKey = "",
      dbDesc
    ).map(f => f(accum))

    rows.foreach { row =>
      processors.foreach(f => f(row))
    }

    // FIXME:
    ???
  }

  private def mkRecordFuncForAtom[A, Dbs <: HList](
    parentCatKey: String,
    getParentId: Dbs => Any,
    dbDesc: Atom[A, Dbs]
  ): (Accum) => Dbs => Unit = {
    val catKey = s"$parentCatKey.${dbDesc.name}"
    def f(accum: Accum)(row: Dbs): Unit =
      accum.addToChildLookup(catKey, getParentId(row), dbDesc.construct(row))

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
            accum.addToChildLookup(
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

  type LookupByParent = mutable.MultiDict[Any, Any]
  def mkEmptyIdMap(): LookupByParent = mutable.MultiDict.empty
  type AllIdMap = mutable.Map[String, LookupByParent]

  private class Accum private (
    topLevelDbItem: mutable.ArrayBuffer[Any],
    childLookup: AllIdMap
  ) {
    def addToTopLevel(a: Any): Unit =
      topLevelDbItem += a

    def addToChildLookup(
      catKey: String,
      parentId: Any,
      value: Any
    ): Unit = {
      val idMap = childLookup.getOrElseUpdate(
        catKey,
        mkEmptyIdMap()
      )
      idMap.addOne(parentId -> value)
    }
  }

  private object Accum {
    def mkEmpty(): Accum = new Accum(
      topLevelDbItem = mutable.ArrayBuffer.empty[Any],
      childLookup = mutable.Map.empty[String, LookupByParent]
    )
  }

}
