package org.uet.dse.neo4jtgg.ocl.ir;

import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads a dynamic Ecore package and validates a real XMI instance against it.
 *
 * <p>This is the executable M2/M1 boundary used by the specification checks.
 * It deliberately does more than XML well-formedness: classifiers, enum
 * literals, containment, multiplicities, IDs, and cross references are all
 * resolved by EMF before domain-specific well-formedness is evaluated.</p>
 */
public final class DynamicEmfModelValidator {
    private DynamicEmfModelValidator() {
    }

    public static LoadedModel load(Path ecorePath, Path xmiPath) throws IOException {
        ResourceSetImpl resources = new ResourceSetImpl();
        resources.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .put("ecore", new EcoreResourceFactoryImpl());
        resources.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .put("xmi", new XMIResourceFactoryImpl());

        Resource metamodel = resources.getResource(fileUri(ecorePath), true);
        if (metamodel.getContents().size() != 1 || !(metamodel.getContents().get(0) instanceof EPackage pkg)) {
            throw new IOException("Expected one EPackage in " + ecorePath);
        }
        resources.getPackageRegistry().put(pkg.getNsURI(), pkg);

        Resource instance = resources.getResource(fileUri(xmiPath), true);
        EcoreUtil.resolveAll(resources);
        List<Issue> issues = new ArrayList<>();
        for (Resource.Diagnostic error : metamodel.getErrors()) {
            issues.add(new Issue("ECORE_LOAD", ecorePath.toString(), error.getMessage()));
        }
        for (Resource.Diagnostic error : instance.getErrors()) {
            issues.add(new Issue("XMI_LOAD", xmiPath.toString(), error.getMessage()));
        }
        EcoreUtil.UnresolvedProxyCrossReferencer.find(instance).forEach((proxy, settings) ->
                issues.add(new Issue("XMI_UNRESOLVED_REFERENCE", EcoreUtil.getURI(proxy).toString(),
                        "Unresolved proxy is referenced from " + settings.size() + " structural setting(s)")));
        if (instance.getContents().size() != 1) {
            issues.add(new Issue("XMI_ROOT", xmiPath.toString(),
                    "Expected exactly one model root but found " + instance.getContents().size()));
        }
        for (EObject root : instance.getContents()) {
            collectDiagnostic(Diagnostician.INSTANCE.validate(root), issues);
        }
        return new LoadedModel(pkg, instance, issues);
    }

    private static URI fileUri(Path path) {
        return URI.createFileURI(path.toAbsolutePath().normalize().toString());
    }

    private static void collectDiagnostic(Diagnostic diagnostic, List<Issue> issues) {
        if (diagnostic.getSeverity() != Diagnostic.OK && diagnostic.getChildren().isEmpty()) {
            String path = diagnostic.getData().stream()
                    .filter(EObject.class::isInstance)
                    .map(EObject.class::cast)
                    .findFirst()
                    .map(EcoreUtil::getURI)
                    .map(Object::toString)
                    .orElse("$");
            issues.add(new Issue("EMF_DIAGNOSTIC", path, diagnostic.getMessage()));
        }
        diagnostic.getChildren().forEach(child -> collectDiagnostic(child, issues));
    }

    public record LoadedModel(EPackage metamodel, Resource resource, List<Issue> issues) {
        public LoadedModel {
            issues = List.copyOf(issues);
        }

        public EObject root() {
            return resource.getContents().isEmpty() ? null : resource.getContents().get(0);
        }

        public boolean valid() {
            return issues.isEmpty();
        }

        public void requireValid(String contract) {
            if (!valid()) {
                throw new IllegalArgumentException(contract + " failed: " + issues);
            }
        }
    }

    public record Issue(String code, String path, String message) {
    }
}
