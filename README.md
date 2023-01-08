# migraine
> (***migra***tion eng***ine***)

A minimalist DB migration manager.

# Migraine

A minimal, modern database migration tool.

# Todos
  - [ ] Configurable database connection
  - [ ] CLI 
    - [ ] Commands
      - [ ] migrate
      - [ ] preview
      - [ ] make-down-migration
      - [ ] configure 
      - [ ] snapshot 
      - [ ] new-migration
      - [ ] reset
      - [ ] rollback
      - [ ] disambiguate 
    - [ ] migraine.conf? (home, project root, etc)
      - connection info, migration path, etc
      - the target local database will change per project
  - [ ] Automatic migration tests (RESEARCH NEEDED)
  - [ ] Dry Run (by default?) (migraine preview)
    - [ ] MVP: Just print out the SQL
    - [ ] Schema diff
    - [ ] Warn for non-reversible migrations
    - [ ] Warn for new columns without default values and not null
  - [ ] Errors
      - [ ] ensure executed migrations haven't diverged from their stored file (using checksum)
    - [ ] Detect when run migrations are missing, no gaps in ids (unless there's a snapshot?).
    - [ ] If accidentally duplicated version ids, CLI should help user resolve ambiguity.
    - [ ] postgres driver is missing (prompt user to add dependency)
    - [ ] database connection issues
  - [ ] Warnings
    - [ ] adding column to existing table without default value
    - [ ] missing migrations folder
  - [ ] Rollback
    - [ ] UX: Check to see how many migrations were run "recently", or in the
      last batch (a group of migrations executed at the same time). If
      there's more than just one, ask the user.
  - [ ] Editing migrations
    - [ ] If a user has edited the mostly recently run migration/migrations and
      tries to run migrate again, ask the user if they want to rollback the
      prior migration before running the edits. (we could store the SQL text
      of the executed migrations... would that get too big?)
  - [x] Execute snapshots
     - [x] If fresh database, begin from most recent snapshot, otherwise, ignore
          snapshots
  - [x] Ensure migration metadata is saved transactionally along with migration
  - [x] If a migration fails, the metadata and other migrations executed in
     the same run should be rolled back.

## Prior Art
  - Flyway
  - Liquibase
  - Active Record (migrations)
  - Play Evolutions
  - DBEvolv (https://github.com/mnubo/dbevolv)
  - Squitch (https://sqitch.org)
  - Delta (https://delta.io)