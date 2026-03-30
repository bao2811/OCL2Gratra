package org.uet.dse.neo4j.model;

import java.util.*;

public class ClassState {
    private String name;
    private String type;
    private Set<String> parents = new HashSet<>();
    private Map<String, Map<String, Object>> attributes = new HashMap<>();
    private Map<String, Map<String, Object>> operations = new HashMap<>();
    private List<Map<String, Object>> invariants = new ArrayList<>();
    private Map<String, List<Map<String, Object>>> preConditions = new HashMap<>();
    private Map<String, List<Map<String, Object>>> postConditions = new HashMap<>();
    private Map<String, List<OpParam>> opParams = new HashMap<>();
    private String metaNodeName;
    private Map<String, Object> acSourceInfo = new HashMap<>();
    private Map<String, Object> acTargetInfo = new HashMap<>();
    private List<String> enumLiterals = new ArrayList<>();
    public Map<String, List<Map<String, Object>>> getPreConditions() {
        return preConditions;
    }
    public void setPreConditions(Map<String, List<Map<String, Object>>> preConditions) {
        this.preConditions = preConditions;
    }
    public Map<String, List<Map<String, Object>>> getPostConditions() {
        return postConditions;
    }
    public void setPostConditions(Map<String, List<Map<String, Object>>> postConditions) {
        this.postConditions = postConditions;
    }
    public Map<String, List<OpParam>> getOpParams() {
        return opParams;
    }
    public void setOpParams(Map<String, List<OpParam>> opParams) {
        this.opParams = opParams;
    }

    // --- Getters & Setters ---
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Set<String> getParents() { return parents; }
    public void setParents(Set<String> parents) { this.parents = parents; }

    public Map<String, Map<String, Object>> getAttributes() { return attributes; }
    public void setAttributes(Map<String, Map<String, Object>> attributes) { this.attributes = attributes; }

    public Map<String, Map<String, Object>> getOperations() { return operations; }
    public void setOperations(Map<String, Map<String, Object>> operations) { this.operations = operations; }

    public List<Map<String, Object>> getInvariants() { return invariants; }
    public void setInvariants(List<Map<String, Object>> invariants) { this.invariants = invariants; }

    // --- Quản lý Invariants ---
    public void addInvariant(Map<String, Object> invProps) {
        this.invariants.add(invProps);
    }

    // --- Quản lý Pre/Post Conditions ---
    public void addPreCondition(String opName, Map<String, Object> props) {
        preConditions.computeIfAbsent(opName, k -> new ArrayList<>()).add(props);
    }

    public List<Map<String, Object>> getPreConditions(String opName) {
        return preConditions.getOrDefault(opName, Collections.emptyList());
    }

    public void addPostCondition(String opName, Map<String, Object> props) {
        postConditions.computeIfAbsent(opName, k -> new ArrayList<>()).add(props);
    }

    public List<Map<String, Object>> getPostConditions(String opName) {
        return postConditions.getOrDefault(opName, Collections.emptyList());
    }

    // --- Quản lý Params ---
    public void addOperationParam(String opName, String pName, String pType, int order) {
        opParams.computeIfAbsent(opName, k -> new ArrayList<>()).add(new OpParam(pName, pType, order));
    }

    public List<OpParam> getOpParamsSorted(String opName) {
        List<OpParam> params = opParams.getOrDefault(opName, new ArrayList<>());
        params.sort(Comparator.comparingInt(p -> p.order));
        return params;
    }

    public boolean isSameAs(ClassState other) {
        if (other == null) return false;
        return Objects.equals(this.name, other.name) &&
                Objects.equals(this.type, other.type) &&
                Objects.equals(this.parents, other.parents) &&
                Objects.equals(this.attributes, other.attributes) &&
                Objects.equals(this.operations, other.operations) &&
                Objects.equals(this.invariants, other.invariants) &&
                Objects.equals(this.preConditions, other.preConditions) &&
                Objects.equals(this.postConditions, other.postConditions) &&
                Objects.equals(this.enumLiterals, other.enumLiterals);
    }

    public static class OpParam {
        String name;
        String type;
        int order;

        OpParam(String n, String t, int o) {
            this.name = n;
            this.type = t;
            this.order = o;
        }

        public String getName() { return name; }
        public String getType() { return type; }
        public int getOrder() { return order; }

        // Cần có equals để so sánh list tham số chính xác
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof OpParam)) return false;
            OpParam opParam = (OpParam) o;
            return order == opParam.order && Objects.equals(name, opParam.name) && Objects.equals(type, opParam.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, type, order);
        }
    }

    public String getMetaNodeName() { return metaNodeName; }
    public void setMetaNodeName(String name) { this.metaNodeName = name; }

    public void setAcSource(String name, String role, String mult) {
        acSourceInfo.put("name", name); acSourceInfo.put("role", role); acSourceInfo.put("mult", mult);
    }
    public void setAcTarget(String name, String role, String mult) {
        acTargetInfo.put("name", name); acTargetInfo.put("role", role); acTargetInfo.put("mult", mult);
    }
    public Map<String, Object> getAcSource() { return acSourceInfo; }
    public Map<String, Object> getAcTarget() { return acTargetInfo; }

    public List<String> getEnumLiterals() { return enumLiterals; }
    public void setEnumLiterals(List<String> literals) { this.enumLiterals = literals; }

    public static class OpState {
        public String body;
        public boolean isQuery; // true nếu là OCL Expression, false nếu là SOIL Statement
    }

}