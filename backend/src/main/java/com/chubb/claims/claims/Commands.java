package com.chubb.claims.claims;

import java.time.LocalDate;
import jakarta.validation.constraints.*;

public final class Commands {
    private Commands() {}
    public enum Kind { MOTOR, PROPERTY }
    public enum Market { MY, SG, TH, ID, PH, HK }
    public enum Scope { ALL, MINE, UNASSIGNED }
    public record Submit(@NotNull Kind kind, @NotNull Market market, @NotNull LocalDate incidentOn,
        @NotBlank @Size(max=2000) String description) {}
    public record Version(@NotNull @Min(1) Long expectedVersion) {}
    public record Ask(@NotNull @Min(1) Long expectedVersion, @NotBlank @Size(max=2000) String question) {}
    public record Answer(@NotNull @Min(1) Long expectedVersion, @NotBlank @Size(max=2000) String answer) {}
    public record Money(@NotNull @Min(1) Long expectedVersion,
        @NotNull @Pattern(regexp="^(0|[1-9][0-9]{0,15})\\.[0-9]{2}$") String amount) {}
    public record Reject(@NotNull @Min(1) Long expectedVersion, @NotBlank @Size(max=2000) String reason) {}
}
