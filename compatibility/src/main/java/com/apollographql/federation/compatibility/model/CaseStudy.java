package com.apollographql.federation.compatibility.model;

public class CaseStudy {
    private final String caseNumber;
    private final String description;

    public CaseStudy(String caseNumber, String description) {
        this.caseNumber = caseNumber;
        this.description = description;
    }

    public String getCaseNumber() {
        return caseNumber;
    }

    public String getDescription() {
        return description;
    }
}
