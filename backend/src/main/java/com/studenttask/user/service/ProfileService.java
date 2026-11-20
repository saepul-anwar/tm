package com.studenttask.user.service;

import com.studenttask.auth.service.AuthService;
import com.studenttask.auth.validation.StrongPassword;
import com.studenttask.auth.validation.StrongPasswordValidator;
import com.studenttask.common.api.FieldError;
import com.studenttask.common.error.AppException;
import com.studenttask.common.error.ErrorCode;
import com.studenttask.user.dto.ProfileResponse;
import com.studenttask.user.dto.UpdateProfileRequest;
import com.studenttask.user.entity.User;
import com.studenttask.user.mapper.ProfileMapper;
import com.studenttask.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Not {@code @Transactional}: BCrypt is deliberately slow, so verification and hashing run outside any
 * transaction; only the final write (and the refresh-token revocation that must accompany a password change)
 * runs in one short transaction via {@link TransactionTemplate}.
 *
 * <p>A wrong {@code currentPassword} is a 400 field error, never 401: the frontend treats 401 as session expiry.
 * Access tokens already issued stay valid until they expire (15 minutes); only refresh tokens are revoked.
 */
@Service
public class ProfileService {

    static final int MAX_NAME_LENGTH = 100;
    private static final int BCRYPT_MAX_BYTES = StrongPasswordValidator.MAX_BYTES;
    private static final String DUMMY_PASSWORD = "dummy-password-for-timing-equalisation";
    private static final String CURRENT_PASSWORD = "currentPassword";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final ProfileMapper mapper;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ProfileService(UserRepository users, PasswordEncoder passwordEncoder, AuthService authService,
            ProfileMapper mapper, TransactionTemplate tx, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.mapper = mapper;
        this.tx = tx;
        this.clock = clock;
    }

    public ProfileResponse get(long userId) {
        return mapper.toProfile(load(userId));
    }

    public ProfileResponse update(long userId, UpdateProfileRequest request) {
        User current = load(userId);
        String fullName = request.fullName() == null ? current.getFullName() : request.fullName().trim();
        String email = request.email() == null ? current.getEmail() : normalizeEmail(request.email());
        String timezone = request.timezone() == null ? current.getTimezone() : request.timezone();
        boolean emailChanges = !email.equals(current.getEmail());
        boolean passwordChanges = request.newPassword() != null;

        List<FieldError> errors = new ArrayList<>();
        validateName(fullName, errors);
        validateTimezone(timezone, errors);
        if (passwordChanges && !StrongPasswordValidator.isStrong(request.newPassword())) {
            errors.add(new FieldError("newPassword", StrongPassword.DEFAULT_MESSAGE));
        }
        if ((emailChanges || passwordChanges) && !currentPasswordMatches(current, request.currentPassword(), errors)) {
            throw validation(errors);
        }
        if (!errors.isEmpty()) {
            throw validation(errors);
        }

        String newHash = passwordChanges ? passwordEncoder.encode(request.newPassword()) : null;
        String verifiedHash = current.getPasswordHash();
        return tx.execute(status -> {
            User user = load(userId);
            if (passwordChanges && !user.getPasswordHash().equals(verifiedHash)) {
                throw new AppException(ErrorCode.CONFLICT, "Data telah diubah di tempat lain. Muat ulang lalu coba lagi.");
            }
            if (emailChanges && users.existsByEmailAndIdNot(email, userId)) {
                throw emailTaken();
            }
            user.changeProfile(fullName, email, timezone, clock.instant());
            if (passwordChanges) {
                user.changePasswordHash(newHash, clock.instant());
            }
            try {
                users.saveAndFlush(user);
            } catch (DataIntegrityViolationException e) {
                throw emailTaken(); // concurrent use of the same email
            }
            if (passwordChanges) {
                authService.revokeAll(userId); // joins this transaction: hash change and revocation are atomic
            }
            return mapper.toProfile(user);
        });
    }

    /**
     * Verifies with BCrypt in every case where it is required (a missing or oversized password still costs one
     * check against a dummy hash). Adds a generic field error on failure; never reveals which part was wrong.
     */
    private boolean currentPasswordMatches(User user, String supplied, List<FieldError> errors) {
        if (supplied == null || supplied.isEmpty()) {
            errors.add(new FieldError(CURRENT_PASSWORD, "Kata sandi saat ini wajib diisi."));
            return false;
        }
        boolean fits = supplied.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_BYTES;
        boolean ok = passwordEncoder.matches(fits ? supplied : DUMMY_PASSWORD, user.getPasswordHash()) && fits;
        if (!ok) {
            errors.add(new FieldError(CURRENT_PASSWORD, "Kata sandi saat ini salah."));
        }
        return ok;
    }

    private static void validateName(String fullName, List<FieldError> errors) {
        if (fullName.isEmpty() || fullName.length() > MAX_NAME_LENGTH) {
            errors.add(new FieldError("fullName", "Nama lengkap wajib diisi dan maksimal 100 karakter."));
        }
    }

    /** Region ids only (e.g. Asia/Jakarta); offsets such as "+07:00" and unknown or differently-cased ids fail. */
    private static void validateTimezone(String timezone, List<FieldError> errors) {
        if (!ZoneId.getAvailableZoneIds().contains(timezone)) {
            errors.add(new FieldError("timezone", "Zona waktu tidak valid. Gunakan ID IANA, mis. Asia/Jakarta."));
        }
    }

    private User load(long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED, "Autentikasi diperlukan."));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static AppException validation(List<FieldError> errors) {
        return new AppException(ErrorCode.VALIDATION_ERROR, "Data yang dikirim tidak valid.", errors);
    }

    private static AppException emailTaken() {
        return new AppException(ErrorCode.CONFLICT, "Email sudah terdaftar.");
    }
}
