# Builtin functions
This folder contains all functions that are builtin and executed directly in this service 
instead of as tools (spinned up tool container, managed by this, started via orch-api).
To add a new function, create a new class in this package, 
extending either 
- AbstractManagedCellFunction (for functions that take a single value as input and return a single value) or
- AbstractManagedRowFunction (for functions that take a whole row as input and return new columns) or
- AbstractManagedPatientFunction (for functions that take all rows of one patient at once).
AbstractManagedRowFunction cannot return columns dynamically, so on compile time the
returnKeys (output columns) must be defined

The documentation of the abstract functions contains information on which methods must or may be 
implemented/overriden.

None of the three can remove a column: a row function writes its results back through its return
mapping and a cell function writes back to the cell it read, so neither has a way to take a column
away. A function that needs to (see `RemoveColumnsFunction`) extends `AbstractManagedFunction`
itself and implements `apply` and `mode`.
