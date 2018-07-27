# SyncedVar
An annotation-based serialization system
Designed to mimic the NetworkedVar and SyncedVar systems found in MLAPI and HLAPI
#Features:
* Module-based serializers
* Ability to remove (or override) default serializers
* Support for value delta serialization
* VarInt compression
* Booleans are represented as bits (as they should), rather than bytes
