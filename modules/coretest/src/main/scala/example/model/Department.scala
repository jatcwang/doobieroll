package example.model

import java.util.UUID

import example.Awesome.EE

final case class Department(
  id: UUID,
  name: String,
  employees: Vector[Employee],
)

object Department {
  def fromDb(db: DbDepartment, ems: Vector[Employee]): Either[EE, Department] =
    Right(
      Department(
        db.id,
        db.name,
        ems
      )
    )
}
