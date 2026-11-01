package com.motorbike.news;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class TopicNotFoundException extends RuntimeException {

    public TopicNotFoundException(String slug) {
        super("Topic non trovato: " + slug);
    }
}
