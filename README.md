# Conference Management System with Reviewer Assignment

## Build and run

Use JDK 17+ and Maven. Run from this folder:

```text
mvn package
mvn exec:java
```

Alternatively, after packaging:

```text
java -jar target/conference-review-system-1.0.jar
```

Run regression tests with `mvn test`. Tests retain their independent fixtures under
`target/test-data/`; they never use the application data file.

## Accounts and existing menus

Login accepts a User ID or an email (case-insensitive). The seeded password is `123456`.
U001/U002 are Authors, U003/U004/U005 Reviewers, and U006 Chair.
Public registration offers Author and Reviewer. An existing Chair can authorise a
new Chair using Chair menu 9. A new account cannot grant itself Chair.

Existing menu numbers remain unchanged. Added entries:

- Reviewer 5: update expertise.
- Chair 6: configuration; 7: conference phase; 8: audit log; 9: authorise a Chair.
- Author 3 (existing): shows paper status and, after a decision, anonymous feedback.
- Submission asks for optional registered co-author IDs after the existing fields.
- At any input, `/cancel` or `q` cancels the task, `/exit` exits, and EOF exits safely.
  `0` keeps its existing menu meaning and cancels a text field or positive-only number input.

## Workflow rules

A fresh conference begins in Submission. Settings are editable only in that phase.
Chair advances Submission -> Review -> Decision using menu 7. Advancing to Decision
requires completed valid reviews for every non-withdrawn paper. Final decisions are
published immediately and cannot be changed. More papers cannot be added after Submission.

Required reviewers can be 1 or 2; neither manual nor automatic assignment can exceed
the configured count or the absolute cap of two. Workload counts assigned papers whose
reviews have not yet been successfully submitted. A successful review releases capacity
without removing the assignment or review record. Failed or duplicate submissions do not
release additional capacity. Withdrawal and conflict-driven unassignment also release any
remaining pending capacity. On restart, pending workload is rebuilt from stored reviews,
including for snapshots saved before this completion-counting rule was introduced.

Automatic assignment compares case-insensitive, trimmed topic names by set intersection.
It ranks by matching topic count descending, workload ascending, then reviewer ID.
Zero-match candidates are not automatically selected; a shortfall is reported. Manual
assignment allows human judgement on expertise but enforces the same conflicts, capacity,
phase, duplicate and count limits. To get a match, use the same topic names on both sides.

The score and confidence range is 1-5; comments and submission metadata must be non-empty.
The mean score is unweighted; mean >= acceptance threshold recommends acceptance.
Significant disagreement means max(score)-min(score) >= disagreement threshold.
A Chair must supply a reason to override a recommendation.

Only the submitting author (the first author) can withdraw, before Decision and before
any final decision. Withdrawal hides the paper and its old reviews from Reviewers,
removes both sides of the assignment, and releases workload. Historical review records
remain stored for provenance. New conflicts before Decision remove affected assignments;
old conflicted reviews are excluded from valid completion, averages and author feedback.
A replacement must review before proceeding. Conflicts are frozen in Decision phase.

Co-authors must already have Author accounts. Their account affiliations are always
included in conflict checks even if other affiliations are entered. Reviewer views omit
structured author identity and affiliation fields; author outputs omit reviewer IDs.
Free-text self-identification is not automatically redacted.

## Persistence and failures

Data is saved to `data/conference-data.ser`, relative to the working directory.
State and audit events commit together. A completed temporary snapshot is flushed and
atomically moved into place; the previous snapshot is retained as `.ser.bak`.
A failed save restores the in-memory transaction, reports a storage error and stops the
session without a success message. Do not continue using objects retained from a rolled-back
transaction in external API clients; reacquire them from the repository.

An unreadable, wrong-type or null existing snapshot is preserved and startup stops.
No demo reset or automatic overwrite occurs. Keep the original and inspect the backup
before any manual recovery. Valid older snapshots are read with serialVersionUID 1;
missing configuration/audit fields receive defaults and a migration event. No past audit
history or decision reasons are fabricated. Legacy snapshots with duplicate roles,
missing references or more than two reviewers are rejected for explicit repair rather
than silently discarding data. Legacy withdrawn-paper assignments are normalised in memory.

Concurrent writers and network deployment are outside this local, single-process project.
The application requires a filesystem supporting atomic replacement; otherwise it fails
safely. Abandoned temporary snapshot files can remain after an interrupted or failed save.
