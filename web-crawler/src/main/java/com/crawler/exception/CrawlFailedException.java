package com.crawler.exception;

public class CrawlFailedException extends RuntimeException {

    public CrawlFailedException(String message) {
        super(message);
    }

    public CrawlFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
