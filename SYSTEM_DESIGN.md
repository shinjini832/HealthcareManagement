# CareFlow System Design & Transaction Control Mechanisms

This document outlines the core transactional and system design mechanisms built into the CareFlow backend to guarantee data consistency, handle race conditions, and ensure reliable asynchronous messaging.

---

## 1. Double-Booking Prevention
To ensure that a doctor cannot be booked twice for overlapping time slots, CareFlow implements a multi-layered concurrency control strategy:

- **Database-Level Unique Constraints**: The `appointments` table features a unique key constraint on the `active_booking_marker` column. This marker is composed of the doctor's ID, the appointment date, and the start time (e.g., `doc_4_date_2026-08-25_time_14:00`). If two parallel transactions try to confirm the same slot, the database throws a `ConstraintViolationException` on the second commit, causing it to roll back.
- **Marker Release**: If an appointment is cancelled or rescheduled, the `active_booking_marker` is updated to `NULL`. Since MySQL allows multiple `NULL` values in a unique column, this frees the slot for future bookings while preserving the history of cancelled records.

---

## 2. Slot Hold Mechanism (Temporary Locking)
When a patient selects a time slot, they need time to fill in the symptom description and choose an urgency level. To prevent other patients from grabbing the slot during this "checkout" window, a temporary hold mechanism is used:

- **Transient Holds**: A record is created in the `slot_holds` table with an expiration timestamp (`expires_at`), which is configured to **5 minutes** from creation. 
- **Pessimistic Write Locking**: When a patient attempts to hold a slot, the system runs a query using a pessimistic write lock (`SELECT ... FOR UPDATE`) on existing active holds:
  ```sql
  SELECT * FROM slot_holds WHERE doctor_id = ? AND slot_date = ? AND start_time = ? AND expires_at > NOW() FOR UPDATE;
  ```
  This blocks concurrent transactions from checking availability or placing holds on the same slot, preventing race conditions.
- **Hold Cleanup & Transition**: When confirming the booking, the active hold is deleted in the same transaction that creates the confirmed appointment, transitioning the temporary hold into a permanent booking.

---

## 3. Doctor Leave Conflict Handling
When a clinic administrator registers a doctor's leave for a specific date, the system must handle existing bookings automatically:

- **Transactional Cascading Cancellation**: The registration of a leave is wrapped in a `@Transactional` block. The system queries all active bookings (`HELD` or `CONFIRMED`) matching the doctor and the leave date.
- **Marker Nullification**: For each conflicting appointment:
  1. The status is updated to `CANCELLED_BY_DOCTOR_LEAVE`.
  2. The `active_booking_marker` is set to `NULL` to release the unique key constraint on that slot.
  3. The Google Calendar event associated with the appointment is deleted via API call.
  4. An cancellation email is queued in the `notification_queue` table.
- **Fail-Safe API Interactions**: To prevent external API failures (like Google Calendar timeouts) from rolling back the leave registry transaction, calendar deletions are wrapped in `try-catch` blocks that log warnings instead of propagating errors.

---

## 4. Notification Failure & Retry Engine
To ensure that critical patient notifications (such as booking confirmations, medication reminders, and leave cancellations) are reliably delivered without slowing down user interactions, CareFlow uses an asynchronous transactional outbox pattern:

- **Asynchronous Outbox**: Mail is never sent directly within the request-response thread. Instead, notifications are serialized and saved in the `notification_queue` table with a `PENDING` status.
- **Spring Scheduler Processing**: A background task (`NotificationScheduler`) runs every 60 seconds to fetch up to 3-retry eligible `PENDING` or `FAILED` items.
- **Robust Error Isolation**: The scheduler processes each notification inside its own `try-catch` block:
  - If `JavaMailSender` succeeds, the item is marked `SENT`.
  - If it fails, the retry count is incremented, the status is set to `FAILED`, and the error stack trace is logged in `error_message`.
  - If the retry count reaches **3**, the item is marked terminally failed and skipped in future runs, preventing endless SMTP loops.
