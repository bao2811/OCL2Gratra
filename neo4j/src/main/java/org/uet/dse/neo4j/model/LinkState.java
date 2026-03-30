package org.uet.dse.neo4j.model;

import java.util.*;

public class LinkState {
    public String assocName;      // Tên Association (M1)
    public String edgeLabel;      // LinkComposeOf, LinkAggregates, LinkAssociateWith
    public List<String> participants = new ArrayList<>(); // Danh sách tên các Object tham gia
    public String linkObjectName; // Tên nếu là AssociationClass instance (LinkObject)
    public boolean isTernary;

    /**
     * Tạo khóa định danh duy nhất cho 1 Link dựa trên tên quan hệ và các đối tượng tham gia
     * Ví dụ: WorksFor_p1_c1
     */
    public String getIdentity() {
        if (linkObjectName != null) return linkObjectName; // LinkObject dùng chính tên của nó làm ID
        StringBuilder sb = new StringBuilder(assocName);
        for (String p : participants) {
            sb.append("_").append(p);
        }
        return sb.toString();
    }

    public boolean isSameAs(LinkState other) {
        if (other == null) return false;
        return Objects.equals(this.assocName, other.assocName) &&
                Objects.equals(this.edgeLabel, other.edgeLabel) &&
                Objects.equals(this.participants, other.participants) &&
                Objects.equals(this.linkObjectName, other.linkObjectName);
    }
}