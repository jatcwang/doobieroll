package orutest.model

import java.util.UUID

import oru.EE

final case class Company(
  id: UUID,
  name: String,
  departments: Vector[Department],
)

object Company {
  def fromDb(db: DbCompany, ems: Vector[Department]): Either[EE, Company] =
    Right(
      Company(
        db.id,
        db.name,
        ems
      )
    )
}
