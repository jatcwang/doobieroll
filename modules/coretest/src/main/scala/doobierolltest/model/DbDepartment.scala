package doobierolltest.model

import java.util.UUID

import doobieroll.TableColumns

final case class DbDepartment(
  id: UUID,
  companyId: UUID,
  name: String,
)

object DbDepartment {
  val columns: TableColumns[DbDepartment] = TableColumns.deriveSnakeCaseTableColumns("department")
}
