# Storage usage

`StorageUsageLoader(context).load()` returns an immutable, on-demand backend snapshot.
It runs on `Dispatchers.IO` and opens the existing database read-only through Android's
SQLite engine, also used by the app's `AndroidSQLiteDriver`. The database must already
exist with the current schema. The loader does not open Room, migrate, checkpoint,
vacuum, delete data, or change tracking. It returns only counts and byte measurements;
coordinates and histories are never loaded into app memory.

Each category includes its table and indexes identified through SQLite's schema.
`AllocatedPages` means the sum of `dbstat.pgsize`: complete allocated B-tree pages,
including internal and overflow pages and unused space inside those pages. Empty
tables and indexes still own root pages. This is available only when the platform
SQLite build supports [dbstat](https://www.sqlite.org/dbstat.html).

When dbstat is unavailable, `EstimatedPages` models each table and index separately:

- Eight bytes for each non-null number; actual integers often use fewer bytes.
- SQLite's byte length for text/blob values and zero payload bytes for nulls.
- Two header bytes per value and twelve bytes per B-tree cell.
- An additional rowid value in each index; all current indexes cover every row.
- 75% occupancy of each page after a twelve-byte page-header allowance, rounded up
  to whole pages, with at least one root page per table/index.

These assumptions deliberately favour a simple storage budget over exact record
encoding. The table's integer primary key is also budgeted as eight bytes, though
SQLite normally stores it as the rowid. The estimate is neither an exact measurement
nor a guaranteed upper bound: page splits, unusually long text/overflow pages,
fragmentation, integer encoding and actual occupancy can change allocation. See
[SQLite's file format](https://www.sqlite.org/fileformat.html). The estimator uses
SQL aggregates and schema metadata; it does not deserialize Location Samples.

The database, WAL, shared-memory and rollback-journal fields report file lengths,
with zero for absent sidecars. These are logical lengths, not filesystem allocated
blocks. The categories exclude other tables, the freelist and shared overhead; they
need not sum to the database file length. WAL may contain new pages and repeated
versions of pages. Its bytes are not attributed to either category or added to their
page accounting.

Queries and file lengths are sampled separately while tracking can keep writing.
There is no atomic filesystem-wide total, and counts/page accounting may observe
different committed states. A database not yet created, missing expected tables,
or another SQLite error causes the request to fail rather than returning invented
zero usage. Only lack of dbstat triggers estimation.
