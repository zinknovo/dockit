package com.javaee.documentservice.client;

/**
 * doc-parser 调用异常
 */
public class DocParserException extends RuntimeException {

    public DocParserException(String message) {
        super(message);
    }

    public DocParserException(String message, Throwable cause) {
        super(message, cause);
    }
}
