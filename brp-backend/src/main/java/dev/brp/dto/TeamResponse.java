package dev.brp.dto;

import java.util.List;

/** Team API response DTO. */
public record TeamResponse(String id, String name, String tag, String source,
                           List<MemberResponse> members) {
    public record MemberResponse(String uuid, String role) {}
}
