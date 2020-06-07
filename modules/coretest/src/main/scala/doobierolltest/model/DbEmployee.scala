package doobierolltest.model

import java.util.UUID

import doobieroll.TableColumns

final case class DbEmployee(
  id: UUID,
  departmentId: UUID,
  name: String,
)

object DbEmployee {
  val columns: TableColumns[DbEmployee] = TableColumns.deriveSnakeTableColumns("employee")
}
