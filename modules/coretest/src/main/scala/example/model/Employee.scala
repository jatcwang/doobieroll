package example.model

import java.util.UUID

import oru.EE

final case class Employee(
  id: UUID,
  name: String,
)

object Employee {
  def fromDb(db: DbEmployee): Either[EE, Employee] =
    Right(
      Employee(
        db.id,
        db.name
      )
    )
}
