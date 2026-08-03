package org.uet.dse.neo4j.model;

import org.uet.dse.neo4j.encoding.CanonicalGraphEncoding;

import java.util.*;

public class LinkState {
    public String assocName;      // Tên Association (M1)
    public String edgeLabel;      // LinkComposeOf, LinkAggregates, LinkAssociateWith
    public List<String> participants = new ArrayList<>(); // Danh sách tên các Object tham gia
    public List<List<String>> qualifierValues = new ArrayList<>(); // Qualifier expressions per association end
    public String linkObjectName; // Tên nếu là AssociationClass instance (LinkObject)
    public boolean isTernary;

    /**
     * Tạo khóa định danh duy nhất cho 1 Link dựa trên tên quan hệ và các đối tượng tham gia
     * Ví dụ: WorksFor_p1_c1
     */
    public String getIdentity() {
        return buildIdentity(assocName, participants, qualifierValues, linkObjectName);
    }

    public boolean isSameAs(LinkState other) {
        if (other == null) return false;
        return Objects.equals(this.assocName, other.assocName) &&
                Objects.equals(this.edgeLabel, other.edgeLabel) &&
                Objects.equals(this.participants, other.participants) &&
                Objects.equals(normalizedQualifiers(this.qualifierValues),
                        normalizedQualifiers(other.qualifierValues)) &&
                Objects.equals(this.linkObjectName, other.linkObjectName);
    }

    private static List<List<String>> normalizedQualifiers(List<List<String>> values) {
        if (values == null || values.stream().allMatch(value -> value == null || value.isEmpty())) {
            return List.of();
        }
        return values;
    }

    public static String buildIdentity(String assocName,
                                       List<String> participants,
                                       List<List<String>> qualifierValues,
                                       String linkObjectName) {
        if (linkObjectName != null) return linkObjectName;
        if (participants != null && participants.size() == 2) {
            List<List<String>> endQualifiers = new ArrayList<>();
            if (qualifierValues != null) {
                qualifierValues.forEach(values -> endQualifiers.add(
                        values == null ? List.of() : List.copyOf(values)));
            }
            while (endQualifiers.size() < 2) endQualifiers.add(List.of());
            return CanonicalGraphEncoding.binaryLinkIdentity(assocName,
                    participants.get(0), participants.get(1),
                    endQualifiers.get(0), endQualifiers.get(1));
        }
        StringBuilder sb = new StringBuilder(assocName != null ? assocName : "");
        for (String p : participants != null ? participants : List.<String>of()) {
            sb.append("_").append(escapeIdentityPart(p));
        }
        boolean hasQualifierValue = qualifierValues != null && qualifierValues.stream()
                .anyMatch(values -> values != null && !values.isEmpty());
        if (hasQualifierValue) {
            sb.append("@q");
            for (int i = 0; i < qualifierValues.size(); i++) {
                sb.append(i).append("=");
                List<String> endQualifiers = qualifierValues.get(i);
                if (endQualifiers != null && !endQualifiers.isEmpty()) {
                    for (int j = 0; j < endQualifiers.size(); j++) {
                        if (j > 0) {
                            sb.append(",");
                        }
                        sb.append(escapeIdentityPart(endQualifiers.get(j)));
                    }
                }
                sb.append(";");
            }
        }
        return sb.toString();
    }

    private static String escapeIdentityPart(String value) {
        if (value == null) {
            return "<null>";
        }
        return value
                .replace("\\", "\\\\")
                .replace("_", "\\_")
                .replace(",", "\\,")
                .replace(";", "\\;");
    }
}
