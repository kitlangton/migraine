# migraine
> (***migra***tion eng***ine***)

A minimalist DB migration manager.

# Migraine

A minimal, modern database migration tool.

# Todos
  - [ ] Configurable database connection
  - [ ] Configurable CLI (how and where to specify migrations folder, connection
    info, etc?)
    - [ ] migraine.conf? (home, project root, etc)
    - [ ] the target local database will change per project
  - [ ] Automatic migration tests (RESEARCH NEEDED)
  - [ ] Dry Run (by default?) (migraine preview)
    - [ ] MVP: Just print out the SQL
    - [ ] Schema diff
    - [ ] Warn for non-reversible migrations
    - [ ] Warn for new columns without default values and not null
  - [x] If a migration fails, the metadata and other migrations executed in
    the same run should be rolled back.
  - [ ] Errors
      - [ ] ensure executed migrations haven't diverged from their stored file (using checksum)
    - [ ] Detect when run migrations are missing, no gaps in ids (unless there's a snapshot?).
    - [ ] If accidentally duplicated version ids, CLI should help user resolve ambiguity.
    - [ ] postgres driver is missing (prompt user to add dependency)
    - [ ] database connection issues
  - [ ] Warnings
    - [ ] adding column to existing table without default value
    - [ ] missing migrations folder
  - [ ] Generate down migrations automatically
  - [ ] Rollback
    - [ ] UX: Check to see how many migrations were run "recently", or in the
      last batch (a group of migrations executed at the same time). If
      there's more than just one, ask the user.
  - [ ] Create snapshot CLI command
  - [ ] Editing migrations
    - [ ] If a user has edited the mostly recently run migration/migrations and
      tries to run migrate again, ask the user if they want to rollback the
      prior migration before running the edits. (we could store the SQL text
      of the executed migrations... would that get too big?)
  - [x] Execute snapshots
     - [x] If fresh database, begin from most recent snapshot, otherwise, ignore
          snapshots
  - [x] Ensure migration metadata is saved transactionally along with migration


## Prior Art
  - [ ] Flyway
  - [ ] Liquibase
  - [ ] Active Record (migrations)
  - [ ] Play Evolutions
  - [ ] DBEvolv (https://github.com/mnubo/dbevolv)
  - [ ] Squitch (https://sqitch.org)
  - [ ] Delta (https://delta.io)
    */

 How should migration snapshots work?

 - [ ] Ask the user up to which migration they want to snapshot
 - [ ] Run all of the migrations up to that point on a fresh database (using docker/test containers?)
 - [ ] Store the resulting database schema in a migration file

 Here's an example set of migrations and snapshots
 M1 M2 M3 S3 M4 M5
 - [ ] S3 is a snapshot of the database after migration M3
 - [ ] M4 and M5 are new migrations that are applied on top of S3

 Scenarios
 - [ ] When run on a fresh database
   - [ ] Find the latest snapshot, then run all migrations after that snapshot
 - [ ] When run on a database that has already been migrated
   - [ ] Simply run all migrations that haven't been run yet, ignoring snapshots
