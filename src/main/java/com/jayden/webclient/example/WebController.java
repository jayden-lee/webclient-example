package com.jayden.webclient.example;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@RestController
public class WebController {

    private static final String SLOW_TWEETS_URI = "http://localhost:8080/slow-tweets";

    @GetMapping("/tweets-blocking")
    public List<Tweet> getTweetsBlocking() {
        log.info("Starting BLOCKING Controller!");

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<List<Tweet>> response = restTemplate.exchange(
            SLOW_TWEETS_URI, HttpMethod.GET, null,
            new ParameterizedTypeReference<List<Tweet>>(){});

        List<Tweet> result = response.getBody();
        result.forEach(tweet -> log.info(tweet.toString()));

        log.info("Exiting BLOCKING Controller!");

        return result;
    }

    @GetMapping(value = "/tweets-non-blocking", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Tweet> getTweetsNonBlocking() {
        log.info("Starting NON-BLOCKING Controller!");

        Flux<Tweet> tweetFlux = WebClient.create()
            .get()
            .uri(SLOW_TWEETS_URI)
            .retrieve()
            .bodyToFlux(Tweet.class);

        tweetFlux.subscribe(tweet -> log.info(tweet.toString()));

        log.info("Exiting NON-BLOCKING Controller!");

        return tweetFlux;
    }
}
