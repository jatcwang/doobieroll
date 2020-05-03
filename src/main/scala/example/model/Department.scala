package example.model

import java.util.UUID

final case class Department(
  id: UUID,
  name: String,
  employees: Vector[Employee],
)
