package oru

/** An atomic group of database columns that converts to a single entity which does not have children.
 *  The construction may fail, represented by the error context F */
trait LeafDef[F[_], A, ADb] {
  def construct(db: ADb): F[A]
}

/** A LeafDef where the construction cannot fail */
trait InfallibleLeafDef[A, ADb] extends LeafDef[cats.Id, A, ADb]

