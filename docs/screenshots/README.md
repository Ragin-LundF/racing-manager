# UI Screenshots

Screenshots of the Racing Manager UI, ordered along a full event lifecycle.
File names are prefixed with a number group: `00x` spectator view, `01x` event
setup, `02x` qualification, `03x`/`04x` race control, `05x` results, `9xx`
operations.

## Spectator View

The public, fullscreen-capable display (opened via *Open Spectator View*).
Leaderboard left, current head-to-head heat centre, upcoming races right.

### 000 — Qualification, heat armed

![Spectator view with an armed qualification heat](images/000_spectator-view-qualification.png)

Heat #1 ready to start; the preliminary leaderboard still has no times.

### 001 — Qualification running

![Spectator view during a running heat](images/001_spectator-view-quali-running.png)

Both lanes show "Running…", the status bar reads *Race in progress*.

### 002 — Qualification finished

![Spectator view with finished heat times](images/002_spectator-view-quali-finished.png)

Final time and km/h per lane, gap to the winner, leaderboard updated.

### 003 — Knockout, heat armed

![Spectator view during the knockout phase](images/003_spectator-view-knockout.png)

The left panel switches to the knockout bracket carrying the qualification times.

### 004 — Knockout winner

![Spectator view with the knockout winner marked](images/004_spectator-view-knockout-winner-round.png)

Result accepted, the winner is marked *WON* in the bracket.

## Event Setup

### 010 — Create event

![Create event form](images/010_event-create.png)

Name, description, lane count, measurement mode (simulated / hardware), optional
max participants and track length.

### 011 — Participants

![Participant list of an event](images/011_event-participants.png)

Participant list with club and vehicle, add / CSV import / randomize, plus the
event action bar and the preliminary-round summary.

## Qualification

### 020 — Setup

![Qualification setup](images/020_qualification-setup.png)

Configure the qualification phase: number of runs per participant.

### 021 — Overview

![Qualification overview with generated schedule](images/021_qualification-overview.png)

Generated schedule with seed, progress counters and a *Run* button per heat;
*Finalize* locks the phase.

## Race Control

### 030 — Qualification heats

![Race control with planned qualification heats](images/030_race-control-quali-overview.png)

Planned heats with lane assignment, *Arm* per heat, and ad-hoc *Create Heat*.

### 031 — Accept a result

![Race control accepting a finished heat](images/031_race-control-quali-accept-race.png)

Finished heat with measured times, arm/start/finish timestamps and
*Accept* / *Reject* / *Repeat*.

### 032 — Finalize qualification

![Confirmation dialog before finalizing qualification](images/032_race-control-quali-finalize.png)

Confirmation dialog before locking the phase; all heats listed with their times.

### 040 — Knockout setup

![Knockout setup with pairing mode](images/040_race-control-knockout-setup.png)

Pairing mode (e.g. first vs. last) for generating the bracket from the
qualification rankings.

## Results

### 050 — Results

![Qualification rankings and knockout results](images/050_race-results.png)

Qualification rankings (best time, speed, runs, DNF) and knockout results;
*Complete Event* closes the event.

## Operations

### 900 — Diagnostics

![Diagnostics page](images/900_diagnostics.png)

Database connectivity and ping, event/participant/heat counters, readiness status.

### 901 — Arduino setup

![Arduino / timing hardware setup](images/901_hardware-setup.png)

Timing device configuration: serial port, baud rate, timeouts, false-start
window, finish value format, raw log file.
