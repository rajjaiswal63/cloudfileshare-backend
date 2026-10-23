package in.bushansirgur.cloudshareapi.service;

import in.bushansirgur.cloudshareapi.document.FileMetadataDocument;
import in.bushansirgur.cloudshareapi.document.ProfileDocument;
import in.bushansirgur.cloudshareapi.dto.FileMetadataDTO;
import in.bushansirgur.cloudshareapi.repository.FileMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileMetadataService {

    private final ProfileService profileService;
    private final UserCreditsService userCreditsService;
    private final FileMetadataRepository fileMetadataRepository;

    @Value("${file.upload.dir:upload}")
    private String uploadDir;

    public List<FileMetadataDTO> uploadFiles(MultipartFile files[]) throws IOException {
        ProfileDocument currentProfile = profileService.getCurrentProfile();
        List<FileMetadataDocument> savedFiles = new ArrayList<>();

        if (!userCreditsService.hasEnoughCredits(files.length)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not enough credits to upload files. Please purchase more credits.");
        }

        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);

        for (MultipartFile file : files) {
            String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
            String fileName = UUID.randomUUID() + (extension != null ? "." + extension : "");
            Path targetLocation = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            FileMetadataDocument fileMetadata = FileMetadataDocument.builder()
                    .fileLocation(targetLocation.toString())
                    .name(file.getOriginalFilename())
                    .size(file.getSize())
                    .type(file.getContentType())
                    .clerkId(currentProfile.getClerkId())
                    .isPublic(false)
                    .uploadedAt(LocalDateTime.now())
                    .build();

            userCreditsService.consumeCredit();
            savedFiles.add(fileMetadataRepository.save(fileMetadata));
        }

        return savedFiles.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public Path resolveFilePath(String fileLocation) {
        if (fileLocation == null || fileLocation.isBlank()) {
            return null;
        }

        Path directPath = Paths.get(fileLocation);
        if (Files.exists(directPath)) {
            return directPath;
        }

        // Fallback: Resolve filename against the configured upload directory
        // This handles cross-platform deployments or directory changes
        Path fileName = directPath.getFileName();
        if (fileName != null) {
            Path fallbackPath = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(fileName);
            if (Files.exists(fallbackPath)) {
                return fallbackPath;
            }
        }

        return directPath;
    }

    private FileMetadataDTO mapToDTO(FileMetadataDocument fileMetadataDocument) {
        Path resolved = resolveFilePath(fileMetadataDocument.getFileLocation());
        String location = resolved != null ? resolved.toString() : fileMetadataDocument.getFileLocation();

        return FileMetadataDTO.builder()
                .id(fileMetadataDocument.getId())
                .fileLocation(location)
                .name(fileMetadataDocument.getName())
                .size(fileMetadataDocument.getSize())
                .type(fileMetadataDocument.getType())
                .clerkId(fileMetadataDocument.getClerkId())
                .isPublic(fileMetadataDocument.getIsPublic())
                .uploadedAt(fileMetadataDocument.getUploadedAt())
                .build();
    }

    public List<FileMetadataDTO> getFiles() {
        ProfileDocument currentProfile = profileService.getCurrentProfile();
        List<FileMetadataDocument> files = fileMetadataRepository.findByClerkId(currentProfile.getClerkId());
        return files.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    public FileMetadataDTO getPublicFile(String id) {
        Optional<FileMetadataDocument> fileOptional = fileMetadataRepository.findById(id);
        if (fileOptional.isEmpty() || !Boolean.TRUE.equals(fileOptional.get().getIsPublic())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Public file not found or is private");
        }

        return mapToDTO(fileOptional.get());
    }

    public FileMetadataDTO getDownloadableFile(String id) {
        FileMetadataDocument file = fileMetadataRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));

        // If file is private, verify user authorization
        if (!Boolean.TRUE.equals(file.getIsPublic())) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access denied: file is private. Please sign in.");
            }
            if (!auth.getName().equals(file.getClerkId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: you do not have permission to download this file.");
            }
        }

        return mapToDTO(file);
    }

    public void deleteFile(String id) {
        ProfileDocument currentProfile = profileService.getCurrentProfile();
        FileMetadataDocument file = fileMetadataRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));

        if (!file.getClerkId().equals(currentProfile.getClerkId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "File does not belong to current user");
        }

        try {
            Path filePath = resolveFilePath(file.getFileLocation());
            if (filePath != null) {
                Files.deleteIfExists(filePath);
            }
        } catch (Exception e) {
            log.warn("Could not delete physical file for id {}: {}", id, e.getMessage());
        }

        fileMetadataRepository.deleteById(id);
    }

    public FileMetadataDTO togglePublic(String id) {
        ProfileDocument currentProfile = profileService.getCurrentProfile();
        FileMetadataDocument file = fileMetadataRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));

        if (!file.getClerkId().equals(currentProfile.getClerkId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "File does not belong to current user");
        }

        file.setIsPublic(!Boolean.TRUE.equals(file.getIsPublic()));
        fileMetadataRepository.save(file);
        return mapToDTO(file);
    }
}
