# Use Room and SQLite as the Primary Store

Kartoffel will use Room on SQLite as the MVP's primary local store because the core workload is app-native persistence of location evidence, H3 coverage cells, sessions, and retention state. H3 provides the spatial indexing model, so the MVP does not need DuckDB, GeoPackage, or a geospatial database until a concrete analytical workload appears.
