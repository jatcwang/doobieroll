package example.model

import java.util.UUID

import oru.EE

final case class Enterprise(
  id: UUID,
  name: String,
  departments: Vector[Department],
  invoices: Vector[Invoice]
)

object Enterprise {
  def fromDb(
    db: DbCompany,
    departments: Vector[Department],
    invoices: Vector[Invoice]
  ): Either[EE, Enterprise] = {
    Right(
      Enterprise(
        id = db.id,
        name = db.name,
        departments = departments,
        invoices = invoices
      )
    )
  }
}
