package doobierolltest.snippets

import doobieroll.TableColumns
import zio.test.DefaultRunnableSpec
import doobieroll.snippets._
import doobierolltest.testutils.FragmentAssertions._

object CommonSnippetSpec extends DefaultRunnableSpec {
  def spec =
    suite("CommonSnippetSpec")(
      suite("selectColumnsFrom")(
        test("Outputs 'SELECT col1,col2,.. FROM mytable'") {
          assertFragmentSqlEqual(selectColumnsFrom(OneField.columns), "SELECT f1 FROM table1 ") &&
          assertFragmentSqlEqual(
            selectColumnsFrom(TwoField.columns),
            "SELECT f1,f2 FROM table2 ",
          ) &&
          assertFragmentSqlEqual(
            selectColumnsFrom(ThreeField.columns),
            "SELECT f1,f2,f3 FROM table3 ",
          )
        },
      ),
      suite("selectColumns")(
        test(
          "Outputs 'SELECT' followed by the provided fragment when a single fragment is provided",
        ) {
          val result = selectColumns(
            TwoField.columns.prefixedF("p"),
          )
          assertFragmentSqlEqual(result, "SELECT p.f1,p.f2 ")
        },
        test(
          "Outputs 'SELECT' followed by the provided fragments joined by ',' when provided with multiple fragments",
        ) {
          val result = selectColumns(
            OneField.columns.listF,
            TwoField.columns.prefixedF("p"),
          )
          assertFragmentSqlEqual(result, "SELECT f1 ,p.f1,p.f2 ")
        },
      ),
    )

  private final case class OneField(
    f1: Int,
  )

  private object OneField {
    val columns: TableColumns[OneField] =
      TableColumns.deriveSnakeCaseTableColumns[OneField]("table1")
  }

  private final case class TwoField(
    f1: Int,
    f2: Int,
  )

  private object TwoField {
    val columns: TableColumns[TwoField] =
      TableColumns.deriveSnakeCaseTableColumns[TwoField]("table2")
  }

  private final case class ThreeField(
    f1: Int,
    f2: Int,
    f3: Int,
  )

  private object ThreeField {
    val columns: TableColumns[ThreeField] =
      TableColumns.deriveSnakeCaseTableColumns[ThreeField]("table3")
  }
}
