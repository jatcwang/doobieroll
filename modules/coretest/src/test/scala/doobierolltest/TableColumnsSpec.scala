package doobierolltest

import doobieroll.TableColumns
import shapeless.test.illTyped
import zio.test._
import testutils.FragmentAssertions._

object TableColumnsSpec extends DefaultRunnableSpec {

  def spec =
    suite("TableColumnSpec")(
      test("'list' returns comma separate column names Fragment") {
        assertFragmentSqlEqual(TestClass.columns.list, "a,str_field,snake_case,pascal_case ")
      },
      test(
        "'listWithParen' returns Fragment of comma separate column names, surround by parenthesis",
      ) {
        assertFragmentSqlEqual(
          TestClass.columns.listWithParen,
          "(a,str_field,snake_case,pascal_case) ",
        )
      },
      test("'prefixed' returns list of field all prefixed Fragment") {
        assertFragmentSqlEqual(
          TestClass.columns.prefixed("pre"),
          "pre.a,pre.str_field,pre.snake_case,pre.pascal_case ",
        )
      },
      test("'tableNamePrefixed' returns ") {
        assertFragmentSqlEqual(
          TestClass.columns.tableNamePrefixed,
          "test_class.a,test_class.str_field,test_class.snake_case,test_class.pascal_case ",
        )
      },
      test(
        "'parameterized' returns Fragment with same number of '?' as the number of columns, separated by commas",
      ) {
        assertFragmentSqlEqual(TestClass.columns.parameterized, "?,?,?,? ")
      },
      test(
        "'parameterizedWithParen' is similar to 'parameterized' but the output is additionally surrounded by parenthesis",
      ) {
        assertFragmentSqlEqual(TestClass.columns.parameterizedWithParen, "(?,?,?,?) ")
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
      TableColumns.deriveSnakeCaseTableColumns[TestClass]("test_class")
  }

}
