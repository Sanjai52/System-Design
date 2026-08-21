package com.example.autocomplete.model;

public class SearchTerm {
    private String term;
    private long frequency;

    public SearchTerm() {
    }

    public SearchTerm(String term, long frequency) {
        this.term = term;
        this.frequency = frequency;
    }

    public String getTerm() {
        return term;
    }

    public void setTerm(String term) {
        this.term = term;
    }

    public long getFrequency() {
        return frequency;
    }

    public void setFrequency(long frequency) {
        this.frequency = frequency;
    }
}
