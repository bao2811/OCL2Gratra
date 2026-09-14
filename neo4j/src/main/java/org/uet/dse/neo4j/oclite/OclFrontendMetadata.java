package org.uet.dse.neo4j.oclite;

/** Stable identity of the syntax front end used in conformance artifacts. */
public record OclFrontendMetadata(
        String frontendId,
        String syntaxBaseline,
        int frontendVersion,
        String certifiedProfile) {

    public static final OclFrontendMetadata CURRENT = new OclFrontendMetadata(
            "neo4j-ocl-front-end",
            "OMG-OCL-2.4",
            2,
            "OCL_val");
}
