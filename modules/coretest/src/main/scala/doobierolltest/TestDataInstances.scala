package doobierolltest

import cats.Id
import doobieroll.Assembler.ParentAssembler
import doobieroll._
import doobierolltest.model._
import shapeless.{::, HNil}
import cats.implicits._

object TestDataInstances {

  object Infallible {

    val employeeLeaf: LeafDef[Id, Employee, DbEmployee :: HNil] =
      new LeafDef[Id, Employee, DbEmployee :: HNil] {
        override def construct(h: DbEmployee :: HNil): Employee =
          Employee.fromDb(h.head)
      }

    val invoiceLeaf: LeafDef[Id, Invoice, DbInvoice :: HNil] =
      new LeafDef[Id, Invoice, DbInvoice :: HNil] {
        override def construct(db: DbInvoice :: HNil): Invoice = Invoice.fromDb(db.head)
      }

    val departmentParent: ParentDef.Aux[Id, Department, DbDepartment, Employee :: HNil] =
      ParentDef.make(
        (d: DbDepartment) => d.id,
        Department.fromDb,
      )

    val companyParent: ParentDef.Aux[Id, Company, DbCompany, Department :: HNil] =
      ParentDef.make((d: DbCompany) => d.id, Company.fromDb)

    val enterpriseParent: ParentDef.Aux[Id, Enterprise, DbCompany, Department :: Invoice :: HNil] =
      ParentDef.make2((d: DbCompany) => d.id, Enterprise.fromDb)

    val employeeAssembler: Assembler[Id, Employee, DbEmployee :: HNil] =
      employeeLeaf.toAssembler
    val departmentAssembler
      : ParentAssembler[Id, Department, DbDepartment :: DbEmployee :: HNil] =
      departmentParent.toAssembler(employeeAssembler)
    val companyAssembler
      : ParentAssembler[Id, Company, DbCompany :: DbDepartment :: DbEmployee :: HNil] =
      companyParent.toAssembler(departmentAssembler)
    val companyOptAssembler
      : ParentAssembler[Id, Company, DbCompany :: Option[DbDepartment] :: Option[
        DbEmployee,
      ] :: HNil] =
      companyParent.toAssembler(departmentParent.toAssembler(employeeAssembler.optional).optional)

    val invoiceAssembler: Assembler[Id, Invoice, DbInvoice :: HNil] =
      invoiceLeaf.toAssembler

    val enterpriseAssembler: Assembler.ParentAssembler[
      Id,
      Enterprise,
      DbCompany :: DbDepartment :: DbEmployee :: DbInvoice :: HNil,
    ] = enterpriseParent.toAssembler(
      departmentAssembler,
      invoiceAssembler,
    )
  }

  object Fallible {
    type ConvRes[+A] = Either[Err, A]

    val employeeLeaf: LeafDef[ConvRes, Employee, DbEmployee :: HNil] =
      new LeafDef[ConvRes, Employee, DbEmployee :: HNil] {
        override def construct(h: DbEmployee :: HNil): ConvRes[Employee] =
          Employee.fromDbFallible(h.head)
      }

    val invoiceLeaf: LeafDef[ConvRes, Invoice, DbInvoice :: HNil] =
      new LeafDef[ConvRes, Invoice, DbInvoice :: HNil] {
        override def construct(db: DbInvoice :: HNil): ConvRes[Invoice] =
          Invoice.fromDbFallible(db.head)
      }

    val departmentParent: ParentDef.Aux[ConvRes, Department, DbDepartment, Employee :: HNil] =
      ParentDef.makeF(
        (d: DbDepartment) => d.id,
        Department.fromDbFallible,
      )

    val companyParent: ParentDef.Aux[ConvRes, Company, DbCompany, Department :: HNil] =
      ParentDef.makeF((d: DbCompany) => d.id, Company.fromDbFallible)

    val enterpriseParent
      : ParentDef.Aux[ConvRes, Enterprise, DbCompany, Department :: Invoice :: HNil] =
      ParentDef.makeF2((d: DbCompany) => d.id, Enterprise.fromDbFallible)

    val employeeAssembler: Assembler[ConvRes, Employee, DbEmployee :: HNil] =
      employeeLeaf.toAssembler
    val departmentAssembler
      : ParentAssembler[ConvRes, Department, DbDepartment :: DbEmployee :: HNil] =
      departmentParent.toAssembler(employeeAssembler)
    val companyAssembler: ParentAssembler[
      ConvRes,
      Company,
      DbCompany :: DbDepartment :: DbEmployee :: HNil,
    ] =
      companyParent.toAssembler(departmentAssembler)
    val companyOptAssembler
      : ParentAssembler[ConvRes, Company, DbCompany :: Option[DbDepartment] :: Option[
        DbEmployee,
      ] :: HNil] =
      companyParent.toAssembler(departmentParent.toAssembler(employeeAssembler.optional).optional)

    val invoiceAssembler: Assembler[ConvRes, Invoice, DbInvoice :: HNil] =
      invoiceLeaf.toAssembler

    val enterpriseAssembler: ParentAssembler[
      ConvRes,
      Enterprise,
      DbCompany :: DbDepartment :: DbEmployee :: DbInvoice :: HNil,
    ] = enterpriseParent.toAssembler(
      departmentAssembler,
      invoiceAssembler,
    )
  }

}
