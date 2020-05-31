package doobierolltest.model

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
      ems,
    )

  def fromDbFallible(db: DbCompany, ems: Vector[Department]): Either[Err, Company] =
    Either.cond(
      !db.name.contains("err"),
      Company(
        db.id,
        db.name,
        ems,
      ),
      Err(s"company ${db.name}"),
    )

}
