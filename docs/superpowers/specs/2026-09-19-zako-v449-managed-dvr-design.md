# Zako 4.49 Managed DVR Scheduling Design

## Goal
Turn Zako's existing future-recording alarm foundation into a cable-box style managed DVR: users can see, edit and cancel upcoming recordings, create manual timers beyond provider EPG coverage, and trust persisted timers to re-arm after app/device restart.

## Existing foundation
Zako 4.48 persists ScheduleStore.Sched records, uses exact AlarmClock alarms, starts RecordingService as a foreground service, and respects provider stream/storage safeguards. 4.49 extends this instead of replacing playback or recording.

## User experience
Recordings has two clear groups: Upcoming and Recorded. Upcoming rows show title, channel, date, start/end time and status. A selected upcoming row offers Edit and Cancel. Manual Recording lets the user select a known channel, date, start time, end time and optional title. EPG-created and manual timers use the same ScheduleStore.

## Scheduling lifecycle
ScheduleStore remains the source of truth. Adding or editing persists first, then arms the exact alarm. Cancel removes both alarm and persisted record. A small re-arm routine scans future persisted schedules at app startup, package replacement, boot completed, and time/timezone changes. It never launches MainActivity. Expired schedules are retained long enough to expose a Missed status, then pruned conservatively.

A BootReceiver handles BOOT_COMPLETED, LOCKED_BOOT_COMPLETED where supported, MY_PACKAGE_REPLACED, TIME_SET and TIMEZONE_CHANGED. It performs only schedule recovery. The recording alarm continues to target AlarmReceiver, which starts RecordingService.

## Background behavior
The app UI does not need to be open. At alarm time AlarmReceiver starts the foreground RecordingService. The device must be powered; Android/Fire OS may still impose vendor restrictions, so device testing remains an acceptance gate. Reboot recovery explicitly re-arms persisted future timers.

## Manual timers
Manual timers are allowed outside EPG range because they depend only on a channel URL and user-selected wall-clock times. Validation requires a future start and end after start. The UI uses the device's local timezone and stores epoch milliseconds so time-zone changes can be re-armed correctly.

## Safety and constraints
Keep package com.easyiptv.player and permanent signing identity. Preserve configured 1/2/3 provider stream budget, USB/internal recording destination, low-memory behavior, Smooth Live behavior, and current recording service. No cloud/backend. Do not extend or fabricate EPG data; provider guide horizon remains provider-controlled.

## Verification
Add structural/TDD verifiers for schedule listing/cancel/edit/manual validation, reboot/package/time-change receiver declarations and re-arm behavior, persistence-first ordering, and no UI launch from recovery receiver. Run existing 4.48 verifiers plus Android release compile. Signed release must verify package, version and permanent certificate before publication. Device acceptance: schedule timer, force-close UI, reboot before another timer, cancel a timer, edit a timer, create manual timer beyond EPG, and confirm recording files/statuses.
