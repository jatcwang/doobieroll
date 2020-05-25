package orutest.model

import java.util.UUID

final case class DbEmployee(
  id: UUID,
  departmentId: UUID,
  name: String,
)
