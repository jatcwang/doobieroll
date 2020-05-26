package orutest

import java.util.UUID

import cats.Id
import oru.UngroupedAssembler.UngroupedParentAssembler
import oru.syntax._
import oru.{Atom, Par, UngroupedAssembler}
import orutest.model._
import shapeless.{::, HNil}

object TestDataInstances {

  val employeeAtom: Atom[Id, Employee, DbEmployee :: HNil] =
    new Atom[Id, Employee, DbEmployee :: HNil] {
      override def construct(h: DbEmployee :: HNil): Employee =
        Employee.fromDb(h.head)
    }

  val invoiceAtom: Atom[Id, Invoice, DbInvoice :: HNil] =
    new Atom[Id, Invoice, DbInvoice :: HNil] {
      override def construct(db: DbInvoice :: HNil): Invoice = Invoice.fromDb(db.head)
    }

  // FIXME: shouldn't need explicit type annotation
  val departmentPar: Par.Aux[Id, Department, DbDepartment, Employee :: HNil] =
    Par.make[Id, Department, DbDepartment, Employee, UUID](
      (d: DbDepartment) => d.id,
      Department.fromDb
    )

  val companyPar: Par.Aux[Id, Company, DbCompany, Department :: HNil] =
    Par.make[Id, Company, DbCompany, Department, UUID]((d: DbCompany) => d.id, Company.fromDb)

  val enterprisePar: Par.Aux[Id, Enterprise, DbCompany, Department :: Invoice :: HNil] =
    Par.make2[Id, Enterprise, DbCompany, Department, Invoice, UUID]((d: DbCompany) => d.id, Enterprise.fromDb)

  val employeeAssembler: UngroupedAssembler[Id, Employee, DbEmployee :: HNil] =
    employeeAtom.asUnordered
  val departmentAssembler
    : UngroupedParentAssembler[Id, Department, DbDepartment :: DbEmployee :: HNil] =
    departmentPar.asUnordered(employeeAssembler)
  val companyAssembler
    : UngroupedParentAssembler[Id, Company, DbCompany :: DbDepartment :: DbEmployee :: HNil] =
    companyPar.asUnordered(departmentAssembler)
  val companyOptAssembler
    : UngroupedParentAssembler[Id, Company, DbCompany :: Option[DbDepartment] :: Option[
      DbEmployee
    ] :: HNil] =
    companyPar.asUnordered(departmentPar.asUnordered(employeeAssembler.optional).optional)

  val invoiceAssembler: UngroupedAssembler[Id, Invoice, DbInvoice :: HNil] = invoiceAtom.asUnordered

  val enterpriseAssembler: UngroupedAssembler.UngroupedParentAssembler[
    Id,
    Enterprise,
    DbCompany :: DbDepartment :: DbEmployee :: DbInvoice :: HNil
  ] = enterprisePar.asUnordered(
    departmentAssembler,
    invoiceAssembler
  )

}
