package io.github.kalayciburak.stackmate.inference;

/** Validates untrusted structured model output against its exact request. */
@FunctionalInterface
public interface InferenceOutputValidator {

    /** Returns a trusted value only when all validation rules pass. */
    OutputValidationResult validate(InferenceRequest request, StructuredInferenceOutput output);
}
