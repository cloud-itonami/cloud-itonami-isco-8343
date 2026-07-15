# cloud-itonami-isco-8343

Open Occupation Blueprint for **ISCO-08 8343**: Crane, Hoist and Related Plant Operators.

This repository designs a forkable OSS business for an independent crane and hoist operations practice: a load-inspection and rigging-log robot manages lift records under a governor-gated actor, so the practice keeps its own lift records instead of renting a closed equipment-management SaaS.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a load-inspection and rigging-log robot performs pre-lift inspection checklist logging and load-weight verification under an actor that proposes
actions and an independent **Crane Operations Governor** that gates them. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions (such as
lift above the crane's registered load-capacity ceiling) require human sign-off.

A live sample of the operator console (robotics safety console, shared template) is rendered in [docs/samples/operator-console.html](docs/samples/operator-console.html) — pure-data HTML output of `kotoba.robotics.ui`.

## Core Contract

```text
lift plan + load specification + site clearance
        |
        v
Crane Advisor -> Crane Operations Governor -> dispatch lift/approve, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, suppress
an operating record, or disclose sensitive data without governor approval and
audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `8343`). Required capabilities:

- :robotics
- :telemetry
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
