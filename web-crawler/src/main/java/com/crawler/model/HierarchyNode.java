package com.crawler.model;

import java.util.ArrayList;
import java.util.List;

public class HierarchyNode {
    private String url;
    private CrawlStatus status;
    private int depth;
    private int discoveredLinks;
    private boolean alreadyVisited;
    private List<HierarchyNode> children = new ArrayList<>();

    public HierarchyNode() {}

    public HierarchyNode(String url, CrawlStatus status, int depth, int discoveredLinks) {
        this.url = url;
        this.status = status;
        this.depth = depth;
        this.discoveredLinks = discoveredLinks;
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public CrawlStatus getStatus() { return status; }
    public void setStatus(CrawlStatus status) { this.status = status; }

    public int getDepth() { return depth; }
    public void setDepth(int depth) { this.depth = depth; }

    public int getDiscoveredLinks() { return discoveredLinks; }
    public void setDiscoveredLinks(int discoveredLinks) { this.discoveredLinks = discoveredLinks; }

    public boolean isAlreadyVisited() { return alreadyVisited; }
    public void setAlreadyVisited(boolean alreadyVisited) { this.alreadyVisited = alreadyVisited; }

    public List<HierarchyNode> getChildren() { return children; }
    public void setChildren(List<HierarchyNode> children) {
        this.children = children != null ? children : new ArrayList<>();
    }
}
