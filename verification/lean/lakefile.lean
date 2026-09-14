import Lake

open Lake DSL

package Ocl2CypherProof where
  version := v!"0.1.0"

@[default_target]
lean_lib Ocl2CypherProof where
  roots := #[`Ocl2CypherProof]

@[default_target]
lean_lib Ocl2Gratra where
  roots := #[`Ocl2Gratra]
