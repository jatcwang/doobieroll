package orutest.model

import java.util.UUID

final case class Company(
  id: UUID,
  name: String,
  departments: Vector[Department],
)

object Company {
  def fromDb(db: DbCompany, ems: Vector[Department]): Company =
      Company(
        db.id,
        db.name,
        ems
      )
}
