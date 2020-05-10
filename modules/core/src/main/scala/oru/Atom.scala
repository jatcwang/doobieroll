package oru

import example.Awesome.EE

/** An atomic group of database columns that converts to a single result type*/
trait Atom[A, Dbs] {
  def construct(db: Dbs): Either[EE, A]
}

