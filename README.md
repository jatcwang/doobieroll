Object Relational Utility for Scala

Conversion from Tuple3[DbParent, DbChild, DbGrandchild] => Parent

where Parent is

case class Parent(
  id: ParentId
  children: List[Children]
)



