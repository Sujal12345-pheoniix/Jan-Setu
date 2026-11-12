package com.jansetu.modules.department.api;

import com.jansetu.modules.department.domain.Department;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class DepartmentDtos {

    private DepartmentDtos() {
    }

    public record DepartmentResponse(UUID id, String code, String name, String description, String contactEmail,
                                     String status, Instant createdAt, Instant updatedAt, long version) {
        public static DepartmentResponse from(Department d) {
            return new DepartmentResponse(d.getId(), d.getCode(), d.getName(), d.getDescription(), d.getContactEmail(),
                    d.getStatus().name(), d.getCreatedAt(), d.getUpdatedAt(), d.getVersion());
        }
    }

    public record CreateDepartmentRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{1,31}$",
                    message = "must be 2-32 letters, digits or underscores, starting with a letter") String code,
            @NotBlank @Size(min = 3, max = 150) String name,
            @Size(max = 1000) String description,
            @Email @Size(max = 254) String contactEmail) {
    }

    public record UpdateDepartmentRequest(
            @Size(min = 3, max = 150) String name,
            @Size(max = 1000) String description,
            @Email @Size(max = 254) String contactEmail,
            @NotNull Long version) {
    }
}
