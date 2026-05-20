package org.uet.dse.neo4jtgg.engine;

public record TransformationOptions(
        TransformationDirection direction,
        TransformationMode mode,
        boolean refreshViewsAfterApply,
        boolean refreshUseMirrorAfterApply) {

    public static TransformationOptions previewForward() {
        return new TransformationOptions(TransformationDirection.FORWARD, TransformationMode.PREVIEW, false, false);
    }

    public static TransformationOptions applyForward() {
        return new TransformationOptions(TransformationDirection.FORWARD, TransformationMode.APPLY, true, false);
    }

    public static TransformationOptions previewBackward() {
        return new TransformationOptions(TransformationDirection.BACKWARD, TransformationMode.PREVIEW, false, false);
    }

    public static TransformationOptions applyBackward() {
        return new TransformationOptions(TransformationDirection.BACKWARD, TransformationMode.APPLY, true, false);
    }
}
