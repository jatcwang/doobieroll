package doobierolltest

import java.util.UUID

import doobie.postgres.implicits.UuidType
import doobie.util.{Read, Write}
import doobierolltest.model.{DbCompany, DbDepartment, DbEmployee, DbInvoice}

object DoobieRowInstances {
  implicit val readDbCompany: Read[DbCompany] =
    Read[(UUID, String)].map { case (id, name) => DbCompany(id, name) }
  implicit val writeDbCompany: Write[DbCompany] =
    Write[(UUID, String)].contramap(company => (company.id, company.name))

  implicit val readDbDepartment: Read[DbDepartment] =
    Read[(UUID, UUID, String)].map { case (id, companyId, name) =>
      DbDepartment(id, companyId, name)
    }
  implicit val writeDbDepartment: Write[DbDepartment] =
    Write[(UUID, UUID, String)].contramap(department =>
      (department.id, department.companyId, department.name),
    )

  implicit val readDbEmployee: Read[DbEmployee] =
    Read[(UUID, UUID, String)].map { case (id, departmentId, name) =>
      DbEmployee(id, departmentId, name)
    }
  implicit val writeDbEmployee: Write[DbEmployee] =
    Write[(UUID, UUID, String)].contramap(employee =>
      (employee.id, employee.departmentId, employee.name),
    )

  implicit val readDbInvoice: Read[DbInvoice] =
    Read[(UUID, Int)].map { case (id, amount) => DbInvoice(id, amount) }
  implicit val writeDbInvoice: Write[DbInvoice] =
    Write[(UUID, Int)].contramap(invoice => (invoice.id, invoice.amount))
}
