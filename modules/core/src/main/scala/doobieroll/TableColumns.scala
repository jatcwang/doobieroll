package doobieroll

import cats.data.NonEmptyList
import cats.implicits._
import shapeless._
import shapeless.ops.hlist.{Mapper, ToTraversable}
import shapeless.ops.record.Keys
import shapeless.tag.Tagged
import doobie.Fragment
import doobieroll.TableColumns.NoSuchField

import scala.annotation.implicitNotFound

sealed abstract case class TableColumns[T](
  tableNameStr: String,
  fieldNames: NonEmptyList[String],
  transform: String => String = identity,
) {
  val allColumns: NonEmptyList[String] = fieldNames.map(transform)

  def tableNameF: Fragment = Fragment.const(tableNameStr)

  /** List th fields, separated by commas. e.g. "field1,field2,field3" */
  def list: Fragment = Fragment.const(listStr)

  def listStr: String = allColumns.toList.mkString(",")

  /** List th fields, separated by commas and surrounded by parens. e.g. "(field1,field2,field3)"
    * This makes INSERT queries easier to write like "INSERT INTO mytable VALUES $\{columns.listWithParen}"
    */
  def listWithParen: Fragment = Fragment.const(listWithParenStr)

  def listWithParenStr: String = s"($listStr)"

  /** Return string of the form '?,?,?' depending on how many fields there is for this TableColumn */
  def parameterized: Fragment =
    Fragment.const(parameterizedStr)

  def parameterizedStr: String = allColumns.map(_ => "?").toList.mkString(",")

  /** Return string of the form '(?,?,?)' depending on how many fields there is for this TableColumn. */
  def parameterizedWithParen: Fragment =
    Fragment.const(parameterizedWithParenStr)

  def parameterizedWithParenStr: String =
    s"($parameterizedStr)"

  /** Prefix each field with the default table name. e.g. "mytable.id, mytable.name, mytable.address"
    */
  def tableNamePrefixed: Fragment = Fragment.const(tableNamePrefixedStr)

  def tableNamePrefixedStr: String =
    allColumns.map(field => s"$tableNameStr.$field").toList.mkString(",")

  /** Prefix each field with the given string. e.g. "c.id, c.name, c.address" */
  def prefixed(prefix: String): Fragment = Fragment.const(prefixedStr(prefix))

  def prefixedStr(prefix: String): String =
    allColumns.map(field => s"$prefix.$field").toList.mkString(",")

  /** Return the column name associated with the provided field */
  def fromField(field: String): Either[NoSuchField, Fragment] =
    fromFieldStr(field).map(Fragment.const(_))

  def fromFieldStr(field: String): Either[NoSuchField, String] =
    Either.cond(fieldNames.contains_(field), transform(field), NoSuchField())

  /** Transform every field name using the provided function, then join them together with commas.
    * This is useful for field prefixes and aliases.
    *
    * {{{
    * cols.joinMap(c => s"person.\$c AS person_\$c") == fr"person.col1 AS person_col1, person.col2 AS person_col2"
    * }}}
    */
  def joinMap(func: String => String): Fragment =
    Fragment.const(allColumns.map(func).toList.mkString(","))

}

object TableColumns {

  private def toSnakeCase(str: String): String =
    str
      .replaceAll(
        "([A-Z]+)([A-Z][a-z])",
        "$1_$2",
      )
      .replaceAll("([a-z\\d])([A-Z])", "$1_$2")
      .toLowerCase

  def deriveSnakeCaseTableColumns[T](tableName: String)(implicit
    mkTableColumns: MkTableColumns[T],
  ): TableColumns[T] =
    deriveTableColumns[T](tableName, toSnakeCase)

  def deriveTableColumns[T](tableName: String, transform: String => String)(implicit
    mkTableColumns: MkTableColumns[T],
  ): TableColumns[T] = {
    val names = mkTableColumns.allColumns
    new TableColumns[T](tableName, names, transform) {}
  }

  case class NoSuchField() extends RuntimeException

}

// A separate class to prevent automatic derivation
@implicitNotFound(
  "Cannot derive TableColumns instance. Please check that the type is a case class and has at least 1 parameter",
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
  ](implicit
    gen: LabelledGeneric.Aux[T, Repr],
    keys: Keys.Aux[Repr, KeysRepr],
    mapper: Mapper.Aux[symbolName.type, KeysRepr, MapperRepr],
    traversable: ToTraversable.Aux[MapperRepr, List, String],
    notHList: Repr =:!= HNil,
  ): MkTableColumns[T] = {
    val _ = (gen, notHList) // These are unused otherwise
    new MkTableColumns[T] {
      override def allColumns: NonEmptyList[String] =
        NonEmptyList.fromListUnsafe(keys().map(symbolName).toList)
    }
  }

}
