package orutest.model

import java.util.UUID

case class Invoice(
  id: UUID,
  amount: Int,
)

object Invoice {
  def fromDb(db: DbInvoice): Invoice =
    Invoice(
      db.id,
      db.amount,
    )
}
