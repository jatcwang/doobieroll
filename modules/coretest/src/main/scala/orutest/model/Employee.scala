package orutest.model

import java.util.UUID

final case class Employee(
  id: UUID,
  name: String,
)

object Employee {
  def fromDb(db: DbEmployee): Employee =
    Employee(
      db.id,
      db.name,
    )

  def fromDbFallible(db: DbEmployee): Either[Err, Employee] =
    Either.cond(
      !db.name.contains("err"),
      Employee(
        db.id,
        db.name,
      ),
      Err(s"employee ${db.name}"),
    )
}
