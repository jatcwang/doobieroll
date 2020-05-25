package orutest.model

import java.util.UUID

final case class DbDepartment(
  id: UUID,
  companyId: UUID,
  name: String,
)
