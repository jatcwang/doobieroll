package doobierolltest

import doobieroll.TableColumns
import shapeless.test.illTyped
import zio.test.Assertion._
import zio.test._

object TableColumnsSpec extends DefaultRunnableSpec {

  def assertEqual[A](left: A, right: A) = assert(left)(equalTo(right))

  def spec = suite("TableColumnSpec")(
    test("'list' returns comma separate column names") {
      assertEqual(TestClass.columns.list, "a,str_field,snake_case,pascal_case")
    },
    test(
      "'listWithParen' returns comma separate column names, surround by parenthesis",
    ) {
      assertEqual(
        TestClass.columns.listWithParen,
        "(a,str_field,snake_case,pascal_case)",
      )
    },
    test("'prefixed' returns list of field all prefixed") {
      assertEqual(
        TestClass.columns.prefixed("pre"),
        "pre.a, pre.str_field, pre.snake_case, pre.pascal_case",
      )
    },
    test("'tableNamePrefixed' returns ") {
      assertEqual(
        TestClass.columns.tableNamePrefixed,
        "test_class.a, test_class.str_field, test_class.snake_case, test_class.pascal_case"
      )
    },
    test(
      "'parameterized' returns same number of '?' as the number of columns, separated by commas",
    ) {
      assertEqual(TestClass.columns.parameterized, "?,?,?,?")
    },
    test(
      "'parameterizedWithParen' is similar to 'parameterized' but the output is additionally surrounded by parenthesis",
    ) {
      assertEqual(TestClass.columns.parameterizedWithParen, "(?,?,?,?)")
    },
    test("Derivation is not allowed for empty case classes") {
      illTyped("deriveSnakeColumnNames[Empty]")
      assertCompletes
    },
  )

  private final case class Empty()

  private final case class TestClass(
    a: Int,
    strField: String,
    snake_case: Int,
    PascalCase: Int,
  )

  private object TestClass {
    val columns: TableColumns[TestClass] =
      TableColumns.deriveSnakeTableColumns[TestClass]("test_class")
  }

}
