# Today Progress - 2026-04-10

## HW5 Submission Branch

- Created `hw5-pure-submission` from the current repo state.
- Simplified the codebase to HW5-only messaging behavior.
- Removed Project 3 engine, database helper, and stylesheet files from the submission branch.
- Verified `mvn clean compile` in both `HW5Server` and `HW5Client`.
- Built the clean submission archive: `HW5_submission_hw5-pure-clean.zip`.
- Returned to `main` for continued Project 3 work.

## Project 3 Branch

- Preserved the styled Checkers UI and supporting files on `main`.
- Kept the instructions screen, retention palette, accessibility toggle, and board styling work in the main branch history.

## Recommended HW5 Local Verification

- `cd HW5Server && mvn clean compile`
- `cd ../HW5Client && mvn clean compile`
- `cd HW5Server && mvn exec:java`
- `cd ../HW5Client && mvn exec:java`

## Submission File

- `HW5_submission_hw5-pure-clean.zip`

## Which Branch To Run

- HW5 alone: use `hw5-pure-submission`.
- Project 3 / Checkers: use `main` only if your full Checkers files are present and compiling.
- If `main` does not compile, restore or re-check the Project 3 files before trying to launch it in IntelliJ.

## Simple Launch Checklist

- `cd HW5Server && mvn clean compile`
- `cd HW5Client && mvn clean compile`
- `cd HW5Server && mvn exec:java`
- `cd HW5Client && mvn exec:java`
- For two-client testing, run the client command twice in separate terminals.
