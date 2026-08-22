package org.uet.dse.neo4jtgg.experiment;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Deterministic grammar-based generator for replayable certified OCL properties. */
final class OclPropertyCaseGenerator {
    static final long DEFAULT_SEED = 20_260_823L;
    static final int DEFAULT_STATIC_CASES = 128;
    static final int DEFAULT_RUNTIME_CASES = 64;

    private OclPropertyCaseGenerator() {
    }

    static List<PropertyCase> generate(long seed, int count) {
        if (count < 1) throw new IllegalArgumentException("property case count must be positive");
        Random random = new Random(seed);
        List<PropertyCase> cases = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            boolean person = index % 2 == 0;
            String context = person ? "Person" : "Company";
            String expression = person ? personExpression(random, index) : companyExpression(random, index);
            cases.add(new PropertyCase("Generated_%04d".formatted(index), context, expression, seed, index));
        }
        return List.copyOf(cases);
    }

    private static String personExpression(Random random, int index) {
        int a = bounded(random);
        int b = bounded(random);
        int c = bounded(random);
        int cardinality = random.nextInt(4);
        return switch (index % 8) {
            case 0 -> "self.age " + comparison(random) + " " + a;
            case 1 -> "(self.age >= " + a + ") xor (self.age >= " + b + ")";
            case 2 -> "Set{" + a + ", " + b + ", " + a + "}->includes(self.age)";
            case 3 -> "Set{" + a + ", " + b + ", " + c + "}->select(x | x >= " + bounded(random)
                    + ")->size() >= " + cardinality;
            case 4 -> "let threshold : Integer = " + a + " in self.age >= threshold";
            case 5 -> "if self.age >= " + a
                    + " then self.name <> '' else self.name = '' endif";
            case 6 -> "Set{" + a + ", " + b + "}->isUnique(x | x)";
            default -> "Set{" + a + ", " + b + ", " + c + "}->one(x | x >= "
                    + bounded(random) + ")";
        };
    }

    private static String companyExpression(Random random, int index) {
        int threshold = bounded(random);
        int cardinality = random.nextInt(5);
        return switch (index % 8) {
            case 0 -> "self.employee->exists(e | e.age " + comparison(random) + " " + threshold + ")";
            case 1 -> "self.employee->forAll(e | e.age " + comparison(random) + " " + threshold + ")";
            case 2 -> "self.employee->select(e | e.age >= " + threshold + ")->size() >= " + cardinality;
            case 3 -> "self.employee->collect(e | e.age)->includes(" + threshold + ")";
            case 4 -> "self.employee->isUnique(e | e.name)";
            case 5 -> "self.employee->size() " + comparison(random) + " " + cardinality;
            case 6 -> "self.employee->reject(e | e.age < " + threshold + ")->notEmpty()";
            default -> "self.employee->one(e | e.age >= " + threshold + ")";
        };
    }

    private static int bounded(Random random) {
        return random.nextInt(221);
    }

    private static String comparison(Random random) {
        return List.of("=", "<>", "<", "<=", ">", ">=").get(random.nextInt(6));
    }

    record PropertyCase(String id, String context, String expression, long seed, int index) {
        String invariant() {
            return "context " + context + " inv " + id + ": " + expression;
        }

        String replay() {
            return "seed=" + seed + ", index=" + index + ", invariant=`" + invariant() + "`";
        }
    }
}
