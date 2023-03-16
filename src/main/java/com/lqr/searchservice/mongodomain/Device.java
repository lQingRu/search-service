package com.lqr.searchservice.mongodomain;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder(toBuilder = true)
public class Device {

    private String type;
    private String brand;
    private String os;
    private List<String> osVersions;
    private List<Application> applications;
    private String personId;
}
