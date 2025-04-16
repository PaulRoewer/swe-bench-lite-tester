package de.data_experts.swe_bench_lite_tester;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class TestPayload {

    @JsonProperty("instance_id")
    private String instance_id;

    @JsonProperty("repoDir")
    private String repoDir;

    @JsonProperty("FAIL_TO_PASS")
    private List<String> FAIL_TO_PASS;

    @JsonProperty("PASS_TO_PASS")
    private List<String> PASS_TO_PASS;

    public String getInstance_id() {
        return instance_id;
    }

    public String getRepoDir() {
        return repoDir;
    }

    public List<String> getFAIL_TO_PASS() {
        return FAIL_TO_PASS;
    }

    public List<String> getPASS_TO_PASS() {
        return PASS_TO_PASS;
    }
}
