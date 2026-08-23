# Text reconstruction and comparison for `yte.png`

Canonical executable reconstruction:
`examples/medical-system-yte/medical-system.use`.

## Structural comparison

| Diagram element | Text reconstruction | Result |
|---|---|---|
| Enumerations | `Gender`, `HealthStatus`, `Brand`, `Administration`, `Specialty`, `BloodType` | matched |
| Generalization | `Doctor`, `Nurse`, `Patient` specialize abstract `Person` | matched |
| Domain classes | `Hospital`, `Department`, `Disease`, `Medication`, `Dosage`, `DosagePlan`, `MedicalRecord`, `RecordEntry` | matched |
| Nested collection attributes | `Sequence(Set(Integer))`, `Set(Sequence(String))`, `Sequence(Sequence(Medication))`, `Sequence(Set(Doctor))` | matched |
| Ternary associations | `Appointment`, `Consultation` | matched |
| Association classes | `Hospitalization`, `Prescription` | matched |
| Aggregation | `HospitalStructure` | matched |
| Compositions | `PatientRecord`, `RecordEntries` | matched |

The older `neo4j/examples/ts/MedicalSystem.use` contains five attributes that
are not present in the diagram: `Person.kk`, `Person.nono`, `Doctor.favNurse`,
`Doctor.hotNurse`, and `Doctor.gd`. They are experimental extensions and are
therefore intentionally excluded from the canonical reconstruction.

The diagram does not show constraints. Test invariants are kept separately in
`examples/medical-system-yte/invariants.ocl` so that the UML reconstruction
remains traceable to the image while the OCL-to-Cypher experiment remains
reproducible.
