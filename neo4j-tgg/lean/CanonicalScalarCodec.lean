import Init.Data.String.Lemmas.Basic

/-!
Certified model of the tagged scalar wire framing implemented by
`CanonicalScalarValueCodec`.  Integer and Real bodies are already-canonical
texts: this is the semantic boundary after Java has restricted Integer to
Int64 and Real to finite canonical Real64 text (including one zero class).
-/

namespace Ocl2CypherProof.CanonicalScalarCodec

inductive CanonicalScalar where
  | string (canonicalEscapedBody : String)
  | integer (canonicalBody : String)
  | real (canonicalBody : String)
  | booleanFalse
  | booleanTrue
  | enumeration (canonicalEscapedBody : String)
deriving Repr, DecidableEq

def escapeChars : List Char → List Char
  | [] => []
  | char :: rest =>
      if char = '%' then
        '%' :: '2' :: '5' :: escapeChars rest
      else if char = '|' then
        '%' :: '7' :: 'C' :: escapeChars rest
      else
        char :: escapeChars rest

def unescapeChars : List Char → Option (List Char)
  | [] => some []
  | '%' :: '2' :: '5' :: rest =>
      Option.map (fun tail => '%' :: tail) (unescapeChars rest)
  | '%' :: '7' :: 'C' :: rest =>
      Option.map (fun tail => '|' :: tail) (unescapeChars rest)
  | '%' :: _ => none
  | char :: rest =>
      Option.map (fun tail => char :: tail) (unescapeChars rest)

theorem unescapeChars_escapeChars (value : List Char) :
    unescapeChars (escapeChars value) = some value := by
  induction value with
  | nil => rfl
  | cons char rest inductionHypothesis =>
      by_cases percent : char = '%'
      · subst char
        simp [escapeChars, unescapeChars, inductionHypothesis]
      · by_cases pipe : char = '|'
        · subst char
          simp [escapeChars, unescapeChars, inductionHypothesis]
        · simp [escapeChars, unescapeChars, percent, pipe, inductionHypothesis]

theorem escapeChars_injective : Function.Injective escapeChars := by
  intro left right equality
  have decoded : some left = some right := by
    simpa only [unescapeChars_escapeChars] using congrArg unescapeChars equality
  exact Option.some.inj decoded

inductive ScalarTag where
  | string
  | integer
  | real
  | boolean
  | enumeration
deriving Repr, DecidableEq

def tagPrefix : ScalarTag → String
  | .string => "v1|S|"
  | .integer => "v1|I|"
  | .real => "v1|R|"
  | .boolean => "v1|B|"
  | .enumeration => "v1|E|"

theorem tagPrefix_injective : Function.Injective tagPrefix := by
  intro left right equality
  cases left <;> cases right <;> try rfl
  all_goals
    exfalso
    have byteEquality := congrArg String.toByteArray equality
    simp only [tagPrefix] at byteEquality
    nomatch byteEquality

theorem tagPrefix_utf8ByteSize (tag : ScalarTag) :
    (tagPrefix tag).utf8ByteSize = 5 := by
  cases tag <;> decide

private theorem equalPrefix_of_equalLength
    {leftPrefix rightPrefix leftBody rightBody : String}
    (prefixSize : leftPrefix.utf8ByteSize = rightPrefix.utf8ByteSize)
    (equality : leftPrefix ++ leftBody = rightPrefix ++ rightBody) :
    leftPrefix = rightPrefix := by
  apply String.toByteArray_inj.mp
  apply ByteArray.append_inj_left
    (by
      simpa only [String.toByteArray_append] using
        congrArg String.toByteArray equality)
  exact prefixSize

def frame (tag : ScalarTag) (body : String) : String :=
  tagPrefix tag ++ body

theorem frame_injective {leftTag rightTag : ScalarTag} {leftBody rightBody : String}
    (equality : frame leftTag leftBody = frame rightTag rightBody) :
    leftTag = rightTag ∧ leftBody = rightBody := by
  have prefixEquality : tagPrefix leftTag = tagPrefix rightTag :=
    equalPrefix_of_equalLength
      ((tagPrefix_utf8ByteSize leftTag).trans (tagPrefix_utf8ByteSize rightTag).symm)
      equality
  have tagEquality : leftTag = rightTag := tagPrefix_injective prefixEquality
  cases tagEquality
  exact ⟨rfl, (String.append_right_inj (tagPrefix leftTag)).mp equality⟩

def scalarTag : CanonicalScalar → ScalarTag
  | .string _ => .string
  | .integer _ => .integer
  | .real _ => .real
  | .booleanFalse => .boolean
  | .booleanTrue => .boolean
  | .enumeration _ => .enumeration

def scalarBody : CanonicalScalar → String
  | .string canonicalEscapedBody => canonicalEscapedBody
  | .integer canonicalBody => canonicalBody
  | .real canonicalBody => canonicalBody
  | .booleanFalse => "false"
  | .booleanTrue => "true"
  | .enumeration canonicalEscapedBody => canonicalEscapedBody

private theorem booleanBodies_ne : ("false" : String) ≠ "true" := by
  intro equality
  have byteEquality := congrArg String.toByteArray equality
  nomatch byteEquality

theorem tagAndBody_injective :
    Function.Injective (fun value => (scalarTag value, scalarBody value)) := by
  intro left right equality
  cases left <;> cases right
  all_goals
    first
    | exact congrArg CanonicalScalar.string (congrArg Prod.snd equality)
    | exact congrArg CanonicalScalar.integer (congrArg Prod.snd equality)
    | exact congrArg CanonicalScalar.real (congrArg Prod.snd equality)
    | exact congrArg CanonicalScalar.enumeration (congrArg Prod.snd equality)
    | rfl
    | skip
  all_goals
    first
    | exact (booleanBodies_ne (congrArg Prod.snd equality)).elim
    | exact (booleanBodies_ne (congrArg Prod.snd equality).symm).elim
    | skip
  all_goals
    have tagEquality := congrArg Prod.fst equality
    nomatch tagEquality

def encode (value : CanonicalScalar) : String :=
  frame (scalarTag value) (scalarBody value)

theorem encode_injective : Function.Injective encode := by
  intro left right equality
  apply tagAndBody_injective
  have components := frame_injective equality
  exact Prod.ext components.1 components.2

end Ocl2CypherProof.CanonicalScalarCodec
