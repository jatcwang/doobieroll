package doobieroll

import cats.data.NonEmptyList
import cats.implicits._
import shapeless._
import shapeless.ops.hlist.{Mapper, ToTraversable}
import shapeless.ops.record.Keys
import shapeless.tag.Tagged
import doobie.Fragment

import scala.annotation.implicitNotFound

sealed abstract case class TableColumns[T](
  tableNameStr: String,
  allColumns: NonEmptyList[String],
) {

  @deprecated(
    "Use tableNameStr instead. From v0.2.* onwards this method will start returning Fragment since " +
      "doobie 0.9.0 allows you to directly interpolate Fragment in fr interpolators",
    since = "0.1.7",
  )
  def tableName: String = tableNameStr

  def tableNameF: Fragment = Fragment.const(tableNameStr)

  /** List th fields, separated by commas. e.g. "field1,field2,field3" */
  @deprecated(
    "Use listStr instead. From v0.2.* onwards this method will start returning Fragment since " +
      "doobie 0.9.0 allows you to directly interpolate Fragment in fr interpolators",
    since = "0.1.7",
  )
  def list: String = listStr

  def listF: Fragment = Fragment.const(listStr)

  def listStr: String = allColumns.toList.mkString(",")

  /** List th fields, separated by commas and surrounded by parens. e.g. "(field1,field2,field3)"
    * This makes INSERT queries easier to write like "INSERT INTO mytable VALUES $\{columns.listWithParen}"
    */
  @deprecated(
    "Use listWithParenStr instead. From v0.2.* onwards this method will start returning Fragment since " +
      "doobie 0.9.0 allows you to directly interpolate Fragment in fr interpolators",
    since = "0.1.7",
  )
  def listWithParen: String = listWithParenStr

  def listWithParenF: Fragment = Fragment.const(listWithParenStr)

  def listWithParenStr: String = s"($listStr)"

  /** Return string of the form '?,?,?' depending on how many fields there is for this TableColumn */
  @deprecated(
    "Use parameterizedStr instead. From v0.2.* onwards this method will start returning Fragment since " +
      "doobie 0.9.0 allows you to directly interpolate Fragment in fr interpolators",
    since = "0.1.7",
  )
  def parameterized: String = parameterizedStr

  def parameterizedF: Fragment =
    Fragment.const(parameterizedStr)

  def parameterizedStr: String = allColumns.map(_ => "?").toList.mkString(",")

  @deprecated(
    "Use parameterizedWithParenStr instead. From v0.2.* onwards this method will start returning Fragment since " +
      "doobie 0.9.0 allows you to directly interpolate Fragment in fr interpolators",
    since = "0.1.7",
  )
  /** Return string of the form '(?,?,?)' depending on how many fields there is for this TableColumn. */
  def parameterizedWithParen: String = parameterizedWithParenStr

  def parameterizedWithParenF: Fragment =
    Fragment.const(parameterizedWithParenStr)

  def parameterizedWithParenStr: String =
    s"($parameterizedStr)"

  /** Prefix each field with the default table name. e.g. "mytable.id, mytable.name, mytable.address"
    */
  @deprecated(
    "Use tableNamePrefixedStr instead. From v0.2.* onwards this method will start returning Fragment since " +
      "doobie 0.9.0 allows you to directly interpolate Fragment in fr interpolators",
    since = "0.1.7",
  )
  def tableNamePrefixed: String = tableNamePrefixedStr

  def tableNamePrefixedF: Fragment = Fragment.const(tableNamePrefixedStr)

  def tableNamePrefixedStr: String =
    allColumns.map(field => s"$tableNameStr.$field").toList.mkString(",")

  /** Prefix each field with the given string. e.g. "c.id, c.name, c.address" */
  @deprecated(
    "Use prefixedStr instead. From v0.2.* onwards this method will start returning Fragment since " +
      "doobie 0.9.0 allows you to directly interpolate Fragment in fr interpolators",
    since = "0.1.7",
  )
  def prefixed(prefix: String): String = prefixedStr(prefix)

  def prefixedF(prefix: String): Fragment = Fragment.const(prefixedStr(prefix))

  def prefixedStr(prefix: String): String =
    allColumns.map(field => s"$prefix.$field").toList.mkString(",")

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
    val names = mkTableColumns.allColumns.map(transform)
    new TableColumns[T](tableName, names) {}
  }

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
