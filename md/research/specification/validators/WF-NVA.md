# Executable well-formedness contract for NVA

`WF_NVA` is a structural/static validator. It does not by itself prove that
an NVA instance is the result of normalization; that property is supplied by
`Ref_OVA_NVA` and the normalization theorem.

## Structural constraints

```text
WF-NVA-ROOT
  exactly one NvaInvariant body and one self variable

WF-NVA-ID
  every nodeId is non-empty and unique within the module

WF-NVA-TYPE
  every constructor resultType agrees with its constructor typing rule

WF-NVA-SCOPE
  every variable resolves to the nearest enclosing declaration

WF-NVA-COLLECTION
  sourceCollectionType is present on collection consumers and agrees with
  the source expression result type

WF-NVA-SET
  collection expressions use finite Set semantics; no ordered/bag constructor
  is admitted

WF-NVA-BOTTOM
  bottom values occur only where the certified value algebra permits them

WF-NVA-NORMAL
  no subexpression matches a left-hand side of a rule in R1

WF-NVA-EXCLUSION
  unsupported constructors (`any`, `one`, `sortedBy`, ordered/bag forms, and
  other excluded OCL forms) are rejected.
```

## Certification predicate

```text
ValidNVA(nva) =
  EcoreConforms_NVA(nva)
  and WF_NVA(nva)
  and NF_R(nva)
```

```text
CertifiedNVA(va,nva) =
  ValidNVA(nva)
  and Ref_OVA_NVA(va,nva)
  and denotationPreserved(va,nva)
```

The final line is a semantic relation and must not be replaced by a boolean
attribute stored in the NVA model.
