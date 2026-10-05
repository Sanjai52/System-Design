package com.crawler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "search")
public class SearchConfig {

    private double tfidfWeight = 1.0;
    private double pagerankWeight = 1.0;

    public double getTfidfWeight() { return tfidfWeight; }
    public void setTfidfWeight(double tfidfWeight) { this.tfidfWeight = tfidfWeight; }

    public double getPagerankWeight() { return pagerankWeight; }
    public void setPagerankWeight(double pagerankWeight) { this.pagerankWeight = pagerankWeight; }
}
