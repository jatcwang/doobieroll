package doobieroll.snippets
import doobie.util.fragment.Fragment
import doobieroll.TableColumns
import cats.syntax.all._

trait CommonSnippets {
  def selectColumnsFrom(tableColumns: TableColumns[_]): Fragment =
    Fragment.const(
      s"SELECT ${tableColumns.listStr} FROM ${tableColumns.tableNameStr}",
    )

  def selectColumns(
    firstColumns: Fragment,
    otherColumns: Fragment*,
  ): Fragment =
    (firstColumns +: otherColumns).toList.foldSmash(
      Fragment.const("SELECT"),
      Fragment.const0(","),
      Fragment.empty,
    )
}
