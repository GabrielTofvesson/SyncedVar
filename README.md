# SyncedVar: *An annotation-based serialization system*
Designed to mimic the NetworkedVar and SyncedVar systems found in MLAPI and HLAPI, this library allows serialization of variables to simplify the program flow when sending data between peers. Alternatively, this library can be used to create a "snapshot" of the current state of a set of variables for rollback at a later time.
## Features
* Module-based serializers
* Ability to remove (or override) default serializers
* Support for value delta serialization
* VarInt compression
* Booleans are represented as bits (as they should), rather than bytes, allowing for an 8x compression rate
* Support for custom (per-field) serialization flags
