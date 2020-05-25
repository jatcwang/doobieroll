package orutest

import orutest.model._
import oru.{Atom, EE, Par, UngroupedAssembler}
import oru.UngroupedAssembler.UngroupedParentAssembler
import shapeless.{::, HNil}
import oru.syntax._

object TestDataInstances {

  val employeeAtom: Atom[Employee, DbEmployee :: HNil] =
    new Atom[Employee, DbEmployee :: HNil] {
      override def construct(h: DbEmployee :: HNil): Either[EE, Employee] =
        Employee.fromDb(h.head)
    }

  val invoiceAtom: Atom[Invoice, DbInvoice :: HNil] =
    new Atom[Invoice, DbInvoice :: HNil] {
      override def construct(db: DbInvoice :: HNil): Either[EE, Invoice] = Invoice.fromDb(db.head)
    }

  val departmentPar: Par.Aux[Department, DbDepartment, Employee :: HNil] =
    Par.make((d: DbDepartment) => d.id, Department.fromDb)

  val companyPar: Par.Aux[Company, DbCompany, Department :: HNil] =
    Par.make((d: DbCompany) => d.id, Company.fromDb)

  val enterprisePar: Par.Aux[Enterprise, DbCompany, Department :: Invoice :: HNil] =
    Par.make2((d: DbCompany) => d.id, Enterprise.fromDb)

  val employeeAssembler: UngroupedAssembler[Employee, DbEmployee :: HNil] =
    employeeAtom.asUnordered
  val departmentAssembler
    : UngroupedParentAssembler[Department, DbDepartment :: DbEmployee :: HNil] =
    departmentPar.asUnordered(employeeAssembler)
  val companyAssembler
    : UngroupedParentAssembler[Company, DbCompany :: DbDepartment :: DbEmployee :: HNil] =
    companyPar.asUnordered(departmentAssembler)
  val companyOptAssembler
    : UngroupedParentAssembler[Company, DbCompany :: Option[DbDepartment] :: Option[
      DbEmployee
    ] :: HNil] =
    companyPar.asUnordered(departmentPar.asUnordered(employeeAssembler.optional).optional)

  val invoiceAssembler: UngroupedAssembler[Invoice, DbInvoice :: HNil] = invoiceAtom.asUnordered

  val enterpriseAssembler: UngroupedAssembler.UngroupedParentAssembler[
    Enterprise,
    DbCompany :: DbDepartment :: DbEmployee :: DbInvoice :: HNil
  ] = enterprisePar.asUnordered(
    departmentAssembler,
    invoiceAssembler
  )

}
