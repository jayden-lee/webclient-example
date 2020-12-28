package com.jayden.webclient.example;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
public class TweetSlowController {

    @GetMapping("/slow-tweets")
    public List<Tweet> getSlowTweets() throws InterruptedException {
        Thread.sleep(1000L);
        return Arrays.asList(
            new Tweet("RestTemplate rules", "@user1"),
            new Tweet("WebClient is better", "@user2"),
            new Tweet("OK, both are useful", "@user1")
        );
    }
}
