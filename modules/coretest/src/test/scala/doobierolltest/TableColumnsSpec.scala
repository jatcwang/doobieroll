package doobierolltest

import doobieroll.TableColumns
import doobieroll.TableColumns.NoSuchField
import shapeless.test.illTyped
import zio.test._
import zio.test.Assertion._
import testutils.FragmentAssertions._

object TableColumnsSpec extends DefaultRunnableSpec {

  def spec =
    suite("TableColumnSpec")(
      test("'listStr' returns comma separate column names") {
        assertStringEqual(TestClass.columns.listStr, "a,str_field,snake_case,pascal_case")
      },
      test("'listF' returns comma separate column names Fragment") {
        assertFragmentSqlEqual(TestClass.columns.listF, "a,str_field,snake_case,pascal_case ")
      },
      test(
        "'listWithParenStr' returns comma separate column names, surround by parenthesis",
      ) {
        assertStringEqual(
          TestClass.columns.listWithParenStr,
          "(a,str_field,snake_case,pascal_case)",
        )
      },
      test(
        "'listWithParenF' returns Fragment of comma separate column names, surround by parenthesis",
      ) {
        assertFragmentSqlEqual(
          TestClass.columns.listWithParenF,
          "(a,str_field,snake_case,pascal_case) ",
        )
      },
      test("'prefixedStr' returns list of field all prefixed") {
        assertStringEqual(
          TestClass.columns.prefixedStr("pre"),
          "pre.a,pre.str_field,pre.snake_case,pre.pascal_case",
        )
      },
      test("'prefixedF' returns list of field all prefixed Fragment") {
        assertFragmentSqlEqual(
          TestClass.columns.prefixedF("pre"),
          "pre.a,pre.str_field,pre.snake_case,pre.pascal_case ",
        )
      },
      test("'tableNamePrefixedStr' returns ") {
        assertStringEqual(
          TestClass.columns.tableNamePrefixedStr,
          "test_class.a,test_class.str_field,test_class.snake_case,test_class.pascal_case",
        )
      },
      test("'tableNamePrefixedF' returns ") {
        assertFragmentSqlEqual(
          TestClass.columns.tableNamePrefixedF,
          "test_class.a,test_class.str_field,test_class.snake_case,test_class.pascal_case ",
        )
      },
      test(
        "'parameterizedStr' returns same number of '?' as the number of columns, separated by commas",
      ) {
        assertStringEqual(TestClass.columns.parameterizedStr, "?,?,?,?")
      },
      test(
        "'parameterizedF' returns Fragment with same number of '?' as the number of columns, separated by commas",
      ) {
        assertFragmentSqlEqual(TestClass.columns.parameterizedF, "?,?,?,? ")
      },
      test(
        "'parameterizedWithParenStr' is similar to 'parameterized' but the output is additionally surrounded by parenthesis",
      ) {
        assertStringEqual(TestClass.columns.parameterizedWithParenStr, "(?,?,?,?)")
      },
      test(
        "'parameterizedWithParenF' is similar to 'parameterized' but the output is additionally surrounded by parenthesis",
      ) {
        assertFragmentSqlEqual(TestClass.columns.parameterizedWithParenF, "(?,?,?,?) ")
      },
      test(
        "'fromFieldStr' returns the column name for an existing field",
      ) {
        assert(TestClass.columns.fromFieldStr("PascalCase"))(isRight(equalTo("pascal_case")))
      },
      test(
        "'fromFieldStr' returns an error for a non existing field",
      ) {
        assert(TestClass.columns.fromFieldStr("notAField"))(isLeft(equalTo(NoSuchField())))
      },
      test(
        "'fromFieldF' returns the column name fragment for an existing field",
      ) {
        assert(TestClass.columns.fromFieldF("PascalCase").map(_.query.sql))(
          isRight(equalTo("pascal_case")),
        )
      },
      test(
        "'fromFieldF' returns an error for a non existing field",
      ) {
        assert(TestClass.columns.fromFieldF("notAField"))(isLeft(equalTo(NoSuchField())))
      },
      test(
        "joinMap",
      ) {
        assertFragmentSqlEqual(
          TestClass.columns.joinMap(c => s"x.$c AS x_$c"),
          "x.a AS x_a,x.str_field AS x_str_field,x.snake_case AS x_snake_case,x.pascal_case AS x_pascal_case ",
        )
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
