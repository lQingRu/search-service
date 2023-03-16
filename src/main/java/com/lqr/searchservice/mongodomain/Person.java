package com.lqr.searchservice.mongodomain;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder(toBuilder = true)
@Getter
public class Person {

    private String id;
    private String name;
    private List<String> hashTags;
    private String description;

}
