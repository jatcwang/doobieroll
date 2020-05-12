package example.model

import java.util.UUID

import oru.EE

final case class BigCompany(
  id: UUID,
  name: String,
  departments: Vector[Department],
  invoices: Vector[Invoice]
)

object BigCompany {
  def fromDb(
    db: DbCompany,
    departments: Vector[Department],
    invoices: Vector[Invoice]
  ): Either[EE, BigCompany] = {
    Right(
      BigCompany(
        id = db.id,
        name = db.name,
        departments = departments,
        invoices = invoices
      )
    )
  }
}
