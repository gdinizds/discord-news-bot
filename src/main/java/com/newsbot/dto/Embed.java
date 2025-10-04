package com.newsbot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Embed {

    @Size(max = 256, message = "Title must be at most 256 characters")
    private String title;

    @Size(max = 4096, message = "Description must be at most 4096 characters")
    private String description;

    private String url;
    private Integer color;

    @JsonProperty("timestamp")
    private String timestamp;
}
