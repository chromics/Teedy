package com.sismics.docs.core.service;

import com.sismics.docs.core.constant.Constants;
import com.sismics.docs.core.dao.UserDao;
import com.sismics.docs.core.dao.UserRequestDao;
import com.sismics.docs.core.model.jpa.UserRequest;
import com.sismics.docs.core.model.jpa.User;
import com.sismics.util.context.ThreadLocalContext;

import jakarta.persistence.EntityManager;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.google.common.base.Strings;

/**
 * Service for managing user registration requests.
 */
public class UserRequestService {

    private final UserRequestDao userRequestDao;
    private final UserDao userDao;
    private static final Logger log = LoggerFactory.getLogger(UserRequestService.class);

    public UserRequestService() {
        this.userRequestDao = new UserRequestDao();
        this.userDao = new UserDao();
    }

    /**
     * Creates a new user request with validations and hashed password.
     *
     * @param request UserRequest object to create (must have username, email, password)
     * @return persisted UserRequest with status set to PENDING
     * @throws IllegalArgumentException if username, email, or password invalid
     */
    public UserRequest createUserRequest(UserRequest request) {
        // Add debug logging
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        try {
            boolean isActive = em.getTransaction().isActive();
            System.out.println("Transaction active at beginning of UserRequestService.createUserRequest: " + isActive);
            if (isActive) {
                System.out.println("Transaction already active in service - Stack trace:");
                new Exception().printStackTrace();
            }
        } catch (Exception e) {
            System.err.println("Error checking transaction in UserRequestService: " + e.getMessage());
        }

        if (request == null) {
            throw new IllegalArgumentException("UserRequest cannot be null.");
        }

        // Validate and trim username
        String username = request.getUsername();
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required and cannot be empty.");
        }
        username = username.trim();
        request.setUsername(username);

        // Validate email format
        String email = request.getEmail();
        if (email == null || !email.matches("^[\\w\\.-]+@[\\w\\.-]+\\.[a-zA-Z]{2,}$")) {
            throw new IllegalArgumentException("Invalid email format.");
        }

        // Validate password and hash it (maybe hash during user creation instead?)
        String plainPassword = request.getPassword();
        if (plainPassword == null || plainPassword.length() < 8) {
            throw new IllegalArgumentException("Password must be between 8 and 50 characters");
        }
        if (plainPassword.length() > 50) {
            throw new IllegalArgumentException("Password must be between 8 and 50 characters");
        }
        request.setPassword(plainPassword);

        // Check transaction status before calling DAO
        try {
            boolean isActiveBeforeDao = em.getTransaction().isActive();
            System.out.println("Transaction active just before calling userRequestDao.create: " + isActiveBeforeDao);
        } catch (Exception e) {
            System.err.println("Error checking transaction before calling DAO: " + e.getMessage());
        }

        // Save the request (without transaction management)
        return userRequestDao.create(request);
    }

    /**
     * Hashes the password using bcrypt with configurable work factor.
     *
     * @param password Plain password
     * @return bcrypt hashed password string
     */
    public String hashPassword(String password) {
        int bcryptWork = Constants.DEFAULT_BCRYPT_WORK;
        String envBcryptWork = System.getenv(Constants.BCRYPT_WORK_ENV);
        if (!Strings.isNullOrEmpty(envBcryptWork)) {
            try {
                int envWork = Integer.parseInt(envBcryptWork);
                if (envWork >= 4 && envWork <= 31) {
                    bcryptWork = envWork;
                } else {
                    log.warn("{} must be between 4 and 31 inclusive. Using default {}.", Constants.BCRYPT_WORK_ENV, Constants.DEFAULT_BCRYPT_WORK);
                }
            } catch (NumberFormatException e) {
                log.warn("{} must be a valid integer between 4 and 31. Using default {}.", Constants.BCRYPT_WORK_ENV, Constants.DEFAULT_BCRYPT_WORK);
            }
        }
        return BCrypt.withDefaults().hashToString(bcryptWork, password.toCharArray());
    }

    /**
     * Approves a pending user request by ID and creates the corresponding User.
     *
     * @param id UserRequest ID to approve
     * @param processedById ID of the admin processing the request
     * @return updated UserRequest with status APPROVED
     * @throws IllegalArgumentException if the request ID or admin ID is invalid
     * @throws IllegalStateException if the request is not found, not pending, or username/email already exists
     * @throws RuntimeException for underlying persistence or server errors
     */
    public UserRequest approveUserRequest(String id, String processedById) throws Exception {
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        try {
            log.info("Transaction active at beginning of UserRequestService.approveUserRequest: {}", em.getTransaction().isActive());
        } catch (Exception e) {
            log.error("Error checking transaction in UserRequestService.approveUserRequest: {}", e.getMessage(), e);
        }

        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("UserRequest ID cannot be null or empty");
        }
        if (processedById == null || processedById.trim().isEmpty()) {
            throw new IllegalArgumentException("Admin ID cannot be null or empty");
        }

        UserRequest request = userRequestDao.getById(id);
        if (request == null) {
            log.warn("UserRequest with ID {} not found", id);
            throw new IllegalStateException("User request with ID " + id + " not found");
        }
        if (!Constants.USER_REQUEST_STATUS_PENDING.equals(request.getStatus())) {
            log.warn("UserRequest with ID {} is not pending and cannot be approved", id);
            throw new IllegalStateException("User request with ID " + id + " is not in pending state");
        }

        // Check for username collision
        if (userDao.getActiveByUsername(request.getUsername()) != null) {
            log.warn("Username already exists: {}", request.getUsername());
            throw new IllegalStateException("Username already exists: " + request.getUsername());
        }

        // Check for email collision
        if (userDao.getActiveByEmail(request.getEmail()) != null) {
            log.warn("Email already exists: {}", request.getEmail());
            throw new IllegalStateException("Email already exists: " + request.getEmail());
        }

        User newUser = new User();
        newUser.setRoleId(Constants.DEFAULT_USER_ROLE);
        newUser.setUsername(request.getUsername());
        newUser.setEmail(request.getEmail());
        newUser.setPassword(request.getPassword());
        newUser.setCreateDate(new Date());
        newUser.setStorageQuota(100 * 1024 * 1024L);
        newUser.setOnboarding(true);

        try {
            // Ensure transaction is active
            if (!em.getTransaction().isActive()) {
                log.info("Beginning transaction for user creation and request update");
                em.getTransaction().begin();
            }
            userDao.create(newUser, processedById);
            request.setStatus(Constants.USER_REQUEST_STATUS_APPROVED);
            UserRequest updatedRequest = userRequestDao.update(request);
            em.getTransaction().commit();
            log.info("Successfully approved user request with ID: {}", id);
            return updatedRequest;
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                log.warn("Rolling back transaction for user request ID: {}", id);
                em.getTransaction().rollback();
            }
            log.error("Failed to create user or update request for ID {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to create user or update request: " + e.getMessage(), e);
        }
    }

    /**
     * Rejects a pending user request by ID.
     *
     * @param id UserRequest ID to reject
     * @return updated UserRequest with status REJECTED, or null if not found or not pending
     */
    public String rejectUserRequest(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("UserRequest ID cannot be null or empty.");
        }

        UserRequest request = userRequestDao.getById(id);
        if (request == null) {
            log.info("UserRequest with ID {} not found.", id);
            return null;
        }

        if (!Constants.USER_REQUEST_STATUS_PENDING.equals(request.getStatus())) {
            log.info("UserRequest with ID {} is not pending and cannot be rejected.", id);
            return null;
        }

        deleteUserRequest(id); // This calls userRequestDao.delete(id)
        return id;
    }


    /**
     * Returns a list of all pending user requests.
     *
     * @return List of UserRequest objects with status PENDING
     */
    public List<UserRequest> getPendingRequests() {
        // Add debug logging
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        try {
            boolean isActive = em.getTransaction().isActive();
            System.out.println("Transaction active at beginning of UserRequestService.getPendingRequests: " + isActive);
        } catch (Exception e) {
            System.err.println("Error checking transaction in UserRequestService.getPendingRequests: " + e.getMessage());
        }

        return userRequestDao.findAllPending();
    }

    /**
     * Retrieves a user request by ID.
     *
     * @param id UserRequest ID
     * @return UserRequest object or null if not found
     */
    public UserRequest getRequestById(String id) {
        // Add debug logging
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        try {
            boolean isActive = em.getTransaction().isActive();
            System.out.println("Transaction active at beginning of UserRequestService.getRequestById: " + isActive);
        } catch (Exception e) {
            System.err.println("Error checking transaction in UserRequestService.getRequestById: " + e.getMessage());
        }

        if (id == null || id.trim().isEmpty()) {
            return null;
        }
        return userRequestDao.getById(id);
    }

    /**
     * Deletes a user request by ID.
     *
     * @param id UserRequest ID to delete
     */
    public void deleteUserRequest(String id) {
        // Add debug logging
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        try {
            boolean isActive = em.getTransaction().isActive();
            System.out.println("Transaction active at beginning of UserRequestService.deleteUserRequest: " + isActive);
        } catch (Exception e) {
            System.err.println("Error checking transaction in UserRequestService.deleteUserRequest: " + e.getMessage());
        }

        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("UserRequest ID cannot be null or empty.");
        }
        userRequestDao.delete(id);
    }

    /**
     * Checks if a username is already in use (either by an active user or pending request).
     *
     * @param username Username to check
     * @return true if username is available, false if already in use
     */
    public boolean isUsernameAvailable(String username) {
        // Add debug logging
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        try {
            boolean isActive = em.getTransaction().isActive();
            System.out.println("Transaction active at beginning of UserRequestService.isUsernameAvailable: " + isActive);
        } catch (Exception e) {
            System.err.println("Error checking transaction in UserRequestService.isUsernameAvailable: " + e.getMessage());
        }

        if (username == null || username.trim().isEmpty()) {
            return false;
        }

        // Check active users
        if (userDao.getActiveByUsername(username) != null) {
            return false;
        }

        // Also check pending user requests with this username
        // This would require a new method in UserRequestDao
        // For now, we'll return true
        return true;
    }

    /**
     * Checks if an email is already in use (either by an active user or pending request).
     *
     * @param email Email to check
     * @return true if email is available, false if already in use
     */
    public boolean isEmailAvailable(String email) {
        // Add debug logging
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        try {
            boolean isActive = em.getTransaction().isActive();
            System.out.println("Transaction active at beginning of UserRequestService.isEmailAvailable: " + isActive);
        } catch (Exception e) {
            System.err.println("Error checking transaction in UserRequestService.isEmailAvailable: " + e.getMessage());
        }

        if (email == null || email.trim().isEmpty()) {
            return false;
        }

        // Check active users
        if (userDao.getActiveByEmail(email) != null) {
            return false;
        }

        // Also check pending user requests with this email
        // This would require a new method in UserRequestDao
        // For now, we'll return true
        return true;
    }

    /**
     * Counts the number of pending user requests.
     *
     * @return count of pending requests
     */
    public long countPendingRequests() {
        // Add debug logging
        EntityManager em = ThreadLocalContext.get().getEntityManager();
        try {
            boolean isActive = em.getTransaction().isActive();
            System.out.println("Transaction active at beginning of UserRequestService.countPendingRequests: " + isActive);
        } catch (Exception e) {
            System.err.println("Error checking transaction in UserRequestService.countPendingRequests: " + e.getMessage());
        }

        List<UserRequest> pendingRequests = userRequestDao.findAllPending();
        return pendingRequests != null ? pendingRequests.size() : 0;
    }
}