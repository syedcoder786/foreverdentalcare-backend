package com.clinic.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Simple unauthenticated endpoint to check that the backend is up and reachable (e.g. after a deploy). */
@RestController
public class HelloController {

    @GetMapping(value = "/api/public/hello", produces = MediaType.TEXT_HTML_VALUE)
    public String hello() {
        return "<!doctype html><html><head><title>Hello World</title></head>"
                + "<body><h1>Hello World</h1><p>Forever Dental Care backend is running.</p></body></html>";
    }
}
