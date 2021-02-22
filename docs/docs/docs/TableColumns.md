---
layout: docs
title: "TableColumns"
permalink: docs/tablecolumns
---

# TableColumns

### What problem does it solve?

In Doobie, we write SQL directly. This results in quite a lot of repetition when we're specifying columns.
Take this postgres upsert query, for example:

```scala mdoc:invisible
import java.util.UUID
import doobie.postgres.implicits._ // for Write[UUID]
```

```scala mdoc:silent
import doobie.Update
import doobie.implicits._

val q: Update[DbCompany] = Update[DbCompany]("""
INSERT INTO company (
  id,
  name,
  phone_number,
  tax_number,
  address
) VALUES (
  ?, ?, ?, ?, ?
)
ON CONFLICT (id) DO UPDATE SET
  id = EXCLUDED.id,
  name = EXCLUDED.name,
  phone_number = EXCLUDED.phone_number,
  tax_number = EXCLUDED.tax_number,
  address = EXCLUDED.address
""")

case class DbCompany(
  id: UUID,
  name: String,
  phoneNumber: String,
  taxNumber: String,
  address: String
)
```

There is a lot of repetition here!

# Using TableColumns

Let's use `TableColumns` to "DRY" up the example above:

```scala mdoc:invisible
import doobie.Fragment
```

```scala mdoc:silent
import doobieroll.TableColumns

object DbCompany {
  val columns: TableColumns[DbCompany]  = TableColumns.deriveSnakeCaseTableColumns(tableName = "company")
}
```

```scala mdoc
val qq: Fragment = fr"""
  |INSERT INTO company
  |${DbCompany.columns.listWithParenF}
  |VALUES
  |${DbCompany.columns.parameterizedWithParenF}
  |ON CONFLICT (id) DO UPDATE SET
  |${updateAllNonKeyColumns(DbCompany.columns)}
""".stripMargin

// You can define your own functions to work with the list of fields!
private def updateAllNonKeyColumns(tableColumns: TableColumns[_]): Fragment =
  Fragment.const(
    // Assume first field is the primary key, so we don't need to set it
    tableColumns.allColumns.toList.drop(1).map(c => s"$c = EXCLUDED.$c").mkString(", ")
  )
```

Other than having less boilerplate, the main benefit of using `TableColumns` is **consistency**.
Since field names and order are consistent across all use sites, we can avoid out of order fields
causing bugs.

## Sorting with TableColumns

Usually when sorting a table, the column (or columns) you sort on are not known at compile time. This means you usually need to validate the column name at runtime before using it in the query.
`TableColumns` can help in this case as well:

```scala mdoc:invisible
import doobieroll.NoSuchField
```

```scala mdoc
def sortCompanies(sortingField: String): Either[NoSuchField, Fragment] =
  DbCompany.columns.fromFieldF(sortingField).map { sortingColumnFr =>
    fr"""
      |SELECT ${DbCompany.columns.listStr} FROM company
      |ORDER BY $sortingColumnFr ASC
    """.stripMargin
  }
```
