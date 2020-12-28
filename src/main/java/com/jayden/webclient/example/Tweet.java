package com.jayden.webclient.example;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class Tweet {

    private String text;
    private String username;

    @Override
    public String toString() {
        return "Tweet{" +
            "text='" + text + '\'' +
            ", username='" + username + '\'' +
            '}';
    }
}
