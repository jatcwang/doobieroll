package doobierolltest.model

import java.util.UUID

import doobieroll.TableColumns

final case class DbCompany(
  id: UUID,
  name: String,
)

object DbCompany {
  val columns: TableColumns[DbCompany] = TableColumns.deriveSnakeCaseTableColumns("company")
}
