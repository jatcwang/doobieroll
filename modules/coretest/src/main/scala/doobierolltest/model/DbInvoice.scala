package doobierolltest.model

import java.util.UUID

import doobieroll.TableColumns

final case class DbInvoice(
  id: UUID,
  amount: Int,
)

object DbInvoice {
  val columns: TableColumns[DbInvoice] = TableColumns.deriveSnakeTableColumns("invoice")
}
