package example.model

import java.util.UUID

final case class Company(
  id: UUID,
  name: String,
  departments: Vector[Department],
)
