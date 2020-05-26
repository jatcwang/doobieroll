package orutest.model

import java.util.UUID

final case class Department(
  id: UUID,
  name: String,
  employees: Vector[Employee],
)

object Department {
  def fromDb(db: DbDepartment, ems: Vector[Employee]): Department =
    Department(
      db.id,
      db.name,
      ems
    )
}
