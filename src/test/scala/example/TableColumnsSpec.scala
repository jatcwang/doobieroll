package example

import oru.TableColumns
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
        "pre.a,pre.str_field,pre.snake_case,pre.pascal_case",
      )
    },
    test(
      "'prefixed' (withParen = true) returns list of field all prefixed, surrounded by parenthesis",
    ) {
      assertEqual(
        TestClass.columns.prefixed("pre", withParen = true),
        "(pre.a,pre.str_field,pre.snake_case,pre.pascal_case)",
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
    }
  )

  final private case class Empty()

  final private case class TestClass(
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
