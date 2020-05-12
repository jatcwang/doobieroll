package example.model

import java.util.UUID

final case class DbInvoice(
  id: UUID,
  amount: Int,
)
