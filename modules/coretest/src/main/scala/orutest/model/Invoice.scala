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

  def fromDbFallible(db: DbInvoice): Either[Err, Invoice] =
    Either.cond(
      db.amount != 0,
      Invoice(
        db.id,
        db.amount,
      ),
      Err(s"invoice ${db.amount}"),
    )
}
