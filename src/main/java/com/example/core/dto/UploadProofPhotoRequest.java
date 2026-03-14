package com.example.core.dto;

import com.example.core.model.ProofStage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UploadProofPhotoRequest {

    @NotNull
    private ProofStage stage;

    @NotBlank
    private String imageBase64;

    private String mimeType;

    private String note;
}
