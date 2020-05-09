package example

import org.flywaydb.core.Flyway

object RunMig {
  def main(args: Array[String]): Unit = {
    val f = Flyway
      .configure(this.getClass.getClassLoader)
      .dataSource(
        "jdbc:postgresql://localhost:5432/testdb",
        "postgres",
        "postgres",
      )
      .load()

    val _ = f.migrate()
  }
}
