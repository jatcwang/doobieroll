package doobieroll

import cats.data.NonEmptyList
import shapeless._
import shapeless.ops.hlist.{Mapper, ToTraversable}
import shapeless.ops.record.Keys
import shapeless.tag.Tagged

import scala.annotation.implicitNotFound

sealed abstract case class TableColumns[T](
  tableName: String,
  allColumns: NonEmptyList[String],
) {

  lazy val list: String = allColumns.toList.mkString(",")

  lazy val listWithParen: String = "(" + list + ")"

  def prefixed(prefix: String, withParen: Boolean = false): String = {
    val fieldsStr =
      allColumns.map(field => s"$prefix.$field").toList.mkString(",")
    if (withParen) "(" + fieldsStr + ")"
    else fieldsStr
  }

  lazy val parameterized: String =
    allColumns.map(_ => "?").toList.mkString(",")

  lazy val parameterizedWithParen: String = "(" + parameterized + ")"

}

object TableColumns {

  private def snakeCaseName(str: String): String =
    str
      .replaceAll(
        "([A-Z]+)([A-Z][a-z])",
        "$1_$2",
      )
      .replaceAll("([a-z\\d])([A-Z])", "$1_$2")
      .toLowerCase

  def deriveSnakeTableColumns[T](tableName: String)(
    implicit mkTableColumns: MkTableColumns[T],
  ): TableColumns[T] = {
    val names = mkTableColumns.allColumns.map(TableColumns.snakeCaseName)
    new TableColumns[T](tableName, names) {}
  }

}

// A separate class to prevent automatic derivation
@implicitNotFound(
  "Cannot derive TableColumns instance. Please check that the type is a case class " + "and has at least 1 parameter",
)
private trait MkTableColumns[T] {
  def allColumns: NonEmptyList[String]
}

private object MkTableColumns {
  object symbolName extends Poly1 {
    implicit def atTaggedSymbol[T]: symbolName.Case[Symbol with Tagged[T]] {
      type Result = String
    } = at[Symbol with Tagged[T]](_.name)
  }

  implicit def familyFormat[
    T,
    Repr <: HList,
    KeysRepr <: HList,
    MapperRepr <: HList,
  ](
    implicit gen: LabelledGeneric.Aux[T, Repr],
    keys: Keys.Aux[Repr, KeysRepr],
    mapper: Mapper.Aux[symbolName.type, KeysRepr, MapperRepr],
    traversable: ToTraversable.Aux[MapperRepr, List, String],
    notHList: Repr =:!= HNil,
  ): MkTableColumns[T] = {
    val _ = (gen, notHList) // Workaround for unused warning
    new MkTableColumns[T] {
      override def allColumns: NonEmptyList[String] =
        NonEmptyList.fromListUnsafe(keys().map(symbolName).toList)
    }
  }

}
