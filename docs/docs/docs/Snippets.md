---
layout: docs
title:  "Snippets"
permalink: docs/snippets
---

# Snippets

Snippets are functions that you can use to construct common SQL fragments.

Import these function in scope with:

```scala mdoc:silent
import doobieroll.snippets._
```

Let's define some [TableColumns](tablecolumns) first and see how we can create common SQL snippets from them:

```scala mdoc:invisible
import java.util.UUID
import doobie.implicits._
```

```scala mdoc:silent
import doobieroll.TableColumns
case class DbCompany(
  id: UUID,
  name: String,
  phoneNumber: String,
)

object DbCompany {
  val columns: TableColumns[DbCompany]  = TableColumns.deriveSnakeCaseTableColumns(tableName = "company")
}

case class DbEmployee(
  id: UUID,
  companyId: UUID,
  firstName: String,
  lastName: String,
)

object DbEmployee {
  val columns: TableColumns[DbEmployee]  = TableColumns.deriveSnakeCaseTableColumns(tableName = "employee")
}
```

## Select columns from single table - `selectColumnsFrom`

```scala mdoc
selectColumnsFrom(DbCompany.columns)
```

## Select columns from multiple tables - `selectColumns`



```scala mdoc
selectColumns(
  DbCompany.columns.prefixed("c"),
  DbEmployee.columns.prefixed("e")
) ++ fr"FROM company c LEFT JOIN employee e ON company.id = employee.company_id"
```
