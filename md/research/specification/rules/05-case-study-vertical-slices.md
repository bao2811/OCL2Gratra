# Rule set R4: nested values and n-ary links

These rules specify the Medical YTE and Car Rental production vertical slices.
They are local refinement rules, not a discharge of universal Theorem 6.

## Nested collection storage

For an attribute value `a : K1(K2(T))`, create one ordered
`HasNestedCollectionValue(index=i)` edge for each outer element. A deepest
scalar collection is stored as the ordered concatenation of canonical tagged
leaves. Empty is `COLLECTION_EMPTY`; scalar bottom is `v1|V`. A deepest entity
collection uses only indexed edges to encoded source `Object` nodes.

```text
R4-NESTED-SCALAR
  encode([xs0,...,xsn])
  = AttributeValue -[index=0]-> encodeLeafList(xs0)
                   ...
                   -[index=n]-> encodeLeafList(xsn)

R4-NESTED-ENTITY
  encode([os0,...,osn])
  = AttributeValue -[index=i]-> NestedCollectionValue
                   -[index=j]-> encodeEntity(os_i_j)
```

The writer must preserve edge indices, scalar tags, duplicates and both order
levels. Entity bottom creates no target edge. Therefore every reachable entity
leaf is the encoding of a source object (`NoGhost`).

## N-ary association storage and projection

An n-ary link has one model-scoped `LinkHub` identified by
`(modelKey, associationKey, linkKey)`. Each participant object connects to the
hub through exactly one spoke carrying `(role,index,associationKey,linkKey)`.

```text
R4-NARY-PROJECT
  participant(tuple, sourceRole) = source
  participant(tuple, targetRole) = target
  ------------------------------------------------------------
  encode(source)-[sourceRole]->hub<-[targetRole]-encode(target)
```

The renderer must constrain model, association, link and both roles. The local
Lean theorem `nary_projection_agreement` proves agreement for an encoded tuple;
it does not yet prove that every production database globally satisfies the
tuple well-formedness constraints.

## Executable and mechanized witnesses

- `medical_nested_discriminator_matrix.csv` contains 14 non-vacuous exact-ID
  discriminators for empty, singleton, duplicate, order, bottom and NoGhost.
- `CaseStudyVerticalSlices.lean` proves local ID injectivity, nested sequence
  payload injectivity/width, scalar-bottom separation, n-ary projection and
  NoGhost lemmas without project `axiom`, `sorry`, `admit`, or `opaque`.
