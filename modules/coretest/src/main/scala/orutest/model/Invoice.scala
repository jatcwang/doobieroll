package orutest.model

import java.util.UUID

import oru.EE

case class Invoice(
  id: UUID,
  amount: Int
)

object Invoice {
  def fromDb(db: DbInvoice): Either[EE, Invoice] =
    Right(
      Invoice(
        db.id,
        db.amount
      )
    )
}
