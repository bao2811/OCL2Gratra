package org.uet.dse.neo4j.migration;

import org.tzi.use.uml.sys.MSystem;
import org.uet.dse.neo4j.sync.model.ModelDiff;
import java.util.*;

public class ObjectIntegrityService {

    /**
     * Phân tích tác động khi Pull Model từ DB về Java
     */
    public StructuralImpactAnalysis analyzePullImpact(MSystem currentSystem, ModelDiff modelDiff) {
        StructuralImpactAnalysis impact = new StructuralImpactAnalysis();
        
        // Những Class có ở Java nhưng KHÔNG có ở DB (Sắp bị xóa khỏi Java)
        List<String> classesToBeDeleted = modelDiff.javaOnlyClasses;

        for (String clsName : classesToBeDeleted) {
            List<String> objectsOfClass = new ArrayList<>();
            currentSystem.state().allObjects().stream()
                .filter(obj -> obj.cls().name().equals(clsName))
                .forEach(obj -> objectsOfClass.add(obj.name()));
            
            if (!objectsOfClass.isEmpty()) {
                impact.orphanObjects.put(clsName, objectsOfClass);
            }
        }
        return impact;
    }
}